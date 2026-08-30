## English

### Summary

- documents the fail-closed constructor-split boundary for isolated `NEW`
  initializer arguments with long, float, or double carriers
- adds reject-before-mutation coverage without admitting new bytecode shapes or
  changing the processor
- loads and executes verifier-valid Java 8 `Date(J)`, `Point2D.Float(FF)`, and
  `Point2D.Double(DD)` reject fixtures on all three constructor paths

### Tests

- `rejectsUnprovenWideNewChainInputsBeforeMutation`
- `unprovenWideNewChainInputShapesPassJava8JvmVerification`

```text
CC=gcc CXX=g++ ./gradlew :obfuscator:test --rerun-tasks \
  --tests by.radioegor146.ir.IrCompilerTest \
  --tests by.radioegor146.CodegenModeTest
```

Local XML:

- `obfuscator/build/test-results/test/TEST-by.radioegor146.ir.IrCompilerTest.xml`:
  504 tests, 0 skipped, 0 failures, 0 errors
- `obfuscator/build/test-results/test/TEST-by.radioegor146.CodegenModeTest.xml`:
  7 tests, 0 skipped, 0 failures, 0 errors

The parent reruns the gate; child pre-rebase totals are discarded.

Admitted: **No**

Ship-ready: **No**

## 中文

### 摘要

- 记录构造器拆分对独立 `NEW` 初始化器中 long、float、double 参数的保守拒绝边界
- 增加拒绝前不变性覆盖，不放行新的字节码形态，也不修改处理器
- 在三个构造器路径上加载并执行可通过 Java 8 JVM 校验的 `Date(J)`、
  `Point2D.Float(FF)` 和 `Point2D.Double(DD)` 拒绝样例

### 测试

- `rejectsUnprovenWideNewChainInputsBeforeMutation`
- `unprovenWideNewChainInputShapesPassJava8JvmVerification`

```text
CC=gcc CXX=g++ ./gradlew :obfuscator:test --rerun-tasks \
  --tests by.radioegor146.ir.IrCompilerTest \
  --tests by.radioegor146.CodegenModeTest
```

本地 XML：

- `obfuscator/build/test-results/test/TEST-by.radioegor146.ir.IrCompilerTest.xml`：
  504 个测试，0 skipped，0 failures，0 errors
- `obfuscator/build/test-results/test/TEST-by.radioegor146.CodegenModeTest.xml`：
  7 个测试，0 skipped，0 failures，0 errors

父任务会重新执行该门禁；子任务在 rebase 前的统计将被丢弃。

Admitted（放行）: **No**

Ship-ready（可发布）: **No**
