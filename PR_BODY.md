# Summary

- Measurement-only remeasurement of the leftover-docs [#452](https://github.com/gaoyu06/native-obfuscator/pull/452) checkout at `a6896796` (`a689679644c73bd577a6087993ca191fc2bf7e4d`).
- Rebased onto leftover-docs [#453](https://github.com/gaoyu06/native-obfuscator/pull/453) at `69292d37` (`69292d37adebe2ed53f877725ce8e52ae4ac52b5`).
- Supersedes [#451](https://github.com/gaoyu06/native-obfuscator/pull/451)'s measurement of the leftover-docs [#450](https://github.com/gaoyu06/native-obfuscator/pull/450) checkout at `4ca8cfd2` (`4ca8cfd21efab528cd49f2cd4202d129a7be6cba`).
- Compiler parent XML at measurement time is **[#452](https://github.com/gaoyu06/native-obfuscator/pull/452) (767)** (`IrCompilerTest` 760 + `CodegenModeTest` 7). Latest published compiler parent XML is **[#453](https://github.com/gaoyu06/native-obfuscator/pull/453) (770)**. This measurement adds no compiler XML.
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

- 仅测量：在 leftover-docs [#452](https://github.com/gaoyu06/native-obfuscator/pull/452) 的 `a6896796`（`a689679644c73bd577a6087993ca191fc2bf7e4d`）检出上重新测量 IR leftovers。
- 已变基到 leftover-docs [#453](https://github.com/gaoyu06/native-obfuscator/pull/453)，`69292d37`（`69292d37adebe2ed53f877725ce8e52ae4ac52b5`）。
- 本次结果取代 [#451](https://github.com/gaoyu06/native-obfuscator/pull/451) 对 leftover-docs [#450](https://github.com/gaoyu06/native-obfuscator/pull/450) 的 `4ca8cfd2`（`4ca8cfd21efab528cd49f2cd4202d129a7be6cba`）检出的测量。
- 测量时的编译器父级 XML 为 **[#452](https://github.com/gaoyu06/native-obfuscator/pull/452)（767）**（`IrCompilerTest` 760 + `CodegenModeTest` 7）。已发布的最新编译器父级 XML 为 **[#453](https://github.com/gaoyu06/native-obfuscator/pull/453)（770）**。本次测量不增加编译器 XML。
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
