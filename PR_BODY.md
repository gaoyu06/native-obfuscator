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

The focused `CC=gcc CXX=g++` Gradle run passed **31/31**, with 0 skipped,
failures, or errors: `CodegenModeTest` 4/4, `IrCompilerTest` 18/18, and
`InterpreterStreamStrategyTest` 9/9.

- Serializer assertions cover every new Java opcode number and assert the same
  constants in the C++ evaluator.
- The g++ translation-unit smoke and linked native harness executed. The
  harness verified `LADD`/`LSUB`/`LMUL` wraparound, negative `I2L` sign
  extension, `L2I` truncation, and `(J)J` value transport.
- Generated-source inspection found evaluator data plus an `evaluate_i64`
  trampoline for `roundTrip(J)J`, with no direct/legacy method body.
- The unsupported-unary test still proves rejection before method, output,
  registration, or cache mutation. CLI tests still prove the `legacy` codegen
  and `direct` IR-lowering defaults.

聚焦 `CC=gcc CXX=g++` Gradle 测试共 **31/31** 通过，0 skipped、0 failures、
0 errors：`CodegenModeTest` 4/4、`IrCompilerTest` 18/18、
`InterpreterStreamStrategyTest` 9/9。

- 序列化断言覆盖全部新增 Java opcode 数值，并断言 C++ evaluator 使用相同常量。
- g++ 翻译单元检查与链接后的原生 harness 均实际执行；harness 验证了
  `LADD`/`LSUB`/`LMUL` 回绕、负数 `I2L` 符号扩展、`L2I` 截断，以及
  `(J)J` 值传递。
- 生成源码检查确认 `roundTrip(J)J` 使用 evaluator 数据和 `evaluate_i64`
  trampoline，且没有 direct/legacy 方法体。
- 不支持的一元操作测试继续证明方法、输出、注册与缓存修改前即拒绝；CLI 测试继续
  证明默认 codegen 为 `legacy`、默认 IR lowering 为 `direct`。
