## Summary / 摘要

- Re-ran the in-tree IR leftover inventory on the post-#322 leftover-docs tree.
- 在 #322 之后的 leftover-docs 树上重新运行仓库内 IR 遗留项清单测量。
- Measurement commit and merge-base with `origin/master`: `ae1b8da1dbabb30585d52f84be3d5355735787eb`.
- 测量提交及其与 `origin/master` 的合并基点：`ae1b8da1dbabb30585d52f84be3d5355735787eb`。
- This measurement supersedes #321 on `73c279e6ac8fa16d9ab64bcf491ec18de01c6b92` (post-#320). #318 remains the earlier post-#317 snapshot; #315 remains the earlier post-#314 snapshot.
- 本次测量取代基于 `73c279e6ac8fa16d9ab64bcf491ec18de01c6b92`（#320 之后）的 #321。#318 仍是 #317 之后的早期快照；#315 仍是 #314 之后的早期快照。

## Joined totals / 关联汇总

| Corpus / 语料 | Inventory / 清单 | IR | Leftovers / 遗留项 |
| --- | ---: | ---: | ---: |
| `ClassicTest` | 108 | 108 | 0 |
| `jdk17` | 82 | 82 | 0 |
| `jdk21` | 47 | 47 | 0 |
| `jdk25` | 21 | 21 | 0 |

`Leftovers` combines legacy fallbacks and constructors left in Java; the exact method join also had zero missing entries.

`遗留项`合并统计 legacy fallback 与留在 Java 中的构造器；精确方法关联也没有缺失条目。

## Verification / 验证

- `python3 docs/measurement/ir-leftover-inventory/measure.py --javac-25 /tmp/temurin25/bin/javac` — passed; the helper's `:obfuscator:shadowJar` build passed.
- 上述测量命令通过；辅助脚本执行的 `:obfuscator:shadowJar` 构建通过。
- Parent verification: measurement-only; Gradle focused gate skipped.
- 父任务验证：仅测量；跳过 Gradle 聚焦门禁。

## Boundaries / 边界

- Processor changed: **No**.
- 处理器变更：**否**。
- Compiler/runtime source and defaults are unchanged: `--codegen=legacy`, `--ir-lower=direct`, and `--backend=cpp`.
- 编译器/运行时源码及默认值未变：`--codegen=legacy`、`--ir-lower=direct` 和 `--backend=cpp`。
- Zero measured leftovers is not coverage-complete, a JDK support badge, or production-goal completion.
- 测得零遗留项不表示覆盖完整、JDK 支持认证或生产目标完成。
- Ship-ready: **No**. Admitted: **No** (measurement only).
- 可发布：**否**。已准入：**否**（仅测量）。
