# Leaf-only constructor-chain `ISUB` / `IMUL`

## English

### (a) Scope

This increment widens the post-#182 constructor-split chain-input proof to
accept exactly one `ISUB` or `IMUL` whose two int-family operands are already
proven leaves: a declared int-family `ILOAD`, an int-family constant, or one
`INEG` over a declared int-family `ILOAD`. The opcodes are intentionally absent
from the leaf helper, so nested binary expressions remain rejected.

Nested expressions, extra-local operands, `IDIV`, `IREM`, shifts, bitwise
operators, `IINC`, and all other unlisted input shapes remain rejected before
mutation. Existing suffix-identity, receiver, control-flow, exception-table,
and three-or-more-call guards are unchanged.

### (b) Ship-ready?

**No.** This is one bounded compiler admission increment. It does not change
defaults or complete the production goal.

### (c) Review and gate

There is no stacked review. The gate is the executed focused
`IrCompilerTest` + `CodegenModeTest` suite, including the new compile-and-run
Java/JNI parity harness under `java -Xverify:all -Xcheck:jni` with CMake/g++.

### (d) Preconditions

Remaining constructor leftovers, unsafe constant dynamic inputs, and
`jsr`/`ret` must continue to reject before mutation. `--codegen` remains
`legacy`.

## 中文

### (a) 范围

本增量在 #182 之后扩展构造方法拆分的调用链输入证明：仅接受一层
`ISUB` 或 `IMUL`，且两个 int-family 操作数都必须是已证明的叶子输入，
即构造方法声明的 int-family `ILOAD`、int-family 常量，或对声明的
int-family `ILOAD` 执行一次 `INEG`。这些操作码不会加入叶子辅助方法，
因此嵌套二元表达式仍会被拒绝。

嵌套表达式、额外局部变量操作数、`IDIV`、`IREM`、移位、位运算、
`IINC` 以及其他未列出的输入形态，仍在任何修改前被拒绝。现有的后缀
一致性、接收者、控制流、异常表和三条及以上调用链守卫保持不变。

### (b) 可发布？

**否。** 这只是一个有界的编译器准入增量，不改变默认值，也不表示生产
目标已经完成。

### (c) 评审与门禁

没有堆叠评审。门禁是实际执行的 `IrCompilerTest` +
`CodegenModeTest` 聚焦测试套件，其中包括新增的完整编译运行
Java/JNI 一致性测试；该测试使用 CMake/g++，并在
`java -Xverify:all -Xcheck:jni` 下运行。

### (d) 前置条件

其余构造方法遗留形态、不安全的 constant dynamic 输入以及 `jsr`/`ret`
必须继续在任何修改前被拒绝。`--codegen` 仍为 `legacy`。
