## English

### Summary

- Remeasures the joined IR leftover inventory on the exact [#385](https://github.com/gaoyu06/native-obfuscator/pull/385) leftover-docs commit `48f5ab56659ca04101478963ab83aff7a0003679`.
- That tree contains the post-[#384](https://github.com/gaoyu06/native-obfuscator/pull/384) fourth-and-fifth five-argument `GregorianCalendar` `NEW` extra-local `int` leftover-docs and the #385 inventory leftover-docs.
- Both the measured compiler base and measurement commit are `48f5ab56659ca04101478963ab83aff7a0003679`.
- This supersedes #385's measurement of the [#383](https://github.com/gaoyu06/native-obfuscator/pull/383) leftover-docs commit `664986503919153f072b9a2444c07a9de26efb6c`.
- Latest compiler parent XML: **[#386](https://github.com/gaoyu06/native-obfuscator/pull/386) (671)** (`IrCompilerTest` 664 + `CodegenModeTest` 7). The branch was rebased onto leftover-docs #386 (`b072b81`); the measured tree remains leftover-docs #385 `48f5ab56659ca04101478963ab83aff7a0003679`.
- This measurement adds no compiler XML. Parent skipped Gradle (measurement-only).

### Joined corpus

| Corpus | Inventory | IR | Legacy fallback | Constructor left in Java | Missing |
| --- | ---: | ---: | ---: | ---: | ---: |
| `ClassicTest` | 108 | 108 | 0 | 0 | 0 |
| `jdk17` | 82 | 82 | 0 | 0 | 0 |
| `jdk21` | 47 | 47 | 0 | 0 | 0 |
| `jdk25` | 21 | 21 | 0 | 0 | 0 |

Zero measured leftovers is not coverage-complete, is not a JDK support badge, and does not authorize a default flip. Processor changed: **No**. Admitted: **No**. Ship-ready: **No**. Defaults remain `--codegen=legacy`, `--ir-lower=direct`, and `--backend=cpp`.

Measurement command:

```bash
python3 docs/measurement/ir-leftover-inventory/measure.py --javac-25 /tmp/temurin25/bin/javac
```

## 中文

### 摘要

- 在 [#385](https://github.com/gaoyu06/native-obfuscator/pull/385) leftover-docs 的精确提交 `48f5ab56659ca04101478963ab83aff7a0003679` 上重新测量合并后的 IR leftover inventory。
- 该提交包含 [#384](https://github.com/gaoyu06/native-obfuscator/pull/384) 之后、五参数 `GregorianCalendar` `NEW` 的第四和第五个参数使用 extra-local `int` 的 leftover-docs，以及 #385 inventory leftover-docs。
- 编译器测量基线和测量提交均为 `48f5ab56659ca04101478963ab83aff7a0003679`。
- 本次测量取代 #385 对 [#383](https://github.com/gaoyu06/native-obfuscator/pull/383) leftover-docs 提交 `664986503919153f072b9a2444c07a9de26efb6c` 的测量。
- 最新编译器父级 XML：**[#386](https://github.com/gaoyu06/native-obfuscator/pull/386) (671)**（`IrCompilerTest` 664 + `CodegenModeTest` 7）。分支已 rebase 到 leftover-docs #386（`b072b81`）；实测树仍为 leftover-docs #385 `48f5ab56659ca04101478963ab83aff7a0003679`。
- 本次测量不新增编译器 XML。父任务跳过 Gradle（仅测量）。

### 合并语料库

| 语料库 | 方法清单 | IR | 回退到 legacy | 构造器保留在 Java | 缺失 |
| --- | ---: | ---: | ---: | ---: | ---: |
| `ClassicTest` | 108 | 108 | 0 | 0 | 0 |
| `jdk17` | 82 | 82 | 0 | 0 | 0 |
| `jdk21` | 47 | 47 | 0 | 0 | 0 |
| `jdk25` | 21 | 21 | 0 | 0 | 0 |

测得 leftover 为零不代表覆盖完整，不是 JDK 支持标志，也不允许切换默认值。Processor changed：**No**。Admitted：**No**。Ship-ready：**No**。默认值仍为 `--codegen=legacy`、`--ir-lower=direct` 和 `--backend=cpp`。
