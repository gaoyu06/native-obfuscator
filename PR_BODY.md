# Extend the shared evaluator i64 ISA / 扩展共享 evaluator 的 i64 ISA

## (a) Change scope / 改动范围

Extend the optional shared IR evaluator with `LLOAD`, `LSTORE`, `LADD`, `LSUB`,
`LMUL`, `LRETURN`, `I2L`, and `L2I`. Java serialization and the C++ evaluator
use the same contiguous `0x23`–`0x2a` map. Long arithmetic uses unsigned
64-bit carriers and JNI `jlong` bit copies for JVM wraparound; conversions
perform sign extension or low-32-bit truncation.

扩展可选的共享 IR evaluator，新增 `LLOAD`、`LSTORE`、`LADD`、`LSUB`、
`LMUL`、`LRETURN`、`I2L` 与 `L2I`。Java 序列化和 C++ evaluator 使用一致的
连续 `0x23`–`0x2a` 映射。long 算术通过无符号 64 位载体与 JNI `jlong`
位拷贝保持 JVM 回绕语义；转换分别执行符号扩展与低 32 位截断。

The command-line defaults remain `--codegen=legacy` and `--ir-lower=direct`;
the evaluator remains `--codegen=ir --ir-lower=eval`. Unsupported methods are
still rejected before method/output/cache mutation and then use the existing
per-method fallback. `LDIV`/`LREM` remain fallback operations because JVM
division-by-zero and overflow behavior is not implemented in this slice.

命令行默认值仍为 `--codegen=legacy` 与 `--ir-lower=direct`；evaluator 仍通过
`--codegen=ir --ir-lower=eval` 启用。不支持的方法仍在修改方法、输出或缓存前被拒绝，
随后沿用现有逐方法回退。由于本阶段未实现 JVM 除零与溢出行为，`LDIV`/`LREM`
继续回退。

This change adds no benchmark numbers and does not rewrite #53 or #59.

本改动不新增 benchmark 数字，也不改写 #53 或 #59。

## (b) Ship-ready? / 是否可直接上线

**No.** The evaluator remains an opt-in, deliberately narrow lowering with
per-method fallback for unsupported IR.

**否。** evaluator 仍是可选且范围受限的 lowering；不支持的 IR 继续逐方法回退。

## (c) Review required? / 是否需要 review

**Yes.** Review the Java/C++ opcode agreement, two-slot local handling,
`jlong` wrap/conversion semantics, generated trampoline selection, and the
fallback-before-mutation invariant.

**是。** 需要审阅 Java/C++ opcode 一致性、双槽位局部变量处理、`jlong`
回绕与转换语义、生成 trampoline 的选择，以及修改前回退不变量。

## (d) Verification / 验证

Focused test counts and native harness results will be recorded from the actual
`CC=gcc CXX=g++` run after this implementation commit.

聚焦测试数量与原生 harness 结果将在本实现提交后，根据实际执行的
`CC=gcc CXX=g++` 结果记录。
