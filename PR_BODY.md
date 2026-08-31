# English

## Summary

- Remeasures the IR leftover inventory on leftover-docs [#419](https://github.com/gaoyu06/native-obfuscator/pull/419) at `82ee119a` (`82ee119a50d4d5cb0eee63cad4d7cdb78282c602`).
- Supersedes [#418](https://github.com/gaoyu06/native-obfuscator/pull/418), which measured leftover-docs [#417](https://github.com/gaoyu06/native-obfuscator/pull/417) at `fcff55af` (`fcff55af45a6299d00327bbcf0041ba252db11f4`).
- The latest compiler parent XML is [#419](https://github.com/gaoyu06/native-obfuscator/pull/419) **(719)**: `IrCompilerTest` 712 + `CodegenModeTest` 7. This measurement adds no compiler XML.
- This is measurement-only. Processor changed: **No**. Admitted: **No**. Ship-ready: **No**.

## Measured joined totals

| Corpus | Inventory | IR | Legacy fallback | Constructor left in Java | Missing |
| --- | ---: | ---: | ---: | ---: | ---: |
| `ClassicTest` | 108 | 108 | 0 | 0 | 0 |
| `jdk17` | 82 | 82 | 0 | 0 | 0 |
| `jdk21` | 47 | 47 | 0 | 0 | 0 |
| `jdk25` | 21 | 21 | 0 | 0 | 0 |

## Validation

```bash
python3 docs/measurement/ir-leftover-inventory/measure.py --javac-25 /tmp/temurin25/bin/javac
```

The defaults remain unchanged: `--codegen=legacy`, `--ir-lower=direct`, and `--backend=cpp`. Zero measured leftovers is not coverage-complete, is not a JDK support badge, and does not authorize a default flip.

# 中文

## 摘要

- 在 leftover-docs [#419](https://github.com/gaoyu06/native-obfuscator/pull/419) 的 `82ee119a`（`82ee119a50d4d5cb0eee63cad4d7cdb78282c602`）上重新测量 IR 遗留项清单。
- 本次结果取代 [#418](https://github.com/gaoyu06/native-obfuscator/pull/418)；后者测量的是 leftover-docs [#417](https://github.com/gaoyu06/native-obfuscator/pull/417) 的 `fcff55af`（`fcff55af45a6299d00327bbcf0041ba252db11f4`）。
- 测量时最新的编译器父级 XML 是 [#419](https://github.com/gaoyu06/native-obfuscator/pull/419) **(719)**：`IrCompilerTest` 712 + `CodegenModeTest` 7。本次测量没有增加编译器 XML。
- 本次仅做测量。处理器变更：**否**。准入：**否**。可发布：**否**。

## 实测连接总数

| 语料 | 清单 | IR | 旧后端回退 | 构造器保留在 Java | 缺失 |
| --- | ---: | ---: | ---: | ---: | ---: |
| `ClassicTest` | 108 | 108 | 0 | 0 | 0 |
| `jdk17` | 82 | 82 | 0 | 0 | 0 |
| `jdk21` | 47 | 47 | 0 | 0 | 0 |
| `jdk25` | 21 | 21 | 0 | 0 | 0 |

## 验证

```bash
python3 docs/measurement/ir-leftover-inventory/measure.py --javac-25 /tmp/temurin25/bin/javac
```

默认值保持不变：`--codegen=legacy`、`--ir-lower=direct` 和 `--backend=cpp`。测得零遗留项不代表覆盖完整，不是 JDK 支持徽章，也不授权切换默认值。
