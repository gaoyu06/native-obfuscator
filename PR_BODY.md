# Conditional constructor extra / 条件构造器额外局部变量

## English

### Scope

This change adds one fail-closed constructor-split admission after
[#174](https://github.com/gaoyu06/native-obfuscator/pull/174):

- exactly two mutually exclusive direct this/super calls;
- the first call returns immediately in retained bytecode;
- the final call falls through to the IR suffix;
- at least one forwarded extra local is unassigned at the exiting call but has
  one compatible type on every CFG path that reaches the hidden JNI bridge; and
- no exception table and empty chain-entry operand stacks.

The immediate-return path never loads the extra or calls the bridge. No
synthetic `null` or zero value is introduced. The existing rejection for an
extra that is unassigned on a bridge-taking path remains unchanged, as do
category-2 overlap checks.

### Readiness and review

- Ship-ready: **No**.
- Stacked review: **No**; this branch is based directly on `origin/master` at
  `88d8890` (the post-[#174](https://github.com/gaoyu06/native-obfuscator/pull/174)
  baseline).
- The gate is the focused test suite, not a production-readiness claim.

### Preconditions and unchanged defaults

Remaining constructor leftovers, unsafe constant-dynamic forms, and `jsr`/`ret`
stay rejected. `--codegen` still defaults to `legacy`; this change does not flip
`--ir-lower` or `--backend`. The production goal is not marked complete.

### Verification

```text
CC=gcc CXX=g++ ./gradlew :obfuscator:test --rerun-tasks \
  --tests by.radioegor146.ir.IrCompilerTest \
  --tests by.radioegor146.CodegenModeTest
```

JUnit XML: 164 `IrCompilerTest` + 7 `CodegenModeTest` = 171 tests, with 0
failures, 0 errors, and 0 skipped. The new CMake/g++ harness compares plain Java
with `--codegen=ir` under `java -Xverify:all -Xcheck:jni` for both the assigned
bridge path and the prefix-exit path.

## 中文

### 范围

本变更在
[#174](https://github.com/gaoyu06/native-obfuscator/pull/174)
之后新增一种严格失败关闭的构造器拆分准入规则：

- 恰好存在两个互斥的直接 this/super 构造调用；
- 第一个调用在保留的字节码中立即 `RETURN`；
- 最后一个调用直接进入 IR 后缀；
- 至少一个需要转发的额外局部变量在提前退出调用处未赋值，但在所有实际到达隐藏
  JNI 桥的 CFG 路径上都能证明为同一种兼容类型；
- 不存在异常表，且两个构造调用入口的操作数栈均为空。

提前退出路径不会读取该局部变量，也不会调用隐藏桥。本变更不会合成 `null` 或
零值。若未赋值路径能够到达隐藏桥，原有拒绝规则保持不变；category-2 槽位重叠
检查也保持不变。

### 就绪状态与评审

- 可直接发布：**否（No）**。
- 堆叠评审：**无（No）**；本分支直接基于 `origin/master` 的 `88d8890`
  （[#174](https://github.com/gaoyu06/native-obfuscator/pull/174) 合入后的基线）。
- 准入门槛是聚焦测试套件通过，不代表生产就绪。

### 前置条件与不变默认值

其余构造器遗留形态、不安全的 constant-dynamic 形态以及 `jsr`/`ret` 仍然拒绝。
`--codegen` 仍默认使用 `legacy`；本变更不切换 `--ir-lower` 或 `--backend`。
生产目标未标记为完成。

### 验证

```text
CC=gcc CXX=g++ ./gradlew :obfuscator:test --rerun-tasks \
  --tests by.radioegor146.ir.IrCompilerTest \
  --tests by.radioegor146.CodegenModeTest
```

JUnit XML 记录：`IrCompilerTest` 164 项，`CodegenModeTest` 7 项，共 171 项；
失败 0、错误 0、跳过 0。新增的 CMake/g++ 运行测试在
`java -Xverify:all -Xcheck:jni` 下比较普通 Java 与 `--codegen=ir` 输出，并覆盖
已赋值桥接路径和前缀提前退出路径。
