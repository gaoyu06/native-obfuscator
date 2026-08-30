# EN

## Scope

- Fixture-only admission for `new-constructor-extra-local-argument-four-second`.
- Keeps the proven extra-local `int` copy as the second initializer argument of an isolated four-argument `java.awt.Insets` `NEW`.
- Processor changed: No.
- Admitted: this compose only.
- Ship-ready: No.
- Default code generation, IR lowering, and backend selections are unchanged.
- The leftover inventory citation remains #327; it is a measurement, not a completion badge.

## Tests

- `admitsThreeImmediateReturnsWithNewExtraLocalFourSecondArgChainInputs`
- `rewrittenThreeImmediateNewExtraLocalFourSecondArgChainInputsPassJvmVerification`
- `threeImmediateNewExtraLocalFourSecondArgChainInputsCompileAndRunWithJavaParity`

After the leftover-docs base `b751add`, the expected parent focused XML total is 584 tests: 577 from `IrCompilerTest` plus 7 from `CodegenModeTest`. The parent re-runs that focused gate.

# 中文

## 范围

- 仅新增 `new-constructor-extra-local-argument-four-second` 测试夹具准入。
- 已证明的额外局部 `int` 副本仅作为独立四参数 `java.awt.Insets` `NEW` 的第二个初始化参数。
- 处理器改动：无。
- 本次仅准入这一种组合。
- 可发布：否。
- 默认代码生成、IR lowering 与后端选择均未改变。
- 遗留项统计继续引用 #327；它是度量结果，不是完成标记。

## 测试

- `admitsThreeImmediateReturnsWithNewExtraLocalFourSecondArgChainInputs`
- `rewrittenThreeImmediateNewExtraLocalFourSecondArgChainInputsPassJvmVerification`
- `threeImmediateNewExtraLocalFourSecondArgChainInputsCompileAndRunWithJavaParity`

基于遗留文档提交 `b751add`，父任务聚焦测试的预期 XML 总数为 584：`IrCompilerTest` 577 个，加上 `CodegenModeTest` 7 个。父任务会重新运行该聚焦测试门禁。
