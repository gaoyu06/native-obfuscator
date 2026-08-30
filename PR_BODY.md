# EN

## Summary

- Fixture-only; processor changed: No.
- Admits one constructor-split compose: a proven extra-local `int` copy used as
  the third argument of an isolated three-argument `java.awt.Color` `NEW`.
- Ship-ready: No.
- Expected parent XML: 569 tests (562 `IrCompilerTest` + 7
  `CodegenModeTest`).
- This does not authorize a default flip; `--codegen=legacy`,
  `--ir-lower=direct`, and `--backend=cpp` remain unchanged.
- Latest leftover inventory citation: [#318](https://github.com/gaoyu06/native-obfuscator/pull/318),
  not a JDK support badge.

# 中文

## 摘要

- 仅修改测试夹具；处理器修改：否。
- 仅放行一种构造器拆分组合：把已证明的额外局部 `int` 副本用作隔离的三参数
  `java.awt.Color` `NEW` 的第三个初始化参数。
- 可发布：否。
- 预期父分支 XML：569 项测试（562 项 `IrCompilerTest` + 7 项
  `CodegenModeTest`）。
- 本变更不授权切换默认值；`--codegen=legacy`、`--ir-lower=direct` 和
  `--backend=cpp` 均保持不变。
- 最新 leftover inventory 引用为 [#318](https://github.com/gaoyu06/native-obfuscator/pull/318)，
  不是 JDK 支持徽章。
