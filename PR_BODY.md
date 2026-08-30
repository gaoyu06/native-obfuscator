## English

Fixture-only IR admission for
`new-constructor-extra-local-argument-three-second-third`.

- Processor changed: No.
- Admitted: this compose only.
- Ship-ready: No.
- Default modes are unchanged: no `--codegen=legacy`,
  `--ir-lower=direct`, or `--backend=cpp` flip.
- The leftover inventory citation remains
  [#325](https://github.com/gaoyu06/native-obfuscator/pull/325);
  it is a measurement reference, not an admission badge.
- Expected parent XML total: 581 tests after the leftover-docs commit
  `0894b16`.

Tests added:

- `admitsThreeImmediateReturnsWithNewExtraLocalThreeSecondThirdArgChainInputs`
- `rewrittenThreeImmediateNewExtraLocalThreeSecondThirdArgChainInputsPassJvmVerification`
- `threeImmediateNewExtraLocalThreeSecondThirdArgChainInputsCompileAndRunWithJavaParity`

## 中文

仅通过夹具准入
`new-constructor-extra-local-argument-three-second-third` IR 组合。

- 处理器改动：否。
- 准入范围：仅此组合。
- 可交付状态：否。
- 默认模式未变更：未切换 `--codegen=legacy`、`--ir-lower=direct`
  或 `--backend=cpp`。
- 剩余项清单继续引用
  [#325](https://github.com/gaoyu06/native-obfuscator/pull/325)；
  该引用是测量依据，不是准入标记。
- 父分支在 leftover-docs 提交 `0894b16` 之后的预期 XML 总数为
  581 个测试。

新增测试：

- `admitsThreeImmediateReturnsWithNewExtraLocalThreeSecondThirdArgChainInputs`
- `rewrittenThreeImmediateNewExtraLocalThreeSecondThirdArgChainInputsPassJvmVerification`
- `threeImmediateNewExtraLocalThreeSecondThirdArgChainInputsCompileAndRunWithJavaParity`
