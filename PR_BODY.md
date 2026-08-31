# Suggested title / 建议标题

`docs: remeasure IR leftovers after leftover-docs #423`

## English

This measurement-only change refreshes the in-tree IR leftover inventory on leftover-docs [#423](https://github.com/gaoyu06/native-obfuscator/pull/423), measured at `9a45cc67` (`9a45cc6714007544b8521c826d92e35312804642`). It supersedes [#422](https://github.com/gaoyu06/native-obfuscator/pull/422)'s measurement of leftover-docs [#421](https://github.com/gaoyu06/native-obfuscator/pull/421) at `d13e6039`.

- Latest compiler parent XML at measurement time: **[#423](https://github.com/gaoyu06/native-obfuscator/pull/423) (725)** (`IrCompilerTest` 718 + `CodegenModeTest` 7). This measurement adds no compiler XML.
- Processor changed: **No**.
- Admitted: **No**.
- Ship-ready: **No**.
- Defaults remain unchanged: `--codegen=legacy`, `--ir-lower=direct`, `--backend=cpp`.
- Zero measured leftovers is **not coverage-complete**, **not a JDK support badge**, and **does not authorize a default flip**.
- This inventory measures checked-in bytecode admission only; it does not establish broad CFG or JNI coverage. The written production goal remains incomplete.

## Joined totals / 汇总总数

| Corpus | Inventory | IR | Fallback | Constructor left in Java | Missing |
| --- | ---: | ---: | ---: | ---: | ---: |
| `ClassicTest` | 108 | 108 | 0 | 0 | 0 |
| `jdk17` | 82 | 82 | 0 | 0 | 0 |
| `jdk21` | 47 | 47 | 0 | 0 | 0 |
| `jdk25` | 21 | 21 | 0 | 0 | 0 |

## 中文

这是一次仅测量的文档更新：在 leftover-docs [#423](https://github.com/gaoyu06/native-obfuscator/pull/423) 上重新统计树内 IR leftover，测量提交为 `9a45cc67`（`9a45cc6714007544b8521c826d92e35312804642`）。本次测量取代 [#422](https://github.com/gaoyu06/native-obfuscator/pull/422) 对 leftover-docs [#421](https://github.com/gaoyu06/native-obfuscator/pull/421)（`d13e6039`）所做的测量。

- 测量时最新的编译器父级 XML：**[#423](https://github.com/gaoyu06/native-obfuscator/pull/423)（725）**（`IrCompilerTest` 718 + `CodegenModeTest` 7）。本次测量不增加编译器 XML。
- Processor changed / 处理器变更：**No / 否**。
- Admitted / 已准入：**No / 否**。
- Ship-ready / 可发布：**No / 否**。
- 默认值保持不变：`--codegen=legacy`、`--ir-lower=direct`、`--backend=cpp`。
- 测得 leftover 为零**不代表覆盖完整**、**不是 JDK 支持标志**，也**不授权切换默认值**。
- 本清单只测量已检入 bytecode 的准入情况，不证明广泛的 CFG 或 JNI 覆盖；书面生产目标仍未完成。
