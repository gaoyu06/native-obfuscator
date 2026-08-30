<!-- Scratch PR body for the parent agent. Remove PR_BODY.md from the branch before merge. -->

## English

### Summary

- Remeasure the post-#342 leftover-docs tree after extra-local `int` values were admitted as the first and fourth `Insets` `NEW` arguments.
- Refresh only the leftover inventory report; no compiler/runtime implementation or defaults changed.
- Supersede #341 on `b4ec2a75d7ec73c1eec76ba119e200486e37e386` (post-#340). #339 remains the earlier post-#338 snapshot, and #337 remains the earlier post-#336 snapshot.

### Joined corpus results

| Corpus | Inventory | IR | Legacy fallback | Constructor left in Java | Missing |
| --- | ---: | ---: | ---: | ---: | ---: |
| `ClassicTest` | 108 | 108 | 0 | 0 | 0 |
| `jdk17` | 82 | 82 | 0 | 0 | 0 |
| `jdk21` | 47 | 47 | 0 | 0 | 0 |
| `jdk25` | 21 | 21 | 0 | 0 | 0 |

### Measurement and status

- Measured SHA / merge-base: `6d7b34276fb3a2b8d043a0cac25f2f8c88fde2a0`
- Latest compiler parent XML: **#342 (602)**; this measurement adds no compiler XML.
- Processor changed: **No**
- Ship-ready: **No**
- Admitted: **No** (measurement only)
- Defaults remain `--codegen=legacy`, `--ir-lower=direct`, and `--backend=cpp`.

Zero measured leftovers is not coverage-complete, is not a JDK support badge, does not complete the production goal, and does not authorize a default flip.

### Verification

```bash
python3 docs/measurement/ir-leftover-inventory/measure.py --javac-25 /tmp/temurin25/bin/javac
```

## 中文

### 摘要

- 在 #342 的 leftover-docs 树上重新测量：#342 已允许 extra-local `int` 作为 `Insets` 的第一个和第四个 `NEW` 参数。
- 仅刷新 leftover inventory 报告；编译器/运行时实现和默认选项均未改变。
- 本次 post-#342 重测取代基于 `b4ec2a75d7ec73c1eec76ba119e200486e37e386`（post-#340）的 #341。#339 仍是更早的 post-#338 快照，#337 仍是更早的 post-#336 快照。

### 语料连接结果

| 语料 | Inventory | IR | Legacy fallback | 构造器保留在 Java | Missing |
| --- | ---: | ---: | ---: | ---: | ---: |
| `ClassicTest` | 108 | 108 | 0 | 0 | 0 |
| `jdk17` | 82 | 82 | 0 | 0 | 0 |
| `jdk21` | 47 | 47 | 0 | 0 | 0 |
| `jdk25` | 21 | 21 | 0 | 0 | 0 |

### 测量与状态

- 测量 SHA / merge-base：`6d7b34276fb3a2b8d043a0cac25f2f8c88fde2a0`
- 最新编译器父级 XML：**#342 (602)**；本次测量不新增编译器 XML。
- Processor changed：**No**
- Ship-ready：**No**
- Admitted：**No**（仅测量）
- 默认选项保持 `--codegen=legacy`、`--ir-lower=direct` 和 `--backend=cpp`。

测得 leftover 为零并不代表覆盖完整，不是 JDK 支持标志，不代表生产目标已完成，也不授权切换默认选项。

### 验证

```bash
python3 docs/measurement/ir-leftover-inventory/measure.py --javac-25 /tmp/temurin25/bin/javac
```
