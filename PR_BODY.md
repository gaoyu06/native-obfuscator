# Fable review: IR `LCMP` admission (`LongCompare`)

Stacked review note for the LCMP admission increment. This PR adds only a Fable
review note (`docs/reviews/ir-lcmp-fable.md`); it implements no IR features and
flips no defaults.

- Base of this stacked PR: `cursor/ir-lcmp-6d81-d05f` (not `master`).
- Reviewed implementation HEAD: `7802c81` (increment commit `7f405f0`).

## (a) What was reviewed

The already-pushed increment that admits JVM `LCMP` on the typed CFG IR path:
`IrNodes.LongCompare` (i64 left, i64 right → i32 result `-1`/`0`/`1`), kept
separate from the NaN-aware `FloatingCompare` and never throwing; `AsmToIr`
wiring across the opcode gate, the type-check pass (pop I64, pop I64, push I32),
and the lowering pass; and `IrCppEmitter.emitLongCompare`, a signed `int64_t`
three-way ternary compare (no `std::isnan`, no subtract). The
`rejectsUnsupportedWideOperationBeforeMutation` sentinel was retargeted from the
now-admitted `LCMP` to the still-rejected `IF_ACMPEQ`.

## (b) Verdict

- **Accept**
- Ship-ready: **No** (incremental, opt-in IR path; defaults remain
  `--codegen=legacy`, `--ir-lower=direct`, `--backend=cpp`).

Operand order and `-1`/`0`/`1` semantics are correct; the emission is a signed
three-way compare that cannot degrade into an overflowing subtract; the
`IF_ACMPEQ` sentinel still proves reject-before-mutation and `IF_ACMPEQ` was not
implemented; no interpreter/evaluator/CLI-default edits leaked in. The evaluator
not lowering `LongCompare` is expected and documented.

## (c) Tests re-run and exact counts

```text
CC=gcc CXX=g++ ./gradlew :obfuscator:test --rerun-tasks \
  --tests by.radioegor146.ir.IrCompilerTest \
  --tests by.radioegor146.CodegenModeTest
```

`BUILD SUCCESSFUL`. From JUnit XML:

- `IrCompilerTest`: tests=105, skipped=0, failures=0, errors=0
- `CodegenModeTest`: tests=7, skipped=0, failures=0, errors=0
- Total: 112, 0 skipped, 0 failures, 0 errors

Toolchain: OpenJDK 21.0.10, g++/gcc 13.3.0, JNI headers present, so the
compiled-and-executed LCMP harness test ran (not skipped).

## (d) 中文摘要

- 结论：**Accept**；可交付：**否**。本 PR 仅新增 Fable 评审说明
  （`docs/reviews/ir-lcmp-fable.md`），不实现 IR 功能，不翻转默认值。
- 已核验：操作数顺序（先 value2 为右、再 value1 为左）与 `-1`/`0`/`1` 语义
  正确；C++ 端为有符号 `int64_t` 三路比较，不使用减法、不含 `std::isnan`，
  不会因溢出错序；改指向 `IF_ACMPEQ` 的哨兵仍验证“变更前拒绝”，且
  `IF_ACMPEQ` 未实现；未泄漏解释器 / evaluator / CLI 默认值改动。
- 复跑：`IrCompilerTest` 105、`CodegenModeTest` 7，共 112，全部通过。
- 本 stacked PR 的基线为 `cursor/ir-lcmp-6d81-d05f`，而非 `master`。
