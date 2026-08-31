## English

### Summary

- Remeasures the joined in-tree IR leftover inventory on the leftover-docs [#393](https://github.com/gaoyu06/native-obfuscator/pull/393) tree.
- Measurement SHA: `051d2837bd526624f612bc6c867a074dd08f4cea`
- Merge-base with `origin/master`: `051d2837bd526624f612bc6c867a074dd08f4cea`
- Supersedes [#393](https://github.com/gaoyu06/native-obfuscator/pull/393)'s measurement of leftover-docs [#391](https://github.com/gaoyu06/native-obfuscator/pull/391) at `51d6aed` (`51d6aedfc0f8c678950f8ee2c2b71f797377648d`).
- Latest compiler parent XML: **[#394](https://github.com/gaoyu06/native-obfuscator/pull/394) (683)** (`IrCompilerTest` 676 + `CodegenModeTest` 7). The branch was rebased onto leftover-docs #394 (`425892c`); the measured tree remains leftover-docs #393 `051d2837bd526624f612bc6c867a074dd08f4cea`.
- This measurement adds no compiler XML. Parent skipped Gradle (measurement-only).
- Zero measured leftovers is **not coverage-complete**, **not a JDK support badge**, and **does not authorize a default flip**.
- No compiler/runtime source or defaults changed. Defaults remain `--codegen=legacy`, `--ir-lower=direct`, and `--backend=cpp`.

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

- 在 leftover-docs [#393](https://github.com/gaoyu06/native-obfuscator/pull/393) 树上重新测量仓库内 IR 遗留项的联合清单。
- 测量 SHA：`051d2837bd526624f612bc6c867a074dd08f4cea`
- 与 `origin/master` 的 merge-base：`051d2837bd526624f612bc6c867a074dd08f4cea`
- 本次测量取代 [#393](https://github.com/gaoyu06/native-obfuscator/pull/393) 对 leftover-docs [#391](https://github.com/gaoyu06/native-obfuscator/pull/391)（`51d6aed` / `51d6aedfc0f8c678950f8ee2c2b71f797377648d`）的测量。
- 最新编译器父级 XML：**[#394](https://github.com/gaoyu06/native-obfuscator/pull/394)（683）**（`IrCompilerTest` 676 + `CodegenModeTest` 7）。分支已 rebase 到 leftover-docs #394（`425892c`）；实测树仍为 leftover-docs #393 `051d2837bd526624f612bc6c867a074dd08f4cea`。
- 本次测量不新增编译器 XML。父任务跳过 Gradle（仅测量）。
- 测得的遗留项为零**不代表覆盖完整**、**不是 JDK 支持标志**，也**不授权翻转默认选项**。
- 未更改编译器/运行时源码或默认选项。默认选项仍为 `--codegen=legacy`、`--ir-lower=direct` 和 `--backend=cpp`。

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
