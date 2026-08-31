# English

## Summary

- Adds fixture-only admission for the proven extra-local `int` copy as the third argument of a six-argument `GregorianCalendar` `NEW` initializer.
- Covers IR admission, rewritten JVM verification, and Java/native parity while retaining the complete `NEW; DUP; args; <init>` sequence in the JVM prefix.
- Keeps one `MethodContext.proxyMethod` and one native method per constructor.

## Baseline and status

- Leftover-docs baseline: #417 at `fcff55af`.
- Latest compiler parent XML until the parent reruns: #417, 716 tests (`IrCompilerTest` 709 + `CodegenModeTest` 7).
- Expected parent XML after leftover-docs: 719 tests (716 + 3).
- Processor changed: No.
- Ship-ready: No.
- Defaults are unchanged: no `--codegen=legacy`, `--ir-lower=direct`, or `--backend=cpp` flip.
- This fixture admission exercises bytecode, CFG, and JNI behavior; it is not a JDK support badge.

## Verification

- Expected child XML: 719 tests (`IrCompilerTest` 712 + `CodegenModeTest` 7), with zero failures, errors, and skips.

# 中文

## 摘要

- 仅增加测试夹具准入：把已证明的额外局部 `int` 副本作为六参数 `GregorianCalendar` `NEW` 初始化器的第三个参数。
- 覆盖 IR 准入、重写后的 JVM 校验和 Java/原生执行一致性，同时将完整的 `NEW; DUP; args; <init>` 序列保留在 JVM 前缀中。
- 每个构造器保持一个 `MethodContext.proxyMethod` 和一个原生方法。

## 基线与状态

- leftover-docs 基线：#417，提交 `fcff55af`。
- 父级重新运行前的最新编译器父级 XML：#417，共 716 项测试（`IrCompilerTest` 709 + `CodegenModeTest` 7）。
- leftover-docs 之后预期的父级 XML：719 项测试（716 + 3）。
- 处理器变更：否。
- 可发布：否。
- 默认配置不变：未切换 `--codegen=legacy`、`--ir-lower=direct` 或 `--backend=cpp`。
- 此测试夹具准入仅验证字节码、CFG 和 JNI 行为，不代表 JDK 支持徽章。

## 验证

- 预期子分支 XML：719 项测试（`IrCompilerTest` 712 + `CodegenModeTest` 7），失败、错误和跳过均为零。
