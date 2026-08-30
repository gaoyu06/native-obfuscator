# English

## Summary

This measurement-only change remeasures the joined IR leftover inventory on the post-[#350](https://github.com/gaoyu06/native-obfuscator/pull/350) leftover-docs tree, after an extra-local `int` was admitted as the first, second, and third `Insets` `NEW` arguments.

- Measured compiler base / measurement commit: `2246b1cffc72d12d2792ba9a2b057751a34ba61d`
- Command: `python3 docs/measurement/ir-leftover-inventory/measure.py --javac-25 /tmp/temurin25/bin/javac`
- JDK 25 compiler: Temurin `javac 25.0.4.1`
- This post-#350 remeasurement supersedes [#349](https://github.com/gaoyu06/native-obfuscator/pull/349) on `94a4e0e1a4a6d7cfcf1e8e5b9a6ec2a85a6659e0` (post-#348). [#347](https://github.com/gaoyu06/native-obfuscator/pull/347) remains the earlier post-#346 snapshot; [#345](https://github.com/gaoyu06/native-obfuscator/pull/345) remains the earlier post-#344 snapshot.
- Latest compiler parent XML remains **[#350](https://github.com/gaoyu06/native-obfuscator/pull/350) (614)** (`IrCompilerTest` 607 + `CodegenModeTest` 7). This measurement adds no compiler XML.

## Joined corpus

| Corpus | Inventory | IR | Legacy fallback | Constructor left in Java | Missing |
| --- | ---: | ---: | ---: | ---: | ---: |
| `ClassicTest` | 108 | 108 | 0 | 0 | 0 |
| `jdk17` | 82 | 82 | 0 | 0 | 0 |
| `jdk21` | 47 | 47 | 0 | 0 | 0 |
| `jdk25` | 21 | 21 | 0 | 0 | 0 |

This is measurement only, not coverage-complete, not a JDK support badge, and not a behavioral/JNI E2E claim. Zero measured leftovers is not production-goal complete and does not authorize changing defaults. The defaults remain `--codegen=legacy`, `--ir-lower=direct`, and `--backend=cpp`.

- Processor changed: **No**
- Ship-ready: **No**
- Admitted: **No** (measurement only)

# 中文

## 摘要

本次仅做测量：在 [#350](https://github.com/gaoyu06/native-obfuscator/pull/350) 的 leftover-docs 树上重新测量合并后的 IR leftover inventory。该树已经接纳了作为 `Insets` `NEW` 第一个、第二个和第三个参数的额外局部 `int`。

- 实测编译器基线 / 测量提交：`2246b1cffc72d12d2792ba9a2b057751a34ba61d`
- 命令：`python3 docs/measurement/ir-leftover-inventory/measure.py --javac-25 /tmp/temurin25/bin/javac`
- JDK 25 编译器：Temurin `javac 25.0.4.1`
- 本次 post-#350 重测取代了 [#349](https://github.com/gaoyu06/native-obfuscator/pull/349) 在 `94a4e0e1a4a6d7cfcf1e8e5b9a6ec2a85a6659e0`（post-#348）上的结果。[#347](https://github.com/gaoyu06/native-obfuscator/pull/347) 仍是较早的 post-#346 快照；[#345](https://github.com/gaoyu06/native-obfuscator/pull/345) 仍是较早的 post-#344 快照。
- 最新编译器父级 XML 仍为 **[#350](https://github.com/gaoyu06/native-obfuscator/pull/350)（614）**（`IrCompilerTest` 607 + `CodegenModeTest` 7）。本次测量不增加编译器 XML。

## 合并语料

| 语料 | Inventory | IR | Legacy fallback | Constructor left in Java | Missing |
| --- | ---: | ---: | ---: | ---: | ---: |
| `ClassicTest` | 108 | 108 | 0 | 0 | 0 |
| `jdk17` | 82 | 82 | 0 | 0 | 0 |
| `jdk21` | 47 | 47 | 0 | 0 | 0 |
| `jdk25` | 21 | 21 | 0 | 0 | 0 |

这只是测量，并不代表覆盖完整，不是 JDK 支持徽章，也不是行为/JNI 端到端结论。测得 0 个 leftover 不代表生产目标完成，也不授权更改默认值。默认值仍为 `--codegen=legacy`、`--ir-lower=direct` 和 `--backend=cpp`。

- Processor changed：**No**
- Ship-ready：**No**
- Admitted：**No**（仅测量）
