<!-- CURSOR_AGENT_PR_BODY_BEGIN -->
# English

## Summary

- Remeasures the joined IR leftover inventory on the post-[#358](https://github.com/gaoyu06/native-obfuscator/pull/358) leftover-docs tree, after an extra-local `int` was admitted as the second five-argument `GregorianCalendar` `NEW` argument.
- Measured compiler base (merge-base with `origin/master`) and measurement commit: `49b2e8ca1edfa3990e202f81f1a15ddafb5f0920`.
- This post-#358 snapshot supersedes [#357](https://github.com/gaoyu06/native-obfuscator/pull/357) on `6efe7343c6da981484de3164010a5de2e94fdb69` (post-#356). [#355](https://github.com/gaoyu06/native-obfuscator/pull/355), [#353](https://github.com/gaoyu06/native-obfuscator/pull/353), [#351](https://github.com/gaoyu06/native-obfuscator/pull/351), and [#349](https://github.com/gaoyu06/native-obfuscator/pull/349) remain the earlier post-#354, post-#352, post-#350, and post-#348 snapshots.
- Latest compiler parent XML remains **[#358](https://github.com/gaoyu06/native-obfuscator/pull/358) (626)** (`IrCompilerTest` 619 + `CodegenModeTest` 7). This measurement adds no compiler XML.

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
- The focused compiler Gradle gate was not run for this measurement-only inventory.

# 中文

## 摘要

- 在 [#358](https://github.com/gaoyu06/native-obfuscator/pull/358) 之后的 leftover-docs 树上重新测量合并后的 IR leftover inventory；该树已接纳额外局部 `int` 作为五参数 `GregorianCalendar` `NEW` 的第二个参数。
- 实测编译器基线（与 `origin/master` 的 merge-base）和测量提交均为 `49b2e8ca1edfa3990e202f81f1a15ddafb5f0920`。
- 本次 post-#358 快照取代基于 `6efe7343c6da981484de3164010a5de2e94fdb69`（post-#356）的 [#357](https://github.com/gaoyu06/native-obfuscator/pull/357)。[#355](https://github.com/gaoyu06/native-obfuscator/pull/355)、[#353](https://github.com/gaoyu06/native-obfuscator/pull/353)、[#351](https://github.com/gaoyu06/native-obfuscator/pull/351) 和 [#349](https://github.com/gaoyu06/native-obfuscator/pull/349) 仍分别是更早的 post-#354、post-#352、post-#350 和 post-#348 快照。
- 最新编译器父级 XML 仍为 **[#358](https://github.com/gaoyu06/native-obfuscator/pull/358)（626）**（`IrCompilerTest` 619 + `CodegenModeTest` 7）；本次测量不增加编译器 XML。

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
- 本次仅测量 inventory，未运行聚焦编译器 Gradle 门禁。

<!-- CURSOR_AGENT_PR_BODY_END -->
