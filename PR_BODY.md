## English

### Summary

- Measurement-only remeasurement of leftover-docs [#442](https://github.com/gaoyu06/native-obfuscator/pull/442) at `8d324696` (`8d324696fcd9e5ffd28b176e508aff09869d9f44`).
- Supersedes [#441](https://github.com/gaoyu06/native-obfuscator/pull/441)'s measurement of leftover-docs [#440](https://github.com/gaoyu06/native-obfuscator/pull/440) at `ef6884ed` (`ef6884ed4e61d84382ec8a435b830ccf18c083bd`).
- Latest compiler parent XML is **[#442](https://github.com/gaoyu06/native-obfuscator/pull/442) (752)** (`IrCompilerTest` 745 + `CodegenModeTest` 7). This measurement adds no compiler XML.
- Processor changed: **No**. Admitted: **No**. Ship-ready: **No**.
- Defaults remain unchanged: `--codegen=legacy`, `--ir-lower=direct`, and `--backend=cpp`.

### Joined totals

Results are joined by exact `class + method + descriptor`.

| Corpus | Inventory | IR | Legacy fallback | Constructor left in Java | Missing |
| --- | ---: | ---: | ---: | ---: | ---: |
| `ClassicTest` | 108 | 108 | 0 | 0 | 0 |
| `jdk17` | 82 | 82 | 0 | 0 | 0 |
| `jdk21` | 47 | 47 | 0 | 0 | 0 |
| `jdk25` | 21 | 21 | 0 | 0 | 0 |

### Measurement command

```bash
python3 docs/measurement/ir-leftover-inventory/measure.py --javac-25 /tmp/temurin25/bin/javac
```

Zero measured leftovers is **not coverage-complete**, **not a JDK support badge**, and **does not authorize a default flip**.

## 中文

### 摘要

- 仅重新测量 leftover-docs [#442](https://github.com/gaoyu06/native-obfuscator/pull/442) 的 `8d324696`（`8d324696fcd9e5ffd28b176e508aff09869d9f44`）。
- 取代 [#441](https://github.com/gaoyu06/native-obfuscator/pull/441) 对 leftover-docs [#440](https://github.com/gaoyu06/native-obfuscator/pull/440) 的 `ef6884ed`（`ef6884ed4e61d84382ec8a435b830ccf18c083bd`）所做的测量。
- 最新编译器父级 XML 是 **[#442](https://github.com/gaoyu06/native-obfuscator/pull/442)（752）**（`IrCompilerTest` 745 + `CodegenModeTest` 7）。本次测量不增加编译器 XML。
- 处理器变更：**否**。已准入：**否**。可发布：**否**。
- 默认值保持不变：`--codegen=legacy`、`--ir-lower=direct` 和 `--backend=cpp`。

### 精确连接总数

结果按精确的 `class + method + descriptor` 连接。

| 语料 | 清单 | IR | 回退到 legacy | 构造器留在 Java | 缺失 |
| --- | ---: | ---: | ---: | ---: | ---: |
| `ClassicTest` | 108 | 108 | 0 | 0 | 0 |
| `jdk17` | 82 | 82 | 0 | 0 | 0 |
| `jdk21` | 47 | 47 | 0 | 0 | 0 |
| `jdk25` | 21 | 21 | 0 | 0 | 0 |

### 测量命令

```bash
python3 docs/measurement/ir-leftover-inventory/measure.py --javac-25 /tmp/temurin25/bin/javac
```

零个已测遗留项**不代表覆盖完整**、**不代表 JDK 支持徽章**，也**不授权切换默认值**。
