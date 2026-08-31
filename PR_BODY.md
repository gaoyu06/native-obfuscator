# English

## Summary

- Remeasures the in-tree IR leftover inventory on leftover-docs [#434](https://github.com/gaoyu06/native-obfuscator/pull/434), SHA `3645eb36` (`3645eb3600e23115ab46885c7450fde97cb47666`), which is both the measured checkout and merge base.
- Supersedes [#433](https://github.com/gaoyu06/native-obfuscator/pull/433)'s measurement of leftover-docs [#432](https://github.com/gaoyu06/native-obfuscator/pull/432) at `ce634292` (`ce634292263daedd23c3086bb66784730350c54b`).
- The latest compiler parent XML at measurement time is **[#434](https://github.com/gaoyu06/native-obfuscator/pull/434) (740)**: `IrCompilerTest` 733 + `CodegenModeTest` 7. This measurement adds no compiler XML.
- Processor changed: **No**. Admitted: **No**. Ship-ready: **No**.
- Defaults are unchanged: `--codegen=legacy`, `--ir-lower=direct`, and `--backend=cpp`.

## Joined totals

| Corpus | Inventory | IR | Fallback | Constructor left in Java | Missing |
| --- | ---: | ---: | ---: | ---: | ---: |
| `ClassicTest` | 108 | 108 | 0 | 0 | 0 |
| `jdk17` | 82 | 82 | 0 | 0 | 0 |
| `jdk21` | 47 | 47 | 0 | 0 | 0 |
| `jdk25` | 21 | 21 | 0 | 0 | 0 |

Inventory is joined to results by exact `class + method + descriptor`. Zero measured leftovers is **not coverage-complete**, **not a JDK support badge**, and **does not authorize a default flip**.

## Measurement

```bash
python3 docs/measurement/ir-leftover-inventory/measure.py --javac-25 /tmp/temurin25/bin/javac
```

# 中文

## 摘要

- 在 leftover-docs [#434](https://github.com/gaoyu06/native-obfuscator/pull/434) 上重新测量仓库内 IR 遗留项；SHA 为 `3645eb36`（`3645eb3600e23115ab46885c7450fde97cb47666`），同时也是本次测量的检出版本和 merge base。
- 本次测量取代 [#433](https://github.com/gaoyu06/native-obfuscator/pull/433) 对 leftover-docs [#432](https://github.com/gaoyu06/native-obfuscator/pull/432) `ce634292`（`ce634292263daedd23c3086bb66784730350c54b`）的测量。
- 测量时最新的编译器父级 XML 为 **[#434](https://github.com/gaoyu06/native-obfuscator/pull/434)（740）**：`IrCompilerTest` 733 + `CodegenModeTest` 7。本次测量不增加编译器 XML。
- Processor changed：**No**。Admitted：**No**。Ship-ready：**No**。
- 默认值保持不变：`--codegen=legacy`、`--ir-lower=direct` 和 `--backend=cpp`。

## 精确连接总数

| 语料 | Inventory | IR | Fallback | 构造器留在 Java | Missing |
| --- | ---: | ---: | ---: | ---: | ---: |
| `ClassicTest` | 108 | 108 | 0 | 0 | 0 |
| `jdk17` | 82 | 82 | 0 | 0 | 0 |
| `jdk21` | 47 | 47 | 0 | 0 | 0 |
| `jdk25` | 21 | 21 | 0 | 0 | 0 |

Inventory 按精确的 `class + method + descriptor` 与结果连接。测得零遗留项**不代表覆盖完整**，**不是 JDK 支持徽章**，并且**不授权切换默认值**。

## 测量命令

```bash
python3 docs/measurement/ir-leftover-inventory/measure.py --javac-25 /tmp/temurin25/bin/javac
```
