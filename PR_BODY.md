# EN

## Summary

- Fixture-only; processor changed: No.
- Admits one constructor-split compose: a proven extra-local `int` copy used as both initializer arguments of an isolated two-argument `java.awt.Point` `NEW`.
- Adds admission, JVM verification, and Java/native parity coverage for `new-constructor-extra-local-argument-two-both`.
- Latest leftover inventory citation: #315 (not a JDK support badge).

## Status

- Ship-ready: No.
- Expected parent XML: 563 tests (556 `IrCompilerTest` + 7 `CodegenModeTest`).
- This change does not authorize a default flip. Keep `--codegen=legacy`, `--ir-lower=direct`, and `--backend=cpp`.

# 中文

## 摘要

- 仅修改测试夹具；处理器变更：否。
- 本次仅准入一种构造器拆分组合：将已证明的额外局部 `int` 副本同时用作隔离的双参数 `java.awt.Point` `NEW` 的两个初始化参数。
- 为 `new-constructor-extra-local-argument-two-both` 增加准入、JVM 验证以及 Java/原生执行一致性测试。
- 最新的 leftover inventory 引用为 #315（不是 JDK 支持徽章）。

## 状态

- 可发布：否。
- 预期父任务 XML：563 个测试（556 个 `IrCompilerTest` + 7 个 `CodegenModeTest`）。
- 本变更不授权切换默认选项。继续保持 `--codegen=legacy`、`--ir-lower=direct` 和 `--backend=cpp`。
