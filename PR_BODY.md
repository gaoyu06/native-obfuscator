# Add JVM-accurate evaluator LDIV/LREM / 为 evaluator 添加 JVM 精确的 LDIV/LREM

## (a) Change scope / 改动范围

Extend the optional shared IR evaluator with JVM-accurate `LDIV` and `LREM`.
Java serialization and the C++ evaluator append the same contiguous opcodes,
`0x2b` and `0x2c`, after the existing `0x23`–`0x2a` i64 block. A zero divisor
uses the trampoline's `JNIEnv*` to leave `ArithmeticException` pending and exits
evaluation immediately. `Long.MIN_VALUE / -1` returns `Long.MIN_VALUE`, and the
matching remainder returns zero, without invoking signed C++ overflow.

为可选的共享 IR evaluator 新增 JVM 精确的 `LDIV` 与 `LREM`。Java 序列化器和
C++ evaluator 在已有 `0x23`–`0x2a` i64 块之后一致追加连续 opcode：
`0x2b` 与 `0x2c`。除数为零时，通过 trampoline 传入的 `JNIEnv*` 保留 pending
`ArithmeticException` 并立即退出 evaluator。`Long.MIN_VALUE / -1` 返回
`Long.MIN_VALUE`，对应余数为零，且不会触发 C++ 有符号溢出。

The command-line defaults remain `--codegen=legacy` and `--ir-lower=direct`;
the evaluator remains `--codegen=ir --ir-lower=eval`. Unsupported methods are
still rejected before method/output/cache mutation and then use the existing
per-method fallback. The shared frontend and direct IR lowering also admit
`LDIV`/`LREM` so the evaluator can receive these typed nodes; direct lowering
uses the same JVM edge-case rules.

命令行默认值仍为 `--codegen=legacy` 与 `--ir-lower=direct`；evaluator 仍通过
`--codegen=ir --ir-lower=eval` 启用。不支持的方法仍在修改方法、输出或缓存前被拒绝，
随后沿用现有逐方法回退。共享 frontend 与 direct IR lowering 也接纳
`LDIV`/`LREM`，使 evaluator 能收到这些类型化节点；direct lowering 使用相同的
JVM 边界语义。

This change adds no benchmark numbers and does not rewrite #53 or #59.

本改动不新增 benchmark 数字，也不改写 #53 或 #59。

## (b) Ship-ready? / 是否可直接上线

**No.** The evaluator remains an opt-in, deliberately narrow lowering with
per-method fallback for unsupported IR.

**否。** evaluator 仍是可选且范围受限的 lowering；不支持的 IR 继续逐方法回退。

## (c) Review required? / 是否需要 review

**Yes.** Review the Java/C++ opcode agreement, pending-exception exit, signed
division edge cases, generated trampoline selection, direct-IR admission, and
the fallback-before-mutation invariant.

**是。** 需要审阅 Java/C++ opcode 一致性、pending exception 退出、有符号除法
边界、生成 trampoline 的选择、direct-IR 准入，以及修改前回退不变量。

## (d) Verification / 验证

The focused `CC=gcc CXX=g++` Gradle run passed **32/32**, with 0 skipped,
failures, or errors: `CodegenModeTest` 4/4, `IrCompilerTest` 19/19, and
`InterpreterStreamStrategyTest` 9/9.

- Serializer assertions pin `LDIV=0x2b` and `LREM=0x2c` and assert the same
  constants in the C++ evaluator.
- The g++ translation-unit smoke, direct-source syntax check, and linked native
  harness executed. The harness verified signed divide/remainder, both zero
  divisor exceptions through fake JNI `FindClass`/`ThrowNew`, immediate exit,
  `Long.MIN_VALUE / -1 == Long.MIN_VALUE`, and remainder zero.
- Generated-source inspection found evaluator data plus `evaluate_i64`
  trampolines for both `divide(JJ)J` and `remainder(JJ)J`, with no direct or
  legacy body.
- The unsupported-unary test still proves rejection before method, output,
  registration, or cache mutation. CLI tests still prove the `legacy` codegen
  and `direct` IR-lowering defaults.

聚焦 `CC=gcc CXX=g++` Gradle 测试共 **32/32** 通过，0 skipped、0 failures、
0 errors：`CodegenModeTest` 4/4、`IrCompilerTest` 19/19、
`InterpreterStreamStrategyTest` 9/9。

- 序列化断言固定 `LDIV=0x2b` 与 `LREM=0x2c`，并断言 C++ evaluator 使用相同常量。
- g++ 翻译单元检查、direct 生成源码语法检查与链接后的原生 harness 均实际执行；
  harness 验证了有符号除法/余数、通过 fake JNI `FindClass`/`ThrowNew` 产生的两种
  零除数异常、立即退出、`Long.MIN_VALUE / -1 == Long.MIN_VALUE` 及余数为零。
- 生成源码检查确认 `divide(JJ)J` 与 `remainder(JJ)J` 均使用 evaluator 数据和
  `evaluate_i64` trampoline，且没有 direct 或 legacy 方法体。
- 不支持的一元操作测试继续证明方法、输出、注册与缓存修改前即拒绝；CLI 测试继续
  证明默认 codegen 为 `legacy`、默认 IR lowering 为 `direct`。
