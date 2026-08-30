# English

## Summary

- Audits the existing seventeen-level constructor chain-input rejects while
  keeping all int, long, float, and double binary-depth budgets at 16.
- Strengthens pre-mutation checks for the existing long/float/double and
  wide-array-backed seventeen-level reject suites: constructor instruction
  identity, generated output, hidden-method inventory, and the singular
  `MethodContext.proxyMethod` remain unchanged.
- Adds `seventeenLevelNestedBinariesPassJava8JvmVerification`, which loads
  untouched classfile-52 int/long/float/double add fixtures and executes their
  positive, negative, and zero construction paths.
- Records the conservative JVM-verification coverage in
  `docs/architecture/ir-flex-ctor-status.md`.

## Verification

```text
CC=gcc CXX=g++ ./gradlew :obfuscator:test --rerun-tasks \
  --tests by.radioegor146.ir.IrCompilerTest \
  --tests by.radioegor146.CodegenModeTest
```

JUnit XML (`obfuscator/build/test-results/test/`):

- `IrCompilerTest`: 472 tests, 0 skipped, 0 failures, 0 errors
- `CodegenModeTest`: 7 tests, 0 skipped, 0 failures, 0 errors
- Total: 479 tests, 0 skipped, 0 failures, 0 errors

## Scope

- Seventeen-level trees admitted: **No**
- Compiler defaults changed: **No**
- Ship-ready: **No**

# 中文

## 摘要

- 审计现有的构造器链输入十七层二元树拒绝行为，并保持 int、long、float、
  double 四类二元深度上限均为 16。
- 加强现有 long/float/double 以及数组叶子十七层拒绝用例的修改前断言：
  构造器指令对象、生成输出、隐藏方法清单和唯一的
  `MethodContext.proxyMethod` 均保持不变。
- 新增 `seventeenLevelNestedBinariesPassJava8JvmVerification`，加载未经 IR
  改写的 classfile 52 int/long/float/double 加法夹具，并执行正数、负数和零
  三条正常构造路径。
- 在 `docs/architecture/ir-flex-ctor-status.md` 中记录该保守拒绝的 JVM
  验证覆盖。

## 验证

```text
CC=gcc CXX=g++ ./gradlew :obfuscator:test --rerun-tasks \
  --tests by.radioegor146.ir.IrCompilerTest \
  --tests by.radioegor146.CodegenModeTest
```

JUnit XML（`obfuscator/build/test-results/test/`）：

- `IrCompilerTest`：472 个测试，0 跳过，0 失败，0 错误
- `CodegenModeTest`：7 个测试，0 跳过，0 失败，0 错误
- 合计：479 个测试，0 跳过，0 失败，0 错误

## 范围

- 是否接纳十七层树：**否**
- 是否修改编译器默认值：**否**
- 是否可发布：**否**
