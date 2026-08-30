# English

## Summary

- Documents the unproven extra-local array `AALOAD` constructor-chain
  leftover as fail-closed; this is not an admission.
- Keeps reject-before-mutation coverage and adds executed Java 8 JVM
  verification for every loadable fixture shape.
- Leaves the admitted extra-array plus extra-index composition unchanged.

## Tests

- `rejectsUnprovenExtraLocalArrayAaloadSourcesBeforeMutation`
- `unprovenExtraLocalArrayAaloadShapesPassJava8JvmVerification`

Gate:

```bash
CC=gcc CXX=g++ ./gradlew :obfuscator:test --rerun-tasks \
  --tests by.radioegor146.ir.IrCompilerTest \
  --tests by.radioegor146.CodegenModeTest
```

Parent JUnit XML under `obfuscator/build/test-results/test/`:

- `IrCompilerTest`: 497 tests, 0 failures, 0 errors, 0 skipped.
- `CodegenModeTest`: 7 tests, 0 failures, 0 errors, 0 skipped.
- Total: 504 tests, 0 failures, 0 errors, 0 skipped.

Cite `unprovenExtraLocalArrayAaloadShapesPassJava8JvmVerification`. Child totals are discarded.

- Admitted: **No** (leftover remains reject)
- Ship-ready: **No**

# 中文

## 摘要

- 将未证明的 extra-local array `AALOAD` 构造器链输入记录为 fail-closed
  leftover；本次不是准入。
- 保留 mutation 前拒绝覆盖，并为所有 JVM 可加载夹具增加实际执行的
  Java 8 JVM 验证。
- 已准入的 extra-array 与 extra-index 组合保持不变。

## 测试

- `rejectsUnprovenExtraLocalArrayAaloadSourcesBeforeMutation`
- `unprovenExtraLocalArrayAaloadShapesPassJava8JvmVerification`

门禁：

```bash
CC=gcc CXX=g++ ./gradlew :obfuscator:test --rerun-tasks \
  --tests by.radioegor146.ir.IrCompilerTest \
  --tests by.radioegor146.CodegenModeTest
```

父级 `obfuscator/build/test-results/test/` JUnit XML 汇总：

- `IrCompilerTest`：497 个测试，0 个失败，0 个错误，0 个跳过。
- `CodegenModeTest`：7 个测试，0 个失败，0 个错误，0 个跳过。
- 总计：504 个测试，0 个失败，0 个错误，0 个跳过。

引用 `unprovenExtraLocalArrayAaloadShapesPassJava8JvmVerification`。子代理汇总作废。

- Admitted: **No**（leftover 继续拒绝）
- Ship-ready: **No**
