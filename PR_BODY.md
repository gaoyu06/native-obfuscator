## English

### (a) What changed?

Admits exactly two reachable constructor-chain calls with distinct, nonempty
straight-line suffixes. The bytecode wrapper retains both `this`/`super` calls
and invokes one shared hidden bridge with a trailing constant path id (`0` or
`1`). The independent IR body branches on that id and contains both proven
suffixes.

The proof remains fail-closed for three calls, branched suffixes, exception
tables, extra-local suffix access, non-original receivers, and chain inputs
outside the existing locally proven leaf families.

### (b) Ship-ready?

No.

### (c) Verification

The focused `IrCompilerTest` and `CodegenModeTest` gate covers frontend
construction, JVM verification, CMake/g++ JNI execution with Java parity, and
pre-mutation rejection cases. JUnit XML reports `IrCompilerTest` 207/207 and
`CodegenModeTest` 7/7, for 214 tests total with 0 failures, 0 errors, and
0 skipped.

### (d) Design and follow-up

This uses one hidden bridge and one existing compiler pipeline. Identical
suffix copies still use the prior one-join rewrite, and immediate-return
multi-call constructors remain unchanged. Broader multi-exit CFGs, suffix
exception tables, and suffix extra-local forwarding remain future work.

## 中文

### (a) 修改内容

新增对一种构造函数形态的准入：恰好有两个可达的构造链调用，且各自具有非空、
直线执行、互不相同的后缀。字节码包装层保留两个 `this`/`super` 调用，并分别
以常量路径编号 `0` 或 `1` 调用同一个隐藏桥接方法。独立 IR 方法根据路径编号
分支，并包含两个已证明安全的后缀。

对于三个调用、带分支的后缀、异常表、后缀额外局部变量访问、非原始接收者，
以及不属于现有局部叶子证明范围的构造链输入，仍然采用失败关闭策略。

### (b) 可直接上线？

否。

### (c) 验证

聚焦测试门禁 `IrCompilerTest` 与 `CodegenModeTest` 覆盖 IR 前端构建、JVM
校验、CMake/g++ JNI 与 Java 输出一致性，以及改写前拒绝检查。JUnit XML
统计为 `IrCompilerTest` 207/207、`CodegenModeTest` 7/7，共 214 个测试，
失败 0、错误 0、跳过 0。

### (d) 设计与后续

本实现只使用一个隐藏桥接方法和现有的一条编译流水线。指令完全相同的后缀副本
仍走已有的单汇合点改写；立即 `RETURN` 的多调用构造函数行为不变。更广泛的
多出口 CFG、后缀异常表和后缀额外局部变量转发仍留待后续处理。
