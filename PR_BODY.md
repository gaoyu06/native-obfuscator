## English

### Summary

Measurement-only remeasurement of the joined IR leftover inventory on the [#381](https://github.com/gaoyu06/native-obfuscator/pull/381) leftover-docs tree.

- Measured compiler base / measurement commit: `109c318237a0cf25daa3c70c7bc19445f1513b91`
- Supersedes #381's measurement of the [#379](https://github.com/gaoyu06/native-obfuscator/pull/379) leftover-docs tree: `9fa518121ee00310888c9dd752094357e494d27c`
- Latest compiler parent XML: **[#382](https://github.com/gaoyu06/native-obfuscator/pull/382) (665)** (`IrCompilerTest` 658 + `CodegenModeTest` 7). The branch was rebased onto leftover-docs #382 (`de096b8552a5c4c9452f682cd3e2aa2c15eb71c5`); the measured tree remains leftover-docs #381 `109c318237a0cf25daa3c70c7bc19445f1513b91`.
- This measurement adds no compiler XML.

### Joined corpus

| Corpus | Inventory | IR | Legacy fallback | Constructor left in Java | Missing |
| --- | ---: | ---: | ---: | ---: | ---: |
| `ClassicTest` | 108 | 108 | 0 | 0 | 0 |
| `jdk17` | 82 | 82 | 0 | 0 | 0 |
| `jdk21` | 47 | 47 | 0 | 0 | 0 |
| `jdk25` | 21 | 21 | 0 | 0 | 0 |

Zero measured leftovers is not coverage-complete, not a JDK support badge, and does not authorize a default flip. Processor changed: **No**. Admitted: **No**. Ship-ready: **No**. Defaults remain `--codegen=legacy`, `--ir-lower=direct`, and `--backend=cpp`. Parent skipped Gradle (measurement-only).

### Measurement

```bash
python3 docs/measurement/ir-leftover-inventory/measure.py --javac-25 /tmp/temurin25/bin/javac
```

## 中文

### 摘要

在 [#381](https://github.com/gaoyu06/native-obfuscator/pull/381) leftover-docs 树上重新测量合并后的 IR 剩余项清单；本次仅做测量。

- 编译器基线 / 测量提交：`109c318237a0cf25daa3c70c7bc19445f1513b91`
- 本次测量取代 #381 对 [#379](https://github.com/gaoyu06/native-obfuscator/pull/379) leftover-docs 树的测量：`9fa518121ee00310888c9dd752094357e494d27c`
- 最新编译器父级 XML：**[#382](https://github.com/gaoyu06/native-obfuscator/pull/382) (665)**（`IrCompilerTest` 658 + `CodegenModeTest` 7）。分支已 rebase 到 leftover-docs #382（`de096b8552a5c4c9452f682cd3e2aa2c15eb71c5`）；实测树仍为 leftover-docs #381 `109c318237a0cf25daa3c70c7bc19445f1513b91`。
- 本次测量不增加编译器 XML。

### 合并语料

| 语料 | 清单方法数 | IR | 回退到 legacy | 构造器保留在 Java | 缺失 |
| --- | ---: | ---: | ---: | ---: | ---: |
| `ClassicTest` | 108 | 108 | 0 | 0 | 0 |
| `jdk17` | 82 | 82 | 0 | 0 | 0 |
| `jdk21` | 47 | 47 | 0 | 0 | 0 |
| `jdk25` | 21 | 21 | 0 | 0 | 0 |

测得的剩余项为零不代表覆盖完整，不是 JDK 支持徽章，也不授权切换默认值。处理器变更：**否**。新增准入：**否**。可发布：**否**。默认值仍为 `--codegen=legacy`、`--ir-lower=direct` 和 `--backend=cpp`。

### 测量命令

```bash
python3 docs/measurement/ir-leftover-inventory/measure.py --javac-25 /tmp/temurin25/bin/javac
```
