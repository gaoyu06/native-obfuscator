## English

### Summary

- Adds fixture-only coverage for the proven extra-local `int` copy used as the first and second initializer arguments of an isolated three-argument `java.awt.Color` `NEW`; the third argument remains isolated.
- Admits only the `new-constructor-extra-local-argument-three-first-second` compose.
- Adds admission, JVM verification, and Java/native parity tests.
- Leftover inventory reference: #321.

### Status

- Processor changed: No.
- Ship-ready: No.
- Expected parent XML: 575 tests (568 `IrCompilerTest` + 7 `CodegenModeTest`).
- This change does not authorize a default flip; `--codegen=legacy`, `--ir-lower=direct`, and `--backend=cpp` remain unchanged.

## 中文

### 摘要

- 仅新增夹具覆盖：已证明的额外局部 `int` 副本同时作为隔离的三参数 `java.awt.Color` `NEW` 的第一、第二个初始化参数，第三个参数仍保持隔离。
- 仅准入 `new-constructor-extra-local-argument-three-first-second` 这一组合。
- 新增准入、JVM 验证以及 Java/原生运行一致性测试。
- 剩余项清单引用：#321。

### 状态

- 处理器变更：否。
- 可发布：否。
- 预期父分支 XML：575 个测试（568 个 `IrCompilerTest` + 7 个 `CodegenModeTest`）。
- 本变更不授权切换默认值；`--codegen=legacy`、`--ir-lower=direct` 和 `--backend=cpp` 均保持不变。
