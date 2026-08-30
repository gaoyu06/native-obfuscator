## English

### Summary

- Measurement-only remeasurement of the checked-in IR admission fixtures on post-#294 master.
- Supersedes the #292 snapshot on `2fbd89d7f12c486c9bc472700830e8dc42f0f9ec` (post-#291).
- #294 admitted isolated six-argument int-family `NEW`; #293 is a fail-closed audit of unproven wide `NEW`.
- Changes only the leftover inventory report and this PR body. Processor changed: **No**.

### Joined totals

| Corpus | Inventory | IR | Legacy fallback | Constructor left in Java | Missing |
| --- | ---: | ---: | ---: | ---: | ---: |
| `ClassicTest` | 108 | 108 | 0 | 0 | 0 |
| `jdk17` | 82 | 82 | 0 | 0 | 0 |
| `jdk21` | 47 | 47 | 0 | 0 | 0 |
| `jdk25` | 21 | 21 | 0 | 0 | 0 |

- Admitted: **No — measurement only**.
- Ship-ready: **No**.

This inventory counts checked-in fixtures only. It is not a JDK support badge, a coverage-complete result, or a behavioral/native E2E claim. Zero measured leftovers does not complete the production goal or authorize changing defaults. The defaults remain `--codegen=legacy`, `--ir-lower=direct`, and `--backend=cpp`; Java 8 remains the only version described as fully supported.

### Validation

```bash
python3 docs/measurement/ir-leftover-inventory/measure.py --javac-25 /tmp/temurin25/bin/javac
```

## 中文

### 摘要

- 仅测量：在合入 #294 后的 master 上，重新测量仓库内已提交测试样例的 IR 准入余项。
- 本次结果取代基于 `2fbd89d7f12c486c9bc472700830e8dc42f0f9ec`（合入 #291 后）的 #292 快照。
- #294 准入了隔离的六参数 int 系列 `NEW`；#293 对未经证明的 wide `NEW` 保持失败关闭。
- 仅更新余项清单报告和本 PR 说明。处理器改动：**否**。

上表为本次逐方法精确关联后的汇总结果。准入项：**否 — 仅测量**。可发布：**否**。

本清单只统计仓库内已提交的测试样例。本次结果不是 JDK 支持声明，不代表覆盖完整，也不是行为或原生端到端验证。测得余项为零不代表生产目标已经完成，也不允许修改任何默认值。默认值仍为 `--codegen=legacy`、`--ir-lower=direct` 和 `--backend=cpp`；Java 8 仍是唯一被描述为完全支持的版本。
