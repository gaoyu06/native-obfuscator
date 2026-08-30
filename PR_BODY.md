## Summary / 摘要

- Re-runs the in-tree IR leftover inventory on the post-#320 leftover-docs tree.
- 在 post-#320 leftover-docs 树上重新运行仓库内 IR leftover inventory。
- Records both the measured commit and its merge-base with `origin/master` as `73c279e6ac8fa16d9ab64bcf491ec18de01c6b92`.
- 记录测量提交及其与 `origin/master` 的 merge-base，二者均为 `73c279e6ac8fa16d9ab64bcf491ec18de01c6b92`。
- Supersedes #318 on `b35fa0b1d219e2204c1c0316dfe4720a3970a6a2` (post-#317); #315 remains the post-#314 snapshot and #310 remains the post-#309 snapshot.
- 本次测量取代基于 `b35fa0b1d219e2204c1c0316dfe4720a3970a6a2`（post-#317）的 #318；#315 仍是 post-#314 快照，#310 仍是 post-#309 快照。

## Joined totals / 汇总结果

| Corpus | Inventory | IR | Leftovers |
| --- | ---: | ---: | ---: |
| `ClassicTest` | 108 | 108 | 0 |
| `jdk17` | 82 | 82 | 0 |
| `jdk21` | 47 | 47 | 0 |
| `jdk25` | 21 | 21 | 0 |

`Leftovers` is the sum of measured legacy fallbacks and constructors left in Java; the exact joins also reported zero missing methods.

`Leftovers` 为测得的 legacy fallback 与留在 Java 中的构造器之和；精确关联结果中 missing 方法数也为零。

## Verification / 验证

- Passed: `python3 docs/measurement/ir-leftover-inventory/measure.py --javac-25 /tmp/temurin25/bin/javac`
- 通过：`python3 docs/measurement/ir-leftover-inventory/measure.py --javac-25 /tmp/temurin25/bin/javac`
- Measurement-only; the focused Gradle gate was skipped. The measurement helper's `:obfuscator:shadowJar` build passed.
- 仅测量；跳过 Gradle focused gate。测量脚本自身执行的 `:obfuscator:shadowJar` 构建通过。
- Processor changed: **No**. Compiler/runtime source and defaults are unchanged.
- Processor 变更：**否**。编译器/运行时源码和默认值均未变更。
- Ship-ready: **No**. Admitted: **No**.
- 可发布：**否**。已准入：**否**。

Zero measured leftovers is not coverage-complete, a JDK support badge, or production-goal completion.

测得零 leftover 不代表覆盖完整、JDK 支持认证或生产目标完成。
