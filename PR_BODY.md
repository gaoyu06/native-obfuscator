# Summary

- Measurement-only remeasurement of the leftover-docs [#450](https://github.com/gaoyu06/native-obfuscator/pull/450) checkout at `4ca8cfd2` (`4ca8cfd21efab528cd49f2cd4202d129a7be6cba`).
- Supersedes [#449](https://github.com/gaoyu06/native-obfuscator/pull/449)'s measurement of the leftover-docs [#448](https://github.com/gaoyu06/native-obfuscator/pull/448) checkout at `9a95f300` (`9a95f30030a6dbfd2772484c8a3148f11f855e71`).
- Latest compiler parent XML is **[#450](https://github.com/gaoyu06/native-obfuscator/pull/450) (764)** (`IrCompilerTest` 757 + `CodegenModeTest` 7). This measurement adds no compiler XML.
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

- 仅测量：在 leftover-docs [#450](https://github.com/gaoyu06/native-obfuscator/pull/450) 的 `4ca8cfd2`（`4ca8cfd21efab528cd49f2cd4202d129a7be6cba`）检出上重新测量 IR leftovers。
- 本次结果取代 [#449](https://github.com/gaoyu06/native-obfuscator/pull/449) 对 leftover-docs [#448](https://github.com/gaoyu06/native-obfuscator/pull/448) 的 `9a95f300`（`9a95f30030a6dbfd2772484c8a3148f11f855e71`）检出的测量。
- 最新编译器父级 XML 为 **[#450](https://github.com/gaoyu06/native-obfuscator/pull/450)（764）**（`IrCompilerTest` 757 + `CodegenModeTest` 7）。本次测量不增加编译器 XML。
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
