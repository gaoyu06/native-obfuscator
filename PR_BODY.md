# Bounded constructor-chain `IADD` input / 有界构造函数链 `IADD` 输入

## English

**(a) Scope:** After #179's direct declared-argument loads, int-family
constants, and one-load `INEG`, and #180's identical nonempty suffix copies,
this increment admits one `IADD` for an int-family constructor-chain argument
only when both operands independently match those already-proven leaf inputs.
The same bounded walker applies to three-or-more immediate-`RETURN` and
identical-suffix constructors. Nested `IADD`, all other arithmetic, extra-local
operands, rewritten receivers, and other unlisted input shapes remain rejected.

**(b) Ship-ready?** **No.** This is one fail-closed compiler admission
increment and does not change production defaults or complete the production
goal.

**(c) Review and gate:** There is no stacked review. The gate is the executed
focused suite for `IrCompilerTest` and `CodegenModeTest`, including JVM
verification and the new three-path compile-and-run CMake/g++ JNI parity
harness under `java -Xverify:all -Xcheck:jni`.

**(d) Preconditions:** Remaining constructor leftovers, unsafe constant
dynamic forms, and `jsr`/`ret` continue to reject before mutation.
`--codegen` remains `legacy`.

## 中文

**(a) 范围：** 在 #179 已支持的构造函数链输入（直接加载已声明参数、int
家族常量、以及对直接加载参数执行一次 `INEG`）和 #180 已支持的相同非空后缀
副本基础上，本次仅新增一种 int 家族参数：一次 `IADD`，且两个操作数都必须各自
满足上述已证明的叶子输入规则。同一有界反向检查适用于三个及以上立即
`RETURN` 或相同后缀的构造函数。嵌套 `IADD`、其他算术、额外局部变量操作数、
改写后的接收者及其他未列出的输入形状仍然拒绝。

**(b) 可发布？** **否。** 这只是一次失败关闭的编译器准入增量，不改变生产
默认值，也不代表生产目标已经完成。

**(c) 评审与门禁：** 不存在堆叠评审。门禁为已执行的 `IrCompilerTest` 与
`CodegenModeTest` 聚焦测试套件，其中包含 JVM 验证，以及新增的三路径
CMake/g++ JNI 编译运行一致性测试；Java 运行参数为
`java -Xverify:all -Xcheck:jni`。

**(d) 前置条件：** 其余构造函数遗留形状、不安全的 constant dynamic 形状及
`jsr`/`ret` 继续在任何改写前拒绝；`--codegen` 仍为 `legacy`。
