# Post-#348 IR leftover inventory remeasurement / #348 后 IR 剩余项重新测量

## English

### Summary

- Remeasures the joined IR leftover inventory on the post-[#348](https://github.com/gaoyu06/native-obfuscator/pull/348) leftover-docs tree, after admitting an extra-local `int` as the third and fourth `Insets` `NEW` arguments.
- Supersedes [#347](https://github.com/gaoyu06/native-obfuscator/pull/347) on `d842255164505180098e47828c758011ae59e2ea` (post-#346). [#345](https://github.com/gaoyu06/native-obfuscator/pull/345) remains the earlier post-#344 snapshot, and [#343](https://github.com/gaoyu06/native-obfuscator/pull/343) remains the earlier post-#342 snapshot.
- Measured commit and merge-base: `94a4e0e1a4a6d7cfcf1e8e5b9a6ec2a85a6659e0`.
- Latest compiler parent XML remains **[#348](https://github.com/gaoyu06/native-obfuscator/pull/348) (611)** (`IrCompilerTest` 604 + `CodegenModeTest` 7). This measurement adds no compiler XML.

### Joined corpus results

| Corpus | Inventory | IR | Legacy fallback | Constructor left in Java | Missing |
| --- | ---: | ---: | ---: | ---: | ---: |
| `ClassicTest` | 108 | 108 | 0 | 0 | 0 |
| `jdk17` | 82 | 82 | 0 | 0 | 0 |
| `jdk21` | 47 | 47 | 0 | 0 | 0 |
| `jdk25` | 21 | 21 | 0 | 0 | 0 |

### Verification and status

- Command: `python3 docs/measurement/ir-leftover-inventory/measure.py --javac-25 /tmp/temurin25/bin/javac`
- JDK 25 compiler: Temurin `javac 25.0.4.1`
- Processor changed: **No**
- Ship-ready: **No**
- Admitted: **No** (measurement only)
- Defaults remain `--codegen=legacy`, `--ir-lower=direct`, and `--backend=cpp`.

Zero measured leftovers is not coverage-complete, is not a JDK support badge, and does not authorize a default flip or mark the production goal complete.

## 中文

### 摘要

- 在 [#348](https://github.com/gaoyu06/native-obfuscator/pull/348) 合入后的 leftover-docs 树上重新测量 IR 剩余项；#348 覆盖了将额外局部 `int` 作为 `Insets` 的第三和第四个 `NEW` 参数的场景。
- 本次快照取代基于 `d842255164505180098e47828c758011ae59e2ea`（#346 后）的 [#347](https://github.com/gaoyu06/native-obfuscator/pull/347)。[#345](https://github.com/gaoyu06/native-obfuscator/pull/345) 仍是 #344 后的早期快照，[#343](https://github.com/gaoyu06/native-obfuscator/pull/343) 仍是 #342 后的早期快照。
- 测量提交和 merge-base 均为 `94a4e0e1a4a6d7cfcf1e8e5b9a6ec2a85a6659e0`。
- 最新编译器父 XML 仍为 **[#348](https://github.com/gaoyu06/native-obfuscator/pull/348) (611)**（`IrCompilerTest` 604 + `CodegenModeTest` 7）；本次测量不增加编译器 XML。

### 联合语料结果

| 语料 | 清单 | IR | legacy fallback | 构造器保留在 Java | 缺失 |
| --- | ---: | ---: | ---: | ---: | ---: |
| `ClassicTest` | 108 | 108 | 0 | 0 | 0 |
| `jdk17` | 82 | 82 | 0 | 0 | 0 |
| `jdk21` | 47 | 47 | 0 | 0 | 0 |
| `jdk25` | 21 | 21 | 0 | 0 | 0 |

### 验证与状态

- 命令：`python3 docs/measurement/ir-leftover-inventory/measure.py --javac-25 /tmp/temurin25/bin/javac`
- JDK 25 编译器：Temurin `javac 25.0.4.1`
- Processor changed：**No**
- Ship-ready：**No**
- Admitted：**No**（仅测量）
- 默认值保持 `--codegen=legacy`、`--ir-lower=direct` 和 `--backend=cpp`。

测得剩余项为零不表示覆盖完整，不是 JDK 支持标志，也不授权切换默认值或将生产目标标记为完成。
