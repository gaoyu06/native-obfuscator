## English

### Summary

- Re-runs the in-tree IR leftover inventory on post-#309 master.
- Measured SHA and merge-base: `688c0ea22719350a49d98b3e3787f56e3c72ea7b`.
- Supersedes #306 on `580ec948f3284af1ca99a073ba656017172d223e` (post-#304).
- Measurement only; no compiler/runtime source or defaults changed.

### Command

```bash
python3 docs/measurement/ir-leftover-inventory/measure.py --javac-25 /tmp/temurin25/bin/javac
```

### Joined totals

| Corpus | Inventory | IR | Legacy fallback | Constructor left in Java | Missing |
| --- | ---: | ---: | ---: | ---: | ---: |
| `ClassicTest` | 108 | 108 | 0 | 0 | 0 |
| `jdk17` | 82 | 82 | 0 | 0 | 0 |
| `jdk21` | 47 | 47 | 0 | 0 | 0 |
| `jdk25` | 21 | 21 | 0 | 0 | 0 |

Parent verification: measurement-only; Gradle focused gate skipped.

Zero leftovers is not a JDK support badge, is not coverage-complete, and does not authorize changing `--codegen=legacy`, `--ir-lower=direct`, or `--backend=cpp` defaults.

- Ship-ready: **No**
- Admitted: **No** (measurement only)

## 中文

### 摘要

- 在 #309 落地后的 `master` 上重新运行仓库内 IR leftover inventory。
- 实测 SHA 与 merge-base：`688c0ea22719350a49d98b3e3787f56e3c72ea7b`。
- 本次测量取代基于 post-#304 `580ec948f3284af1ca99a073ba656017172d223e` 的 #306。
- 仅做测量；未修改编译器、运行时源码或默认选项。

### 命令

```bash
python3 docs/measurement/ir-leftover-inventory/measure.py --javac-25 /tmp/temurin25/bin/javac
```

### 精确关联后的总数

| 语料 | Inventory | IR | Legacy fallback | 构造器留在 Java | Missing |
| --- | ---: | ---: | ---: | ---: | ---: |
| `ClassicTest` | 108 | 108 | 0 | 0 | 0 |
| `jdk17` | 82 | 82 | 0 | 0 | 0 |
| `jdk21` | 47 | 47 | 0 | 0 | 0 |
| `jdk25` | 21 | 21 | 0 | 0 | 0 |

父级验证：仅测量；跳过 Gradle focused gate。

零 leftover 不是 JDK 支持徽章，不代表覆盖完整，也不授权修改 `--codegen=legacy`、`--ir-lower=direct` 或 `--backend=cpp` 默认值。

- Ship-ready：**No**
- Admitted：**No**（仅测量）
