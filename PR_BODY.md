# EN

## Summary

- Admit the fixture shape
  `new-constructor-extra-local-argument-six-all`.
- Keep each complete
  `NEW GregorianCalendar; DUP; ILOAD 3` (six times);
  `<init>(IIIIII)V`
  sequence in the retained JVM constructor prefix.
- Preserve one hidden bridge, singular `MethodContext.proxyMethod`, and one
  native method per constructor.

## Scope

- Leftover-docs baseline: #426 at `073431c1`
  (`073431c18498f33ead2478e509dc94bd150d6f1a`).
- Processor changed: No.
- Defaults unchanged: `--codegen`, `--ir-lower`, and `--backend` are unchanged.
- This is fixture admission, not a JDK support badge.
- Ship-ready: No.

## Validation

- Parent XML: **731** tests (`IrCompilerTest` 724 + `CodegenModeTest` 7), 0 failures, 0 errors, 0 skips, including `threeImmediateNewExtraLocalSixAllArgChainInputsCompileAndRunWithJavaParity`.
- Child-local command:
  `CC=gcc CXX=g++ ./gradlew :obfuscator:test --tests by.radioegor146.ir.IrCompilerTest --tests by.radioegor146.CodegenModeTest`

# 中文

## 摘要

- 加入测试夹具形状
  `new-constructor-extra-local-argument-six-all`。
- 将每个完整的
  `NEW GregorianCalendar; DUP; ILOAD 3`（六次）；
  `<init>(IIIIII)V`
  序列保留在 JVM 构造器前缀中。
- 保持一个隐藏桥接方法、唯一的 `MethodContext.proxyMethod`，并且每个构造器
  只有一个 native 方法。

## 范围

- leftover-docs 基线：#426，`073431c1`
  (`073431c18498f33ead2478e509dc94bd150d6f1a`)。
- Processor changed：No。
- 默认值不变：`--codegen`、`--ir-lower` 和 `--backend` 均未修改。
- 这是测试夹具准入，不是 JDK 支持标识。
- Ship-ready：No。

## 验证

- 父级 XML：**731** 个测试（`IrCompilerTest` 724 + `CodegenModeTest` 7），0 失败 / 0 错误 / 0 跳过，含 `threeImmediateNewExtraLocalSixAllArgChainInputsCompileAndRunWithJavaParity`。
- 子分支本地命令：
  `CC=gcc CXX=g++ ./gradlew :obfuscator:test --tests by.radioegor146.ir.IrCompilerTest --tests by.radioegor146.CodegenModeTest`
