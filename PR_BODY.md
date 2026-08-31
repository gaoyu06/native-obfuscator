# English

## Summary

- Measurement-only remeasurement of leftover-docs [#444](https://github.com/gaoyu06/native-obfuscator/pull/444) at `c89015d2`.
- Supersedes [#443](https://github.com/gaoyu06/native-obfuscator/pull/443)'s measurement of leftover-docs [#442](https://github.com/gaoyu06/native-obfuscator/pull/442) at `8d324696`.
- Latest compiler parent XML is **[#444](https://github.com/gaoyu06/native-obfuscator/pull/444) (755)** (`IrCompilerTest` 748 + `CodegenModeTest` 7). This measurement adds no compiler XML.
- Processor changed: **No**. Admitted: **No**. Ship-ready: **No**.
- Defaults remain unchanged: `--codegen=legacy`, `--ir-lower=direct`, and `--backend=cpp`.

## Joined totals

| Corpus | Inventory | IR | Legacy fallback | Constructor left in Java | Missing |
| --- | ---: | ---: | ---: | ---: | ---: |
| `ClassicTest` | 108 | 108 | 0 | 0 | 0 |
| `jdk17` | 82 | 82 | 0 | 0 | 0 |
| `jdk21` | 47 | 47 | 0 | 0 | 0 |
| `jdk25` | 21 | 21 | 0 | 0 | 0 |

## Measurement command

```bash
python3 docs/measurement/ir-leftover-inventory/measure.py --javac-25 /tmp/temurin25/bin/javac
```

Zero measured leftovers is **not coverage-complete**, **not a JDK support badge**, and **does not authorize a default flip**.

# 中文

## 摘要

- 仅测量：在 `c89015d2` 上重新测量 leftover-docs [#444](https://github.com/gaoyu06/native-obfuscator/pull/444)。
- 本次测量取代 [#443](https://github.com/gaoyu06/native-obfuscator/pull/443) 对 `8d324696` 上 leftover-docs [#442](https://github.com/gaoyu06/native-obfuscator/pull/442) 的测量。
- 最新编译器父级 XML 为 **[#444](https://github.com/gaoyu06/native-obfuscator/pull/444) (755)**（`IrCompilerTest` 748 + `CodegenModeTest` 7）。本次测量不增加编译器 XML。
- Processor changed：**No**。Admitted：**No**。Ship-ready：**No**。
- 默认值保持不变：`--codegen=legacy`、`--ir-lower=direct` 和 `--backend=cpp`。

## 精确连接汇总

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

零 leftover **不代表覆盖完整**，**不是 JDK 支持徽章**，也**不授权切换默认值**。
