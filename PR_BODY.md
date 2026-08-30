## English

### Summary

- Remeasure the in-tree IR leftover inventory on post-#296 master at `ee8f987ee6a212fb257a1527e764ddbf0cd4aa09`.
- Supersede the #295 inventory on `44596132e0debebc422261c50ac2278e889d3400`.
- Record 108/108 IR methods for ClassicTest, 82/82 for jdk17, 47/47 for jdk21, and 21/21 for jdk25, with zero measured leftovers.

### Validation

```bash
python3 docs/measurement/ir-leftover-inventory/measure.py --javac-25 /tmp/temurin25/bin/javac
```

This is measurement only. Zero measured leftovers is not a JDK support badge, is not coverage-complete, and does not authorize changing the defaults (`--codegen=legacy`, `--ir-lower=direct`, `--backend=cpp`).

- Admitted: **No**
- Ship-ready: **No**

## 中文

### 摘要

- 在提交 `ee8f987ee6a212fb257a1527e764ddbf0cd4aa09` 的 post-#296 master 上重新测量仓库内 IR 遗留项清单。
- 本次结果取代 #295 在 `44596132e0debebc422261c50ac2278e889d3400` 上发布的清单。
- ClassicTest、jdk17、jdk21、jdk25 分别测得 108/108、82/82、47/47、21/21 个 IR 方法，测得的遗留项均为零。

### 验证

```bash
python3 docs/measurement/ir-leftover-inventory/measure.py --javac-25 /tmp/temurin25/bin/javac
```

本次仅为测量。测得遗留项为零并不代表获得 JDK 支持标志，不代表覆盖完整，也不授权修改默认值（`--codegen=legacy`、`--ir-lower=direct`、`--backend=cpp`）。

- 已准入：**否**
- 可发布：**否**
