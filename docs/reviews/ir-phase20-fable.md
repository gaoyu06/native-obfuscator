# Fable review: IR phase 20 — LDIV, LREM, LNEG (PR #134)

- **Reviewed tip:** `origin/cursor/ir-compiler-phase20-6d81` at
  `257b153` (`compiler: IR phase 20 LDIV, LREM, and LNEG`)
- **Base:** `master` at `76ebeddb005e01033523384275c8c0c1641ada81`
- **Verdict: accept with nits.** No compiler correctness bug found; no code
  change was needed on this review branch. The nits below are
  documentation/test-shape observations only.

## Scope of the change

PR #134 admits the remaining trapping/wrapping JVM long arithmetic into the
opt-in direct IR compiler (`--codegen=ir`):

- `LDIV`/`LREM` via a new dedicated `IrNodes.LongDivRem` node (modeled on the
  i32 `IntDivRem`), carrying `bytecodeOffset` and `sourceLine`.
- `LNEG` via a new dedicated `IrNodes.LongUnary` node (i64-typed `NEGATE`).
- `AsmToIr` gains admission, abstract-stack typing, and lowering;
  `CfgBuilder.mayThrow` gains `LDIV`/`LREM`; `IrCppEmitter` gains
  `emitLongDivRem` and `emitLongUnary`; `IrMethod` gains pretty-printing.
- Three new focused tests, corpus/smoke additions, and the retargeting of the
  `unsupportedWide` rejection fixture from `LDIV` (now supported) to `LCMP`
  (still unsupported).

Touched files: `IrNodes.java`, `AsmToIr.java`, `CfgBuilder.java`,
`IrCppEmitter.java`, `IrMethod.java`, `IrCompilerTest.java`, plus
`PR_BODY.md` and `docs/architecture/ir-phase20-status.md`. `Main.java`,
`NativeObfuscator.java`, the interpreter, the evaluator, and `--ir-lower`
are **not** touched — confirmed by `git diff origin/master...HEAD
--name-only`.

## Checklist findings

### 1. `LongDivRem` / `LongUnary` node design — verified

`LongDivRem` is a dedicated node with `DIVIDE`/`REMAINDER` operations; its
constructor enforces `requireI64` on result, left, and right, and it keeps
`bytecodeOffset` + `sourceLine` for the exceptional exit. `LongUnary` is a
dedicated i64 `NEGATE` node with `requireI64` on result and operand.
`LongBinary` remains exactly `ADD`/`SUBTRACT`/`MULTIPLY`/`AND`/`OR`/`XOR` —
no `DIVIDE`/`REMAINDER` was added to the exception-free wrapping node, so
the "may throw" property stays encoded in the node type.

### 2. `AsmToIr` stack order — verified

For `LDIV`/`LREM` the lowering pops the right operand (top of stack) first,
then the left, both checked `I64`, and pushes an `I64` result. The abstract
stack pre-pass (`applyStackTyping`) performs the same two `I64` pops and one
`I64` push, and `LNEG` pops one `I64` and pushes `I64`. The focused test
`lowersLongDivideAndRemainderWithoutCppUndefinedBehavior` asserts the
`I64`/`I64`/`I64` shape on both nodes, and the emitted smoke source shows
`(int64_t) arg0 / (int64_t) arg1` — dividend `arg0` on the left, so the
operand order survives to codegen.

### 3. `CfgBuilder.mayThrow` — verified

The diff adds exactly `opcode == Opcodes.LDIV || opcode == Opcodes.LREM` to
`mayThrow`. `LNEG` is not listed, which is correct: JVMS `lneg` cannot throw
(negation of `Long.MIN_VALUE` wraps, it does not trap). The
`longDivideByZeroInsideTryUsesSharedCatchDispatch` test confirms a `try`
around `LDIV` produces the `java/lang/ArithmeticException` exception edge and
the shared `IR_CATCH_0` dispatch.

### 4. `emitLongDivRem` vs `emitIntDivRem` — verified

`emitLongDivRem` is a faithful i64 mirror of `emitIntDivRem`:

1. **Zero divisor:** `if (right == 0LL)` calls `utils::throw_re` with
   `java/lang/ArithmeticException` and the message `LDIV / by 0` /
   `LREM % by 0` (pool offsets, with the node's `sourceLine`), then appends
   the existing `exceptionalExit(method, block)` — same shape as the int
   path.
2. **Overflow guard:** `if (left == ((jlong) 0x8000000000000000ULL) &&
   right == -1LL)` assigns `Long.MIN_VALUE` for `DIVIDE` and `0LL` for
   `REMAINDER`. `CppAst.LongLiteral` renders `Long.MIN_VALUE` as
   `((jlong) 0x8000000000000000ULL)`, avoiding the unrepresentable
   `-9223372036854775808LL` literal.
3. **Ordinary path:** the `else` branch performs
   `(jlong) ((int64_t) left / (int64_t) right)` or `%`. Because the guard is
   an if/else, the signed division is never evaluated for
   `Long.MIN_VALUE / -1`, so no signed C++ overflow (UB) is reachable.

### 5. `emitLongUnary` — verified

`NEGATE` emits `(jlong) (-(uint64_t) operand)`: unsigned negation is
well-defined modular arithmetic, so `Long.MIN_VALUE` wraps to itself as JVMS
`lneg` requires, with no signed-overflow UB.

### 6. Differential check of the emitted patterns

I compiled the exact emitted patterns with
`g++ 13.3.0 -std=c++17 -O2 -fsanitize=undefined` and compared against Java
semantics; all cases matched and UBSan reported nothing:

| Case | Java value | Emitted-pattern C++ value |
| --- | --- | --- |
| `Long.MIN_VALUE / -1` | `Long.MIN_VALUE` | `Long.MIN_VALUE` |
| `Long.MIN_VALUE % -1` | `0` | `0` |
| `7 / 2`, `7 % 2` | `3`, `1` | `3`, `1` |
| `-7 / 2`, `-7 % 2` | `-3`, `-1` | `-3`, `-1` |
| `7 / -2`, `7 % -2` | `-3`, `1` | `-3`, `1` |
| `-7 / -2`, `-7 % -2` | `3`, `-1` | `3`, `-1` |
| `Long.MIN_VALUE / 1` | `Long.MIN_VALUE` | `Long.MIN_VALUE` |
| `-Long.MIN_VALUE` (`lneg`) | `Long.MIN_VALUE` | `Long.MIN_VALUE` |

C++ `/` and `%` on `int64_t` truncate toward zero, which is exactly JVMS
`ldiv`/`lrem` semantics, so no extra sign fixup is needed.

### 7. Tests and default mode — verified

The three new focused tests plus the corpus/smoke additions are present in
`IrCompilerTest`. `CodegenMode` still declares `LEGACY, IR` and `Main.java`
is untouched, so `--codegen` still defaults to `legacy`; `CodegenModeTest`
(5 tests) passes. The previously-rejecting `unsupportedWide` fixture was
correctly retargeted to `LCMP` now that `LDIV` is admitted.

## Test results (run by this reviewer at the reviewed tip)

```text
CC=gcc CXX=g++ ./gradlew :obfuscator:test \
  --tests by.radioegor146.ir.IrCompilerTest \
  --tests by.radioegor146.CodegenModeTest
```

Result on 2026-08-29 (OpenJDK 21.0.10, gcc/g++ 13.3.0, Linux):
`BUILD SUCCESSFUL`. Gradle JUnit XML:

```text
IrCompilerTest:  tests=97, skipped=0, failures=0, errors=0
CodegenModeTest: tests=5,  skipped=0, failures=0, errors=0
Total: 102 tests, 0 skipped, 0 failures, 0 errors
```

This matches the counts claimed in `PR_BODY.md` and
`docs/architecture/ir-phase20-status.md` exactly.

## Nits (non-blocking, docs/test-shape only)

1. **`(jlong)` narrowing from `uint64_t` above `INT64_MAX`** in the `lneg`
   emission is implementation-defined pre-C++20 (well-defined modular
   conversion in C++20). This is the same carrier pattern already accepted
   for `LongBinary`/`LongShift`, and the differential check confirms the
   expected wrap on this toolchain, so it is a consistency observation, not
   a defect.
2. **Weak substring assertion.** `assertTrue(cpp.contains("== 0"))` in the
   div/rem test also matches `== 0LL` by prefix; asserting the full `== 0LL`
   spelling (as the guard actually renders) would be marginally stronger.
   Harmless as-is.
3. **`LongUnary` has no `sourceLine`.** Correct, since `lneg` cannot reach
   an exceptional exit; noted only because `LongDivRem` carries one and a
   future reader might wonder about the asymmetry.

## Ship-readiness / 交付准备度

- **(a) Scope / 范围:** Independent review of the phase-20 compiler
  increment: typed-CFG admission and structured C++ lowering of `LDIV`,
  `LREM`, and `LNEG` behind opt-in `--codegen=ir`. /
  对第 20 阶段编译器增量的独立审查：在可选 `--codegen=ir` 之后对
  `LDIV`、`LREM`、`LNEG` 的 typed-CFG 接纳与结构化 C++ 下降。
- **(b) Ship-ready? / 可直接发布？:** **No.** This is one compiler
  increment; the production goal is incomplete. /
  **否。** 这只是一个编译器增量；生产目标尚未完成。
- **(c) Review focus / 审查重点:** The zero-divisor exceptional path, the
  `Long.MIN_VALUE / -1` guard, the `LongDivRem`-vs-`LongBinary` node split,
  and the `CfgBuilder.mayThrow` addition — all verified above. /
  除零异常路径、`Long.MIN_VALUE / -1` 保护、`LongDivRem` 与 `LongBinary`
  的节点拆分，以及 `CfgBuilder.mayThrow` 的新增 —— 均已在上文核实。
- **(d) Integration / 集成:** Keep `--codegen` defaulting to `legacy` with
  per-method fallback; do not combine with evaluator, interpreter, or
  `--ir-lower` work. No JDK support badge is claimed. /
  保持 `--codegen` 默认 `legacy` 并保留逐方法 fallback；不要与 evaluator、
  interpreter 或 `--ir-lower` 工作合并。不声称任何 JDK 支持徽章。

## Boundaries respected

- No compiler code was changed on this review branch; the verdict required
  none.
- This review does not claim JDK 17/21/25 corpus support and reports no
  benchmark numbers.
- The `--codegen` default stays `legacy`; nothing here flips it.
- No merge action is taken or recommended for PR #134.
