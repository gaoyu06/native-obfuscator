## English

### Summary
- Admits only the proven extra-local `int` copy used as the second and third arguments of each four-argument `java.awt.Insets` initializer in the retained constructor bytecode.
- Adds admission, JVM verification, and Java/native parity fixtures.
- Processor changed: No.
- Defaults changed: No (`--codegen`, `--ir-lower`, and `--backend` are unchanged).
- Ship-ready: No.

### Verification
- Focused gate: `IrCompilerTest` and `CodegenModeTest`.
- Expected parent XML total: 605 (`IrCompilerTest` 598 + `CodegenModeTest` 7).
- Java 8 remains the only fully supported version.

## 中文

### 摘要
- 仅准入构造函数字节码中已证明的额外局部 `int` 副本，用作每个四参数 `java.awt.Insets` 初始化器的第二和第三个参数。
- 新增准入、JVM 验证以及 Java/native 一致性夹具。
- 处理器变更：否。
- 默认值变更：否（`--codegen`、`--ir-lower` 和 `--backend` 均保持不变）。
- 可发布：否。

### 验证
- 聚焦测试门禁：`IrCompilerTest` 和 `CodegenModeTest`。
- 预期父分支 XML 总数：605（`IrCompilerTest` 598 + `CodegenModeTest` 7）。
- Java 8 仍是唯一获得完整支持的版本。
