## English

### Leftover admitted

- Admits one leaf-only `DSUB` or `DMUL` as a proven double constructor-chain
  argument, alongside the existing `DADD` case.
- Keeps the one-level double binary budget unchanged and retains all double
  arithmetic in the JVM bytecode prefix.
- Adds admission, JVM verification, and full CMake/g++ JNI parity coverage for
  both shapes.

### Still rejected

- `DDIV`, `DREM`, and `DNEG`.
- Nested double binaries and extra-local double operands.

### Verification

```text
CC=gcc CXX=g++ ./gradlew :obfuscator:test --rerun-tasks \
  --tests by.radioegor146.ir.IrCompilerTest \
  --tests by.radioegor146.CodegenModeTest
```

JUnit XML totals: pending.

Ship-ready: **No**

## 中文

### 本次准入的剩余项

- 在已有 `DADD` 的基础上，准入单层、叶子操作数已证明的 `DSUB` 和
  `DMUL` 构造器链参数。
- double 二元运算预算仍为一层；所有 double 运算仍保留在 JVM 字节码前缀
  中执行。
- 为两种形状新增准入、JVM 校验以及完整 CMake/g++ JNI 结果一致性测试。

### 仍然拒绝

- `DDIV`、`DREM` 和 `DNEG`。
- 嵌套 double 二元运算和额外局部变量中的 double 操作数。

### 验证

```text
CC=gcc CXX=g++ ./gradlew :obfuscator:test --rerun-tasks \
  --tests by.radioegor146.ir.IrCompilerTest \
  --tests by.radioegor146.CodegenModeTest
```

JUnit XML 汇总：待运行。

可发布：**否（No）**
