# IR phase 20 status

Phase 20 extends the opt-in direct Java bytecode → typed CFG IR → C++/JNI
compiler with the remaining JVM long arithmetic that can trap or wrap:
`LDIV`, `LREM`, and `LNEG`. The base is current `origin/master` at
`76ebeddb005e01033523384275c8c0c1641ada81`, including phase 19 and the
interpreter ISA v2 landing.

`LDIV` and `LREM` are lowered through a dedicated `LongDivRem` node modeled on
the existing i32 `IntDivRem`: result, left, and right are all `I64`, and the
operation is `DIVIDE`/`REMAINDER`. The node keeps `bytecodeOffset` and
`sourceLine` so exceptional exits carry a line number. It is intentionally
separate from `LongBinary`, which is exception-free wrapping `uint64_t`
add/sub/mul/and/or/xor. `CfgBuilder.mayThrow` now includes `LDIV`/`LREM` so a
zero divisor inside a `try` produces the same shared catch-dispatch exception
edges as `IDIV`/`IREM`.

`LNEG` is lowered through a dedicated `LongUnary` node (i64-typed negate) rather
than overloading the i32 `Unary`, so the type contract is enforced by
construction. It does not throw.

C++ emission for `LongDivRem` mirrors `emitIntDivRem`:

1. If the divisor is `0`, call `utils::throw_re` with
   `java/lang/ArithmeticException` and the messages `LDIV / by 0` /
   `LREM % by 0`, then take the existing `exceptionalExit` path.
2. If the dividend is `Long.MIN_VALUE` and the divisor is `-1`, produce
   `Long.MIN_VALUE` for `LDIV` and `0` for `LREM`, avoiding signed C++
   overflow.
3. Otherwise perform a signed `int64_t` `/` or `%` (truncation toward zero)
   and assign back through the `jlong` carrier.

`LongUnary` negates on the unsigned `uint64_t` carrier and assigns back to
`jlong`, so negating `Long.MIN_VALUE` wraps to `Long.MIN_VALUE` exactly as the
JVM specifies.

The CLI and API default remains `legacy`. Unsupported methods retain
per-method legacy fallback. This increment does not change the evaluator,
reader, `--ir-lower`, interpreter, classfile version handling, or constructor
restoration.

## Verification

The focused compiler and mode suites are run with:

```text
CC=gcc CXX=g++ ./gradlew :obfuscator:test \
  --tests by.radioegor146.ir.IrCompilerTest \
  --tests by.radioegor146.CodegenModeTest
```

Result on 2026-08-29: `BUILD SUCCESSFUL`. Gradle's JUnit XML reports:

```text
IrCompilerTest: tests=97, skipped=0, failures=0, errors=0
CodegenModeTest: tests=5, skipped=0, failures=0, errors=0
Total: 102 tests, 0 skipped, 0 failures, 0 errors
```

The phase-20 tests cover the frontend `LongDivRem` `I64`/`I64`/`I64` and
`LongUnary` `I64` shapes, the emitted zero check, the `Long.MIN_VALUE`/`-1`
guard, the signed `int64_t` divide and remainder, the `Long.MIN_VALUE` and `0`
overflow results, the wrapping `LNEG` through the unsigned carrier, and a
`try`/`catch` around `LDIV` that still builds the shared exception-edge
dispatch. Every generated method is marked with a `// IR codegen:` admission
comment. The generated C++ smoke translation unit includes the new operations
and passed its `g++ -std=c++17 -fsyntax-only` gate.

## Ship-readiness / 交付准备度

- **(a) Scope / 范围:** Direct typed-CFG IR support for `LDIV`, `LREM`, and
  `LNEG`, including the zero-divisor exceptional edge, the
  `Long.MIN_VALUE / -1` wrap, and wrapping long negation. /
  直接 typed-CFG IR 支持 `LDIV`、`LREM` 和 `LNEG`，包括除零异常边、
  `Long.MIN_VALUE / -1` 回绕，以及 long 取负的回绕语义。
- **(b) Ship-ready? / 可直接发布？:** **No.** /
  **否。**
- **(c) Review focus / 审查重点:** Check the zero-divisor exceptional path,
  the `Long.MIN_VALUE`/`-1` guard, the split of `LongDivRem` from the
  exception-free `LongBinary`, and the `CfgBuilder.mayThrow` addition of
  `LDIV`/`LREM`. /
  请重点审查除零异常路径、`Long.MIN_VALUE`/`-1` 保护、`LongDivRem` 与
  无异常的 `LongBinary` 的节点拆分，以及 `CfgBuilder.mayThrow` 对
  `LDIV`/`LREM` 的加入。
- **(d) Integration / 集成:** Keep `--codegen` defaulting to `legacy` and
  retain per-method fallback; do not combine this increment with evaluator,
  interpreter, or `--ir-lower` changes. The production goal is incomplete. /
  保持 `--codegen` 默认值为 `legacy` 并保留逐方法 fallback；不要将本增量
  与 evaluator、interpreter 或 `--ir-lower` 变更合并。生产目标尚未完成。
