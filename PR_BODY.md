## English

### Summary

- admit the retained-prefix constructor leaf
  `ALOAD declared int[]; int-family constant; IALOAD`
- keep the complete array load in JVM bytecode behind one hidden bridge
- reject computed indexes and extra-local array sources before mutation
- cover admission, JVM verification, native parity, and fail-closed cases

### Validation

```text
CC=gcc CXX=g++ ./gradlew :obfuscator:test --rerun-tasks \
  --tests by.radioegor146.ir.IrCompilerTest \
  --tests by.radioegor146.CodegenModeTest
```

Ship-ready: **No**

## 中文

### 摘要

- 支持构造函数保留前缀中的
  `ALOAD 已声明的 int[]; int-family 常量; IALOAD` 叶节点
- 完整数组读取继续保留在 JVM 字节码中，并位于唯一隐藏桥接之后
- 计算索引和额外局部数组来源在任何修改前仍会被拒绝
- 覆盖准入、JVM 验证、原生执行一致性和安全拒绝场景

### 验证

```text
CC=gcc CXX=g++ ./gradlew :obfuscator:test --rerun-tasks \
  --tests by.radioegor146.ir.IrCompilerTest \
  --tests by.radioegor146.CodegenModeTest
```

可发布：**否**
