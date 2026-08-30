<!-- CURSOR_AGENT_PR_BODY_BEGIN -->
# English

## Summary

- Re-runs the in-tree IR leftover inventory on post-#317 master (`b35fa0b`).
- The measured tree includes #317, which covers an extra-local `int` used as both arguments of a two-argument `NEW`.
- Measured SHA and merge-base: `b35fa0b1d219e2204c1c0316dfe4720a3970a6a2`.
- Supersedes #315 on `434c4895cd650b05f7474f7eb23292fe744869fe` (post-#314). #310 remains the post-#309 snapshot; #306 remains the post-#304 snapshot; #301 remains the post-#299 snapshot.
- Updates measurement documentation only; processor changed: **No**. Compiler/runtime source and defaults are unchanged.

## Results

| Corpus | Inventory | IR | Leftovers |
| --- | ---: | ---: | ---: |
| ClassicTest | 108 | 108 | 0 |
| jdk17 | 82 | 82 | 0 |
| jdk21 | 47 | 47 | 0 |
| jdk25 | 21 | 21 | 0 |

Command:

```bash
python3 docs/measurement/ir-leftover-inventory/measure.py --javac-25 /tmp/temurin25/bin/javac
```

This is measurement only. Zero measured leftovers is not a JDK support badge, is not coverage-complete, is not a behavioral/native E2E claim, and does not authorize changing defaults. The defaults remain `--codegen=legacy`, `--ir-lower=direct`, and `--backend=cpp`.

Parent verification: measurement-only; Gradle focused gate skipped.

Ship-ready: **No**. Admitted: **No** (measurement only).

# 中文

## 摘要

- 在 post-#317 master（`b35fa0b`）上重新运行仓库内 IR leftover inventory。
- 实测树包含 #317，即额外局部 `int` 同时用作双参数 `NEW` 的两个参数。
- 实测 SHA 与 merge-base：`b35fa0b1d219e2204c1c0316dfe4720a3970a6a2`。
- 本次测量取代基于 post-#314 `434c4895cd650b05f7474f7eb23292fe744869fe` 的 #315。#310 仍是 post-#309 快照；#306 仍是 post-#304 快照；#301 仍是 post-#299 快照。
- 仅更新测量文档；处理器变更：**否**。编译器、运行时源码和默认选项均未修改。

## 结果

| 语料 | 方法总数 | IR | 剩余项 |
| --- | ---: | ---: | ---: |
| ClassicTest | 108 | 108 | 0 |
| jdk17 | 82 | 82 | 0 |
| jdk21 | 47 | 47 | 0 |
| jdk25 | 21 | 21 | 0 |

本次仅为测量。零个实测剩余项不是 JDK 支持徽章，不代表覆盖完整，不是行为或原生端到端结论，也不授权修改默认选项。默认选项仍为 `--codegen=legacy`、`--ir-lower=direct` 和 `--backend=cpp`。

父级核验：仅测量；跳过 Gradle 聚焦门禁。

可发布：**否**。已准入：**否**（仅测量）。
<!-- CURSOR_AGENT_PR_BODY_END -->
