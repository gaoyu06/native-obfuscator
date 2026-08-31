## English

### Summary

- Fixture-only IR admission increment for `new-constructor-extra-local-argument-five-first-fourth`.
- Keeps the retained JVM prefix for each constructor chain leaf as:
  `NEW java/util/GregorianCalendar; DUP; ILOAD 3; ICONST_2; ICONST_3; ILOAD 3; ICONST_5; INVOKESPECIAL <init>(IIIII)V`.
- Leaves `ConstructorSpecialMethodProcessor` and all production compiler/runtime code unchanged.
- Ship-ready: **No**. The default remains `--codegen=legacy`; this does not flip `--codegen`, `--ir-lower`, or `--backend`.

### Tests

- `admitsThreeImmediateReturnsWithNewExtraLocalFiveFirstFourthArgChainInputs`
- `rewrittenThreeImmediateNewExtraLocalFiveFirstFourthArgChainInputsPassJvmVerification`
- `threeImmediateNewExtraLocalFiveFirstFourthArgChainInputsCompileAndRunWithJavaParity`

The expected parent XML total after the leftover-docs increment is **647** tests: 640 `IrCompilerTest` tests plus 7 `CodegenModeTest` tests.

## 中文

### 摘要

- 仅新增 `new-constructor-extra-local-argument-five-first-fourth` 的 IR 准入测试夹具。
- 每个构造器链叶节点保留的 JVM 前缀为：
  `NEW java/util/GregorianCalendar; DUP; ILOAD 3; ICONST_2; ICONST_3; ILOAD 3; ICONST_5; INVOKESPECIAL <init>(IIIII)V`。
- `ConstructorSpecialMethodProcessor` 与所有生产编译器/运行时代码均未改动。
- 可发布状态：**否**。默认仍为 `--codegen=legacy`，本增量不切换 `--codegen`、`--ir-lower` 或 `--backend`。

### 测试

- `admitsThreeImmediateReturnsWithNewExtraLocalFiveFirstFourthArgChainInputs`
- `rewrittenThreeImmediateNewExtraLocalFiveFirstFourthArgChainInputsPassJvmVerification`
- `threeImmediateNewExtraLocalFiveFirstFourthArgChainInputsCompileAndRunWithJavaParity`

在 leftover-docs 增量之后，父分支预期 XML 总数为 **647**：`IrCompilerTest` 640 个，加上 `CodegenModeTest` 7 个。
