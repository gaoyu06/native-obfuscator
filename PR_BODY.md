<!-- CURSOR_AGENT_PR_BODY_BEGIN -->
# English

## Summary

- Remeasures the joined IR leftover inventory on the post-[#372](https://github.com/gaoyu06/native-obfuscator/pull/372) leftover-docs tree, after an extra-local `int` was admitted as the **first and fourth** five-argument `GregorianCalendar` `NEW` arguments.
- Measured compiler base (merge-base with `origin/master`) and measurement commit: `80ed2e089da9da105ff63767228b8999470a4b8e`.
- This post-#372 snapshot supersedes [#371](https://github.com/gaoyu06/native-obfuscator/pull/371) on `28f3f15c4e1131cc0d29ecee64aff0eab286b312` (post-#370).
- Latest compiler parent XML remains **[#372](https://github.com/gaoyu06/native-obfuscator/pull/372) (647)** (`IrCompilerTest` 640 + `CodegenModeTest` 7). This measurement adds no compiler XML.

## Joined corpus

| Corpus | Inventory | IR | Legacy fallback | Constructor left in Java | Missing |
| --- | ---: | ---: | ---: | ---: | ---: |
| `ClassicTest` | 108 | 108 | 0 | 0 | 0 |
| `jdk17` | 82 | 82 | 0 | 0 | 0 |
| `jdk21` | 47 | 47 | 0 | 0 | 0 |
| `jdk25` | 21 | 21 | 0 | 0 | 0 |

## Parent verification

Measurement-only inventory change: processor changed **No**; admitted **No**; focused Gradle compiler gate skipped. The helper confirmed merge-base and measurement commit `80ed2e0` match leftover-docs #372, and this branch only changes `docs/benchmarks/ir-leftover-inventory.md` plus scratch `PR_BODY.md`.

Ship-ready: **No**. Defaults remain `--codegen=legacy`, `--ir-lower=direct`, and `--backend=cpp`. Zero leftovers is not coverage-complete, is not a JDK support badge, and does not authorize a default flip.

# 中文

## 摘要

- 在 [#372](https://github.com/gaoyu06/native-obfuscator/pull/372) 之后的 leftover-docs 树上重新测量合并后的 IR leftover inventory；该树已接纳额外局部 `int` 作为五参数 `GregorianCalendar` `NEW` 的**第一和第四个**参数。
- 实测编译器基线（与 `origin/master` 的 merge-base）和测量提交均为 `80ed2e089da9da105ff63767228b8999470a4b8e`。
- 本次 post-#372 快照取代基于 `28f3f15c4e1131cc0d29ecee64aff0eab286b312`（post-#370）的 [#371](https://github.com/gaoyu06/native-obfuscator/pull/371)。
- 最新编译器父级 XML 仍为 **[#372](https://github.com/gaoyu06/native-obfuscator/pull/372)（647）**（`IrCompilerTest` 640 + `CodegenModeTest` 7）；本次测量不增加编译器 XML。

## 合并语料库

| 语料库 | 清单总数 | IR | Legacy fallback | 构造器留在 Java | 缺失 |
| --- | ---: | ---: | ---: | ---: | ---: |
| `ClassicTest` | 108 | 108 | 0 | 0 | 0 |
| `jdk17` | 82 | 82 | 0 | 0 | 0 |
| `jdk21` | 47 | 47 | 0 | 0 | 0 |
| `jdk25` | 21 | 21 | 0 | 0 | 0 |

## 父任务验证

本次仅为 inventory 测量：处理器变更为**否**；接纳变更为**否**；按要求跳过 Gradle 聚焦编译器门。测量脚本确认 merge-base 和测量提交 `80ed2e0` 与 leftover-docs #372 一致，且此分支只改 `docs/benchmarks/ir-leftover-inventory.md` 和草稿 `PR_BODY.md`。

可交付状态：**否**。默认仍为 `--codegen=legacy`、`--ir-lower=direct` 和 `--backend=cpp`。零 leftover 不代表覆盖完整，不是 JDK 支持标志，也不允许翻转默认值。

<!-- CURSOR_AGENT_PR_BODY_END -->
