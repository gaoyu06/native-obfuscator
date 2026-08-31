## English

### Summary

- Remeasures the in-tree IR leftover inventory on leftover-docs [#432](https://github.com/gaoyu06/native-obfuscator/pull/432), SHA `ce634292` (`ce634292263daedd23c3086bb66784730350c54b`).
- Supersedes [#431](https://github.com/gaoyu06/native-obfuscator/pull/431)'s measurement of leftover-docs [#430](https://github.com/gaoyu06/native-obfuscator/pull/430), SHA `46a53713` (`46a537135b309b2002e7610d6e38407f3ac26f82`).
- Latest compiler parent XML at measurement time is **#432 (737)**: `IrCompilerTest` 730 + `CodegenModeTest` 7. This measurement adds no compiler XML.
- Processor changed: **No**. Admitted: **No**. Ship-ready: **No**.
- Defaults are unchanged: `--codegen=legacy`, `--ir-lower=direct`, and `--backend=cpp`.

### Joined totals

| Corpus | Inventory | IR | Fallback | Constructor left | Missing |
| --- | ---: | ---: | ---: | ---: | ---: |
| `ClassicTest` | 108 | 108 | 0 | 0 | 0 |
| `jdk17` | 82 | 82 | 0 | 0 | 0 |
| `jdk21` | 47 | 47 | 0 | 0 | 0 |
| `jdk25` | 21 | 21 | 0 | 0 | 0 |

Zero measured leftovers is **not coverage-complete**, **not a JDK support badge**, and **does not authorize a default flip**.

### Measurement

```bash
python3 docs/measurement/ir-leftover-inventory/measure.py --javac-25 /tmp/temurin25/bin/javac
```

## 中文

### 摘要

- 在 leftover-docs [#432](https://github.com/gaoyu06/native-obfuscator/pull/432) 上重新测量仓库内 IR 遗留项，SHA 为 `ce634292`（`ce634292263daedd23c3086bb66784730350c54b`）。
- 本次测量取代 [#431](https://github.com/gaoyu06/native-obfuscator/pull/431) 对 leftover-docs [#430](https://github.com/gaoyu06/native-obfuscator/pull/430) 的测量，旧 SHA 为 `46a53713`（`46a537135b309b2002e7610d6e38407f3ac26f82`）。
- 测量时最新的编译器父级 XML 为 **#432（737）**：`IrCompilerTest` 730 + `CodegenModeTest` 7。本次测量不新增编译器 XML。
- 处理器已更改：**否**。已准入：**否**。可发布：**否**。
- 默认值保持不变：`--codegen=legacy`、`--ir-lower=direct` 和 `--backend=cpp`。

### 精确连接后的总计

| 语料 | 清单 | IR | 回退 | 构造器保留在 Java | 缺失 |
| --- | ---: | ---: | ---: | ---: | ---: |
| `ClassicTest` | 108 | 108 | 0 | 0 | 0 |
| `jdk17` | 82 | 82 | 0 | 0 | 0 |
| `jdk21` | 47 | 47 | 0 | 0 | 0 |
| `jdk25` | 21 | 21 | 0 | 0 | 0 |

测得零遗留项**不代表覆盖完整**，**不是 JDK 支持徽章**，也**不授权翻转默认值**。

### 测量命令

```bash
python3 docs/measurement/ir-leftover-inventory/measure.py --javac-25 /tmp/temurin25/bin/javac
```
