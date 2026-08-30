# IR leftover inventory on post-#330 master / post-#330 master 上的 IR 遗留项清单

## English

This measurement-only remeasurement inventories checked-in fixtures with explicit `--codegen=ir`. It supersedes #329 on `b751addb1d42d52be7ec18943a0202c53d31a88b` (post-#328); #327 remains the earlier post-#326 snapshot, and #325 remains the earlier post-#324 snapshot.

- Measured SHA: `753c401b06d3f3cfdcea56165bf09b6287f2fa19`
- Merge-base SHA: `753c401b06d3f3cfdcea56165bf09b6287f2fa19`
- Latest compiler parent XML: #330 (584); this measurement adds no compiler XML.
- Processor changed: **No**
- Ship-ready: **No**
- Admitted: **No** (measurement only)

Inventory means `javap -p -s -c` methods with a `Code:` body, joined by exact `class + method + descriptor`.

| Corpus | Inventory | IR | Legacy fallback | Constructor left in Java | Missing |
| --- | ---: | ---: | ---: | ---: | ---: |
| `ClassicTest` | 108 | 108 | 0 | 0 | 0 |
| `jdk17` | 82 | 82 | 0 | 0 | 0 |
| `jdk21` | 47 | 47 | 0 | 0 | 0 |
| `jdk25` | 21 | 21 | 0 | 0 | 0 |

Command actually run:

```bash
python3 docs/measurement/ir-leftover-inventory/measure.py --javac-25 /tmp/temurin25/bin/javac
```

This is not a JDK support badge, coverage-complete result, or behavioral/native E2E claim. Zero measured leftovers does not complete the production goal or authorize changing defaults. `--codegen=legacy`, `--ir-lower=direct`, and `--backend=cpp` remain unchanged.

## 中文

本次仅做测量，使用显式 `--codegen=ir` 对仓库内已检入的 fixture 进行遗留项清点。本次重测取代基于 `b751addb1d42d52be7ec18943a0202c53d31a88b`（post-#328）的 #329；#327 仍是较早的 post-#326 快照，#325 仍是较早的 post-#324 快照。

- 实际测量 SHA：`753c401b06d3f3cfdcea56165bf09b6287f2fa19`
- Merge-base SHA：`753c401b06d3f3cfdcea56165bf09b6287f2fa19`
- 最新编译器父 XML：#330（584）；本次测量未新增编译器 XML。
- Processor 有变更：**否**
- 可发布：**否**
- 已准入：**否**（仅测量）

清单统计 `javap -p -s -c` 输出中包含 `Code:` 主体的方法，并按精确的 `class + method + descriptor` 连接结果。

| 语料 | 清单方法数 | IR | 回退到 legacy | 构造器留在 Java | 缺失 |
| --- | ---: | ---: | ---: | ---: | ---: |
| `ClassicTest` | 108 | 108 | 0 | 0 | 0 |
| `jdk17` | 82 | 82 | 0 | 0 | 0 |
| `jdk21` | 47 | 47 | 0 | 0 | 0 |
| `jdk25` | 21 | 21 | 0 | 0 | 0 |

实际运行命令：

```bash
python3 docs/measurement/ir-leftover-inventory/measure.py --javac-25 /tmp/temurin25/bin/javac
```

本结果不是 JDK 支持标志，不代表覆盖完整，也不是行为或原生 E2E 结论。测得零遗留项不表示生产目标已完成，也不授权修改默认值。`--codegen=legacy`、`--ir-lower=direct` 和 `--backend=cpp` 均保持不变。
