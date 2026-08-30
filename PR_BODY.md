# IR leftover inventory on post-#328 master / post-#328 master IR 遗留清单

## English

Remeasures the checked-in fixtures with explicit `--codegen=ir` after [#328](https://github.com/gaoyu06/native-obfuscator/pull/328), which admitted extra-local `int` values as the second and third `Color` `NEW` arguments.

- Measured compiler base: `b751addb1d42d52be7ec18943a0202c53d31a88b`
- Merge-base with `origin/master`: `b751addb1d42d52be7ec18943a0202c53d31a88b`
- Measurement commit: `b751addb1d42d52be7ec18943a0202c53d31a88b`
- This remeasurement supersedes [#327](https://github.com/gaoyu06/native-obfuscator/pull/327) on `0894b1609410b98b91db0d69a34adfa99cc4b090` (post-#326). [#325](https://github.com/gaoyu06/native-obfuscator/pull/325) remains the earlier post-#324 snapshot; [#323](https://github.com/gaoyu06/native-obfuscator/pull/323) remains the earlier post-#322 snapshot.
- Latest compiler parent XML: [#328](https://github.com/gaoyu06/native-obfuscator/pull/328) (581). This measurement adds no compiler XML.

### Joined totals

| Corpus | Inventory | IR | Legacy fallback | Constructor left in Java | Missing |
| --- | ---: | ---: | ---: | ---: | ---: |
| `ClassicTest` | 108 | 108 | 0 | 0 | 0 |
| `jdk17` | 82 | 82 | 0 | 0 | 0 |
| `jdk21` | 47 | 47 | 0 | 0 | 0 |
| `jdk25` | 21 | 21 | 0 | 0 | 0 |

- Processor changed: **No**
- Ship-ready: **No**
- Admitted: **No** (measurement only)

This is measurement only: it is not a JDK support badge, coverage-complete, or a behavioral/native E2E claim. Zero measured leftovers does not complete the production goal or authorize changing defaults. `--codegen=legacy`, `--ir-lower=direct`, and `--backend=cpp` remain unchanged.

Inventory is the set of `javap -p -s -c` methods with a `Code:` body, joined by exact `class + method + descriptor`. `// IR codegen:` means IR; `falling back to legacy for this method` means legacy fallback; `leaving constructor bytecode unchanged` means constructor left in Java.

Command actually run:

```bash
python3 docs/measurement/ir-leftover-inventory/measure.py --javac-25 /tmp/temurin25/bin/javac
```

## 中文

本次在 [#328](https://github.com/gaoyu06/native-obfuscator/pull/328) 之后，使用显式 `--codegen=ir` 重新测量仓库内已签入的 fixtures；#328 接纳了作为第二和第三个 `Color` `NEW` 参数的额外局部 `int` 值。

- 被测编译器基线：`b751addb1d42d52be7ec18943a0202c53d31a88b`
- 与 `origin/master` 的 merge-base：`b751addb1d42d52be7ec18943a0202c53d31a88b`
- 测量提交：`b751addb1d42d52be7ec18943a0202c53d31a88b`
- 本次重测取代了 `0894b1609410b98b91db0d69a34adfa99cc4b090`（post-#326）上的 [#327](https://github.com/gaoyu06/native-obfuscator/pull/327)。[#325](https://github.com/gaoyu06/native-obfuscator/pull/325) 仍是较早的 post-#324 快照；[#323](https://github.com/gaoyu06/native-obfuscator/pull/323) 仍是较早的 post-#322 快照。
- 最新编译器父 XML 仍为 [#328](https://github.com/gaoyu06/native-obfuscator/pull/328)（581）；本次测量不新增编译器 XML。

上表为合并后的总数。

- Processor changed（处理器已更改）：**No**
- Ship-ready（可发布）：**No**
- Admitted（已接纳）：**No**（仅测量）

本结果仅代表测量：它不是 JDK 支持标章，不代表覆盖完整，也不是行为或原生端到端验证。测得零遗留不表示生产目标完成，也不授权修改默认值。`--codegen=legacy`、`--ir-lower=direct` 和 `--backend=cpp` 均保持不变。

清单由 `javap -p -s -c` 中带 `Code:` 方法体的方法组成，并按精确的 `class + method + descriptor` 连接。`// IR codegen:` 表示 IR；`falling back to legacy for this method` 表示回退到 legacy；`leaving constructor bytecode unchanged` 表示构造器保留在 Java 中。

实际运行的命令：

```bash
python3 docs/measurement/ir-leftover-inventory/measure.py --javac-25 /tmp/temurin25/bin/javac
```
