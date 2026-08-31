# Summary

- Measurement-only remeasurement of the leftover-docs [#446](https://github.com/gaoyu06/native-obfuscator/pull/446) checkout at `7e95adc3` (`7e95adc3811e137f0e6d6949fbf6f7a6c059b2d0`).
- Supersedes [#445](https://github.com/gaoyu06/native-obfuscator/pull/445)'s measurement of the leftover-docs [#444](https://github.com/gaoyu06/native-obfuscator/pull/444) checkout at `c89015d2` (`c89015d2b35ec7bf9511ad0301a5434d2e05a818`).
- Latest compiler parent XML is **[#446](https://github.com/gaoyu06/native-obfuscator/pull/446) (758)** (`IrCompilerTest` 751 + `CodegenModeTest` 7). This measurement adds no compiler XML.
- Processor changed: **No**. Admitted: **No**. Ship-ready: **No**.
- Defaults remain unchanged: `--codegen=legacy`, `--ir-lower=direct`, and `--backend=cpp`.
- Inventory and results are joined by exact `class + method + descriptor`.

## Joined totals

| Corpus | Inventory | IR | Legacy fallback | Constructor left in Java | Missing |
| --- | ---: | ---: | ---: | ---: | ---: |
| `ClassicTest` | 108 | 108 | 0 | 0 | 0 |
| `jdk17` | 82 | 82 | 0 | 0 | 0 |
| `jdk21` | 47 | 47 | 0 | 0 | 0 |
| `jdk25` | 21 | 21 | 0 | 0 | 0 |

## Measurement

```bash
python3 docs/measurement/ir-leftover-inventory/measure.py --javac-25 /tmp/temurin25/bin/javac
```

Zero measured leftovers is **not coverage-complete**, **not a JDK support badge**, and **does not authorize a default flip**.

# 中文摘要

- 仅测量：在 leftover-docs [#446](https://github.com/gaoyu06/native-obfuscator/pull/446) 的 `7e95adc3`（`7e95adc3811e137f0e6d6949fbf6f7a6c059b2d0`）检出上重新测量 IR leftovers。
- 本次结果取代 [#445](https://github.com/gaoyu06/native-obfuscator/pull/445) 对 leftover-docs [#444](https://github.com/gaoyu06/native-obfuscator/pull/444) 的 `c89015d2`（`c89015d2b35ec7bf9511ad0301a5434d2e05a818`）检出的测量。
- 最新编译器父级 XML 为 **[#446](https://github.com/gaoyu06/native-obfuscator/pull/446)（758）**（`IrCompilerTest` 751 + `CodegenModeTest` 7）。本次测量不增加编译器 XML。
- Processor changed：**No**。Admitted：**No**。Ship-ready：**No**。
- 默认值保持不变：`--codegen=legacy`、`--ir-lower=direct` 和 `--backend=cpp`。
- inventory 与结果按精确的 `class + method + descriptor` 连接。

## 连接后的总计

| 语料 | Inventory | IR | Legacy fallback | Constructor left in Java | Missing |
| --- | ---: | ---: | ---: | ---: | ---: |
| `ClassicTest` | 108 | 108 | 0 | 0 | 0 |
| `jdk17` | 82 | 82 | 0 | 0 | 0 |
| `jdk21` | 47 | 47 | 0 | 0 | 0 |
| `jdk25` | 21 | 21 | 0 | 0 | 0 |

## 测量命令

```bash
python3 docs/measurement/ir-leftover-inventory/measure.py --javac-25 /tmp/temurin25/bin/javac
```

零测量 leftovers **不代表覆盖完整**，**不是 JDK 支持徽章**，也**不授权切换默认值**。
