## English

### Summary

- Remeasures the IR leftover inventory on the post-[#344](https://github.com/gaoyu06/native-obfuscator/pull/344) leftover-docs tree, after extra-local `int` values were admitted as the second and third `Insets` `NEW` arguments.
- Measured SHA and merge-base: `39f17585091e00c7f4fa53b2f2a00fe0df8bddb7`.
- Supersedes [#343](https://github.com/gaoyu06/native-obfuscator/pull/343) on `6d7b34276fb3a2b8d043a0cac25f2f8c88fde2a0` (post-#342). [#341](https://github.com/gaoyu06/native-obfuscator/pull/341) remains the earlier post-#340 snapshot, and [#339](https://github.com/gaoyu06/native-obfuscator/pull/339) remains the earlier post-#338 snapshot.
- Latest compiler parent XML remains **#344 (605)** (`IrCompilerTest` 598 + `CodegenModeTest` 7). This measurement adds no compiler XML.

### Joined corpus results

| Corpus | Inventory | IR | Legacy fallback | Constructor left in Java | Missing |
| --- | ---: | ---: | ---: | ---: | ---: |
| `ClassicTest` | 108 | 108 | 0 | 0 | 0 |
| `jdk17` | 82 | 82 | 0 | 0 | 0 |
| `jdk21` | 47 | 47 | 0 | 0 | 0 |
| `jdk25` | 21 | 21 | 0 | 0 | 0 |

### Status and validation

- Processor changed: **No**
- Ship-ready: **No**
- Admitted: **No** (measurement only)
- Defaults remain `--codegen=legacy`, `--ir-lower=direct`, and `--backend=cpp`.
- Command: `python3 docs/measurement/ir-leftover-inventory/measure.py --javac-25 /tmp/temurin25/bin/javac`
- Zero measured leftovers is not coverage-complete, is not a JDK support badge, and does not authorize a default flip or completing the production goal.

## 中文

### 摘要

- 在 [#344](https://github.com/gaoyu06/native-obfuscator/pull/344) 后的 leftover-docs 树上重新测量 IR leftover inventory；#344 已接纳将额外局部 `int` 值用作 `Insets` 的第二和第三个 `NEW` 参数。
- 测量 SHA 与 merge-base：`39f17585091e00c7f4fa53b2f2a00fe0df8bddb7`。
- 本次 post-#344 重测取代基于 `6d7b34276fb3a2b8d043a0cac25f2f8c88fde2a0`（post-#342）的 [#343](https://github.com/gaoyu06/native-obfuscator/pull/343)。[#341](https://github.com/gaoyu06/native-obfuscator/pull/341) 仍是较早的 post-#340 快照，[#339](https://github.com/gaoyu06/native-obfuscator/pull/339) 仍是较早的 post-#338 快照。
- 最新 compiler parent XML 仍为 **#344 (605)**（`IrCompilerTest` 598 + `CodegenModeTest` 7）；本次测量不增加 compiler XML。

### 合并语料结果

| 语料 | Inventory | IR | Legacy fallback | Java 中保留的构造器 | Missing |
| --- | ---: | ---: | ---: | ---: | ---: |
| `ClassicTest` | 108 | 108 | 0 | 0 | 0 |
| `jdk17` | 82 | 82 | 0 | 0 | 0 |
| `jdk21` | 47 | 47 | 0 | 0 | 0 |
| `jdk25` | 21 | 21 | 0 | 0 | 0 |

### 状态与验证

- Processor changed：**No**
- Ship-ready：**No**
- Admitted：**No**（仅测量）
- 默认值保持不变：`--codegen=legacy`、`--ir-lower=direct`、`--backend=cpp`。
- 命令：`python3 docs/measurement/ir-leftover-inventory/measure.py --javac-25 /tmp/temurin25/bin/javac`
- 测得 leftover 为零不代表覆盖完整，不是 JDK 支持标志，也不授权切换默认值或宣布生产目标完成。
