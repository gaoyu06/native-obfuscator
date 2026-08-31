# EN

## Summary

- Admit the fixture shape `new-constructor-extra-local-argument-six-sixth`.
- Keep the complete `NEW GregorianCalendar; DUP; args; <init>(IIIIII)V`
  sequence in the retained JVM constructor prefix, with the sixth initializer
  input loaded from proven local 3.
- Preserve one hidden bridge, singular `MethodContext.proxyMethod`, and one
  native method per constructor.

## Scope

- Leftover-docs baseline: #423 at `9a45cc67`
  (`9a45cc6714007544b8521c826d92e35312804642`).
- Processor changed: No.
- Defaults unchanged: `--codegen`, `--ir-lower`, and `--backend` are unchanged.
- This is fixture admission, not a JDK support badge.
- Ship-ready: No.

## Validation

- Latest compiler parent XML until the parent re-runs: #423 (725):
  `IrCompilerTest` 718 + `CodegenModeTest` 7.
- Expected parent XML after leftover-docs: 728 (725 + 3):
  `IrCompilerTest` 721 + `CodegenModeTest` 7.
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

- leftover-docs 基线：#423，`9a45cc67`
  (`9a45cc6714007544b8521c826d92e35312804642`)。
- Processor changed：No。
- 默认值不变：`--codegen`、`--ir-lower` 和 `--backend` 均未修改。
- 这是测试夹具准入，不是 JDK 支持标识。
- Ship-ready：No。

## 验证

- 在父分支重新运行前，最新 compiler parent XML 为 #423 (725)：
  `IrCompilerTest` 718 + `CodegenModeTest` 7。
- leftover-docs 之后预期 parent XML 为 728 (725 + 3)：
  `IrCompilerTest` 721 + `CodegenModeTest` 7。
- 子分支本地命令：
  `CC=gcc CXX=g++ ./gradlew :obfuscator:test --tests by.radioegor146.ir.IrCompilerTest --tests by.radioegor146.CodegenModeTest`
