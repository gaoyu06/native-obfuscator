# English

## Summary

- Remeasures the joined IR leftover inventory on the post-[#354](https://github.com/gaoyu06/native-obfuscator/pull/354) leftover-docs tree, after an extra-local `int` was admitted as the first, third, and fourth `Insets` `NEW` arguments.
- Measured compiler base (merge-base with `origin/master`) and measurement commit: `b8478ccfd82ee5b5834573dea8295cf010659bb9`.
- This post-#354 snapshot supersedes [#353](https://github.com/gaoyu06/native-obfuscator/pull/353) on `764ecf5a5df8a95fc5eda3dd2d61119cfefc24ee` (post-#352). [#351](https://github.com/gaoyu06/native-obfuscator/pull/351) remains the earlier post-#350 snapshot, and [#349](https://github.com/gaoyu06/native-obfuscator/pull/349) remains the earlier post-#348 snapshot.
- Latest compiler parent XML remains **#354 (620)** (`IrCompilerTest` 613 + `CodegenModeTest` 7). This measurement adds no compiler XML.

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

- 在 [#354](https://github.com/gaoyu06/native-obfuscator/pull/354) 之后的 leftover-docs 树上重新测量合并后的 IR leftover inventory；该树已接纳额外局部 `int` 作为 `Insets` 的第一个、第三个和第四个 `NEW` 参数。
- 实测编译器基线（与 `origin/master` 的 merge-base）和测量提交均为 `b8478ccfd82ee5b5834573dea8295cf010659bb9`。
- 本次 post-#354 快照取代基于 `764ecf5a5df8a95fc5eda3dd2d61119cfefc24ee`（post-#352）的 [#353](https://github.com/gaoyu06/native-obfuscator/pull/353)。[#351](https://github.com/gaoyu06/native-obfuscator/pull/351) 仍是更早的 post-#350 快照，[#349](https://github.com/gaoyu06/native-obfuscator/pull/349) 仍是更早的 post-#348 快照。
- 最新编译器父级 XML 仍为 **#354（620）**（`IrCompilerTest` 613 + `CodegenModeTest` 7）；本次测量不增加编译器 XML。

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
