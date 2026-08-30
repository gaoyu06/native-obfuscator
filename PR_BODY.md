# English

## Summary

- Re-ran the in-tree IR leftover inventory on post-#304 `master`, measured at `580ec948f3284af1ca99a073ba656017172d223e`.
- This is the leftover-docs tree after extra-local six-argument `NEW` #304, isolated float `NEW` #303, and extra-local five-argument `NEW` #302.
- This remeasurement supersedes #301 on `d070653d8176ef5bab338f76eb2992c1bffbbfbf` (post-#299).
- Measurement only: no compiler/runtime processor, tests, or defaults changed.

## Joined totals

| Corpus | Inventory | IR | Legacy fallback | Constructor left in Java | Missing |
| --- | ---: | ---: | ---: | ---: | ---: |
| `ClassicTest` | 108 | 108 | 0 | 0 | 0 |
| `jdk17` | 82 | 82 | 0 | 0 | 0 |
| `jdk21` | 47 | 47 | 0 | 0 | 0 |
| `jdk25` | 21 | 21 | 0 | 0 | 0 |

Measured leftovers: **0**.

## Command

```bash
python3 docs/measurement/ir-leftover-inventory/measure.py --javac-25 /tmp/temurin25/bin/javac
```

## Guardrails

- Zero measured leftovers is **not a JDK support badge**, is **not coverage-complete**, and does **not** authorize a default flip or mark the production goal complete.
- Defaults remain `--codegen=legacy`, `--ir-lower=direct`, and `--backend=cpp`.
- Processor changed: **No**.
- Ship-ready: **No**.
- Admitted: **No** (measurement only).

# 中文

## 摘要

- 在 #304 之后的 `master` 上重新运行了仓库内 IR 遗留项清单；实测提交为 `580ec948f3284af1ca99a073ba656017172d223e`。
- 此 leftover-docs 提交位于 extra-local 六参数 `NEW` #304、独立 float `NEW` #303 和 extra-local 五参数 `NEW` #302 之后。
- 本次重新测量取代基于 `d070653d8176ef5bab338f76eb2992c1bffbbfbf`（#299 之后）的 #301。
- 仅做测量：未修改编译器/运行时处理器、测试或默认值。

## 精确连接后的汇总

| 语料集 | 清单方法数 | IR | 回退到 legacy | 构造器留在 Java | 缺失 |
| --- | ---: | ---: | ---: | ---: | ---: |
| `ClassicTest` | 108 | 108 | 0 | 0 | 0 |
| `jdk17` | 82 | 82 | 0 | 0 | 0 |
| `jdk21` | 47 | 47 | 0 | 0 | 0 |
| `jdk25` | 21 | 21 | 0 | 0 | 0 |

实测遗留项：**0**。

## 命令

```bash
python3 docs/measurement/ir-leftover-inventory/measure.py --javac-25 /tmp/temurin25/bin/javac
```

## 边界说明

- 实测遗留项为零**不代表 JDK 支持徽章**、**不代表覆盖完整**，也**不授权切换默认值**或将生产目标标记为完成。
- 默认值保持 `--codegen=legacy`、`--ir-lower=direct`、`--backend=cpp`。
- 处理器变更：**否**。
- 可发布：**否**。
- 已准入：**否**（仅测量）。
