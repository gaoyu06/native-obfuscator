# English

## Summary

- Re-runs the in-tree IR leftover inventory on post-#299 master.
- Supersedes the #297 measurement on `ee8f987ee6a212fb257a1527e764ddbf0cd4aa09`.
- Updates measurement documentation only; no compiler/runtime source or defaults change.

## Results

| Corpus | Inventory | IR | Leftovers |
| --- | ---: | ---: | ---: |
| ClassicTest | 108 | 108 | 0 |
| jdk17 | 82 | 82 | 0 |
| jdk21 | 47 | 47 | 0 |
| jdk25 | 21 | 21 | 0 |

Measured commit and merge-base: `d070653d8176ef5bab338f76eb2992c1bffbbfbf`.

Command:

```bash
python3 docs/measurement/ir-leftover-inventory/measure.py --javac-25 /tmp/temurin25/bin/javac
```

Zero measured leftovers is not a JDK support badge, is not coverage-complete, and does not authorize changing defaults. The defaults remain `--codegen=legacy`, `--ir-lower=direct`, and `--backend=cpp`.

Ship-ready: **No**. Admitted: **No** (measurement only).

# 中文

## 摘要

- 在合入 #299 后的 master 上重新运行仓库内 IR leftover inventory。
- 本次测量取代基于 `ee8f987ee6a212fb257a1527e764ddbf0cd4aa09` 的 #297 测量。
- 仅更新测量文档；不修改编译器、运行时源码或默认选项。

## 结果

| 语料 | 方法总数 | IR | 剩余项 |
| --- | ---: | ---: | ---: |
| ClassicTest | 108 | 108 | 0 |
| jdk17 | 82 | 82 | 0 |
| jdk21 | 47 | 47 | 0 |
| jdk25 | 21 | 21 | 0 |

测量提交与 merge-base：`d070653d8176ef5bab338f76eb2992c1bffbbfbf`。

命令：

```bash
python3 docs/measurement/ir-leftover-inventory/measure.py --javac-25 /tmp/temurin25/bin/javac
```

测得剩余项为零不代表已获得 JDK 支持认证，不代表覆盖完整，也不授权修改默认选项。默认选项仍为 `--codegen=legacy`、`--ir-lower=direct` 和 `--backend=cpp`。

可发布：**否**。已准入：**否**（仅测量）。
