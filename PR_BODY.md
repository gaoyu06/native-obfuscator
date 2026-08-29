## English

### Scope

Admit a narrow constructor-prefix `ASTORE 0` case on the opt-in IR
constructor-split path. A must-style CFG analysis requires every such store to
receive the original constructor receiver and requires each selected this/super
chain call to consume that same receiver. Proven copies through locals and JVM
stack shuffles are supported; general receiver-alias forwarding is not.

The retained wrapper keeps the prefix and this/super call in bytecode, then
loads local 0 for the hidden bridge. Ambiguous or non-receiver stores reject
before constructor, hidden-pool, or C++ mutation.

Focused tests cover split admission, reject-before-mutation behavior, JVM
verification to the unresolved native bridge, and plain-Java versus full IR JNI
stdout parity through CMake/g++ with `-Xverify:all -Xcheck:jni`.

### Readiness and review

- Ship-ready: **No**.
- Stacked review: **No**. The gate is the focused test suite.
- Default mode: unchanged; `--codegen` remains `legacy`.

### Gate

```text
CC=gcc CXX=g++ ./gradlew :obfuscator:test --rerun-tasks \
  --tests by.radioegor146.ir.IrCompilerTest \
  --tests by.radioegor146.CodegenModeTest
```

JUnit XML: 155 tests (148 + 7), 0 failures, 0 errors, 0 skipped.

### Preconditions that remain rejected

Unsafe/non-identity `ASTORE 0`, mixed prefix/suffix catch regions,
non-diamond multi-super control flow, conditionally assigned prefix extras,
unsafe `ConstantDynamic`, and `jsr`/`ret` remain rejected. This change does not
flip any default.

## 中文

### 范围

本变更仅在可选启用的 IR 构造函数拆分路径中，接纳一种严格受限的构造函数前缀
`ASTORE 0`。must-style CFG 分析要求每个此类存储的输入都可证明为原始构造函数
接收者，并要求每个选中的 this/super 链式调用都消费同一接收者。支持经局部变量
和 JVM 栈重排得到的已证明副本；不支持通用接收者别名转发。

保留的包装方法继续在字节码中执行前缀和 this/super 调用，之后从局部变量 0
加载接收者并调用隐藏桥。只要存储值有歧义或不是接收者，就会在修改构造函数、
隐藏方法池或 C++ 输出之前拒绝。

聚焦测试覆盖拆分接纳、拒绝前不产生修改、JVM 验证后到达未解析 native 桥，
以及通过 CMake/g++ 的普通 Java 与完整 IR JNI 输出一致性；运行参数包括
`-Xverify:all -Xcheck:jni`。

### 就绪状态与审查

- 可发布：**否**。
- 堆叠审查：**否**。准入门槛是聚焦测试套件。
- 默认模式：不变；`--codegen` 仍为 `legacy`。

### 测试门槛

```text
CC=gcc CXX=g++ ./gradlew :obfuscator:test --rerun-tasks \
  --tests by.radioegor146.ir.IrCompilerTest \
  --tests by.radioegor146.CodegenModeTest
```

JUnit XML：共 155 项（148 + 7），0 failure、0 error、0 skipped。

### 仍保持拒绝的前置条件

不安全或非同一接收者的 `ASTORE 0`、跨前缀/后缀的混合 catch 区域、非菱形
multi-super 控制流、条件赋值的前缀额外局部变量、不安全的
`ConstantDynamic`，以及 `jsr`/`ret` 仍会被拒绝。本变更不切换任何默认选项。
