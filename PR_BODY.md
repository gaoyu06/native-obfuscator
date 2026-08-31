# English

## Summary

- Remeasures the joined in-tree IR leftover inventory on the leftover-docs #401 tree.
- Measurement SHA: `2b94f0b611d6b71c121bfaf363903992d8fb8a0f`.
- Merge-base with `origin/master` at measurement: `2b94f0b611d6b71c121bfaf363903992d8fb8a0f`.
- Supersedes #401's measurement of leftover-docs #399 at `454af268` (`454af2687204a17204d2a60c511d019582a14774`).
- Latest compiler parent XML: **[#402](https://github.com/gaoyu06/native-obfuscator/pull/402) (695)** (`IrCompilerTest` 688 + `CodegenModeTest` 7). The branch was rebased onto leftover-docs #402 (`678f5ef3`); the measured tree remains leftover-docs #401 `2b94f0b611d6b71c121bfaf363903992d8fb8a0f`.
- This measurement adds no compiler XML. Parent skipped Gradle (measurement-only).

## Joined corpus totals

| Corpus | Inventory | IR | Legacy fallback | Constructor left in Java | Missing |
| --- | ---: | ---: | ---: | ---: | ---: |
| `ClassicTest` | 108 | 108 | 0 | 0 | 0 |
| `jdk17` | 82 | 82 | 0 | 0 | 0 |
| `jdk21` | 47 | 47 | 0 | 0 | 0 |
| `jdk25` | 21 | 21 | 0 | 0 | 0 |

- Processor changed: **No**
- Admitted: **No**
- Ship-ready: **No**
- Zero measured leftovers is **not coverage-complete**, **not a JDK support badge**, and **does not authorize a default flip**.
- Defaults remain `--codegen=legacy`, `--ir-lower=direct`, and `--backend=cpp`.

# 中文

## 摘要

- 在 leftover-docs #401 树上重新测量合并后的仓库内 IR 遗留项清单。
- 测量 SHA：`2b94f0b611d6b71c121bfaf363903992d8fb8a0f`。
- 测量时与 `origin/master` 的 merge-base：`2b94f0b611d6b71c121bfaf363903992d8fb8a0f`。
- 本次测量取代 #401 中对 leftover-docs #399（`454af268`，完整 SHA `454af2687204a17204d2a60c511d019582a14774`）的测量。
- 最新编译器父级 XML：**[#402](https://github.com/gaoyu06/native-obfuscator/pull/402)（695）**（`IrCompilerTest` 688 + `CodegenModeTest` 7）。分支已 rebase 到 leftover-docs #402（`678f5ef3`）；实测树仍为 leftover-docs #401 `2b94f0b611d6b71c121bfaf363903992d8fb8a0f`。
- 本次测量不新增编译器 XML。父任务跳过 Gradle（仅测量）。

## 合并语料总计

| 语料 | 清单方法数 | IR | Legacy 回退 | 构造器保留在 Java | 缺失 |
| --- | ---: | ---: | ---: | ---: | ---: |
| `ClassicTest` | 108 | 108 | 0 | 0 | 0 |
| `jdk17` | 82 | 82 | 0 | 0 | 0 |
| `jdk21` | 47 | 47 | 0 | 0 | 0 |
| `jdk25` | 21 | 21 | 0 | 0 | 0 |

- 处理器变更（Processor changed）：**否**
- 已准入（Admitted）：**否**
- 可发布（Ship-ready）：**否**
- 零遗留项**不代表覆盖完整**、**不是 JDK 支持标志**，也**不授权切换默认值**。
- 默认值仍为 `--codegen=legacy`、`--ir-lower=direct` 和 `--backend=cpp`。
