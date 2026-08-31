# English

## Summary

- Adds fixture-only admission for the proven extra-local `int` copy as the third argument of a six-argument `GregorianCalendar` `NEW` initializer.
- Covers IR admission, rewritten JVM verification, and Java/native parity while retaining the complete `NEW; DUP; args; <init>` sequence in the JVM prefix.
- Keeps one `MethodContext.proxyMethod` and one native method per constructor.

## Baseline and status

- Processor changed: **No**
- Product defaults (`--codegen`, `--ir-lower`, and `--backend`) unchanged
- Rebased onto leftover-docs #418 (`3d0c9355a0ef70e75a34db68f3a4969787acbc15`).
- Parent re-ran **719/719** (`IrCompilerTest` 712 + `CodegenModeTest` 7), including `threeImmediateNewExtraLocalSixThirdArgChainInputsCompileAndRunWithJavaParity`. Zero failures/errors/skips.
- Ship-ready: **No**
- This is fixture admission coverage, not a JDK support badge.

# 中文

## 摘要

- 仅增加测试夹具准入：把已证明的额外局部 `int` 副本作为六参数 `GregorianCalendar` `NEW` 初始化器的第三个参数。
- 覆盖 IR 准入、重写后的 JVM 校验和 Java/原生执行一致性，同时将完整的 `NEW; DUP; args; <init>` 序列保留在 JVM 前缀中。
- 每个构造器保持一个 `MethodContext.proxyMethod` 和一个原生方法。

## 基线与状态

- 是否修改处理器：**否**
- 产品默认值（`--codegen`、`--ir-lower` 和 `--backend`）保持不变
- 已 rebase 到 leftover-docs #418（`3d0c9355a0ef70e75a34db68f3a4969787acbc15`）。
- 父级重跑 **719/719**（`IrCompilerTest` 712 + `CodegenModeTest` 7），含 `threeImmediateNewExtraLocalSixThirdArgChainInputsCompileAndRunWithJavaParity`。失败/错误/跳过均为零。
- 可发布状态：**否**
- 此项仅为测试夹具准入覆盖，不代表 JDK 支持徽章。
