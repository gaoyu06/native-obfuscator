# docs: remeasure IR leftovers after leftover-docs #415

## English

Remeasure the joined in-tree IR leftover inventory on the leftover-docs [#415](https://github.com/gaoyu06/native-obfuscator/pull/415) tree.

- Measurement SHA: `621d39be1063d334d71eb5f8b8c61e0e1421ff29`
- Merge-base with `origin/master` at measurement: `621d39be1063d334d71eb5f8b8c61e0e1421ff29`
- Supersedes [#414](https://github.com/gaoyu06/native-obfuscator/pull/414)'s measurement of leftover-docs [#413](https://github.com/gaoyu06/native-obfuscator/pull/413) at `bb97bc39` (`bb97bc39648825f253c92b982743f8f6d13e3573`).
- Latest compiler parent XML: **[#415](https://github.com/gaoyu06/native-obfuscator/pull/415) (713)** (`IrCompilerTest` 706 + `CodegenModeTest` 7). Parent will skip Gradle because this is measurement-only. This measurement adds no compiler XML.

### Joined totals

| Corpus | Inventory | IR | Legacy fallback | Constructor left in Java | Missing |
| --- | ---: | ---: | ---: | ---: | ---: |
| `ClassicTest` | 108 | 108 | 0 | 0 | 0 |
| `jdk17` | 82 | 82 | 0 | 0 | 0 |
| `jdk21` | 47 | 47 | 0 | 0 | 0 |
| `jdk25` | 21 | 21 | 0 | 0 | 0 |

- Processor changed: **No**
- Admitted: **No**
- Ship-ready: **No**

Zero measured leftovers is not coverage-complete, is not a JDK support badge, and does not authorize a default flip. Defaults remain `--codegen=legacy`, `--ir-lower=direct`, and `--backend=cpp`.

## 中文

在 leftover-docs [#415](https://github.com/gaoyu06/native-obfuscator/pull/415) 树上重新测量仓库内 IR 剩余项，并按精确的类名、方法名和描述符连接结果。

- 测量 SHA：`621d39be1063d334d71eb5f8b8c61e0e1421ff29`
- 测量时与 `origin/master` 的 merge-base：`621d39be1063d334d71eb5f8b8c61e0e1421ff29`
- 本次测量取代 [#414](https://github.com/gaoyu06/native-obfuscator/pull/414) 对 leftover-docs [#413](https://github.com/gaoyu06/native-obfuscator/pull/413) `bb97bc39`（`bb97bc39648825f253c92b982743f8f6d13e3573`）的测量。
- 测量时最新的编译器父级 XML 为 **[#415](https://github.com/gaoyu06/native-obfuscator/pull/415) (713)**（`IrCompilerTest` 706 + `CodegenModeTest` 7）。父级会跳过 Gradle，因为这只是测量变更。本次测量不增加编译器 XML。

### 连接后的总计

| 语料 | 清单 | IR | 回退到 legacy | 构造器留在 Java | 缺失 |
| --- | ---: | ---: | ---: | ---: | ---: |
| `ClassicTest` | 108 | 108 | 0 | 0 | 0 |
| `jdk17` | 82 | 82 | 0 | 0 | 0 |
| `jdk21` | 47 | 47 | 0 | 0 | 0 |
| `jdk25` | 21 | 21 | 0 | 0 | 0 |

- 处理器已更改：**否**
- 已准入：**否**
- 可发布：**否**

测得的剩余项为零不表示覆盖完整，不是 JDK 支持徽章，也不授权切换默认值。默认值仍为 `--codegen=legacy`、`--ir-lower=direct` 和 `--backend=cpp`。
