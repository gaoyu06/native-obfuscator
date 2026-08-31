# Parent PR title / 父 PR 标题

`docs: remeasure IR leftovers after leftover-docs #405`

## English

Remeasures the joined in-tree IR leftover inventory on the leftover-docs [#405](https://github.com/gaoyu06/native-obfuscator/pull/405) tree.

- Measurement SHA: `0914806e849601538b24b8fec4b92d614db828f8`
- Merge-base with `origin/master` at measurement: `0914806e849601538b24b8fec4b92d614db828f8`
- Supersedes [#405](https://github.com/gaoyu06/native-obfuscator/pull/405)'s measurement of leftover-docs [#403](https://github.com/gaoyu06/native-obfuscator/pull/403) at `a760bc0d` (`a760bc0d857f60210e649dc09af6e187487d0b2c`).
- Latest compiler parent XML after rebase onto leftover-docs #406 (`e6d8766c`): **[#406](https://github.com/gaoyu06/native-obfuscator/pull/406) (701)** (`IrCompilerTest` 694 + `CodegenModeTest` 7). Measured tree remains leftover-docs #405 `0914806e`. Parent skipped Gradle (measurement-only).

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

在 leftover-docs [#405](https://github.com/gaoyu06/native-obfuscator/pull/405) 树上重新测量仓库内各语料合并后的 IR 遗留项清单。

- 测量 SHA：`0914806e849601538b24b8fec4b92d614db828f8`
- 测量时与 `origin/master` 的 merge-base：`0914806e849601538b24b8fec4b92d614db828f8`
- 本次测量取代 [#405](https://github.com/gaoyu06/native-obfuscator/pull/405) 对 leftover-docs [#403](https://github.com/gaoyu06/native-obfuscator/pull/403) `a760bc0d`（`a760bc0d857f60210e649dc09af6e187487d0b2c`）所做的测量。
- rebase 到 leftover-docs #406（`e6d8766c`）后最新的编译器父级 XML：**[#406](https://github.com/gaoyu06/native-obfuscator/pull/406)（701）**（`IrCompilerTest` 694 + `CodegenModeTest` 7）。测量树仍为 leftover-docs #405 `0914806e`。父级跳过 Gradle（仅测量）。

| 语料 | 清单总数 | IR | Legacy 回退 | 构造函数保留在 Java | 缺失 |
| --- | ---: | ---: | ---: | ---: | ---: |
| `ClassicTest` | 108 | 108 | 0 | 0 | 0 |
| `jdk17` | 82 | 82 | 0 | 0 | 0 |
| `jdk21` | 47 | 47 | 0 | 0 | 0 |
| `jdk25` | 21 | 21 | 0 | 0 | 0 |

- Processor changed（处理器已更改）：**No（否）**
- Admitted（已准入）：**No（否）**
- Ship-ready（可发布）：**No（否）**

零遗留项并不表示覆盖完整，不是 JDK 支持徽章，也不授权切换默认值。默认值仍为 `--codegen=legacy`、`--ir-lower=direct` 和 `--backend=cpp`。
