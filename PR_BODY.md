## English

### Scope

This change narrowly admits constructors with three or more reachable direct
this/super calls when every selected call:

- receives the original constructor receiver;
- receives only direct loads of declared constructor arguments;
- has an empty pre-existing operand stack at entry; and
- is immediately followed by `RETURN`.

The existing CFG proof still requires every successful path to execute exactly
one direct chain call and every candidate to be reachable. After those checks,
each noncanonical return is replaced with `GOTO` to the canonical return, then
the existing one-join / one-hidden-bridge split is reused.

Computed or constant chain inputs, unequal suffixes, post-call work,
double-call paths, skip-call paths, unsafe receiver stores, and general
multi-exit rewriting remain rejected. Existing admitted constructor shapes are
unchanged.

### Readiness

Ship-ready: **No**.

This is a focused opt-in IR constructor-split increment. It does not complete
the production goal and does not change the default codegen, IR-lowering, or
backend selections.

### Review and gate

No stacked review is requested. The gate is the focused test suite:

```text
CC=gcc CXX=g++ ./gradlew :obfuscator:test --rerun-tasks \
  --tests by.radioegor146.ir.IrCompilerTest \
  --tests by.radioegor146.CodegenModeTest
```

JUnit XML: 167 `IrCompilerTest` + 7 `CodegenModeTest` = 174 tests, with
0 failures, 0 errors, and 0 skipped. The new CMake/g++ harness compares plain
Java with `--codegen=ir` for all three retained chain-call paths under
`java -Xverify:all -Xcheck:jni`.

### Preconditions

Remaining constructor leftovers, unsafe constant-dynamic forms, and `jsr`/`ret`
must stay rejected. No default flip is included.

## 中文

### 范围

本变更仅在以下条件全部可证明时，有限放行包含三个或更多可达直接
this/super 调用的构造函数：

- 每个被选择的调用都使用原始构造函数接收者；
- 调用参数只来自构造函数已声明参数的直接加载；
- 调用入口没有遗留操作数栈值；
- 每个调用后都立即执行 `RETURN`。

现有 CFG 证明仍要求每条成功路径恰好执行一次直接构造链调用，并且每个
候选调用都可达。证明通过后，将非规范 `RETURN` 替换为跳转到规范返回的
`GOTO`，然后复用现有的单汇合点、单隐藏桥接拆分。

计算值或常量形式的调用输入、不相等后缀、调用后工作、同一路径执行两次
构造链调用、跳过构造链调用、不安全的接收者写入，以及通用多出口改写仍然
拒绝。已有放行的构造函数形状保持不变。

### 就绪状态

可发布：**否**。

这是 opt-in IR 构造函数拆分路径上的小范围增量，不代表生产目标完成，也
不更改默认 codegen、IR lowering 或 backend 选择。

### 审查与门禁

不要求 stacked review；门禁为以下聚焦测试：

```text
CC=gcc CXX=g++ ./gradlew :obfuscator:test --rerun-tasks \
  --tests by.radioegor146.ir.IrCompilerTest \
  --tests by.radioegor146.CodegenModeTest
```

JUnit XML 结果：`IrCompilerTest` 167 项，`CodegenModeTest` 7 项，共
174 项；失败 0、错误 0、跳过 0。新增 CMake/g++ 运行测试在
`java -Xverify:all -Xcheck:jni` 下覆盖三个保留的构造链调用路径，并比较
普通 Java 与 `--codegen=ir` 的输出。

### 前置条件

其余构造函数遗留形状、不安全的 constant-dynamic 形式以及 `jsr`/`ret`
必须继续拒绝。本变更不包含任何默认选项切换。
