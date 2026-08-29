# ir: admit JVM LCMP on the typed CFG IR path (`--codegen=ir`, `--ir-lower=direct`)

## Scope

- Adds `IrNodes.LongCompare`: a dedicated node for JVM `LCMP` with an i64
  left operand, an i64 right operand, and an i32 result (`-1`/`0`/`1`),
  types enforced in the constructor like the other i64 nodes. Longs have no
  NaN, so this stays separate from the NaN-aware `FloatingCompare`, and it
  never throws.
- `AsmToIr` admits `LCMP` in the opcode gate and wires the stack effect in
  both the type-check and lowering passes (pop I64 value2, pop I64 value1,
  push I32), following the `isFloatingCompareOp` pattern.
- `IrCppEmitter` lowers it to a direct signed three-way compare on the
  `int64_t` carrier:
  `((int64_t) left > (int64_t) right) ? 1 : (((int64_t) left < (int64_t) right) ? -1 : 0)`
  — the ordered half of `emitFloatingCompare` without `std::isnan`, and not
  a subtract, so overflow cannot misorder results.
- `CfgBuilder.mayThrow` already excluded `LCMP`; no change was needed there.
- Retargets `rejectsUnsupportedWideOperationBeforeMutation` from `LCMP`
  (now admitted) to `IF_ACMPEQ`, which is still rejected on this tree, so
  the reject-before-mutation guarantee stays proven. `IF_ACMPEQ`/`IF_ACMPNE`
  are not implemented here.
- Status note: `docs/architecture/ir-lcmp-status.md`.

## Tests

```text
CC=gcc CXX=g++ ./gradlew :obfuscator:test --rerun-tasks \
  --tests by.radioegor146.ir.IrCompilerTest \
  --tests by.radioegor146.CodegenModeTest
```

`BUILD SUCCESSFUL`; 112 tests, 0 skipped, 0 failures, 0 errors
(`IrCompilerTest` 105, `CodegenModeTest` 7, from JUnit XML). New coverage:
node shape (I64/I64 → I32), signed-ternary emission (no `isnan`, no
subtract), a compiled-and-executed harness for negative/zero/positive
results including `Long.MIN_VALUE` vs `-1`, equal longs, and both orderings
of `MIN_VALUE` vs `MAX_VALUE`, an `LCMP`-driven `IFGE` branch, the existing
`g++ -std=c++17 -fsyntax-only` smoke test with the new methods included,
and the retargeted `IF_ACMPEQ` reject-before-mutation sentinel.

## Ship-ready?

**No.** This is an incremental IR-frontend/emitter change on an opt-in
path. It is not production-ready and must not flip any defaults:
`--codegen` stays `legacy`, `--ir-lower` stays `direct`, `--backend` stays
`cpp`. No production goal is marked complete, and no JDK support badge is
claimed.

## Review required?

**Yes.** A Fable-appropriate IR review is required before landing.

## Review preconditions

- Verify the JVM operand order (value2 popped first as right, value1 as
  left) and the `-1`/`0`/`1` contract against the `LCMP` specification.
- Verify the emitted ternary is a signed `int64_t` compare and cannot be
  simplified by the host compiler into an overflowing subtract.
- Confirm the retargeted `IF_ACMPEQ` sentinel still exercises the
  before-mutation rejection path.
- Known limitation (expected): the evaluator lowering (`--ir-lower=eval`)
  does not lower `LongCompare`; such methods fall back as before.

## 审查前提（中文）

- 本次改动为可选 IR 路径（`--codegen=ir`、`--ir-lower=direct`）新增 JVM
  `LCMP` 支持：新增 `IrNodes.LongCompare` 节点（i64/i64 → i32），C++ 端按
  `int64_t` 有符号三路比较的三元表达式生成，不使用减法，不含
  `std::isnan`，不会抛出异常。
- **尚未达到可交付状态（Ship-ready: 否）**，不得据此翻转任何默认值：
  `--codegen` 保持 `legacy`，`--ir-lower` 保持 `direct`，`--backend` 保持
  `cpp`。不宣称任何 JDK 支持徽章，不标记任何生产目标完成。
- **需要评审（Review required: 是）**：合入前需进行适配 Fable 的 IR 评审。
- 评审要点：确认操作数顺序（先弹出 value2 作为右操作数，再弹出 value1 作为
  左操作数）与 `-1`/`0`/`1` 语义；确认生成代码是有符号 `int64_t` 比较而非
  可能溢出的减法；确认改指向 `IF_ACMPEQ` 的哨兵测试仍验证“变更前拒绝”。
- 已知且符合预期的限制：evaluator 降级路径（`--ir-lower=eval`）尚不支持
  `LongCompare`，包含 `LCMP` 的方法在该模式下按原有方式回退。
