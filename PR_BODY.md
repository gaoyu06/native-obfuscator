## English

### Summary

- admit constructor `IALOAD` chain-input leaves when the array source is a
  proven prefix extra-local copy of an unchanged declared `int[]` argument
- retain the `ASTORE`, constant index, and `IALOAD` in the JVM prefix so null,
  bounds, and array semantics remain JVM-executed
- keep computed stores, overwritten locals or source arguments, array stores,
  non-`int[]` sources, and computed indexes fail-closed before mutation
- add admission, JVM verification, native Java-parity, and rejection coverage

### Verification

- Focused Gradle/XML results will be recorded after the pre-test commit is
  pushed.

Ship-ready: **No**

## 中文

### 摘要

- 构造器链调用参数中的 `IALOAD` 现在可使用已证明的前缀额外局部变量；
  该局部变量必须由未被修改的已声明 `int[]` 参数经直接 `ALOAD` 和唯一、
  支配所有链调用的 `ASTORE` 复制而来
- `ASTORE`、常量索引和 `IALOAD` 全部保留在 JVM 前缀中，因此空指针、
  越界和数组语义仍由 JVM 执行
- 计算得到的存储值、被覆盖的额外局部变量或源参数、数组写入、非
  `int[]` 源以及计算索引仍会在任何改写前按失败关闭策略拒绝
- 增加准入、JVM 校验、原生代码与 Java 行为一致性以及拒绝路径覆盖

### 验证

- 在测试前提交推送后，将补充聚焦 Gradle 测试和 XML 汇总结果。

可发布：**否**
