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

Local JUnit XML: pending focused gate.

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

本地 JUnit XML：等待 focused gate。

- Admitted: **No**（leftover 继续拒绝）
- Ship-ready: **No**
