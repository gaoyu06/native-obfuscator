# docs: remeasure IR leftovers after leftover-docs #395

## English

This docs-only change remeasures the joined in-tree IR leftover inventory on the [#395](https://github.com/gaoyu06/native-obfuscator/pull/395) leftover-docs tree.

- Measurement SHA: `38c8cf02920609f12a2717f799c8a0b228876b65`
- Merge-base with `origin/master` at measurement: `38c8cf02920609f12a2717f799c8a0b228876b65`
- This remeasurement supersedes #395's measurement of the [#393](https://github.com/gaoyu06/native-obfuscator/pull/393) leftover-docs tree at `051d283` (`051d2837bd526624f612bc6c867a074dd08f4cea`).
- Latest compiler parent XML: **[#394](https://github.com/gaoyu06/native-obfuscator/pull/394) (683)** (`IrCompilerTest` 676 + `CodegenModeTest` 7). This measurement adds no compiler XML.
- Processor changed: **No**
- Admitted: **No**
- Ship-ready: **No**
- Zero measured leftovers is **not coverage-complete**, **not a JDK support badge**, and **does not authorize a default flip**.
- Defaults remain `--codegen=legacy`, `--ir-lower=direct`, and `--backend=cpp`.

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

## 中文

此纯文档变更在 [#395](https://github.com/gaoyu06/native-obfuscator/pull/395) leftover-docs 树上重新测量仓库内 IR 剩余项的精确关联清单。

- 测量 SHA：`38c8cf02920609f12a2717f799c8a0b228876b65`
- 测量时与 `origin/master` 的 merge-base：`38c8cf02920609f12a2717f799c8a0b228876b65`
- 本次重新测量取代 #395 中对 [#393](https://github.com/gaoyu06/native-obfuscator/pull/393) leftover-docs 树 `051d283`（`051d2837bd526624f612bc6c867a074dd08f4cea`）的测量。
- 测量时最新的编译器父级 XML：**[#394](https://github.com/gaoyu06/native-obfuscator/pull/394)（683）**（`IrCompilerTest` 676 + `CodegenModeTest` 7）。本次测量未新增编译器 XML。
- Processor changed（处理器变更）：**No（否）**
- Admitted（纳入）：**No（否）**
- Ship-ready（可发布）：**No（否）**
- 测得零剩余项**不代表覆盖完整**，**不是 JDK 支持标志**，也**不授权切换默认值**。
- 默认值保持 `--codegen=legacy`、`--ir-lower=direct` 和 `--backend=cpp`。

| 语料 | 清单 | IR | Legacy fallback | 构造器保留在 Java | 缺失 |
| --- | ---: | ---: | ---: | ---: | ---: |
| `ClassicTest` | 108 | 108 | 0 | 0 | 0 |
| `jdk17` | 82 | 82 | 0 | 0 | 0 |
| `jdk21` | 47 | 47 | 0 | 0 | 0 |
| `jdk25` | 21 | 21 | 0 | 0 | 0 |

测量命令：

```bash
python3 docs/measurement/ir-leftover-inventory/measure.py --javac-25 /tmp/temurin25/bin/javac
```
