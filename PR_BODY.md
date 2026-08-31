## English

### Summary

- Remeasures the joined in-tree IR leftover inventory on leftover-docs [#387](https://github.com/gaoyu06/native-obfuscator/pull/387).
- Measurement SHA: `3b9fce8969eb6f6d455bdeec2f5e1f9a2324b9c3`
- Merge-base with `origin/master`: `3b9fce8969eb6f6d455bdeec2f5e1f9a2324b9c3`
- Supersedes [#387](https://github.com/gaoyu06/native-obfuscator/pull/387)'s measurement of leftover-docs [#385](https://github.com/gaoyu06/native-obfuscator/pull/385) at `48f5ab56659ca04101478963ab83aff7a0003679`.
- Latest compiler parent XML: [#388](https://github.com/gaoyu06/native-obfuscator/pull/388) (674) (`IrCompilerTest` 667 + `CodegenModeTest` 7). The branch was rebased onto leftover-docs #388 (`a05cacb`); the measured tree remains leftover-docs #387 `3b9fce8969eb6f6d455bdeec2f5e1f9a2324b9c3`.
- This measurement adds no compiler XML. Parent skipped Gradle (measurement-only).
- Processor changed: No. Admitted: No. Ship-ready: No.

### Joined corpus

| Corpus | Inventory | IR | Legacy fallback | Constructor left in Java | Missing |
| --- | ---: | ---: | ---: | ---: | ---: |
| `ClassicTest` | 108 | 108 | 0 | 0 | 0 |
| `jdk17` | 82 | 82 | 0 | 0 | 0 |
| `jdk21` | 47 | 47 | 0 | 0 | 0 |
| `jdk25` | 21 | 21 | 0 | 0 | 0 |

Zero measured leftovers is not coverage-complete, not a JDK support badge, and does not authorize a default flip. Defaults remain `--codegen=legacy`, `--ir-lower=direct`, and `--backend=cpp`.

### Measurement

```bash
python3 docs/measurement/ir-leftover-inventory/measure.py --javac-25 /tmp/temurin25/bin/javac
```

## 中文

### 摘要

- 在 leftover-docs [#387](https://github.com/gaoyu06/native-obfuscator/pull/387) 上重新测量仓库内合并后的 IR 遗留项清单。
- 测量 SHA：`3b9fce8969eb6f6d455bdeec2f5e1f9a2324b9c3`
- 与 `origin/master` 的 merge-base：`3b9fce8969eb6f6d455bdeec2f5e1f9a2324b9c3`
- 本次测量取代 [#387](https://github.com/gaoyu06/native-obfuscator/pull/387) 对 leftover-docs [#385](https://github.com/gaoyu06/native-obfuscator/pull/385)（`48f5ab56659ca04101478963ab83aff7a0003679`）的测量。
- 最新编译器父级 XML：[#388](https://github.com/gaoyu06/native-obfuscator/pull/388)（674，`IrCompilerTest` 667 + `CodegenModeTest` 7）。分支已 rebase 到 leftover-docs #388（`a05cacb`）；实测树仍为 leftover-docs #387 `3b9fce8969eb6f6d455bdeec2f5e1f9a2324b9c3`。
- 本次测量不新增编译器 XML。父任务跳过 Gradle（仅测量）。
- Processor changed：No。Admitted：No。Ship-ready：No。

### 合并语料

| 语料 | 清单数 | IR | Legacy fallback | 构造器保留在 Java | 缺失 |
| --- | ---: | ---: | ---: | ---: | ---: |
| `ClassicTest` | 108 | 108 | 0 | 0 | 0 |
| `jdk17` | 82 | 82 | 0 | 0 | 0 |
| `jdk21` | 47 | 47 | 0 | 0 | 0 |
| `jdk25` | 21 | 21 | 0 | 0 | 0 |

测得零遗留项不代表覆盖完整，不是 JDK 支持标志，也不允许切换默认值。默认值仍为 `--codegen=legacy`、`--ir-lower=direct` 和 `--backend=cpp`。

### 测量命令

```bash
python3 docs/measurement/ir-leftover-inventory/measure.py --javac-25 /tmp/temurin25/bin/javac
```
