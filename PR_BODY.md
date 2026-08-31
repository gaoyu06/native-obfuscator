# English

## Summary

- Remeasures the joined in-tree IR leftover inventory on the leftover-docs #403 tree.
- Measurement SHA: `a760bc0d857f60210e649dc09af6e187487d0b2c`.
- Merge-base with `origin/master` at measurement: `a760bc0d857f60210e649dc09af6e187487d0b2c`.
- Supersedes #403's measurement of the leftover-docs #401 tree at `2b94f0b6` (`2b94f0b611d6b71c121bfaf363903992d8fb8a0f`).
- Latest compiler parent XML at measurement time: **#402 (695)** (`IrCompilerTest` 688 + `CodegenModeTest` 7). This measurement adds no compiler XML.

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
- Zero leftovers is **not coverage-complete**, **not a JDK support badge**, and **does not authorize a default flip**.
- Defaults remain `--codegen=legacy`, `--ir-lower=direct`, and `--backend=cpp`.

# 中文

## 摘要

- 在 leftover-docs #403 树上重新测量仓库内合并后的 IR 遗留项清单。
- 测量 SHA：`a760bc0d857f60210e649dc09af6e187487d0b2c`。
- 测量时与 `origin/master` 的合并基点：`a760bc0d857f60210e649dc09af6e187487d0b2c`。
- 本次重新测量取代 #403 对 leftover-docs #401 树 `2b94f0b6`（`2b94f0b611d6b71c121bfaf363903992d8fb8a0f`）的测量。
- 测量时最新的编译器父级 XML：**#402（695）**（`IrCompilerTest` 688 + `CodegenModeTest` 7）。本次测量不增加编译器 XML。

## 合并语料总计

| 语料 | 清单方法数 | IR | 回退到 legacy | 构造函数保留在 Java | 缺失 |
| --- | ---: | ---: | ---: | ---: | ---: |
| `ClassicTest` | 108 | 108 | 0 | 0 | 0 |
| `jdk17` | 82 | 82 | 0 | 0 | 0 |
| `jdk21` | 47 | 47 | 0 | 0 | 0 |
| `jdk25` | 21 | 21 | 0 | 0 | 0 |

- 处理器变更：**否**
- 已准入：**否**
- 可发布：**否**
- 零遗留项**不代表覆盖完整**、**不是 JDK 支持徽章**，并且**不授权切换默认值**。
- 默认值仍为 `--codegen=legacy`、`--ir-lower=direct` 和 `--backend=cpp`。
