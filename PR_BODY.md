## English

### Summary

- Remeasures the joined in-tree IR leftover inventory on the leftover-docs [#391](https://github.com/gaoyu06/native-obfuscator/pull/391) tree.
- Measurement SHA: `51d6aedfc0f8c678950f8ee2c2b71f797377648d`
- Merge-base with `origin/master`: `51d6aedfc0f8c678950f8ee2c2b71f797377648d`
- Supersedes [#391](https://github.com/gaoyu06/native-obfuscator/pull/391)'s measurement of leftover-docs [#389](https://github.com/gaoyu06/native-obfuscator/pull/389) at `1db7af5`.
- Latest compiler parent XML: **[#392](https://github.com/gaoyu06/native-obfuscator/pull/392) (680)** (`IrCompilerTest` 673 + `CodegenModeTest` 7). The branch was rebased onto leftover-docs #392 (`c827709`); the measured tree remains leftover-docs #391 `51d6aedfc0f8c678950f8ee2c2b71f797377648d`.
- This measurement adds no compiler XML. Parent skipped Gradle (measurement-only).

### Joined corpus

| Corpus | Inventory | IR | Legacy fallback | Constructor left in Java | Missing |
| --- | ---: | ---: | ---: | ---: | ---: |
| `ClassicTest` | 108 | 108 | 0 | 0 | 0 |
| `jdk17` | 82 | 82 | 0 | 0 | 0 |
| `jdk21` | 47 | 47 | 0 | 0 | 0 |
| `jdk25` | 21 | 21 | 0 | 0 | 0 |

- Processor changed: **No**
- Admitted: **No**
- Ship-ready: **No**

### Measurement

```bash
python3 docs/measurement/ir-leftover-inventory/measure.py --javac-25 /tmp/temurin25/bin/javac
```

## 中文

### 摘要

- 在 leftover-docs [#391](https://github.com/gaoyu06/native-obfuscator/pull/391) 树上重新测量仓库内 IR 遗留项的联合清单。
- 测量 SHA：`51d6aedfc0f8c678950f8ee2c2b71f797377648d`
- 与 `origin/master` 的 merge-base：`51d6aedfc0f8c678950f8ee2c2b71f797377648d`
- 本次测量取代 [#391](https://github.com/gaoyu06/native-obfuscator/pull/391) 对 leftover-docs [#389](https://github.com/gaoyu06/native-obfuscator/pull/389)（`1db7af5`）的测量。
- 最新编译器父级 XML：**[#392](https://github.com/gaoyu06/native-obfuscator/pull/392)（680）**（`IrCompilerTest` 673 + `CodegenModeTest` 7）。分支已 rebase 到 leftover-docs #392（`c827709`）；实测树仍为 leftover-docs #391 `51d6aedfc0f8c678950f8ee2c2b71f797377648d`。
- 本次测量不新增编译器 XML。父任务跳过 Gradle（仅测量）。

### 联合语料结果

| 语料 | 清单数 | IR | Legacy 回退 | 构造器保留在 Java | 缺失 |
| --- | ---: | ---: | ---: | ---: | ---: |
| `ClassicTest` | 108 | 108 | 0 | 0 | 0 |
| `jdk17` | 82 | 82 | 0 | 0 | 0 |
| `jdk21` | 47 | 47 | 0 | 0 | 0 |
| `jdk25` | 21 | 21 | 0 | 0 | 0 |

- Processor 是否变更：**否**
- 是否准入：**否**
- 是否可发布：**否**

### 测量命令

```bash
python3 docs/measurement/ir-leftover-inventory/measure.py --javac-25 /tmp/temurin25/bin/javac
```
