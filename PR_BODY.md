## English

### What changed

- Admits the fixture-only constructor split where one proven prefix extra-local double copy is loaded as both arguments of an isolated two-argument `Point2D.Double` initializer.
- Adds admission, rewritten-JVM-verification, and Java/native IR parity coverage for `DLOAD 4; DLOAD 4`.
- Keeps the complete `NEW; DUP; args; <init>` sequence in the retained JVM prefix.

### Scope

- Processor changed: No
- Expected parent XML after landing: 557 tests (550 `IrCompilerTest` + 7 `CodegenModeTest`)
- Ship-ready: **No**
- This increment does not authorize a code-generation default flip.
- Leftover inventory #310 is tracking context, not a JDK support badge.

## 中文

### 变更内容

- 仅通过测试夹具准入一种构造器拆分：同一个已证明的前缀额外局部 `double` 副本，被加载为隔离的双参数 `Point2D.Double` 初始化器的两个参数。
- 为 `DLOAD 4; DLOAD 4` 新增准入、改写后 JVM 验证以及 Java/原生 IR 输出一致性测试。
- 完整的 `NEW; DUP; args; <init>` 序列仍保留在 JVM 前缀中。

### 范围

- 处理器变更：否
- 落地主分支后的预期 XML：557 个测试（`IrCompilerTest` 550 + `CodegenModeTest` 7）
- 可发布：**否**
- 此增量不授权切换代码生成默认值。
- 剩余清单 #310 仅用于跟踪上下文，不是 JDK 支持徽章。
