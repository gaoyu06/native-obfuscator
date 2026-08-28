# Review the shared evaluator i64 ISA / 审查共享 evaluator 的 i64 ISA

## (a) Change scope / 改动范围

Review PR #68's optional shared IR evaluator support for `LLOAD`, `LSTORE`,
`LADD`, `LSUB`, `LMUL`, `LRETURN`, `I2L`, and `L2I`. The review verifies the
Java/C++ `0x23`–`0x2a` agreement, two-slot locals, i64 wrap/conversion
semantics, `(J)J` evaluator selection, fallback-before-mutation, unchanged
defaults, and the direct-IR sibling-file boundary.

审查 PR #68 对可选共享 IR evaluator 的 `LLOAD`、`LSTORE`、`LADD`、`LSUB`、
`LMUL`、`LRETURN`、`I2L` 与 `L2I` 支持。审查范围包括 Java/C++ 两侧
`0x23`–`0x2a` 一致性、双槽位局部变量、i64 回绕与转换语义、`(J)J`
evaluator 路径、修改前回退、默认值保持不变，以及 direct-IR sibling 文件边界。

Static review found no correctness bug, so this review adds documentation only.
The reviewed compiler changes are required: the evaluator and direct lowering
share the typed frontend, and direct remains the default IR lowering.
`LDIV`/`LREM` still fall back. No benchmark values were added, and #53/#59 were
not rewritten.

静态审查未发现正确性缺陷，因此本 review 仅新增文档。被审查分支中的 compiler
改动是必要的：evaluator 与 direct lowering 共用 typed frontend，且 direct
仍是默认 IR lowering。`LDIV`/`LREM` 继续回退。本次未添加 benchmark 数值，
也未改写 #53/#59。

## (b) Ship-ready? / 是否可直接上线

**No.** The evaluator remains opt-in and intentionally limited, with
per-method fallback for unsupported IR.

**No（否）。** evaluator 仍需显式启用且支持范围有限；不支持的 IR 继续逐方法回退。

## (c) Review verdict / 审查结论

**Accept, subject to the focused re-run in (d).** The serializer/native opcode
map agrees, i64 arithmetic uses unsigned 64-bit carriers, `I2L` sign-extends,
`L2I` truncates, and generated `roundTrip(J)J` uses `evaluate_i64` without a
direct or legacy body.

**接受，但以 (d) 的聚焦复测为最终依据。** serializer/native opcode 映射一致；
i64 算术使用无符号 64 位载体，`I2L` 执行符号扩展，`L2I` 执行截断；
生成的 `roundTrip(J)J` 使用 `evaluate_i64`，不包含 direct 或 legacy 方法体。

## (d) Verification / 验证

The required post-commit `CC=gcc CXX=g++` focused re-run is pending. It covers
`CodegenModeTest`, `IrCompilerTest`, and `InterpreterStreamStrategyTest`; the
final documentation commit will replace this sentence with the observed
per-class and total counts.

所要求的 post-commit `CC=gcc CXX=g++` 聚焦复测尚待执行，覆盖
`CodegenModeTest`、`IrCompilerTest` 与 `InterpreterStreamStrategyTest`。
最终文档提交会用实测的各测试类及总计数替换本段。
