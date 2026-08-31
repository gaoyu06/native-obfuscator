# Title for parent / 父 PR 标题

`docs: remeasure IR leftovers after leftover-docs #409`

## English

Remeasures the joined in-tree IR leftover inventory on the leftover-docs [#409](https://github.com/gaoyu06/native-obfuscator/pull/409) tree.

- Measurement SHA: `2d46d0d15be42608ee9bc0b9193623cf172614ff`
- Merge-base with `origin/master` at measurement: `2d46d0d15be42608ee9bc0b9193623cf172614ff`
- Supersedes [#409](https://github.com/gaoyu06/native-obfuscator/pull/409)'s measurement of leftover-docs [#407](https://github.com/gaoyu06/native-obfuscator/pull/407) at `f1c9a811` (`f1c9a81106adef6440cb1e90412e0af1f5d875c8`).
- Latest compiler parent XML: **[#408](https://github.com/gaoyu06/native-obfuscator/pull/408) (704)** (`IrCompilerTest` 697 + `CodegenModeTest` 7). This measurement adds no compiler XML.

| Corpus | Inventory | IR | Legacy fallback | Constructor left in Java | Missing |
| --- | ---: | ---: | ---: | ---: | ---: |
| `ClassicTest` | 108 | 108 | 0 | 0 | 0 |
| `jdk17` | 82 | 82 | 0 | 0 | 0 |
| `jdk21` | 47 | 47 | 0 | 0 | 0 |
| `jdk25` | 21 | 21 | 0 | 0 | 0 |

- Processor changed: **No**
- Admitted: **No**
- Ship-ready: **No**

Zero leftovers is not coverage-complete, is not a JDK support badge, and does not authorize a default flip. Defaults remain `--codegen=legacy`, `--ir-lower=direct`, and `--backend=cpp`.

## 中文

在 leftover-docs [#409](https://github.com/gaoyu06/native-obfuscator/pull/409) 树上重新测量合并后的仓内 IR 遗留项清单。

- 测量 SHA：`2d46d0d15be42608ee9bc0b9193623cf172614ff`
- 测量时与 `origin/master` 的 merge-base：`2d46d0d15be42608ee9bc0b9193623cf172614ff`
- 本次测量取代 [#409](https://github.com/gaoyu06/native-obfuscator/pull/409) 对 leftover-docs [#407](https://github.com/gaoyu06/native-obfuscator/pull/407) `f1c9a811`（`f1c9a81106adef6440cb1e90412e0af1f5d875c8`）的测量。
- 最新编译器父级 XML：**[#408](https://github.com/gaoyu06/native-obfuscator/pull/408)（704）**（`IrCompilerTest` 697 + `CodegenModeTest` 7）。本次测量未新增编译器 XML。

| 语料 | 清单总数 | IR | Legacy 回退 | 构造器留在 Java | 缺失 |
| --- | ---: | ---: | ---: | ---: | ---: |
| `ClassicTest` | 108 | 108 | 0 | 0 | 0 |
| `jdk17` | 82 | 82 | 0 | 0 | 0 |
| `jdk21` | 47 | 47 | 0 | 0 | 0 |
| `jdk25` | 21 | 21 | 0 | 0 | 0 |

- 处理器已更改：**否**
- 已准入：**否**
- 可发布：**否**

遗留项为零不代表覆盖完整，不是 JDK 支持徽章，也不授权切换默认值。默认值仍为 `--codegen=legacy`、`--ir-lower=direct` 和 `--backend=cpp`。
