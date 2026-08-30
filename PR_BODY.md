## English

### Scope

- Fixture-only IR admission; processor changed: No.
- Admits only `new-constructor-extra-local-argument-four-all`: a proven
  extra-local `int` copy used as all four initializer arguments of an
  isolated four-argument `java.awt.Insets` `NEW`.
- Ship-ready: No.
- No default compiler or backend selection is flipped.
- The leftover inventory citation remains #333; it is a measurement, not a
  badge.

### Tests

- `admitsThreeImmediateReturnsWithNewExtraLocalFourAllArgChainInputs`
- `rewrittenThreeImmediateNewExtraLocalFourAllArgChainInputsPassJvmVerification`
- `threeImmediateNewExtraLocalFourAllArgChainInputsCompileAndRunWithJavaParity`

After the parent integrates this fixture onto leftover-docs commit `c99c0f9`,
the expected focused-gate XML total is 593 tests: 586
`IrCompilerTest` tests plus 7 `CodegenModeTest` tests. The parent re-runs the
focused gate and discards child XML.

## 中文

### 范围

- 仅增加 IR 测试夹具；处理器改动：无。
- 仅准入 `new-constructor-extra-local-argument-four-all`：将已证明的额外
  局部 `int` 副本同时作为隔离的四参数 `java.awt.Insets` `NEW` 的全部四个
  初始化参数。
- 可发布：否。
- 不切换默认编译器或后端选项。
- 剩余项清单仍引用 #333；该引用是一次测量，不是徽章。

### 测试

- `admitsThreeImmediateReturnsWithNewExtraLocalFourAllArgChainInputs`
- `rewrittenThreeImmediateNewExtraLocalFourAllArgChainInputsPassJvmVerification`
- `threeImmediateNewExtraLocalFourAllArgChainInputsCompileAndRunWithJavaParity`

父分支将此夹具集成到 leftover-docs 提交 `c99c0f9` 后，预期聚焦门禁 XML
总数为 593：586 个 `IrCompilerTest` 测试和 7 个 `CodegenModeTest` 测试。
父分支会重新运行聚焦门禁，并丢弃子分支 XML。
