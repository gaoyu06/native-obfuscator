# EN

## (a) Scope

This increment widens the post-#179 constructor split for three or more
reachable direct this/super calls. It admits empty or nonempty straight-line
suffix copies only when every instruction and operand is identical to the
canonical final copy, while retaining the #179 direct declared-input,
int-family constant, and single-`INEG` chain-input proof. The copies normalize
to one shared join and one hidden bridge. Non-identical copies and copies with
labels or branches remain rejected before mutation.

## (b) Ship-ready?

**No.** This is one bounded constructor-leftover increment. Defaults do not
change, and the production goal is not complete.

## (c) Review and gate

There is no stacked review. The gate is the executed focused
`IrCompilerTest` + `CodegenModeTest` suite, including the new three-path JVM
verification and CMake/g++ compile-and-run Java-parity harness under
`java -Xverify:all -Xcheck:jni`.

## (d) Preconditions

Remaining constructor leftovers, unsafe constant dynamic, and `jsr`/`ret`
stay reject-before-mutation. `--codegen` remains `legacy`.

# 中文

## (a) 范围

本增量在 #179 之后，继续有限扩展三个或更多可达直接 this/super 调用的构造器拆分。
仅当每个空或非空直线后缀副本的每条指令及操作数都与字节码顺序中的最后一个规范副本
完全一致时才接纳，同时保留 #179 对直接声明参数、int 家族常量和单次 `INEG`
链调用输入的证明。所有副本会归一化为一个共享汇合点和一个隐藏桥。非相同副本以及
含标签或分支的副本仍在任何修改前拒绝。

## (b) 可发布？

**否。** 这只是一个有界的构造器遗留项增量；默认配置不变，生产目标尚未完成。

## (c) 评审与门禁

没有堆叠评审。门禁为已执行的 `IrCompilerTest` + `CodegenModeTest` 聚焦测试套件，
其中包含新的三路径 JVM 验证，以及在 `java -Xverify:all -Xcheck:jni` 下运行的
CMake/g++ 编译运行 Java 一致性测试。

## (d) 前置条件

其余构造器遗留形状、不安全的 constant dynamic 和 `jsr`/`ret` 继续在修改前拒绝。
`--codegen` 保持为 `legacy`。
