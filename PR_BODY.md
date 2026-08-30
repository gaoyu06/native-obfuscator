# Summary / 摘要

- Add fixture-only IR admission coverage for an extra-local `int` used as the first, third, and fourth arguments of a four-argument `java.awt.Insets` `NEW` initializer.
- Wire the exact fixture shape so the retained JVM prefix emits `ILOAD 3`, `ICONST_2`, `ILOAD 3`, `ILOAD 3` for each initializer.
- Add admission, JVM verification, and Java/native parity tests. No production processor or default changes.

- 新增仅测试夹具的 IR 准入覆盖：四参数 `java.awt.Insets` 的 `NEW` 初始化中，第一、第三和第四个参数使用额外局部 `int`。
- 为精确夹具形状接线，使保留的 JVM 前缀在每次初始化时生成 `ILOAD 3`、`ICONST_2`、`ILOAD 3`、`ILOAD 3`。
- 新增准入、JVM 验证和 Java/native 一致性测试；不修改生产处理器或默认选项。

## Validation / 验证

```sh
CC=gcc CXX=g++ ./gradlew :obfuscator:test --rerun-tasks \
  --tests by.radioegor146.ir.IrCompilerTest \
  --tests by.radioegor146.CodegenModeTest
```

Child XML totals: 613 passing `IrCompilerTest` tests plus 7 passing `CodegenModeTest` tests, 620 total. The parent reruns the focused gate; child XML is discarded.

子任务 XML 总数：`IrCompilerTest` 613 项通过，`CodegenModeTest` 7 项通过，共 620 项。父任务会重新运行聚焦测试门禁；子任务 XML 将被丢弃。

## Scope / 范围

- Processor changed: No
- Defaults changed: No
- Ship-ready: No

- 处理器变更：否
- 默认选项变更：否
- 可发布：否
