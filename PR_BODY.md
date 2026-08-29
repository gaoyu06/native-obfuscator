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

**Accept.** The serializer/native opcode map agrees, i64 arithmetic uses
unsigned 64-bit carriers, `I2L` sign-extends, `L2I` truncates, and generated
`roundTrip(J)J` uses `evaluate_i64` without a direct or legacy body.

**接受。** serializer/native opcode 映射一致；i64 算术使用无符号 64 位载体，
`I2L` 执行符号扩展，`L2I` 执行截断；生成的 `roundTrip(J)J` 使用
`evaluate_i64`，不包含 direct 或 legacy 方法体。

## (d) Verification / 验证

The post-commit `CC=gcc CXX=g++` focused re-run passed **31/31**, with 0
skipped, failures, or errors: `CodegenModeTest` 4/4, `IrCompilerTest` 18/18,
and `InterpreterStreamStrategyTest` 9/9. The g++ translation-unit check and
linked native harness both ran. The harness covered i64 arithmetic wraparound,
negative `I2L`, low-32-bit `L2I`, and `(J)J` value transport.

post-commit `CC=gcc CXX=g++` 聚焦复测共 **31/31** 通过，0 skipped、0
failures、0 errors：`CodegenModeTest` 4/4、`IrCompilerTest` 18/18、
`InterpreterStreamStrategyTest` 9/9。g++ 翻译单元检查与链接后的 native
harness 均实际执行；harness 覆盖了 i64 算术回绕、负数 `I2L`、低 32 位
`L2I` 截断与 `(J)J` 值传递。
