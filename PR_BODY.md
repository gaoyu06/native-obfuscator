# Extend the IR evaluator integer ISA / 扩展 IR evaluator 整数 ISA

## (a) Change scope / 改动范围

Extend the opt-in shared evaluator with `IAND`, `IOR`, `IXOR`, `ISHL`, `ISHR`,
and `IUSHR`. Java serialization and the C++ evaluator use the same contiguous
opcodes (`0x13`–`0x18`). Shifts mask their distance with `& 31`; arithmetic
right shift uses explicit sign extension, and all results retain JVM 32-bit
bit/wrap semantics.

扩展可选的共享 evaluator，新增 `IAND`、`IOR`、`IXOR`、`ISHL`、`ISHR` 和
`IUSHR`。Java 序列化与 C++ evaluator 使用完全一致的连续 opcode
（`0x13`–`0x18`）。移位距离均以 `& 31` 掩码；算术右移显式补符号位；所有结果保持
JVM 32 位比特与回绕语义。

The exact integer-op shape of `IrFriendlyIntKernel.run(I)I` is included in
generation and linked native evaluator tests. No benchmark timings are added.

测试覆盖 `IrFriendlyIntKernel.run(I)I` 的完整整数操作形态，包括生成路径与链接后的
原生 evaluator 执行；本改动不新增任何性能数字。

## (b) Ship-ready? / 是否可直接上线

**No.** The evaluator remains an opt-in, deliberately narrow lowering with
per-method legacy fallback for unsupported IR.

**否。** evaluator 仍是可选且有意收窄的 lowering；不支持的 IR 继续逐方法回退到
legacy。

## (c) Review required? / 是否需要 review

**Yes.** Review the cross-language opcode agreement, JVM shift semantics,
fallback-before-mutation invariant, and native runtime coverage.

**是。** 需要审阅跨语言 opcode 一致性、JVM 移位语义、改写前回退不变量，以及原生
运行覆盖。

## (d) Verification / 验证

The focused Gradle suite passed **28/28**, with 0 skipped, failures, or errors:
`CodegenModeTest` 4/4, `IrCompilerTest` 17/17, and
`InterpreterStreamStrategyTest` 7/7.

聚焦 Gradle 测试共 **28/28** 通过，0 skipped、0 failures、0 errors：
`CodegenModeTest` 4/4、`IrCompilerTest` 17/17、
`InterpreterStreamStrategyTest` 7/7。

- Serializer assertions cover all six new opcode numbers. The g++ evaluator
  translation-unit syntax smoke and linked runtime harness both executed.
- The native harness evaluated each new operation, including masked shift
  distances and negative `ISHR`/`IUSHR` inputs.
- An equivalent `IrFriendlyIntKernel.run(I)I` stream ran natively for 10 rounds
  and returned `802611040`, matching the Java reference. Generated-source
  inspection found its evaluator data/trampoline and no legacy body/fallback.
- The unsupported-unary test still proves rejection before method/output/cache
  mutation. CLI tests still prove the `legacy` and `direct` defaults.

- 序列化断言覆盖全部六个新增 opcode 数值；g++ evaluator 翻译单元语法检查与链接后的
  运行 harness 均实际执行。
- 原生 harness 执行了每个新增操作，包括移位距离掩码以及负数 `ISHR`/`IUSHR`。
- 等价的 `IrFriendlyIntKernel.run(I)I` 数据流原生执行 10 轮，结果
  `802611040` 与 Java 参考一致；生成源码确认其使用 evaluator 数据/trampoline，
  没有 legacy 函数体或回退。
- 不支持的一元操作测试继续证明方法、输出与缓存改写前即拒绝；CLI 测试继续证明
  `legacy` 与 `direct` 默认值不变。
