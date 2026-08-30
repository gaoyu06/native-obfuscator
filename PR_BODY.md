## English

### Summary

- Fixture-only admission for `new-constructor-extra-local-argument-four-third`.
- Processor changed: No.
- Admitted: this compose only—the proven extra-local `int` copy is the third initializer argument of an isolated four-argument `java.awt.Insets` `NEW`.
- Ship-ready: No.
- Default flip: No; `--codegen=legacy`, `--ir-lower=direct`, and `--backend=cpp` remain unchanged.
- The leftover-inventory citation remains [#329](https://github.com/gaoyu06/native-obfuscator/pull/329); it is a measurement, not a badge.

### Tests

- `admitsThreeImmediateReturnsWithNewExtraLocalFourThirdArgChainInputs`
- `rewrittenThreeImmediateNewExtraLocalFourThirdArgChainInputsPassJvmVerification`
- `threeImmediateNewExtraLocalFourThirdArgChainInputsCompileAndRunWithJavaParity`

After the leftover-docs commit `753c401`, the expected parent XML total is 587 tests: 580 in `IrCompilerTest` plus 7 in `CodegenModeTest`. The parent re-runs the focused gate.

## 中文

### 摘要

- 仅新增 `new-constructor-extra-local-argument-four-third` 测试夹具准入。
- 处理器改动：否。
- 准入范围：仅此组合——已证明的额外局部 `int` 副本只作为隔离的四参数 `java.awt.Insets` `NEW` 的第三个初始化参数。
- 可发布：否。
- 默认选项切换：否；`--codegen=legacy`、`--ir-lower=direct` 和 `--backend=cpp` 均保持不变。
- 剩余项清单仍引用 [#329](https://github.com/gaoyu06/native-obfuscator/pull/329)；它是测量结果，不是徽章。

### 测试

- `admitsThreeImmediateReturnsWithNewExtraLocalFourThirdArgChainInputs`
- `rewrittenThreeImmediateNewExtraLocalFourThirdArgChainInputsPassJvmVerification`
- `threeImmediateNewExtraLocalFourThirdArgChainInputsCompileAndRunWithJavaParity`

在剩余项文档提交 `753c401` 之后，父分支预期 XML 总数为 587：`IrCompilerTest` 580 个，`CodegenModeTest` 7 个。父分支会重新运行聚焦门禁。
