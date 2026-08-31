## English

### Summary

- Remeasures the joined in-tree IR leftover inventory on the exact [leftover-docs #389](https://github.com/gaoyu06/native-obfuscator/pull/389) tree.
- Measurement SHA: `1db7af56ee6fc787977efbd2974ce070bc71a8da`.
- Merge-base with `origin/master`: `1db7af56ee6fc787977efbd2974ce070bc71a8da`.
- This supersedes #389's measurement of leftover-docs [#387](https://github.com/gaoyu06/native-obfuscator/pull/387) at `3b9fce8`.
- Latest compiler parent XML: **[#388](https://github.com/gaoyu06/native-obfuscator/pull/388) (674)** (`IrCompilerTest` 667 + `CodegenModeTest` 7).
- Processor changed: **No**. Admitted: **No**. Ship-ready: **No**.

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

- 在准确的 [leftover-docs #389](https://github.com/gaoyu06/native-obfuscator/pull/389) 源码树上重新测量仓库内合并后的 IR 遗留项清单。
- 测量 SHA：`1db7af56ee6fc787977efbd2974ce070bc71a8da`。
- 与 `origin/master` 的 merge-base：`1db7af56ee6fc787977efbd2974ce070bc71a8da`。
- 本次结果取代 #389 对 `3b9fce8` 处 leftover-docs [#387](https://github.com/gaoyu06/native-obfuscator/pull/387) 的测量。
- 最新编译器父级 XML：**[#388](https://github.com/gaoyu06/native-obfuscator/pull/388)（674）**（`IrCompilerTest` 667 + `CodegenModeTest` 7）。
- Processor 是否变更：**否**。是否准入：**否**。是否可发布：**否**。

合并语料结果见上表：`ClassicTest`、`jdk17`、`jdk21` 和 `jdk25` 均无测得遗留项。零遗留项不代表覆盖完整，不是 JDK 支持标章，也不授权切换默认配置。默认值仍为 `--codegen=legacy`、`--ir-lower=direct` 和 `--backend=cpp`。
