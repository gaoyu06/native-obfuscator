# Fable review — IR `LCMP` admission (`LongCompare`)

Reviewer role: independent Fable reviewer. This review covers the already-pushed
LCMP admission increment on the typed CFG IR path. No IR features were
implemented here; this is a docs + review-note change only.

## Under review

- Implementation branch: `cursor/ir-lcmp-6d81-d05f` (parent GitHub PR #153)
- Implementation HEAD: `7802c81` (increment commit `7f405f0`)
- Base: `master`
- Review branch: `cursor/ir-lcmp-review-6d81`

## Verdict

- **Accept**
- Ship-ready: **No**

This is an incremental, opt-in IR-frontend/emitter change. It is correct and
well-tested for what it claims, but it is not production-ready and does not (and
must not) flip any defaults.

## What I checked

### 1. `LongCompare` node, `AsmToIr` wiring, and `emitLongCompare` — operand order and `-1`/`0`/`1`

`IrNodes.LongCompare` is a dedicated node: constructor enforces `requireI32`
result, `requireI64` left, `requireI64` right, and it has no `nanResult` field —
correctly separate from the NaN-aware `FloatingCompare`. It exposes no throwing
behavior.

`AsmToIr` admits `LCMP` in three places consistently:
- opcode gate (`|| opcode == Opcodes.LCMP`)
- type-check pass: `popType(I64)`, `popType(I64)`, `push(I32)`
- lowering pass: `right = pop(I64)` first, `left = pop(I64)` second, `push I32`

JVM operand order is correct. `LCMP` pushes value1 then value2, so value2 is on
top of the stack. The lowering pops `right` first (value2) and `left` second
(value1). `emitLongCompare` then produces:

```
((int64_t) left > (int64_t) right) ? 1 : (((int64_t) left < (int64_t) right) ? -1 : 0)
```

So value1 > value2 → 1, value1 == value2 → 0, value1 < value2 → -1, which is the
`LCMP` specification. The `longCompareMethod` test fixture (`LLOAD 0`, `LLOAD 2`,
`LCMP`) maps left→`arg0`, right→`arg1`, and the emitted string asserted by the
tests (`((int64_t) arg0 > (int64_t) arg1) ? 1 :`) confirms this.

### 2. Emission cannot become an overflowing subtract

The lowering is a direct signed three-way comparison on the `int64_t` carrier
using `>` and `<`, never a subtract. Both operands are cast to `int64_t`, so the
comparison is signed. The runtime harness test compiles and executes the
generated code and explicitly checks `MIN_VALUE` vs `-1`, equal longs,
`MIN_VALUE` vs itself, and both orderings of `MIN_VALUE` vs `MAX_VALUE` — the
exact cases a subtract-based lowering would misorder on overflow. The unit test
also asserts the emitted C++ contains no `arg0 - `, no `- (int64_t) arg1`, and no
`- (uint64_t) arg1`.

### 3. `IF_ACMPEQ` reject-before-mutation sentinel

`unsupportedWideOperationMethod` was retargeted from `LCMP` (now admitted) to a
reference compare using `ALOAD`/`ALOAD`/`IF_ACMPEQ`. The test
`rejectsUnsupportedWideOperationBeforeMutation` now asserts
`error.getOpcode() == Opcodes.IF_ACMPEQ`, and still verifies no mutation
occurred (native flag unset, empty output/nativeMethods, empty method cache).
`IF_ACMPEQ` does not appear anywhere in the IR frontend (`AsmToIr` has no
`IF_ACMPEQ` reference), so it remains genuinely outside the supported subset and
was not implemented. The sentinel therefore still proves reject-before-mutation.

### 4. No interpreter / evaluator / CLI-default edits leaked in

The increment touches only IR frontend/emitter/node files plus tests and docs:

```
PR_BODY.md
docs/architecture/ir-lcmp-status.md
obfuscator/src/main/java/by/radioegor146/ir/IrMethod.java
obfuscator/src/main/java/by/radioegor146/ir/IrNodes.java
obfuscator/src/main/java/by/radioegor146/ir/emit/IrCppEmitter.java
obfuscator/src/main/java/by/radioegor146/ir/frontend/AsmToIr.java
obfuscator/src/test/java/by/radioegor146/ir/IrCompilerTest.java
```

No CLI/config file changed, so defaults are unchanged (`--codegen=legacy`,
`--ir-lower=direct`, `--backend=cpp`). The interpreter/evaluator backend
(`InterpreterStreamStrategy`) handles only a limited node subset and does not
reference `LongCompare` (nor `FloatingCompare`); such methods fall back as
before. This matches the claim that the evaluator does not lower `LongCompare`.

## What I re-ran and the exact counts

```text
CC=gcc CXX=g++ ./gradlew :obfuscator:test --rerun-tasks \
  --tests by.radioegor146.ir.IrCompilerTest \
  --tests by.radioegor146.CodegenModeTest
```

Result: `BUILD SUCCESSFUL`. Exact counts from JUnit XML:

- `by.radioegor146.ir.IrCompilerTest`: tests="105" skipped="0" failures="0" errors="0"
- `by.radioegor146.CodegenModeTest`: tests="7" skipped="0" failures="0" errors="0"
- Total: 112 tests, 0 skipped, 0 failures, 0 errors

Toolchain present in this environment: OpenJDK 21.0.10, g++/gcc 13.3.0, JNI
headers available — so the compiled-and-executed LCMP harness test actually ran
(0 skipped) rather than being short-circuited.

## Nits / blocking issues

- No blocking issues.
- Nit (non-blocking): `emitLongCompare` re-casts both operands to `int64_t`.
  Given the i64 carrier this is likely redundant, but it is harmless and makes
  the signed intent explicit at the C++ level; keeping it is fine.
- Nit (non-blocking): the evaluator (`--ir-lower=eval`) not lowering
  `LongCompare` is expected and documented, but it is worth a one-line tracking
  note if evaluator parity is a future goal.

## 中文摘要

本次评审针对已推送的 JVM `LCMP` 接纳增量（新增 `IrNodes.LongCompare` 节点，
i64/i64 → i32，取值 `-1`/`0`/`1`）。

- **结论：Accept（接受）；可交付：否（Ship-ready: No）。**
- 操作数顺序正确：先弹出 value2 作为右操作数、再弹出 value1 作为左操作数；
  C++ 端生成有符号 `int64_t` 三路比较三元表达式，符合 `-1`/`0`/`1` 语义。
- 生成代码使用 `>`/`<` 有符号比较而非减法，不会因溢出而错序；运行期测试实际
  编译并执行，覆盖 `MIN_VALUE` 与 `-1`、`MIN` 与 `MAX` 两个方向等边界。
- 改指向 `IF_ACMPEQ` 的哨兵测试仍验证“变更前拒绝”，且 `IF_ACMPEQ` 未在 IR
  前端实现。
- 未泄漏解释器 / evaluator / CLI 默认值改动；默认值保持
  `legacy` / `direct` / `cpp`；evaluator 不降级 `LongCompare` 属预期。
- 复跑测试：`IrCompilerTest` 105、`CodegenModeTest` 7，共 112，全部通过，
  0 跳过 / 0 失败 / 0 错误。
