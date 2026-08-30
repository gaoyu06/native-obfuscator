# Summary

This is a measurement-only remeasurement of the checked-in IR admission corpora after #285. It changes no compiler, runtime, tests, CLI defaults, or production-goal status.

- Measured compiler base: `5a9a0414ccef396e79a0bb32924b25649d146eea`
- Measurement commit: `5a9a0414ccef396e79a0bb32924b25649d146eea`
- Admitted: **N/A — measurement**
- Ship-ready: **No**
- Defaults remain `--codegen=legacy`, `--ir-lower=direct`, and `--backend=cpp`.

This is not a JDK 17/21/25 support claim, not coverage-complete, and not behavioral/native E2E evidence. Zero measured leftovers does not complete the production goal or authorize changing defaults.

## Joined totals

| Corpus | Inventory | IR | Legacy fallback | Constructor left in Java | Missing |
| --- | ---: | ---: | ---: | ---: | ---: |
| `ClassicTest` | 108 | 108 | 0 | 0 | 0 |
| `jdk17` | 82 | 82 | 0 | 0 | 0 |
| `jdk21` | 47 | 47 | 0 | 0 | 0 |
| `jdk25` | 21 | 21 | 0 | 0 | 0 |

# 中文说明

这是 #285 合入后针对仓库内 IR 接纳语料的纯测量复测。本次不修改编译器、运行时、测试、CLI 默认值，也不改变生产目标状态。

- 测量编译器基线：`5a9a0414ccef396e79a0bb32924b25649d146eea`
- 测量提交：`5a9a0414ccef396e79a0bb32924b25649d146eea`
- 接纳：**N/A — 仅测量**
- 可发布：**否**
- 默认值继续保持 `--codegen=legacy`、`--ir-lower=direct` 和 `--backend=cpp`。

本次结果不代表支持 JDK 17/21/25，不代表覆盖完整，也不是行为或原生端到端证据。测得零遗留项不代表生产目标已经完成，也不允许据此修改任何默认值。
