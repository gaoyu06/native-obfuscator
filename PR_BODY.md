# Remeasure IR leftovers after leftover-docs #438 / 在 leftover-docs #438 后重新测量 IR 遗留项

## English

### Scope

- Measurement-only remeasurement of leftover-docs [#438](https://github.com/gaoyu06/native-obfuscator/pull/438).
- Measured checkout and merge base with `origin/master`: `a158191a` (`a158191a3f3f5bcf77b5f01dbd59b022fd3c1cf1`).
- Supersedes [#437](https://github.com/gaoyu06/native-obfuscator/pull/437)'s measurement of leftover-docs [#436](https://github.com/gaoyu06/native-obfuscator/pull/436) at `4fed275e` (`4fed275ea8e31858920ebb6ae76bf015c773f273`).
- Latest compiler parent XML: **[#438](https://github.com/gaoyu06/native-obfuscator/pull/438) (746)** (`IrCompilerTest` 739 + `CodegenModeTest` 7). This measurement adds no compiler XML.
- Processor changed: **No**. Admitted: **No**. Ship-ready: **No**.
- Defaults remain unchanged: `--codegen=legacy`, `--ir-lower=direct`, and `--backend=cpp`.

### Measurement

Inventory and output were joined by exact `class + method + descriptor`.

```bash
python3 docs/measurement/ir-leftover-inventory/measure.py --javac-25 /tmp/temurin25/bin/javac
```

| Corpus | Inventory | IR | Legacy fallback | Constructor left in Java | Missing |
| --- | ---: | ---: | ---: | ---: | ---: |
| `ClassicTest` | 108 | 108 | 0 | 0 | 0 |
| `jdk17` | 82 | 82 | 0 | 0 | 0 |
| `jdk21` | 47 | 47 | 0 | 0 | 0 |
| `jdk25` | 21 | 21 | 0 | 0 | 0 |

Zero measured leftovers is **not coverage-complete**, **not a JDK support badge**, and **does not authorize a default flip**. It does not mark the production goal complete.

Gradle tests were not rerun, as this is a measurement-only documentation update.

## 中文

### 范围

- 仅测量：重新测量 leftover-docs [#438](https://github.com/gaoyu06/native-obfuscator/pull/438)。
- 被测 checkout 及其与 `origin/master` 的 merge base：`a158191a`（`a158191a3f3f5bcf77b5f01dbd59b022fd3c1cf1`）。
- 取代 [#437](https://github.com/gaoyu06/native-obfuscator/pull/437) 对 leftover-docs [#436](https://github.com/gaoyu06/native-obfuscator/pull/436) `4fed275e`（`4fed275ea8e31858920ebb6ae76bf015c773f273`）的测量。
- 最新编译器父级 XML：**[#438](https://github.com/gaoyu06/native-obfuscator/pull/438)（746）**（`IrCompilerTest` 739 + `CodegenModeTest` 7）。本次测量未增加编译器 XML。
- 处理器已更改：**否**。已准入：**否**。可发布：**否**。
- 默认值保持不变：`--codegen=legacy`、`--ir-lower=direct` 和 `--backend=cpp`。

### 测量

清单和结果按精确的 `class + method + descriptor` 连接。测量命令及汇总表见上文。

测得的遗留项为零，**不代表覆盖完整**，**不是 JDK 支持标志**，也**不授权切换默认值**。本次测量不表示生产目标已完成。

按要求未重新运行 Gradle 测试；本次变更仅更新测量文档。
