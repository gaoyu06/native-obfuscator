# EN

## Summary

- Fixture-only; processor changed: No.
- Admits one constructor-split compose: a proven extra-local `int` copy used as the **second** initializer argument of an isolated three-argument `java.awt.Color` `NEW`.
- The admitted leaf is `NEW Color; DUP; ICONST_1; ILOAD 3; ICONST_3; INVOKESPECIAL Color.<init>(III)V`.
- Ship-ready: No.

## Scope and gate

- Expected parent XML: **566** (**559** `IrCompilerTest` + **7** `CodegenModeTest`).
- This change does not authorize a default flip. `--codegen=legacy`, `--ir-lower=direct`, and `--backend=cpp` remain unchanged.
- The latest leftover inventory is [#315](https://github.com/gaoyu06/native-obfuscator/pull/315). It is an in-tree measurement, not a JDK support badge and not proof of coverage completeness.

# 中文

## 摘要

- 仅修改测试夹具；处理器修改：否。
- 本次只接纳一种构造器拆分组合：把已证明的额外局部 `int` 副本用作独立三参数 `java.awt.Color` `NEW` 的**第二个**初始化参数。
- 接纳的叶节点为 `NEW Color; DUP; ICONST_1; ILOAD 3; ICONST_3; INVOKESPECIAL Color.<init>(III)V`。
- 可发布：否。

## 范围与门禁

- 预期父分支 XML：**566**（**559** 个 `IrCompilerTest` + **7** 个 `CodegenModeTest`）。
- 本变更不授权切换默认值；`--codegen=legacy`、`--ir-lower=direct` 和 `--backend=cpp` 均保持不变。
- 最新 leftover inventory 为 [#315](https://github.com/gaoyu06/native-obfuscator/pull/315)。它只是仓库内测量结果，不是 JDK 支持徽章，也不代表覆盖完整。
