# English

## Summary

- Measurement-only remeasurement of the leftover-docs [#440](https://github.com/gaoyu06/native-obfuscator/pull/440) checkout at `ef6884ed` (`ef6884ed4e61d84382ec8a435b830ccf18c083bd`).
- Supersedes [#439](https://github.com/gaoyu06/native-obfuscator/pull/439)'s measurement of leftover-docs [#438](https://github.com/gaoyu06/native-obfuscator/pull/438) at `a158191a` (`a158191a3f3f5bcf77b5f01dbd59b022fd3c1cf1`).
- The latest compiler parent XML is **[#440](https://github.com/gaoyu06/native-obfuscator/pull/440) (749)**: `IrCompilerTest` 742 + `CodegenModeTest` 7. This measurement adds no compiler XML.
- Processor changed: **No**. Admitted: **No**. Ship-ready: **No**.
- Defaults are unchanged: `--codegen=legacy`, `--ir-lower=direct`, and `--backend=cpp`.
- Inventory and results are joined by exact `class + method + descriptor`.

Zero measured leftovers is **not coverage-complete**, **not a JDK support badge**, and **does not authorize a default flip**.

## Joined totals / 精确连接汇总

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

No Gradle test suite was rerun; only the requested inventory measurement was run.

# 中文

## 摘要

- 本次仅重新测量 leftover-docs [#440](https://github.com/gaoyu06/native-obfuscator/pull/440) 检出版本 `ef6884ed`（`ef6884ed4e61d84382ec8a435b830ccf18c083bd`）。
- 本次测量取代 [#439](https://github.com/gaoyu06/native-obfuscator/pull/439) 对 leftover-docs [#438](https://github.com/gaoyu06/native-obfuscator/pull/438) `a158191a`（`a158191a3f3f5bcf77b5f01dbd59b022fd3c1cf1`）的测量。
- 最新编译器父级 XML 为 **[#440](https://github.com/gaoyu06/native-obfuscator/pull/440)（749）**：`IrCompilerTest` 742 + `CodegenModeTest` 7。本次测量未增加编译器 XML。
- 处理器已更改：**否**。已准入：**否**。可发布：**否**。
- 默认值保持不变：`--codegen=legacy`、`--ir-lower=direct` 和 `--backend=cpp`。
- 清单和结果按精确的 `class + method + descriptor` 连接；上表是连接后的汇总。

测得零剩余项**不表示覆盖完整**，**不是 JDK 支持徽章**，并且**不授权切换默认值**。

## 测量命令

```bash
python3 docs/measurement/ir-leftover-inventory/measure.py --javac-25 /tmp/temurin25/bin/javac
```

未重新运行 Gradle 测试套件；仅运行了要求的清单测量。
