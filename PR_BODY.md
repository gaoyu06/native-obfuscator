# English

## Summary

- Remeasures the joined IR leftover inventory on the exact post-[#379](https://github.com/gaoyu06/native-obfuscator/pull/379) leftover-docs commit `9fa518121ee00310888c9dd752094357e494d27c`, after extra-local `int` was admitted as the **second and fifth** five-argument `GregorianCalendar` `NEW` arguments.
- Measured compiler base (merge-base with `origin/master`) and measurement commit are both `9fa518121ee00310888c9dd752094357e494d27c`.
- This post-#379 snapshot supersedes [#378](https://github.com/gaoyu06/native-obfuscator/pull/378) on `de3f430bd01f225bb293dccd2e7898fcf6254f51` (post-#377).
- Latest compiler parent XML is **[#379](https://github.com/gaoyu06/native-obfuscator/pull/379) (659)** (`IrCompilerTest` 652 + `CodegenModeTest` 7). This measurement adds no compiler XML.

## Joined corpus

| Corpus | Inventory | IR | Legacy fallback | Constructor left in Java | Missing |
| --- | ---: | ---: | ---: | ---: | ---: |
| `ClassicTest` | 108 | 108 | 0 | 0 | 0 |
| `jdk17` | 82 | 82 | 0 | 0 | 0 |
| `jdk21` | 47 | 47 | 0 | 0 | 0 |
| `jdk25` | 21 | 21 | 0 | 0 | 0 |

## Parent verification

Measurement-only inventory change: processor changed **No**; admitted **No**. Merge-base and measurement commit `9fa518121ee00310888c9dd752094357e494d27c` match leftover-docs #379. This branch only changes `docs/benchmarks/ir-leftover-inventory.md` plus scratch `PR_BODY.md`.

Ship-ready: **No**. Defaults remain `--codegen=legacy`, `--ir-lower=direct`, and `--backend=cpp`. Zero leftovers is not coverage-complete, is not a JDK support badge, and does not authorize a default flip.

# 中文

## 摘要

- 在 [#379](https://github.com/gaoyu06/native-obfuscator/pull/379) leftover-docs 提交 `9fa518121ee00310888c9dd752094357e494d27c` 上重新测量合并后的 IR leftover inventory；该树已接纳额外局部 `int` 作为五参数 `GregorianCalendar` `NEW` 的**第二和第五个**参数。
- 实测编译器基线（与 `origin/master` 的 merge-base）和测量提交均为 `9fa518121ee00310888c9dd752094357e494d27c`。
- 本次 post-#379 快照取代基于 `de3f430bd01f225bb293dccd2e7898fcf6254f51`（post-#377）的 [#378](https://github.com/gaoyu06/native-obfuscator/pull/378)。
- 最新编译器父级 XML 为 **[#379](https://github.com/gaoyu06/native-obfuscator/pull/379)（659）**（`IrCompilerTest` 652 + `CodegenModeTest` 7）；本次测量不增加编译器 XML。

## 合并语料库

| 语料库 | 清单总数 | IR | Legacy fallback | 构造器留在 Java | 缺失 |
| --- | ---: | ---: | ---: | ---: | ---: |
| `ClassicTest` | 108 | 108 | 0 | 0 | 0 |
| `jdk17` | 82 | 82 | 0 | 0 | 0 |
| `jdk21` | 47 | 47 | 0 | 0 | 0 |
| `jdk25` | 21 | 21 | 0 | 0 | 0 |

## 父任务验证

本次仅为 inventory 测量：处理器变更为**否**；接纳变更为**否**。merge-base 和测量提交 `9fa518121ee00310888c9dd752094357e494d27c` 与 leftover-docs #379 一致，且此分支只改 `docs/benchmarks/ir-leftover-inventory.md` 和草稿 `PR_BODY.md`。

可交付状态：**否**。默认仍为 `--codegen=legacy`、`--ir-lower=direct` 和 `--backend=cpp`。零 leftover 不代表覆盖完整，不是 JDK 支持标志，也不允许翻转默认值。
