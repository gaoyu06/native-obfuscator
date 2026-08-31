# docs: remeasure IR leftovers after leftover-docs #413

## English

### Summary

- Measurement SHA: `bb97bc39648825f253c92b982743f8f6d13e3573`
- Merge-base with `origin/master` at measurement: `bb97bc39648825f253c92b982743f8f6d13e3573`
- Supersedes [#412](https://github.com/gaoyu06/native-obfuscator/pull/412)'s measurement of leftover-docs [#411](https://github.com/gaoyu06/native-obfuscator/pull/411) at `357d30e1` (`357d30e1d84ebff5cee76030943cae0485350bb7`).
- Latest compiler parent XML: **[#413](https://github.com/gaoyu06/native-obfuscator/pull/413) (710)** (`IrCompilerTest` 703 + `CodegenModeTest` 7).
- This measurement adds no compiler XML. Parent will skip Gradle (measurement-only).
- Processor changed: **No**.
- Admitted: **No**.
- Ship-ready: **No**.

### Joined totals

| Corpus | Inventory | IR | Legacy fallback | Constructor left in Java | Missing |
| --- | ---: | ---: | ---: | ---: | ---: |
| `ClassicTest` | 108 | 108 | 0 | 0 | 0 |
| `jdk17` | 82 | 82 | 0 | 0 | 0 |
| `jdk21` | 47 | 47 | 0 | 0 | 0 |
| `jdk25` | 21 | 21 | 0 | 0 | 0 |

Zero measured leftovers is not coverage-complete, is not a JDK support badge, and does not authorize a default flip. Defaults remain `--codegen=legacy`, `--ir-lower=direct`, and `--backend=cpp`.

### Measurement

```bash
python3 docs/measurement/ir-leftover-inventory/measure.py --javac-25 /tmp/temurin25/bin/javac
```

The run used only Java sources under `obfuscator/test_data/tests/`; it did not fetch historical fixture branches or assemble `pull-requests/PullRequest72/TestStringConcatFactory.j`.

## 中文

### 摘要

- 测量 SHA：`bb97bc39648825f253c92b982743f8f6d13e3573`
- 测量时与 `origin/master` 的 merge-base：`bb97bc39648825f253c92b982743f8f6d13e3573`
- 本次测量取代 [#412](https://github.com/gaoyu06/native-obfuscator/pull/412) 对 leftover-docs [#411](https://github.com/gaoyu06/native-obfuscator/pull/411)（`357d30e1`，完整 SHA 为 `357d30e1d84ebff5cee76030943cae0485350bb7`）的测量。
- 最新编译器父级 XML：**[#413](https://github.com/gaoyu06/native-obfuscator/pull/413) (710)**（`IrCompilerTest` 703 + `CodegenModeTest` 7）。
- 本次测量不增加编译器 XML。父任务将跳过 Gradle（仅测量变更）。
- Processor changed：**No**。
- Admitted：**No**。
- Ship-ready：**No**。

### 精确关联后的总计

| 语料库 | 方法清单 | IR | Legacy fallback | 构造器保留在 Java | Missing |
| --- | ---: | ---: | ---: | ---: | ---: |
| `ClassicTest` | 108 | 108 | 0 | 0 | 0 |
| `jdk17` | 82 | 82 | 0 | 0 | 0 |
| `jdk21` | 47 | 47 | 0 | 0 | 0 |
| `jdk25` | 21 | 21 | 0 | 0 | 0 |

测得零 leftover 不代表覆盖完整，不是 JDK 支持徽章，也不授权切换默认值。默认值仍为 `--codegen=legacy`、`--ir-lower=direct` 和 `--backend=cpp`。

### 测量命令

```bash
python3 docs/measurement/ir-leftover-inventory/measure.py --javac-25 /tmp/temurin25/bin/javac
```

本次运行只使用 `obfuscator/test_data/tests/` 下的 Java 源文件；未获取历史 fixture 分支，也未汇编 `pull-requests/PullRequest72/TestStringConcatFactory.j`。
