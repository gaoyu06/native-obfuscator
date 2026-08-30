# IR constructor split: extra-local double as second NEW argument

## English

### What changed

- Adds fixture-only coverage admitting a proven extra-local double copy as the **second** initializer argument of an isolated two-argument `NEW`.
- Keeps the first `Point2D.Double` initializer argument isolated as `DCONST_1`.
- Adds admission, JVM verification, and Java/native parity tests.

### Scope

- Processor changed: **No**
- Expected parent XML after landing: **551 tests** (`IrCompilerTest` 544 + `CodegenModeTest` 7)
- Ship-ready: **No**
- This does not authorize a codegen/default flip.
- Leftover inventory #310 is tracking context, not a JDK support badge.

## 中文

### 变更内容

- 仅新增测试夹具覆盖：允许已证明的额外局部变量 double 副本作为隔离双参数 `NEW` 的**第二个**初始化参数。
- `Point2D.Double` 的第一个初始化参数仍是隔离的 `DCONST_1`。
- 新增准入、JVM 验证以及 Java/原生输出一致性测试。

### 范围

- 是否修改处理器：**否**
- 合入后父分支预期 XML：**551 项测试**（`IrCompilerTest` 544 + `CodegenModeTest` 7）
- 可发布：**否**
- 此变更不授权切换代码生成或其他默认选项。
- 剩余项清单 #310 仅用于跟踪上下文，不是 JDK 支持徽章。
