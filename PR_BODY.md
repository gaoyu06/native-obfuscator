# EN

## (a) Scope

This increment admits leaf-only `ISHL`, `ISHR`, and `IUSHR` int-family
constructor-chain arguments after #185. Each value and shift-count operand must
independently be a proven declared int-family `ILOAD`, int-family constant, or
one `INEG` over a declared int-family `ILOAD`. The retained prefix keeps the
original shift bytecode and JVM shift-count semantics.

Nested binary expressions, trapping `IDIV`/`IREM`, `IINC`, extra-local inputs,
and every other unproven operand shape remain rejected before mutation.

## (b) Ship-ready?

**No.** This is one bounded constructor-split admission increment. It does not
change defaults or complete the production goal.

## (c) Review and gate

There is no stacked review. The gate is the executed focused
`IrCompilerTest` + `CodegenModeTest` suite, including the new compile-and-run
CMake/g++ JNI harness under `java -Xverify:all -Xcheck:jni`.

## (d) Preconditions

Remaining constructor leftovers, unsafe constant dynamic inputs, and
`jsr`/`ret` stay reject-before-mutation. `--codegen` remains `legacy`.

# 中文

## (a) 范围

本次增量在 #185 之后，仅接纳构造器调用链中叶子级的 int-family `ISHL`、
`ISHR` 和 `IUSHR` 参数。移位值和移位位数都必须分别可证明为已声明的
int-family `ILOAD`、int-family 常量，或对已声明 int-family `ILOAD` 进行一次
`INEG`。保留的前缀继续执行原始移位字节码，并保留 JVM 的移位位数语义。

嵌套二元表达式、可能抛出算术异常的 `IDIV`/`IREM`、`IINC`、额外局部变量
输入以及其他任何未证明的操作数形状，仍会在修改前被拒绝。

## (b) 可发布？

**否。** 这只是一个有界的构造器拆分接纳增量，不更改默认选项，也不表示
生产目标已经完成。

## (c) 审查与门禁

没有堆叠审查。门禁是已执行的 `IrCompilerTest` + `CodegenModeTest` 聚焦测试
套件，其中包括新增的 CMake/g++ JNI 编译运行测试，并使用
`java -Xverify:all -Xcheck:jni`。

## (d) 前置条件

其余构造器遗留形状、不安全的 constant dynamic 输入以及 `jsr`/`ret` 仍在
修改前被拒绝；`--codegen` 仍为 `legacy`。
