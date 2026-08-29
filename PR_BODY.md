# English

(a) Does this complete the production goal? **No.**

(b) Is this ship-ready? **No.**

(c) What changed?

- Extends the bounded constructor chain-input proof from the two binary levels
  landed in #197 to exactly three levels for `IADD`, `ISUB`, `IMUL`, `IAND`,
  `IOR`, `IXOR`, `ISHL`, `ISHR`, and `IUSHR`.
- Uses an explicit depth budget of three; four-or-more-level trees and
  extra-local operands remain rejected before constructor mutation.
- Keeps `IDIV` and `IREM` leaf-only outer operations. They are not part of the
  recursive binary set, so nested or inner-tree division and remainder remain
  rejected.
- Adds admission/rewrite, JVM verification, and CMake/g++ Java-parity coverage
  for three immediate superclass returns whose call inputs are all three-level
  trees. The retained bytecode still converges on one hidden native bridge.
- The default remains `--codegen legacy`.

(d) How was it verified?

- Ran the required focused Gradle rerun with `CC=gcc CXX=g++`.
- JUnit XML records `IrCompilerTest` 271/271 and `CodegenModeTest` 7/7:
  278 tests total, with 0 failures, 0 errors, and 0 skipped.
- The runtime parity test used `CodegenMode.IR`, CMake/g++, `-Xverify:all`, and
  `-Xcheck:jni`.

# 中文

(a) 这是否完成生产目标？**否。**

(b) 这是否已可交付？**否。**

(c) 本次改动：

- 在 #197 已支持两层二元运算的基础上，将构造链输入证明严格扩展到三层；
  递归集合仍仅包含 `IADD`、`ISUB`、`IMUL`、`IAND`、`IOR`、`IXOR`、
  `ISHL`、`ISHR` 和 `IUSHR`。
- 使用显式的三层深度预算；四层及以上的树和额外局部变量操作数仍会在构造函数
  变更前被拒绝。
- `IDIV` 和 `IREM` 仍只能作为最外层且两个操作数均为叶子的运算。它们没有加入
  递归二元运算集合，因此嵌套或作为内部节点的除法、取余仍被拒绝。
- 新增三个立即调用父类构造函数并返回的接纳与重写、JVM 验证及 CMake/g++
  Java 输出一致性测试；每个调用输入都是三层树，保留的字节码仍汇合到一个隐藏
  native 桥接方法。
- 默认选项仍为 `--codegen legacy`。

(d) 验证方式：

- 使用 `CC=gcc CXX=g++` 执行了要求的 Gradle 聚焦重跑。
- JUnit XML 记录 `IrCompilerTest` 271/271、`CodegenModeTest` 7/7；
  总计 278 个测试，失败 0、错误 0、跳过 0。
- 运行时一致性测试使用 `CodegenMode.IR`、CMake/g++、`-Xverify:all` 和
  `-Xcheck:jni`。
