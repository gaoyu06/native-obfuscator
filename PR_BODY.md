## English

### Scope

- Admits the former `float-binary` constructor-chain leftover: one leaf-only
  `FADD` whose operands are declared float argument loads or float constants.
- Uses a separate one-level float binary budget; the int and long budgets
  remain unchanged at four.
- Keeps `FADD` and its JVM float semantics in the retained bytecode prefix.
  All paths continue to share one hidden JNI bridge and the singular
  `MethodContext.proxyMethod`.

### Still rejected

- Nested `FADD`; `FSUB`, `FMUL`, `FDIV`, `FREM`, and `FNEG`.
- Extra-local float operands.
- Computed double and reference constructor-chain inputs.

### Verification

```text
CC=gcc CXX=g++ ./gradlew :obfuscator:test --rerun-tasks \
  --tests by.radioegor146.ir.IrCompilerTest \
  --tests by.radioegor146.CodegenModeTest
```

JUnit XML totals: 317 tests (310 `IrCompilerTest` + 7
`CodegenModeTest`), 0 failures, 0 errors, 0 skipped.

Ship-ready: **No**.

## 中文

### 范围

- 接纳原先的 `float-binary` 构造器链遗留项：仅允许一层、叶子操作数形式的
  `FADD`；操作数必须是已声明的 float 参数加载或 float 常量。
- float 二元表达式使用独立的一层预算；int 和 long 的四层预算保持不变。
- `FADD` 及其 JVM 浮点语义保留在字节码前缀中执行。所有路径仍共享一个隐藏
  JNI 桥接方法，并继续使用唯一的 `MethodContext.proxyMethod`。

### 仍然拒绝

- 嵌套 `FADD`，以及 `FSUB`、`FMUL`、`FDIV`、`FREM` 和 `FNEG`。
- 从额外局部变量加载的 float 操作数。
- 计算得到的 double 和引用类型构造器链输入。

### 验证

```text
CC=gcc CXX=g++ ./gradlew :obfuscator:test --rerun-tasks \
  --tests by.radioegor146.ir.IrCompilerTest \
  --tests by.radioegor146.CodegenModeTest
```

JUnit XML 汇总：共 317 项测试（`IrCompilerTest` 310 项，
`CodegenModeTest` 7 项），0 失败，0 错误，0 跳过。

可发布：**否（No）**。
