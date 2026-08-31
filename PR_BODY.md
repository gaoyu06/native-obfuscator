# IR leftover inventory after leftover-docs #436

## English

### Summary

- Remeasures the exact leftover-docs [#436](https://github.com/gaoyu06/native-obfuscator/pull/436) checkout at `4fed275e` (`4fed275ea8e31858920ebb6ae76bf015c773f273`); this SHA is both the measured checkout and the merge base with `origin/master`.
- Supersedes [#435](https://github.com/gaoyu06/native-obfuscator/pull/435)'s measurement of leftover-docs [#434](https://github.com/gaoyu06/native-obfuscator/pull/434) at `3645eb36` (`3645eb3600e23115ab46885c7450fde97cb47666`).
- The latest compiler parent XML is **[#436](https://github.com/gaoyu06/native-obfuscator/pull/436) (743)**: `IrCompilerTest` 736 + `CodegenModeTest` 7. This measurement adds no compiler XML.
- Processor changed: **No**. Admitted: **No**. Ship-ready: **No**.
- Defaults are unchanged: `--codegen=legacy`, `--ir-lower=direct`, and `--backend=cpp`.
- Results are joined by exact `class + method + descriptor`.

Zero measured leftovers is **not coverage-complete**, **not a JDK support badge**, and **does not authorize a default flip**.

### Joined totals

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

Gradle test suites were not rerun, as required for this measurement-only update.

## 中文

### 摘要

- 在 leftover-docs [#436](https://github.com/gaoyu06/native-obfuscator/pull/436) 的精确检出 `4fed275e`（`4fed275ea8e31858920ebb6ae76bf015c773f273`）上重新测量；该 SHA 同时是测量检出和与 `origin/master` 的合并基点。
- 本次结果取代 [#435](https://github.com/gaoyu06/native-obfuscator/pull/435) 对 leftover-docs [#434](https://github.com/gaoyu06/native-obfuscator/pull/434) `3645eb36`（`3645eb3600e23115ab46885c7450fde97cb47666`）的测量。
- 最新编译器父级 XML 为 **[#436](https://github.com/gaoyu06/native-obfuscator/pull/436)（743）**：`IrCompilerTest` 736 + `CodegenModeTest` 7。本次测量未增加编译器 XML。
- 处理器已更改：**否**。已准入：**否**。可发布：**否**。
- 默认值未更改：`--codegen=legacy`、`--ir-lower=direct` 和 `--backend=cpp`。
- 结果按精确的 `class + method + descriptor` 连接。

测得零遗留项**不代表覆盖完整**、**不是 JDK 支持标志**，并且**不授权切换默认值**。

### 连接后总计

| 语料 | 清单 | IR | 旧版回退 | 构造器留在 Java | 缺失 |
| --- | ---: | ---: | ---: | ---: | ---: |
| `ClassicTest` | 108 | 108 | 0 | 0 | 0 |
| `jdk17` | 82 | 82 | 0 | 0 | 0 |
| `jdk21` | 47 | 47 | 0 | 0 | 0 |
| `jdk25` | 21 | 21 | 0 | 0 | 0 |

### 测量命令

```bash
python3 docs/measurement/ir-leftover-inventory/measure.py --javac-25 /tmp/temurin25/bin/javac
```

按照仅测量更新的要求，未重新运行 Gradle 测试套件。
