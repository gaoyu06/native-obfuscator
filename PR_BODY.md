## English

### Summary

- Adds fixture-only IR admission coverage for `new-constructor-extra-local-argument-four-first-second-third`.
- Admits a proven extra-local int copy as only the first, second, and third `java.awt.Insets` initializer arguments; the fourth argument remains `ICONST_4`.
- Keeps the complete `NEW; DUP; args; INVOKESPECIAL <init>(IIII)V` sequence in the retained JVM prefix.

### Scope

- Processor changed: No.
- Defaults changed: No (`--codegen`, `--ir-lower`, and `--backend` are unchanged).
- Ship-ready: No.
- Admitted scope: first+second+third extra-local `Insets` compose only.
- Unsupported constructor bytecode shapes remain rejected; this does not widen admission for unproven or computed inputs, unsupported arrays, skipped super calls, or post-call bytecode.
- Java 8 remains the only fully supported version.

### Tests

- `admitsThreeImmediateReturnsWithNewExtraLocalFourFirstSecondThirdArgChainInputs`
- `rewrittenThreeImmediateNewExtraLocalFourFirstSecondThirdArgChainInputsPassJvmVerification`
- `threeImmediateNewExtraLocalFourFirstSecondThirdArgChainInputsCompileAndRunWithJavaParity`

The focused `IrCompilerTest` and `CodegenModeTest` gate passes. Child XML is 607 + 7 = 614 and will be discarded by the parent. Expected parent XML: **614**.

## 中文

### 摘要

- 新增仅限测试夹具的 IR 准入覆盖：`new-constructor-extra-local-argument-four-first-second-third`。
- 仅准入已证明的额外局部 int 副本作为 `java.awt.Insets` 初始化器的第一、第二和第三个参数；第四个参数仍为 `ICONST_4`。
- 完整的 `NEW; DUP; args; INVOKESPECIAL <init>(IIII)V` 序列继续保留在 JVM 前缀中。

### 范围

- 处理器变更：否。
- 默认值变更：否（`--codegen`、`--ir-lower` 和 `--backend` 均未变更）。
- 可发布：否。
- 准入范围：仅第一+第二+第三参数使用额外局部值的 `Insets` 组合。
- 不受支持的构造函数字节码形状仍会被拒绝；本增量不会放宽未证明或计算型输入、不支持的数组、跳过父类调用或调用后字节码的准入。
- Java 8 仍是唯一完全支持的版本。

### 测试

- `admitsThreeImmediateReturnsWithNewExtraLocalFourFirstSecondThirdArgChainInputs`
- `rewrittenThreeImmediateNewExtraLocalFourFirstSecondThirdArgChainInputsPassJvmVerification`
- `threeImmediateNewExtraLocalFourFirstSecondThirdArgChainInputsCompileAndRunWithJavaParity`

聚焦的 `IrCompilerTest` 与 `CodegenModeTest` 测试门禁已通过。子分支 XML 为 607 + 7 = 614，父分支会丢弃该 XML。预期父分支 XML：**614**。
