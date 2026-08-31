# English

## Summary

- Adds fixture-only IR admission coverage for the second and third integer arguments of the five-argument `GregorianCalendar` `NEW` initializer.
- Keeps the retained JVM prefix as `NEW java/util/GregorianCalendar; DUP; ICONST_1; ILOAD 3; ILOAD 3; ICONST_4; ICONST_5; INVOKESPECIAL <init>(IIIII)V`.
- Leaves `ConstructorSpecialMethodProcessor` and all production compiler/runtime code unchanged.
- Keeps the default `--codegen=legacy`; no `--codegen`, `--ir-lower`, or `--backend` default is changed.

## Tests

- `admitsThreeImmediateReturnsWithNewExtraLocalFiveSecondThirdArgChainInputs`
- `rewrittenThreeImmediateNewExtraLocalFiveSecondThirdArgChainInputsPassJvmVerification`
- `threeImmediateNewExtraLocalFiveSecondThirdArgChainInputsCompileAndRunWithJavaParity`

The fixture retains `ILOAD 2; ISTORE 3`, uses `BIPUSH 8` as the extra-local source in the main pass, and checks the three control-flow paths, JVM verification, JNI/native execution, and Java parity. Each constructor has one native method and one singular `MethodContext.proxyMethod` hidden bridge.

Expected parent XML after the leftover-docs baseline: **653 tests** (`646` `IrCompilerTest` + `7` `CodegenModeTest`).

Ship-ready: **No**.

# 中文

## 摘要

- 仅增加测试夹具，覆盖五参数 `GregorianCalendar` `NEW` 初始化器中第二和第三个整型参数的 IR 准入。
- 保留 JVM 前缀：`NEW java/util/GregorianCalendar; DUP; ICONST_1; ILOAD 3; ILOAD 3; ICONST_4; ICONST_5; INVOKESPECIAL <init>(IIIII)V`。
- `ConstructorSpecialMethodProcessor` 及所有生产编译器/运行时代码保持不变。
- 默认值仍为 `--codegen=legacy`；不修改 `--codegen`、`--ir-lower` 或 `--backend` 的默认配置。

## 测试

- `admitsThreeImmediateReturnsWithNewExtraLocalFiveSecondThirdArgChainInputs`
- `rewrittenThreeImmediateNewExtraLocalFiveSecondThirdArgChainInputsPassJvmVerification`
- `threeImmediateNewExtraLocalFiveSecondThirdArgChainInputsCompileAndRunWithJavaParity`

该夹具保留 `ILOAD 2; ISTORE 3`，主流程继续使用 `BIPUSH 8` 作为额外局部变量来源，并检查三条控制流路径、JVM 验证、JNI/原生执行及 Java 一致性。每个构造函数只有一个原生方法和一个由单数 `MethodContext.proxyMethod` 表示的隐藏桥接方法。

基于 leftover-docs 基线，父分支预期 XML 总数为 **653 个测试**（`IrCompilerTest` 的 `646` 个加 `CodegenModeTest` 的 `7` 个）。

可发布：**否（No）**。
