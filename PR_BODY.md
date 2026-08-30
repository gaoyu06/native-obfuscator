# English

## Summary

This is a measurement-only post-#288 remeasurement of the checked-in IR admission corpora. It overwrites `docs/benchmarks/ir-leftover-inventory.md` with the helper-generated evidence from compiler base and measurement commit `cdce5a3025c8ffb72af424b28aa18c68ffebdc14`.

No compiler, runtime, test, or CLI-default code changed.

## Measurement

Command run:

```bash
python3 docs/measurement/ir-leftover-inventory/measure.py --javac-25 /tmp/temurin25/bin/javac
```

Joined totals:

| Corpus | Inventory | IR | Legacy fallback | Constructor left in Java | Missing |
| --- | ---: | ---: | ---: | ---: | ---: |
| `ClassicTest` | 108 | 108 | 0 | 0 | 0 |
| `jdk17` | 82 | 82 | 0 | 0 | 0 |
| `jdk21` | 47 | 47 | 0 | 0 | 0 |
| `jdk25` | 21 | 21 | 0 | 0 | 0 |

This is not a JDK support badge, a coverage-complete result, or a behavioral/native E2E claim. Zero measured leftovers does not complete the production goal or authorize changing any default. The defaults remain `--codegen=legacy`, `--ir-lower=direct`, and `--backend=cpp`.

- Processor changed: **No**
- Admitted: **N/A — measurement**
- Ship-ready: **No**

# 中文

## 摘要

本 PR 仅重新测量 #288 之后仓库内已检入语料的 IR 准入情况，并用编译器基线及测量提交 `cdce5a3025c8ffb72af424b28aa18c68ffebdc14` 上的辅助脚本输出覆盖 `docs/benchmarks/ir-leftover-inventory.md`。

本次未修改编译器、运行时、测试或 CLI 默认值。

## 测量结果

实际运行命令：

```bash
python3 docs/measurement/ir-leftover-inventory/measure.py --javac-25 /tmp/temurin25/bin/javac
```

合并统计：

| Corpus | Inventory | IR | Legacy fallback | Constructor left in Java | Missing |
| --- | ---: | ---: | ---: | ---: | ---: |
| `ClassicTest` | 108 | 108 | 0 | 0 | 0 |
| `jdk17` | 82 | 82 | 0 | 0 | 0 |
| `jdk21` | 47 | 47 | 0 | 0 | 0 |
| `jdk25` | 21 | 21 | 0 | 0 | 0 |

本结果不是 JDK 支持声明，不代表覆盖完整，也不是行为或原生端到端验证。测得零遗留项不代表生产目标已经完成，也不允许修改任何默认值。默认值仍为 `--codegen=legacy`、`--ir-lower=direct` 和 `--backend=cpp`。

- Processor changed（处理器是否变更）：**No**
- Admitted（准入）：**N/A — measurement**
- Ship-ready（可发布）：**No**
