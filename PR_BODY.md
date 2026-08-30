# Summary / 摘要

- Raise the finite constructor chain-input binary nesting budget from 8 to 16
  for int, long, float, and double carriers.
- 将构造器链输入的有限二元嵌套预算从 8 提升至 16，覆盖 int、long、float
  和 double 四类 JVM carrier。
- Admit the existing nine-level `IADD`, `LADD`, `FADD`, and `DADD` leftovers
  while retaining their arithmetic in the JVM constructor prefix behind one
  hidden bridge.
- 准入现有九层 `IADD`、`LADD`、`FADD`、`DADD` 遗留形态；运算继续留在 JVM
  构造器前缀中，并共享一个隐藏桥接方法。

# Safety / 安全性

- Keep `MAX_DISTINCT_SUFFIXES` at 8 and reject seventeen-level binary trees
  before any constructor, proxy, or hidden-method mutation.
- `MAX_DISTINCT_SUFFIXES` 保持为 8；十七层二元树在构造器、代理方法或隐藏方法
  发生任何修改之前即失败关闭。
- Existing unsafe skip-super, `ASTORE 0`, unsafe `*NEG`, unproven shift-count,
  prefix-to-suffix jump, spanning-catch, and unassigned-extra rejects remain
  unchanged.
- 现有 skip-super、`ASTORE 0`、不安全 `*NEG`、未经证明的移位计数、前缀到后缀
  跳转、跨区间 catch、未赋值额外局部变量等拒绝规则均保持不变。
- Ship-ready: **No**

# Verification / 验证

- `IrCompilerTest`: 412 tests, 0 skipped, 0 failures, 0 errors.
- `CodegenModeTest`: 7 tests, 0 skipped, 0 failures, 0 errors.
- `IrCompilerTest`：412 项测试，0 跳过，0 失败，0 错误。
- `CodegenModeTest`：7 项测试，0 跳过，0 失败，0 错误。
- Added dedicated admission and rewritten-class JVM verification coverage for
  all four nine-level families, plus one combined CMake/g++ JNI Java-parity
  runtime under `-Xverify:all -Xcheck:jni`.
- 已为四类九层形态增加专用准入与改写后 JVM 验证覆盖，并增加一次合并的
  CMake/g++ JNI Java 一致性运行（`-Xverify:all -Xcheck:jni`）。
