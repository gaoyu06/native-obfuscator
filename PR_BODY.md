## English

### Summary

- Raise the proven constructor chain-input binary-tree budget from 4 to 8 for
  int, long, float, and double families.
- Admit the existing five-level add and inner division fixtures, including the
  outer double-division fixture.
- Add per-family admission, rewritten JVM verification, and full CMake/g++ JNI
  Java-parity coverage.
- Add one nine-level rejection fixture per family to keep the new limit bounded
  and fail closed.

### Scope

- Chain-input trees remain in the retained JVM constructor prefix and share one
  hidden bridge after identical-return normalization.
- Leaf rules, extra-local admissions, unsafe `*NEG` handling, and CLI defaults
  are unchanged.

### Verification

```text
CC=gcc CXX=g++ ./gradlew :obfuscator:test --rerun-tasks \
  --tests by.radioegor146.ir.IrCompilerTest \
  --tests by.radioegor146.CodegenModeTest
```

Parsed JUnit XML: 378 tests, 0 failures, 0 errors, 0 skipped.

Ship-ready: **No**

## 中文

### 摘要

- 将 int、long、float、double 四类构造器链输入二叉树的已证明预算从 4
  提升到 8。
- 接纳现有五层加法、内层除法样例，以及 double 的外层除法样例。
- 为每种类型补充接纳测试、重写后 JVM 验证测试，以及完整的 CMake/g++
  JNI Java 一致性测试。
- 每种类型新增一个九层拒绝样例，确保新上限仍有边界并保持失败关闭。

### 范围

- 链输入树继续保留在 JVM 构造器前缀中；相同返回后缀归一化后共用一个
  hidden bridge。
- 不修改叶子规则、extra-local 接纳范围、不安全的 `*NEG` 处理或 CLI
  默认值。

### 验证

```text
CC=gcc CXX=g++ ./gradlew :obfuscator:test --rerun-tasks \
  --tests by.radioegor146.ir.IrCompilerTest \
  --tests by.radioegor146.CodegenModeTest
```

JUnit XML 解析结果：378 个测试，0 个失败，0 个错误，0 个跳过。

可交付状态（Ship-ready）：**No**
