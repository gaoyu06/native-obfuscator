# Title / 标题

`docs: remeasure IR leftovers after leftover-docs #407`

## English

### Summary

- Remeasures joined in-tree IR leftovers on leftover-docs #407.
- Measurement SHA: `f1c9a81106adef6440cb1e90412e0af1f5d875c8`.
- Merge-base with `origin/master` at measurement: `f1c9a81106adef6440cb1e90412e0af1f5d875c8`.
- Supersedes #407's measurement of leftover-docs #405 at `0914806e` (`0914806e849601538b24b8fec4b92d614db828f8`).
- Latest compiler parent XML: **#406 (701)** (`IrCompilerTest` 694 + `CodegenModeTest` 7). This measurement adds no compiler XML.

### Joined corpus totals

| Corpus | Inventory | IR | Legacy fallback | Constructor left in Java | Missing |
| --- | ---: | ---: | ---: | ---: | ---: |
| `ClassicTest` | 108 | 108 | 0 | 0 | 0 |
| `jdk17` | 82 | 82 | 0 | 0 | 0 |
| `jdk21` | 47 | 47 | 0 | 0 | 0 |
| `jdk25` | 21 | 21 | 0 | 0 | 0 |

- Processor changed: **No**
- Admitted: **No**
- Ship-ready: **No**
- Zero leftovers is not coverage-complete, is not a JDK support badge, and does not authorize a default flip. Defaults remain `--codegen=legacy`, `--ir-lower=direct`, and `--backend=cpp`.

## 中文

### 摘要

- 在 leftover-docs #407 上重新测量仓库内联合集的 IR 遗留项。
- 测量 SHA：`f1c9a81106adef6440cb1e90412e0af1f5d875c8`。
- 测量时与 `origin/master` 的 merge-base：`f1c9a81106adef6440cb1e90412e0af1f5d875c8`。
- 本次测量取代 #407 中对 leftover-docs #405 `0914806e`（`0914806e849601538b24b8fec4b92d614db828f8`）的测量。
- 最新编译器父级 XML：**#406 (701)**（`IrCompilerTest` 694 + `CodegenModeTest` 7）。本次测量未增加编译器 XML。

### 联合集总计

| 语料集 | 清单 | IR | Legacy fallback | 构造器保留在 Java | 缺失 |
| --- | ---: | ---: | ---: | ---: | ---: |
| `ClassicTest` | 108 | 108 | 0 | 0 | 0 |
| `jdk17` | 82 | 82 | 0 | 0 | 0 |
| `jdk21` | 47 | 47 | 0 | 0 | 0 |
| `jdk25` | 21 | 21 | 0 | 0 | 0 |

- Processor changed（处理器变更）：**No**
- Admitted（准入）：**No**
- Ship-ready（可发布）：**No**
- 零遗留项不代表覆盖完整，不是 JDK 支持标章，也不授权切换默认值。默认值仍为 `--codegen=legacy`、`--ir-lower=direct` 和 `--backend=cpp`。
