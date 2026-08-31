# EN

## Summary

- Admit the fixture shape `new-constructor-extra-local-argument-six-sixth`.
- Keep the complete `NEW GregorianCalendar; DUP; args; <init>(IIIIII)V`
  sequence in the retained JVM constructor prefix, with the sixth initializer
  input loaded from proven local 3.
- Preserve one hidden bridge, singular `MethodContext.proxyMethod`, and one
  native method per constructor.

## Scope

- Leftover-docs baseline: #424 at `287db160`
  (`287db1608e34bcd39239acd819620eb8fccbb1f0`).
- Processor changed: No.
- Defaults unchanged: `--codegen`, `--ir-lower`, and `--backend` are unchanged.
- This is fixture admission, not a JDK support badge.
- Ship-ready: No.

## Validation

- Parent XML: **728** tests (`IrCompilerTest` 721 + `CodegenModeTest` 7), 0 failures, 0 errors, 0 skips, including `threeImmediateNewExtraLocalSixSixthArgChainInputsCompileAndRunWithJavaParity`.
- Child-local command:
  `CC=gcc CXX=g++ ./gradlew :obfuscator:test --tests by.radioegor146.ir.IrCompilerTest --tests by.radioegor146.CodegenModeTest`

# 中文

## 摘要

- 加入测试夹具形状 `new-constructor-extra-local-argument-six-sixth`。
- 将完整的
  `NEW GregorianCalendar; DUP; args; <init>(IIIIII)V`
  序列保留在 JVM 构造器前缀中，第六个初始化参数从已证明的局部变量 3
  读取。
- 保持一个隐藏桥接方法、唯一的 `MethodContext.proxyMethod`，并且每个构造器
  只有一个 native 方法。

## 范围

- leftover-docs 基线：#424，`287db160`
  (`287db1608e34bcd39239acd819620eb8fccbb1f0`)。
- Processor changed：No。
- 默认值不变：`--codegen`、`--ir-lower` 和 `--backend` 均未修改。
- 这是测试夹具准入，不是 JDK 支持标识。
- Ship-ready：No。

## 验证

- 父级 XML：**728** 个测试（`IrCompilerTest` 721 + `CodegenModeTest` 7），0 失败 / 0 错误 / 0 跳过，含 `threeImmediateNewExtraLocalSixSixthArgChainInputsCompileAndRunWithJavaParity`。
- 子分支本地命令：
  `CC=gcc CXX=g++ ./gradlew :obfuscator:test --tests by.radioegor146.ir.IrCompilerTest --tests by.radioegor146.CodegenModeTest`
