## English

### What changed

- Admits one fixture-only constructor-split shape: a proven extra-local `int` copy as the **second** initializer argument of an isolated two-argument `java.awt.Point` `NEW`.
- Keeps the first initializer argument isolated as `ICONST_1`.
- Adds admission, JVM-verification, and Java/native parity coverage.

Processor changed: **No**  
Expected parent XML after landing: **560** (`IrCompilerTest` 553 + `CodegenModeTest` 7)  
Ship-ready: **No**

This change does not authorize a code-generation default flip. Leftover inventory #310 is tracking evidence, not a JDK support badge.

## 中文

### 变更内容

- 仅通过测试夹具新增一种构造器拆分准入形态：在隔离的双参数 `java.awt.Point` `NEW` 中，将已证明的额外局部 `int` 副本作为初始化器的**第二个**参数。
- 第一个初始化器参数仍为隔离的 `ICONST_1`。
- 新增准入、JVM 校验以及 Java/原生输出一致性测试。

处理器变更：**否**  
合入后父分支预期 XML：**560**（`IrCompilerTest` 553 + `CodegenModeTest` 7）  
可发布：**否**

本变更不授权切换代码生成默认值。剩余项清单 #310 仅为跟踪证据，并非 JDK 支持徽章。
