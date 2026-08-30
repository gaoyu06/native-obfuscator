## English

### Summary

- Measurement-only remeasurement of the checked-in IR admission fixtures on post-#291 master.
- Supersedes the #289 snapshot on `cdce5a3025c8ffb72af424b28aa18c68ffebdc14`.
- Changes only the leftover inventory report and this PR body. Processor changed: **No**.

### Joined totals

| Corpus | Inventory | IR | Legacy fallback | Constructor left in Java | Missing |
| --- | ---: | ---: | ---: | ---: | ---: |
| `ClassicTest` | 108 | 108 | 0 | 0 | 0 |
| `jdk17` | 82 | 82 | 0 | 0 | 0 |
| `jdk21` | 47 | 47 | 0 | 0 | 0 |
| `jdk25` | 21 | 21 | 0 | 0 | 0 |

- Admitted: **N/A — measurement**.
- Ship-ready: **No**.

This is not a JDK support badge, a coverage-complete result, or a behavioral/native E2E claim. Zero measured leftovers does not complete the production goal or authorize changing defaults. The defaults remain `--codegen=legacy`, `--ir-lower=direct`, and `--backend=cpp`.

### Validation

```bash
python3 docs/measurement/ir-leftover-inventory/measure.py --javac-25 /tmp/temurin25/bin/javac
```

## 中文

### 摘要

- 仅测量：在合入 #291 后的 master 上，重新测量仓库内已提交测试样例的 IR 准入余项。
- 本次结果取代基于 `cdce5a3025c8ffb72af424b28aa18c68ffebdc14` 的 #289 快照。
- 仅更新余项清单报告和本 PR 说明。处理器改动：**否**。

上表为本次逐方法精确关联后的汇总结果。准入项：**N/A — 仅测量**。可发布：**否**。

本次结果不是 JDK 支持声明，不代表覆盖完整，也不是行为或原生端到端验证。测得余项为零不代表生产目标已经完成，也不允许修改任何默认值。默认值仍为 `--codegen=legacy`、`--ir-lower=direct` 和 `--backend=cpp`。
