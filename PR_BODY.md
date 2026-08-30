## English

### Scope

- Fixture-only IR admission for the exact shape `new-constructor-extra-local-argument-four-first-fourth`.
- Admits an isolated four-argument `java.awt.Insets` `NEW` whose first and fourth initializer arguments use the same proven extra-local `int` copy.
- The complete `NEW; DUP; ILOAD 3; ICONST_2; ICONST_3; ILOAD 3; INVOKESPECIAL <init>(IIII)V` remains in the retained JVM prefix.
- `ConstructorSpecialMethodProcessor` and all other processors are unchanged.
- Processor changed: **No**.
- Ship-ready: **No**.
- Defaults remain `--codegen=legacy`, `--ir-lower=direct`, and `--backend=cpp`.

### Tests

- Adds admission, rewritten-JVM-verification, and compile-and-run Java-parity coverage.
- The parent will re-run the focused gate after integration onto leftover-docs `b4ec2a7`; expected parent XML total: **602** (**595** `IrCompilerTest` + **7** `CodegenModeTest`).
- Leftover inventory remains the measurement recorded in [#339](https://github.com/gaoyu06/native-obfuscator/pull/339), not an admission or readiness badge.

## 中文

### 范围

- 仅增加精确形状 `new-constructor-extra-local-argument-four-first-fourth` 的 IR 准入测试夹具。
- 准入一个隔离的四参数 `java.awt.Insets` `NEW`：构造器的第一、第四个实参复用同一个已证明的额外局部 `int` 副本。
- 完整的 `NEW; DUP; ILOAD 3; ICONST_2; ICONST_3; ILOAD 3; INVOKESPECIAL <init>(IIII)V` 保留在 JVM 前缀中。
- 未修改 `ConstructorSpecialMethodProcessor` 或其他处理器。
- 处理器变更：**否**。
- 可交付：**否**。
- 默认值仍为 `--codegen=legacy`、`--ir-lower=direct` 和 `--backend=cpp`。

### 测试

- 新增准入、重写后 JVM 验证及编译运行 Java 一致性测试。
- 父任务集成到 leftover-docs `b4ec2a7` 后会重新运行聚焦门禁；预期父任务 XML 总数为 **602**（`IrCompilerTest` **595**，`CodegenModeTest` **7**）。
- 剩余清单仍引用 [#339](https://github.com/gaoyu06/native-obfuscator/pull/339) 的测量结果；它不是准入或就绪徽章。
