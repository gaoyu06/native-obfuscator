# docs: remeasure IR leftovers after leftover-docs #411

## English

### Summary

- Remeasures the joined in-tree IR leftover inventory on leftover-docs #411.
- Measurement SHA: `357d30e1d84ebff5cee76030943cae0485350bb7`.
- Merge-base with `origin/master` at measurement: `357d30e1d84ebff5cee76030943cae0485350bb7`.
- Supersedes #411's measurement of leftover-docs #409 at `2d46d0d1` (`2d46d0d15be42608ee9bc0b9193623cf172614ff`).
- Latest compiler parent XML: **#410 (707)** (`IrCompilerTest` 700 + `CodegenModeTest` 7).
- This measurement adds no compiler XML. The parent will skip Gradle because this is measurement-only.

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

Zero measured leftovers is not coverage-complete, not a JDK support badge, and does not authorize a default flip. Defaults remain `--codegen=legacy`, `--ir-lower=direct`, and `--backend=cpp`.

## 中文

### 摘要

- 在 leftover-docs #411 上重新测量仓库内 IR 剩余项，并按精确的类名、方法名和描述符联结结果。
- 测量 SHA：`357d30e1d84ebff5cee76030943cae0485350bb7`。
- 测量时与 `origin/master` 的 merge-base：`357d30e1d84ebff5cee76030943cae0485350bb7`。
- 本次结果取代 #411 对 leftover-docs #409 `2d46d0d1`（`2d46d0d15be42608ee9bc0b9193623cf172614ff`）的测量。
- 最新编译器父级 XML：**#410 (707)**（`IrCompilerTest` 700 + `CodegenModeTest` 7）。
- 本次测量不增加编译器 XML。由于仅包含测量文档，父代理将跳过 Gradle。

### 联结总计

| 语料 | 清单方法数 | IR | 回退到 legacy | 构造器保留在 Java | 缺失 |
| --- | ---: | ---: | ---: | ---: | ---: |
| `ClassicTest` | 108 | 108 | 0 | 0 | 0 |
| `jdk17` | 82 | 82 | 0 | 0 | 0 |
| `jdk21` | 47 | 47 | 0 | 0 | 0 |
| `jdk25` | 21 | 21 | 0 | 0 | 0 |

- 处理器变更：**否**
- 已准入：**否**
- 可发布：**否**

测得剩余项为零不代表覆盖完整，不是 JDK 支持标志，也不授权切换默认值。默认值仍为 `--codegen=legacy`、`--ir-lower=direct` 和 `--backend=cpp`。
