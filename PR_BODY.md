# docs: remeasure IR leftovers after leftover-docs #425

## English

### Summary

- Remeasures the in-tree IR leftover inventory on leftover-docs [#425](https://github.com/gaoyu06/native-obfuscator/pull/425), commit `a54d9a28` (`a54d9a28ced1bbabcd4b035cc3eef5309ea3937c`).
- Supersedes [#424](https://github.com/gaoyu06/native-obfuscator/pull/424)'s measurement of the leftover-docs [#423](https://github.com/gaoyu06/native-obfuscator/pull/423) tree at `9a45cc67` (`9a45cc6714007544b8521c826d92e35312804642`).
- Latest compiler parent XML at measurement time is **#425 (728)**: `IrCompilerTest` 721 + `CodegenModeTest` 7. This measurement adds no compiler XML.
- Processor changed: **No**. Admitted: **No**. Ship-ready: **No**.
- Defaults remain unchanged: `--codegen=legacy`, `--ir-lower=direct`, `--backend=cpp`.
- Zero measured leftovers is **not coverage-complete**, **not a JDK support badge**, and **does not authorize a default flip**. The written production goal remains incomplete.

### Joined totals

| Corpus | Inventory | IR | Fallback | Constructor left in Java | Missing |
| --- | ---: | ---: | ---: | ---: | ---: |
| `ClassicTest` | 108 | 108 | 0 | 0 | 0 |
| `jdk17` | 82 | 82 | 0 | 0 | 0 |
| `jdk21` | 47 | 47 | 0 | 0 | 0 |
| `jdk25` | 21 | 21 | 0 | 0 | 0 |

### Scope

This is a measurement-only documentation change. It changes no processor/compiler/test source, bytecode or CFG handling, JNI path, CLI behavior, or defaults.

## 中文

### 摘要

- 在 leftover-docs [#425](https://github.com/gaoyu06/native-obfuscator/pull/425) 的提交 `a54d9a28`（`a54d9a28ced1bbabcd4b035cc3eef5309ea3937c`）上重新测量树内 IR 遗留清单。
- 本次测量取代 [#424](https://github.com/gaoyu06/native-obfuscator/pull/424) 对 leftover-docs [#423](https://github.com/gaoyu06/native-obfuscator/pull/423) 提交 `9a45cc67`（`9a45cc6714007544b8521c826d92e35312804642`）的测量。
- 测量时最新的编译器父级 XML 是 **#425（728）**：`IrCompilerTest` 721 + `CodegenModeTest` 7。本次测量不增加编译器 XML。
- 处理器变更：**否**。准入：**否**。可交付：**否**。
- 默认值保持不变：`--codegen=legacy`、`--ir-lower=direct`、`--backend=cpp`。
- 测得零遗留**不代表覆盖完整**，**不是 JDK 支持标志**，也**不授权切换默认值**。书面生产目标仍未完成。

### 联合总计

| 语料 | 清单 | IR | 回退 | 构造器留在 Java | 缺失 |
| --- | ---: | ---: | ---: | ---: | ---: |
| `ClassicTest` | 108 | 108 | 0 | 0 | 0 |
| `jdk17` | 82 | 82 | 0 | 0 | 0 |
| `jdk21` | 47 | 47 | 0 | 0 | 0 |
| `jdk25` | 21 | 21 | 0 | 0 | 0 |

### 范围

这只是测量文档变更，不修改处理器、编译器或测试源码，不修改字节码或 CFG 处理、JNI 路径、CLI 行为或默认值。
