# English

## Summary

Remeasures the exact-method-joined IR admission inventory on current `origin/master` after #263.

- Measured commit: `c0304febc41f1c665fb42ce0947f38bf0c29947a`
- Compiler base (merge-base with `origin/master`): `c0304febc41f1c665fb42ce0947f38bf0c29947a`
- Measurement command: `python3 docs/measurement/ir-leftover-inventory/measure.py --javac-25 /tmp/temurin25/bin/javac`

## Joined totals

| Corpus | Inventory | IR | Legacy fallback | Constructor left in Java | Missing |
| --- | ---: | ---: | ---: | ---: | ---: |
| `ClassicTest` | 108 | 108 | 0 | 0 | 0 |
| `jdk17` | 82 | 82 | 0 | 0 | 0 |
| `jdk21` | 47 | 47 | 0 | 0 | 0 |
| `jdk25` | 21 | 21 | 0 | 0 | 0 |

No leftover methods appeared in any measured corpus.

## Interpretation

This is admission measurement only. It is not a JDK support badge, is not coverage-complete, and does not show that the production goal is complete. The default remains `--codegen=legacy`.

Ship-ready: **No**.

# 中文

## 摘要

在 #263 之后的当前 `origin/master` 上，重新测量按精确方法连接的 IR 准入清单。

- 测量提交：`c0304febc41f1c665fb42ce0947f38bf0c29947a`
- 编译器基线（与 `origin/master` 的 merge-base）：`c0304febc41f1c665fb42ce0947f38bf0c29947a`
- 测量命令：`python3 docs/measurement/ir-leftover-inventory/measure.py --javac-25 /tmp/temurin25/bin/javac`

## 连接后的总数

| 语料库 | 清单方法数 | IR | Legacy 回退 | 构造器保留在 Java | 缺失 |
| --- | ---: | ---: | ---: | ---: | ---: |
| `ClassicTest` | 108 | 108 | 0 | 0 | 0 |
| `jdk17` | 82 | 82 | 0 | 0 | 0 |
| `jdk21` | 47 | 47 | 0 | 0 | 0 |
| `jdk25` | 21 | 21 | 0 | 0 | 0 |

所有已测量语料库均未出现遗留方法。

## 解释

这仅是准入测量，不是 JDK 支持标志，也不代表覆盖完整或生产目标已经完成。默认值仍为 `--codegen=legacy`。

可发布：**否**。
