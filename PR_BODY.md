# English

## Summary

- Remeasures the joined IR leftover inventory on the post-[#352](https://github.com/gaoyu06/native-obfuscator/pull/352) leftover-docs tree, after an extra-local `int` was admitted as the first, second, and fourth `Insets` `NEW` arguments.
- Measured compiler base and measurement commit: `764ecf5a5df8a95fc5eda3dd2d61119cfefc24ee`.
- This post-#352 snapshot supersedes [#351](https://github.com/gaoyu06/native-obfuscator/pull/351) on `2246b1cffc72d12d2792ba9a2b057751a34ba61d` (post-#350). [#349](https://github.com/gaoyu06/native-obfuscator/pull/349) remains the earlier post-#348 snapshot, and [#347](https://github.com/gaoyu06/native-obfuscator/pull/347) remains the earlier post-#346 snapshot.
- Latest compiler parent XML remains **#352 (617)** (`IrCompilerTest` 610 + `CodegenModeTest` 7). This measurement adds no compiler XML.

## Joined corpus

| Corpus | Inventory | IR | Legacy fallback | Constructor left in Java | Missing |
| --- | ---: | ---: | ---: | ---: | ---: |
| `ClassicTest` | 108 | 108 | 0 | 0 | 0 |
| `jdk17` | 82 | 82 | 0 | 0 | 0 |
| `jdk21` | 47 | 47 | 0 | 0 | 0 |
| `jdk25` | 21 | 21 | 0 | 0 | 0 |

## Caveats

- This is **measurement only**, **not coverage-complete**, **not a JDK support badge**, and **not a behavioral/JNI E2E claim**.
- Zero measured leftovers is not production-goal complete and does not authorize changing any default.
- Defaults remain `--codegen=legacy`, `--ir-lower=direct`, and `--backend=cpp`.
- Processor changed: **No**. Ship-ready: **No**. Admitted: **No** (measurement only).

# 中文

## 摘要

- 在 [#352](https://github.com/gaoyu06/native-obfuscator/pull/352) 之后的 leftover-docs 树上重新测量合并后的 IR leftover inventory；该树已接纳额外局部 `int` 作为 `Insets` 的第一个、第二个和第四个 `NEW` 参数。
- 实测编译器基线和测量提交均为 `764ecf5a5df8a95fc5eda3dd2d61119cfefc24ee`。
- 本次 post-#352 快照取代基于 `2246b1cffc72d12d2792ba9a2b057751a34ba61d`（post-#350）的 [#351](https://github.com/gaoyu06/native-obfuscator/pull/351)。[#349](https://github.com/gaoyu06/native-obfuscator/pull/349) 仍是更早的 post-#348 快照，[#347](https://github.com/gaoyu06/native-obfuscator/pull/347) 仍是更早的 post-#346 快照。
- 最新编译器父级 XML 仍为 **#352（617）**（`IrCompilerTest` 610 + `CodegenModeTest` 7）；本次测量不增加编译器 XML。

## 合并语料

| 语料 | Inventory | IR | Legacy fallback | Java 中保留构造器 | Missing |
| --- | ---: | ---: | ---: | ---: | ---: |
| `ClassicTest` | 108 | 108 | 0 | 0 | 0 |
| `jdk17` | 82 | 82 | 0 | 0 | 0 |
| `jdk21` | 47 | 47 | 0 | 0 | 0 |
| `jdk25` | 21 | 21 | 0 | 0 | 0 |

## 注意事项

- 这只是**测量**，**不代表覆盖完整**，**不是 JDK 支持徽章**，也**不是行为/JNI 端到端声明**。
- 测得零 leftover 不代表生产目标已经完成，也不允许据此更改任何默认值。
- 默认值保持为 `--codegen=legacy`、`--ir-lower=direct` 和 `--backend=cpp`。
- Processor changed：**No**。Ship-ready：**No**。Admitted：**No**（仅测量）。
