# English

## Summary

- Remeasures the IR leftover inventory on leftover-docs [#421](https://github.com/gaoyu06/native-obfuscator/pull/421) at `d13e6039f3d472c3a71832bf70a99dd529f2210b`.
- Supersedes [#420](https://github.com/gaoyu06/native-obfuscator/pull/420)'s measurement of leftover-docs [#419](https://github.com/gaoyu06/native-obfuscator/pull/419) at `82ee119a50d4d5cb0eee63cad4d7cdb78282c602`.
- The latest compiler parent XML is **[#421](https://github.com/gaoyu06/native-obfuscator/pull/421) (722)**: `IrCompilerTest` 715 + `CodegenModeTest` 7. This measurement adds no compiler XML.
- This is measurement-only: processor changed **No**, admitted **No**, ship-ready **No**.

## Measured results

| Corpus | Inventory | IR | Legacy fallback | Constructor left in Java | Missing |
| --- | ---: | ---: | ---: | ---: | ---: |
| `ClassicTest` | 108 | 108 | 0 | 0 | 0 |
| `jdk17` | 82 | 82 | 0 | 0 | 0 |
| `jdk21` | 47 | 47 | 0 | 0 | 0 |
| `jdk25` | 21 | 21 | 0 | 0 | 0 |

Measured with:

```bash
python3 docs/measurement/ir-leftover-inventory/measure.py --javac-25 /tmp/temurin25/bin/javac
```

The bytecode/CFG/JNI processor and defaults are unchanged: `--codegen=legacy`, `--ir-lower=direct`, and `--backend=cpp` remain the defaults. Zero measured leftovers is not coverage-complete, is not a JDK support badge, and does not authorize a default flip.

# 中文

## 摘要

- 在 leftover-docs [#421](https://github.com/gaoyu06/native-obfuscator/pull/421) 的 `d13e6039f3d472c3a71832bf70a99dd529f2210b` 上重新测量 IR 剩余项清单。
- 本次结果取代 [#420](https://github.com/gaoyu06/native-obfuscator/pull/420) 对 leftover-docs [#419](https://github.com/gaoyu06/native-obfuscator/pull/419) `82ee119a50d4d5cb0eee63cad4d7cdb78282c602` 的测量。
- 测量时最新的编译器父级 XML 为 **[#421](https://github.com/gaoyu06/native-obfuscator/pull/421)（722）**：`IrCompilerTest` 715 + `CodegenModeTest` 7。本次测量没有增加编译器 XML。
- 本次仅做测量：处理器变更 **否**，准入 **否**，可发布 **否**。

## 测量结果

| 语料 | 清单方法数 | IR | Legacy 回退 | 构造器留在 Java | 缺失 |
| --- | ---: | ---: | ---: | ---: | ---: |
| `ClassicTest` | 108 | 108 | 0 | 0 | 0 |
| `jdk17` | 82 | 82 | 0 | 0 | 0 |
| `jdk21` | 47 | 47 | 0 | 0 | 0 |
| `jdk25` | 21 | 21 | 0 | 0 | 0 |

字节码/CFG/JNI 处理器及默认值均未改变：`--codegen=legacy`、`--ir-lower=direct` 和 `--backend=cpp` 仍为默认值。测得零剩余项不代表覆盖完整，不是 JDK 支持标志，也不授权切换默认值。
