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

Verification will run the focused Java suite, including serializer/path
assertions, the evaluator translation-unit g++ syntax smoke, and a linked
native harness that executes all six new operations plus the IR-friendly
kernel shape. Defaults remain covered by `CodegenModeTest`.

验证将运行聚焦 Java 测试集，其中包括序列化与路径断言、evaluator 翻译单元的 g++
语法检查，以及执行全部六个新增操作与 IR-friendly 内核形态的原生链接 harness。
`CodegenModeTest` 继续覆盖默认选项不变。
