# EN

## Summary

- Adds the fixture-only IR admission shape `new-constructor-extra-local-argument-five-first-fifth`.
- Retains this JVM constructor prefix in each branch:
  `NEW java/util/GregorianCalendar; DUP; ILOAD 3; ICONST_2; ICONST_3; ICONST_4; ILOAD 3; INVOKESPECIAL <init>(IIIII)V`.
- Keeps `ConstructorSpecialMethodProcessor` and all production compiler/runtime code unchanged.
- Keeps the default `--codegen=legacy`; no `--codegen`, `--ir-lower`, or `--backend` default is changed.

## Tests

- `admitsThreeImmediateReturnsWithNewExtraLocalFiveFirstFifthArgChainInputs`
- `rewrittenThreeImmediateNewExtraLocalFiveFirstFifthArgChainInputsPassJvmVerification`
- `threeImmediateNewExtraLocalFiveFirstFifthArgChainInputsCompileAndRunWithJavaParity`

The runtime fixture expects three `java.util.GregorianCalendar` class-name lines. After the leftover-docs parent, the expected parent XML total is 650 tests: 643 `IrCompilerTest` tests plus 7 `CodegenModeTest` tests.

Ship-ready: **No**

# 中文

## 摘要

- 仅在测试夹具中新增 IR 准入形状 `new-constructor-extra-local-argument-five-first-fifth`。
- 每个分支保留以下 JVM 构造器前缀：
  `NEW java/util/GregorianCalendar; DUP; ILOAD 3; ICONST_2; ICONST_3; ICONST_4; ILOAD 3; INVOKESPECIAL <init>(IIIII)V`。
- `ConstructorSpecialMethodProcessor` 以及所有生产编译器/运行时代码均保持不变。
- 默认值仍为 `--codegen=legacy`；未更改 `--codegen`、`--ir-lower` 或 `--backend` 的默认值。

## 测试

- `admitsThreeImmediateReturnsWithNewExtraLocalFiveFirstFifthArgChainInputs`
- `rewrittenThreeImmediateNewExtraLocalFiveFirstFifthArgChainInputsPassJvmVerification`
- `threeImmediateNewExtraLocalFiveFirstFifthArgChainInputsCompileAndRunWithJavaParity`

运行时夹具预期输出三行 `java.util.GregorianCalendar` 类名。基于 leftover-docs 父提交，预期父 XML 总数为 650：`IrCompilerTest` 643 项，加上 `CodegenModeTest` 7 项。

可发布：**否**
