# English

## Summary

- Strengthen the unbounded three-immediate reject test with explicit empty
  output, null proxy, and empty hidden-method assertions.
- Verify the untouched classfile-52 `post-call` fixture on selectors `7`, `-7`,
  and `0` without running the IR transform.
- Document why the `ACONST_NULL; ASTORE 0` fixture cannot be JVM-loaded and
  remains distinct from the admitted identity-preserving `ASTORE 0` form.

## Safety

- Admitted: **No**
- Ship-ready: **No**
- No production proof, default, suffix cap, binary-depth cap, or skip-super
  behavior changed.

## Verification

```text
CC=gcc CXX=g++ ./gradlew :obfuscator:test --rerun-tasks \
  --tests by.radioegor146.ir.IrCompilerTest \
  --tests by.radioegor146.CodegenModeTest
```

JUnit XML totals:

- `IrCompilerTest`: 484 tests, 0 failures, 0 errors, 0 skipped.
- `CodegenModeTest`: 7 tests, 0 failures, 0 errors, 0 skipped.
- Total: 491 tests, 0 failures, 0 errors, 0 skipped.

# 中文

## 摘要

- 为无界三分支立即返回拒绝测试补充显式断言，确认输出为空、代理方法为
  `null`，且隐藏方法清单为空。
- 不执行 IR 转换，直接以 classfile 52 加载 `post-call` 原始夹具，并执行选择值
  `7`、`-7` 和 `0`。
- 记录 `ACONST_NULL; ASTORE 0` 夹具无法通过 JVM 验证的原因，并说明它与已接纳
  的恒等保持 `ASTORE 0` 形式不同。

## 安全性

- 接纳新形状：**否**
- 可发布：**否**
- 未改动生产证明、默认值、后缀上限、二叉深度上限或 skip-super 行为。

## 验证

```text
CC=gcc CXX=g++ ./gradlew :obfuscator:test --rerun-tasks \
  --tests by.radioegor146.ir.IrCompilerTest \
  --tests by.radioegor146.CodegenModeTest
```

JUnit XML 汇总：

- `IrCompilerTest`：484 个测试，0 个失败，0 个错误，0 个跳过。
- `CodegenModeTest`：7 个测试，0 个失败，0 个错误，0 个跳过。
- 合计：491 个测试，0 个失败，0 个错误，0 个跳过。
