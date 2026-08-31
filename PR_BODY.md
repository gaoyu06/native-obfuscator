# English

## Summary

- Remeasures the IR leftover inventory on leftover-docs [#417](https://github.com/gaoyu06/native-obfuscator/pull/417) at `fcff55af`.
- Supersedes [#416](https://github.com/gaoyu06/native-obfuscator/pull/416), which measured leftover-docs [#415](https://github.com/gaoyu06/native-obfuscator/pull/415) at `621d39be`.
- The latest compiler parent XML is [#417](https://github.com/gaoyu06/native-obfuscator/pull/417) **(716)**: `IrCompilerTest` 709 + `CodegenModeTest` 7. This measurement adds no compiler XML.
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

- 在 leftover-docs [#417](https://github.com/gaoyu06/native-obfuscator/pull/417) 的 `fcff55af` 上重新测量 IR 遗留项清单。
- 本次结果取代 [#416](https://github.com/gaoyu06/native-obfuscator/pull/416)；后者测量的是 leftover-docs [#415](https://github.com/gaoyu06/native-obfuscator/pull/415) 的 `621d39be`。
- 测量时最新的编译器父级 XML 是 [#417](https://github.com/gaoyu06/native-obfuscator/pull/417) **(716)**：`IrCompilerTest` 709 + `CodegenModeTest` 7。本次测量没有增加编译器 XML。
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
