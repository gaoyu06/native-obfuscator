# Constructor prefix-to-suffix conditional branch / 构造函数前缀到后缀的条件分支

## English

### Scope

- Admit one exact opt-in IR constructor-split shape with two direct
  `this`/`super` calls: after the first proven receiver call,
  `ILOAD <declared int-family argument>; IFNE <shared suffix>; RETURN`; the
  second receiver call falls through to the same suffix.
- Preserve the conditional branch and early return in bytecode, and compile
  only the shared initialized-receiver suffix through the existing IR/JNI path.
- Require an empty exception table, empty chain-call entry stacks, the original
  constructor receiver at both calls, and exactly one direct chain call at every
  suffix entry and successful return.
- Keep general prefix-to-suffix jumps/switches and skip-super paths rejected
  before mutation.

### Readiness and review

- Ship-ready: **No**.
- No stacked review is required. The review gate is the focused constructor and
  codegen test command, including JVM verification and CMake/g++ JNI runtime
  parity.
- The default remains `--codegen=legacy`; this change does not flip
  `--ir-lower`, `--backend`, or any production default.

### Focused gate

```text
CC=gcc CXX=g++ ./gradlew :obfuscator:test --rerun-tasks \
  --tests by.radioegor146.ir.IrCompilerTest \
  --tests by.radioegor146.CodegenModeTest
```

Parsed JUnit XML: 161 `IrCompilerTest` tests plus 7 `CodegenModeTest` tests,
168 total; 0 failures, 0 errors, and 0 skipped. The gate passed.

### Preconditions

- Remaining constructor leftovers stay rejected.
- Unsafe constant-dynamic forms stay rejected.
- `jsr`/`ret` stays rejected.
- No default flip is authorized by this change.

## 中文

### 范围

- 仅接纳一种精确的、需显式启用的 IR 构造函数拆分形态，其中有两个直接
  `this`/`super` 调用：第一个已证明使用原始接收者的调用之后必须是
  `ILOAD <已声明的 int 类参数>; IFNE <共享后缀>; RETURN`，第二个调用则
  顺序进入同一个共享后缀。
- 条件分支和提前返回继续保留在字节码中；只有接收者已初始化的共享后缀
  通过现有 IR/JNI 路径编译。
- 要求异常表为空、两个调用的入口操作数栈无遗留值、两个调用都使用原始
  构造函数接收者，并且每个后缀入口及成功返回路径都恰好执行一次直接
  构造链调用。
- 一般性的前缀到后缀跳转/开关以及跳过 `super` 的路径仍在任何修改前拒绝。

### 就绪状态与审查

- 可发布：**否**。
- 不需要堆叠审查。审查门槛是聚焦的构造函数与代码生成测试命令，其中包括
  JVM 验证以及 CMake/g++ JNI 运行时一致性。
- 默认值仍为 `--codegen=legacy`；本变更不会翻转 `--ir-lower`、
  `--backend` 或任何生产默认值。

### 聚焦测试门槛

执行上方完整测试命令。解析后的 JUnit XML 结果为：
`IrCompilerTest` 161 项、`CodegenModeTest` 7 项，共 168 项；失败 0、
错误 0、跳过 0。测试门槛已通过。

### 前置条件

- 其余构造函数遗留形态继续拒绝。
- 不安全的常量动态形式继续拒绝。
- `jsr`/`ret` 继续拒绝。
- 本变更不授权翻转任何默认值。
