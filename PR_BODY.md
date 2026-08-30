# Fixture-only IR admission: extra-local `int` in Insets arguments 2–4

## English

### Summary

- Adds the fixture shape
  `new-constructor-extra-local-argument-four-second-third-fourth`.
- Covers a retained JVM constructor prefix with
  `NEW java/awt/Insets; DUP; ICONST_1; ILOAD 3; ILOAD 3; ILOAD 3;
  INVOKESPECIAL <init>(IIII)V`.
- Verifies the native `(II)V` body contains only `RETURN`, the proxy is
  `(Ljava/lang/Object;II)V`, and exactly one hidden bridge is emitted.
- Adds admission, JVM verification, and Java/native parity tests.
- Does not change the constructor processor, production compiler code, or
  code-generation defaults.

### Verification

```sh
CC=gcc CXX=g++ ./gradlew :obfuscator:test --rerun-tasks \
  --tests by.radioegor146.ir.IrCompilerTest \
  --tests by.radioegor146.CodegenModeTest
```

The child XML result is reported in the handoff and discarded. The parent
re-runs the focused gate.

Ship-ready: **No**

## 中文

### 摘要

- 新增夹具形状
  `new-constructor-extra-local-argument-four-second-third-fourth`。
- 覆盖保留在 JVM 前缀中的构造器字节码：
  `NEW java/awt/Insets; DUP; ICONST_1; ILOAD 3; ILOAD 3; ILOAD 3;
  INVOKESPECIAL <init>(IIII)V`。
- 验证原生 `(II)V` 方法体仅包含 `RETURN`，代理描述符为
  `(Ljava/lang/Object;II)V`，并且只生成一个隐藏桥接方法。
- 新增准入、JVM 校验和 Java/原生运行一致性测试。
- 不修改构造器处理器、生产编译器代码或代码生成默认值。

### 验证

```sh
CC=gcc CXX=g++ ./gradlew :obfuscator:test --rerun-tasks \
  --tests by.radioegor146.ir.IrCompilerTest \
  --tests by.radioegor146.CodegenModeTest
```

子任务 XML 结果会在交接信息中报告后丢弃；父任务会重新运行聚焦门禁。

可交付状态：**否**
