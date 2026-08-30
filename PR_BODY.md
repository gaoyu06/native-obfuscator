## English

### Summary
- Adds fixture-only coverage for the proven extra-local `int` copy used as the first and third arguments of an isolated three-argument `java.awt.Color` `NEW`.
- Adds admission, JVM verification, and Java/native parity tests for `new-constructor-extra-local-argument-three-first-third`.
- Processor changed: No.
- Admitted: this compose only.
- Ship-ready: No. This does not authorize a default flip; `--codegen=legacy`, `--ir-lower=direct`, and `--backend=cpp` remain unchanged.
- Expected parent XML: 578 tests (`IrCompilerTest` 571 + `CodegenModeTest` 7).
- Leftover inventory: #323.

### Tests
- `admitsThreeImmediateReturnsWithNewExtraLocalThreeFirstThirdArgChainInputs`
- `rewrittenThreeImmediateNewExtraLocalThreeFirstThirdArgChainInputsPassJvmVerification`
- `threeImmediateNewExtraLocalThreeFirstThirdArgChainInputsCompileAndRunWithJavaParity`

## 中文

### 摘要
- 仅新增测试夹具覆盖：已证明的额外局部 `int` 副本同时作为隔离的三参数 `java.awt.Color` `NEW` 的第一个和第三个初始化参数。
- 为 `new-constructor-extra-local-argument-three-first-third` 新增准入、JVM 验证和 Java/原生运行一致性测试。
- 处理器改动：否。
- 准入范围：仅此组合。
- 可发布：否。本变更不授权切换默认值；`--codegen=legacy`、`--ir-lower=direct` 和 `--backend=cpp` 保持不变。
- 预期父分支 XML：578 个测试（`IrCompilerTest` 571 + `CodegenModeTest` 7）。
- 剩余项清单引用：#323。

### 测试
- `admitsThreeImmediateReturnsWithNewExtraLocalThreeFirstThirdArgChainInputs`
- `rewrittenThreeImmediateNewExtraLocalThreeFirstThirdArgChainInputsPassJvmVerification`
- `threeImmediateNewExtraLocalThreeFirstThirdArgChainInputsCompileAndRunWithJavaParity`
