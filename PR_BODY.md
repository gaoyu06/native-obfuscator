# Post-#324 IR leftover inventory / #324 后 IR 遗留项清单

## English

This measurement reruns the in-tree IR leftover inventory on the post-#324 leftover-docs tree. It supersedes #323's post-#322 snapshot; #321 remains the earlier post-#320 snapshot, and #318 remains the earlier post-#317 snapshot.

- Measured commit: `4f8612a6e75911c7cbd91bad5d424763f8b5b460`
- Merge-base with `origin/master`: `4f8612a6e75911c7cbd91bad5d424763f8b5b460`
- Processor changed: **No**
- Ship-ready: **No**
- Admitted: **No** (measurement only)
- Compiler/runtime source and defaults changed: **No**

| Corpus | Inventory | IR | Leftovers |
| --- | ---: | ---: | ---: |
| ClassicTest | 108 | 108 | 0 |
| jdk17 | 82 | 82 | 0 |
| jdk21 | 47 | 47 | 0 |
| jdk25 | 21 | 21 | 0 |

Zero measured leftovers is not coverage-complete, a JDK support badge, or authorization to change defaults.

Verification: ran `python3 docs/measurement/ir-leftover-inventory/measure.py --javac-25 /tmp/temurin25/bin/javac`. This is measurement-only; the focused Gradle gate was skipped.

## 中文

本次测量在 #324 之后的 leftover-docs 树上重新运行仓库内 IR 遗留项清单。它取代 #323 的 post-#322 快照；#321 仍是较早的 post-#320 快照，#318 仍是较早的 post-#317 快照。

- 测量提交：`4f8612a6e75911c7cbd91bad5d424763f8b5b460`
- 与 `origin/master` 的合并基点：`4f8612a6e75911c7cbd91bad5d424763f8b5b460`
- Processor 有变更：**否**
- 可发布：**否**
- 已准入：**否**（仅测量）
- 编译器/运行时源码及默认值有变更：**否**

| 语料集 | 清单总数 | IR | 遗留项 |
| --- | ---: | ---: | ---: |
| ClassicTest | 108 | 108 | 0 |
| jdk17 | 82 | 82 | 0 |
| jdk21 | 47 | 47 | 0 |
| jdk25 | 21 | 21 | 0 |

测得遗留项为零并不代表覆盖完整，不是 JDK 支持徽章，也不授权更改默认值。

验证：已运行 `python3 docs/measurement/ir-leftover-inventory/measure.py --javac-25 /tmp/temurin25/bin/javac`。本次仅进行测量，跳过了 Gradle 聚焦门禁。
