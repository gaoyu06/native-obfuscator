## English

Remeasures the in-tree IR leftover inventory on post-[#332](https://github.com/gaoyu06/native-obfuscator/pull/332) master.

- Measured compiler base / measurement commit: `c4b646136a708a219db71860159c8214e93ab162`
- Merge-base with `origin/master`: `c4b646136a708a219db71860159c8214e93ab162`
- This post-#332 remeasurement supersedes [#331](https://github.com/gaoyu06/native-obfuscator/pull/331) on `753c401b06d3f3cfdcea56165bf09b6287f2fa19` (post-#330). [#329](https://github.com/gaoyu06/native-obfuscator/pull/329) remains the earlier post-#328 snapshot, and [#327](https://github.com/gaoyu06/native-obfuscator/pull/327) remains the earlier post-#326 snapshot.
- Latest compiler parent XML: [#332](https://github.com/gaoyu06/native-obfuscator/pull/332) (587). This measurement adds no compiler XML.
- Processor changed: **No**
- Ship-ready: **No**
- Admitted: **No** (measurement only)

| Corpus | Inventory | IR | Legacy fallback | Constructor left in Java | Missing |
| --- | ---: | ---: | ---: | ---: | ---: |
| ClassicTest | 108 | 108 | 0 | 0 | 0 |
| jdk17 | 82 | 82 | 0 | 0 | 0 |
| jdk21 | 47 | 47 | 0 | 0 | 0 |
| jdk25 | 21 | 21 | 0 | 0 | 0 |

Command actually run:

```bash
python3 docs/measurement/ir-leftover-inventory/measure.py --javac-25 /tmp/temurin25/bin/javac
```

This measures checked-in fixtures with explicit `--codegen=ir`. Inventory means methods with a `Code:` body in `javap -p -s -c`, joined by exact `class + method + descriptor`. `// IR codegen:` means IR; `falling back to legacy for this method` means legacy fallback; `leaving constructor bytecode unchanged` means constructor left in Java.

This is measurement only: it is not a JDK support badge, not coverage-complete, and not a behavioral/native E2E claim. Zero measured leftovers does not complete the production goal or authorize changing defaults. `--codegen=legacy`, `--ir-lower=direct`, and `--backend=cpp` remain unchanged.

## 中文

在 post-[#332](https://github.com/gaoyu06/native-obfuscator/pull/332) master 上重新测量仓库内的 IR 遗留清单。

- 实测编译器基线 / 测量提交：`c4b646136a708a219db71860159c8214e93ab162`
- 与 `origin/master` 的 merge-base：`c4b646136a708a219db71860159c8214e93ab162`
- 本次 post-#332 重测取代了基于 `753c401b06d3f3cfdcea56165bf09b6287f2fa19`（post-#330）的 [#331](https://github.com/gaoyu06/native-obfuscator/pull/331)。[#329](https://github.com/gaoyu06/native-obfuscator/pull/329) 仍是更早的 post-#328 快照，[#327](https://github.com/gaoyu06/native-obfuscator/pull/327) 仍是更早的 post-#326 快照。
- 最新编译器父 XML 仍为 [#332](https://github.com/gaoyu06/native-obfuscator/pull/332)（587）；本次测量没有新增编译器 XML。
- Processor 是否改变：**否**
- 是否可发布：**否**
- 是否准入：**否**（仅测量）

| 语料 | 清单总数 | IR | Legacy 回退 | 构造器留在 Java | 缺失 |
| --- | ---: | ---: | ---: | ---: | ---: |
| ClassicTest | 108 | 108 | 0 | 0 | 0 |
| jdk17 | 82 | 82 | 0 | 0 | 0 |
| jdk21 | 47 | 47 | 0 | 0 | 0 |
| jdk25 | 21 | 21 | 0 | 0 | 0 |

实际运行的命令：

```bash
python3 docs/measurement/ir-leftover-inventory/measure.py --javac-25 /tmp/temurin25/bin/javac
```

本次测量针对仓库内已检入、显式使用 `--codegen=ir` 的 fixture。清单定义为 `javap -p -s -c` 输出中具有 `Code:` 方法体的方法，并按精确的 `class + method + descriptor` 连接。`// IR codegen:` 表示 IR；`falling back to legacy for this method` 表示 Legacy 回退；`leaving constructor bytecode unchanged` 表示构造器留在 Java。

本次仅做测量：它不是 JDK 支持标志，不代表覆盖完整，也不是行为或原生 E2E 结论。遗留项为零不代表生产目标完成，也不授权修改默认值。`--codegen=legacy`、`--ir-lower=direct` 和 `--backend=cpp` 均保持不变。
