## English

### What changed

- Adds fixture-only coverage for the `new-constructor-extra-local-double-arguments` compose.
- Admits a proven prefix extra-local double copy as the first argument of an isolated two-argument `NEW` initializer.
- Keeps the complete `NEW; DUP; arguments; INVOKESPECIAL` sequence in the retained JVM constructor prefix.
- Adds admission, JVM-verification, and Java/native parity tests for this compose only.

Processor changed: **No**

Expected parent XML after landing: **545 tests** (`IrCompilerTest` 538 + `CodegenModeTest` 7).

Ship-ready: **No**

This increment does not claim that the leftover inventory is empty or establish a JDK-support badge. It does not authorize changing the default codegen, IR-lowering, or backend mode.

## 中文

### 变更内容

- 仅通过测试夹具新增 `new-constructor-extra-local-double-arguments` 组合覆盖。
- 准入已证明的构造器前缀额外局部 `double` 副本，作为隔离双参数 `NEW` 初始化器的第一个参数。
- 完整的 `NEW; DUP; 参数; INVOKESPECIAL` 序列仍保留在 JVM 构造器前缀中。
- 仅为本组合新增准入、JVM 校验以及 Java/原生输出一致性测试。

处理器是否变更：**否**

合入后父分支预期 XML：**545 个测试**（`IrCompilerTest` 538 + `CodegenModeTest` 7）。

可发布：**否**

本增量不宣称剩余清单已经清空，也不构成 JDK 支持标志；本增量不授权切换代码生成、IR lowering 或后端默认值。
