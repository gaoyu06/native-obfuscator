# English

## Summary

- Remeasures the in-tree IR leftover inventory on the post-[#340](https://github.com/gaoyu06/native-obfuscator/pull/340) leftover-docs tree, after extra-local `int` values were admitted as the first and third `Insets` `NEW` arguments.
- Measured SHA: `b4ec2a75d7ec73c1eec76ba119e200486e37e386`
- Merge-base with `origin/master`: `b4ec2a75d7ec73c1eec76ba119e200486e37e386`
- Supersedes [#339](https://github.com/gaoyu06/native-obfuscator/pull/339) on `f7446f5a6bbd3bb316897896a7f8cfee0532d2d7` (post-#338). [#337](https://github.com/gaoyu06/native-obfuscator/pull/337), [#335](https://github.com/gaoyu06/native-obfuscator/pull/335), and [#333](https://github.com/gaoyu06/native-obfuscator/pull/333) remain the earlier post-#336, post-#334, and post-#332 snapshots.
- Latest compiler parent XML remains [#340](https://github.com/gaoyu06/native-obfuscator/pull/340) (599). This measurement adds no compiler XML.
- Processor changed: **No**
- Ship-ready: **No**
- Admitted: **No** (measurement only)

## Joined corpus totals

| Corpus | Inventory | IR | Legacy fallback | Constructor left in Java | Missing |
| --- | ---: | ---: | ---: | ---: | ---: |
| `ClassicTest` | 108 | 108 | 0 | 0 | 0 |
| `jdk17` | 82 | 82 | 0 | 0 | 0 |
| `jdk21` | 47 | 47 | 0 | 0 | 0 |
| `jdk25` | 21 | 21 | 0 | 0 | 0 |

## Command actually run

```bash
python3 docs/measurement/ir-leftover-inventory/measure.py --javac-25 /tmp/temurin25/bin/javac
```

The focused Gradle gate was skipped because this change is measurement-only. The measurement helper's own `:obfuscator:shadowJar` build completed successfully.

Zero measured leftovers is not coverage-complete, not a JDK support badge, does not complete the production goal, and does not authorize changing defaults. Defaults remain `--codegen=legacy`, `--ir-lower=direct`, and `--backend=cpp`.

# 中文

## 摘要

- 在 [#340](https://github.com/gaoyu06/native-obfuscator/pull/340) 之后的 leftover-docs 树上重新测量仓库内 IR 剩余项；该树已支持把额外局部 `int` 值作为 `Insets` 的第一个和第三个 `NEW` 参数。
- 测量 SHA：`b4ec2a75d7ec73c1eec76ba119e200486e37e386`
- 与 `origin/master` 的 merge-base：`b4ec2a75d7ec73c1eec76ba119e200486e37e386`
- 本次结果取代基于 `f7446f5a6bbd3bb316897896a7f8cfee0532d2d7`（post-#338）的 [#339](https://github.com/gaoyu06/native-obfuscator/pull/339)。[#337](https://github.com/gaoyu06/native-obfuscator/pull/337)、[#335](https://github.com/gaoyu06/native-obfuscator/pull/335) 和 [#333](https://github.com/gaoyu06/native-obfuscator/pull/333) 仍分别是更早的 post-#336、post-#334 和 post-#332 快照。
- 最新编译器父 XML 仍为 [#340](https://github.com/gaoyu06/native-obfuscator/pull/340)（599）。本次测量不增加编译器 XML。
- Processor changed（处理器已更改）：**No**
- Ship-ready（可发布）：**No**
- Admitted（已准入）：**No**（仅测量）

## 合并语料统计

| 语料 | Inventory | IR | Legacy fallback | Constructor left in Java | Missing |
| --- | ---: | ---: | ---: | ---: | ---: |
| `ClassicTest` | 108 | 108 | 0 | 0 | 0 |
| `jdk17` | 82 | 82 | 0 | 0 | 0 |
| `jdk21` | 47 | 47 | 0 | 0 | 0 |
| `jdk25` | 21 | 21 | 0 | 0 | 0 |

## 实际运行的命令

```bash
python3 docs/measurement/ir-leftover-inventory/measure.py --javac-25 /tmp/temurin25/bin/javac
```

由于本次改动仅为测量，未运行聚焦 Gradle gate。测量脚本自身执行的 `:obfuscator:shadowJar` 构建已成功完成。

测得零剩余项不代表覆盖完整，不是 JDK 支持标志，不代表生产目标完成，也不授权更改默认值。默认值仍为 `--codegen=legacy`、`--ir-lower=direct` 和 `--backend=cpp`。
