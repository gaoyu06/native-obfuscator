## English

### Summary

- Remeasures the joined IR leftover inventory on the exact [#383](https://github.com/gaoyu06/native-obfuscator/pull/383) leftover-docs commit `664986503919153f072b9a2444c07a9de26efb6c`.
- That tree contains the post-[#382](https://github.com/gaoyu06/native-obfuscator/pull/382) third-and-fifth five-argument `GregorianCalendar` `NEW` extra-local `int` leftover-docs and the #383 inventory leftover-docs.
- Supersedes #383's measurement of the [#381](https://github.com/gaoyu06/native-obfuscator/pull/381) leftover-docs commit `109c318237a0cf25daa3c70c7bc19445f1513b91`.

### Measurement

- Measured compiler base / merge-base: `664986503919153f072b9a2444c07a9de26efb6c`
- Measurement commit: `664986503919153f072b9a2444c07a9de26efb6c`
- Latest compiler parent XML: **[#382](https://github.com/gaoyu06/native-obfuscator/pull/382) (665)** (`IrCompilerTest` 658 + `CodegenModeTest` 7)
- This measurement adds no compiler XML.

| Corpus | Inventory | IR | Legacy fallback | Constructor left in Java | Missing |
| --- | ---: | ---: | ---: | ---: | ---: |
| `ClassicTest` | 108 | 108 | 0 | 0 | 0 |
| `jdk17` | 82 | 82 | 0 | 0 | 0 |
| `jdk21` | 47 | 47 | 0 | 0 | 0 |
| `jdk25` | 21 | 21 | 0 | 0 | 0 |

Zero measured leftovers is not coverage-complete, is not a JDK support badge, and does not authorize a default flip. Defaults remain `--codegen=legacy`, `--ir-lower=direct`, and `--backend=cpp`.

- Processor changed: **No**
- Admitted: **No**
- Ship-ready: **No**

## 中文

### 摘要

- 在 [#383](https://github.com/gaoyu06/native-obfuscator/pull/383) 的精确 leftover-docs 提交 `664986503919153f072b9a2444c07a9de26efb6c` 上重新测量合并后的 IR 剩余项清单。
- 该提交包含 [#382](https://github.com/gaoyu06/native-obfuscator/pull/382) 之后五参数 `GregorianCalendar` `NEW` 的第三和第五个参数所对应的 extra-local `int` leftover-docs，以及 #383 的清单 leftover-docs。
- 本次结果取代 #383 对 [#381](https://github.com/gaoyu06/native-obfuscator/pull/381) leftover-docs 提交 `109c318237a0cf25daa3c70c7bc19445f1513b91` 的测量。

### 测量

- 编译器基线 / merge-base：`664986503919153f072b9a2444c07a9de26efb6c`
- 测量提交：`664986503919153f072b9a2444c07a9de26efb6c`
- 最新编译器父级 XML：**[#382](https://github.com/gaoyu06/native-obfuscator/pull/382) (665)**（`IrCompilerTest` 658 + `CodegenModeTest` 7）
- 本次测量不新增编译器 XML。

| 语料库 | 清单方法数 | IR | 回退到 legacy | 构造器保留在 Java | 缺失 |
| --- | ---: | ---: | ---: | ---: | ---: |
| `ClassicTest` | 108 | 108 | 0 | 0 | 0 |
| `jdk17` | 82 | 82 | 0 | 0 | 0 |
| `jdk21` | 47 | 47 | 0 | 0 | 0 |
| `jdk25` | 21 | 21 | 0 | 0 | 0 |

测得剩余项为零并不代表覆盖完整，不是 JDK 支持标志，也不授权切换默认值。默认值仍为 `--codegen=legacy`、`--ir-lower=direct` 和 `--backend=cpp`。

- Processor changed：**No**
- Admitted：**No**
- Ship-ready：**No**
