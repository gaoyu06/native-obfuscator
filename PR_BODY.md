# English

## Summary

This measurement reruns the in-tree IR leftover inventory on the post-#346 leftover-docs tree after an extra-local `int` was admitted as the second and fourth `Insets` `NEW` arguments. It supersedes #345's post-#344 snapshot; #343 remains the earlier post-#342 snapshot, and #341 remains the earlier post-#340 snapshot.

- Measured commit: `d842255164505180098e47828c758011ae59e2ea`
- Merge-base with `origin/master`: `d842255164505180098e47828c758011ae59e2ea`
- Processor changed: **No**
- Ship-ready: **No**
- Admitted: **No** (measurement only)
- Compiler/runtime source changed: **No**
- Defaults remain `--codegen=legacy`, `--ir-lower=direct`, and `--backend=cpp`
- Latest compiler parent XML remains **#346 (608)** (`IrCompilerTest` 601 + `CodegenModeTest` 7); this measurement adds no compiler XML

| Corpus | Inventory | IR | Legacy fallback | Constructor left in Java | Missing |
| --- | ---: | ---: | ---: | ---: | ---: |
| ClassicTest | 108 | 108 | 0 | 0 | 0 |
| jdk17 | 82 | 82 | 0 | 0 | 0 |
| jdk21 | 47 | 47 | 0 | 0 | 0 |
| jdk25 | 21 | 21 | 0 | 0 | 0 |

Zero measured leftovers is not coverage-complete, is not a JDK support badge, and does not authorize a default flip or mark the production goal complete.

Verification: ran `python3 docs/measurement/ir-leftover-inventory/measure.py --javac-25 /tmp/temurin25/bin/javac`. The exact method join reported no missing methods. This is measurement-only; the focused Gradle gate was skipped.

# 中文

## 摘要

本次测量在 #346 之后的 leftover-docs 树上重新运行仓库内 IR 遗留项清单；#346 已准入将额外局部 `int` 用作 `Insets` `NEW` 的第二和第四个参数。本次结果取代 #345 的 post-#344 快照；#343 仍是较早的 post-#342 快照，#341 仍是较早的 post-#340 快照。

- 测量提交：`d842255164505180098e47828c758011ae59e2ea`
- 与 `origin/master` 的合并基点：`d842255164505180098e47828c758011ae59e2ea`
- Processor 有变更：**否**
- 可发布：**否**
- 已准入：**否**（仅测量）
- 编译器/运行时源码有变更：**否**
- 默认值仍为 `--codegen=legacy`、`--ir-lower=direct`、`--backend=cpp`
- 最新编译器父级 XML 仍为 **#346 (608)**（`IrCompilerTest` 601 + `CodegenModeTest` 7）；本次测量不增加编译器 XML

| 语料集 | 清单总数 | IR | legacy fallback | 构造器保留在 Java | 缺失 |
| --- | ---: | ---: | ---: | ---: | ---: |
| ClassicTest | 108 | 108 | 0 | 0 | 0 |
| jdk17 | 82 | 82 | 0 | 0 | 0 |
| jdk21 | 47 | 47 | 0 | 0 | 0 |
| jdk25 | 21 | 21 | 0 | 0 | 0 |

测得遗留项为零并不代表覆盖完整，不是 JDK 支持徽章，也不授权切换默认值或将生产目标标记为完成。

验证：已运行 `python3 docs/measurement/ir-leftover-inventory/measure.py --javac-25 /tmp/temurin25/bin/javac`。精确方法连接没有报告缺失方法。本次仅进行测量，跳过了 Gradle 聚焦门禁。
