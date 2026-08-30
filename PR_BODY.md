# Post-#314 IR leftover inventory / #314 后 IR 遗留清单

## English

### Summary

- Re-runs the in-tree IR leftover inventory on post-#314 master.
- Measured SHA and merge-base: `434c4895cd650b05f7474f7eb23292fe744869fe`.
- This post-#314 measurement supersedes #310 on `688c0ea22719350a49d98b3e3787f56e3c72ea7b` (post-#309). #306 and #301 remain earlier snapshots.
- This is measurement-only. It changes no compiler/runtime source and does not change the `--codegen=legacy`, `--ir-lower=direct`, or `--backend=cpp` defaults.

### Joined totals

| Corpus | Inventory | IR | Legacy fallback | Constructor left in Java | Missing |
| --- | ---: | ---: | ---: | ---: | ---: |
| `ClassicTest` | 108 | 108 | 0 | 0 | 0 |
| `jdk17` | 82 | 82 | 0 | 0 | 0 |
| `jdk21` | 47 | 47 | 0 | 0 | 0 |
| `jdk25` | 21 | 21 | 0 | 0 | 0 |

### Verification

```bash
python3 docs/measurement/ir-leftover-inventory/measure.py --javac-25 /tmp/temurin25/bin/javac
```

Parent verification: measurement-only; Gradle focused gate skipped.

- Existing compiler parent XML baseline: **557/557** (`IrCompilerTest` 550 + `CodegenModeTest` 7); it was not rerun for this measurement-only change.
- Zero measured leftovers is not coverage-complete, is not a JDK support badge, and does not authorize changing defaults.
- Processor changed: **No**
- Ship-ready: **No**
- Admitted: **No** (measurement only)

## 中文

### 摘要

- 在 #314 合入后的 master 上重新运行仓库内 IR 遗留清单测量。
- 实际测量 SHA 与 merge-base：`434c4895cd650b05f7474f7eb23292fe744869fe`。
- 本次 #314 后的测量取代 #310 在 `688c0ea22719350a49d98b3e3787f56e3c72ea7b`（#309 后）的结果；#306 与 #301 仍保留为更早快照。
- 本次仅做测量，不修改编译器/运行时源码，也不改变 `--codegen=legacy`、`--ir-lower=direct` 或 `--backend=cpp` 默认值。

### 精确连接后的总数

| 语料 | 清单方法数 | IR | 回退 legacy | 构造器留在 Java | 缺失 |
| --- | ---: | ---: | ---: | ---: | ---: |
| `ClassicTest` | 108 | 108 | 0 | 0 | 0 |
| `jdk17` | 82 | 82 | 0 | 0 | 0 |
| `jdk21` | 47 | 47 | 0 | 0 | 0 |
| `jdk25` | 21 | 21 | 0 | 0 | 0 |

### 验证

```bash
python3 docs/measurement/ir-leftover-inventory/measure.py --javac-25 /tmp/temurin25/bin/javac
```

父项验证：仅测量；跳过 Gradle 聚焦门禁。

- 现有编译器父项 XML 基线为 **557/557**（`IrCompilerTest` 550 + `CodegenModeTest` 7）；本次仅测量变更未重新运行该门禁。
- 测得零遗留不代表覆盖完整，不是 JDK 支持徽章，也不授权修改默认值。
- 处理器修改：**否**
- 可发布：**否**
- 已准入：**否**（仅测量）
