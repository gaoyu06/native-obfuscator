# English

## (a) Measurement-only scope

This remeasures IR admission for checked-in fixtures with explicit `--codegen=ir`. It changes only the generated inventory report and this handoff file; it does not change compiler/runtime source or CLI defaults.

This is **not a JDK support badge**, **not coverage-complete**, and **not a behavioral/native E2E claim**. Zero measured leftovers does **not** authorize changing the `--codegen` default.

This remeasurement supersedes #199, the post-#198 snapshot at `4214d7498c4b902d1dbf54f0bc14a3be16649b89`. #191 remains the earlier post-#190 snapshot, and #181 remains the earlier post-#180 snapshot. #200–#206 landed after #199, covering catch tables, relocated prefix handlers, receiver-alias families, and identical-copy extras. The joined fixture totals are unchanged from #199, so this report does not claim those landings changed fixture leftovers.

## (b) Ship-ready?

**No.** This is an admission inventory only and does not mark the production goal complete.

## (c) Gate and measured evidence

No focused compiler Gradle gate was run for this inventory-only change. The helper rebuilt `:obfuscator:shadowJar`; the inventory gate is the generated report's measured SHA and joined totals.

- Measured SHA: `42e52c0076e4a0d3d69be81e47de3c916ca4919e`
- Merge-base with `origin/master`: `42e52c0076e4a0d3d69be81e47de3c916ca4919e`

| Corpus | Inventory | IR | Leftover (`legacy-fallback`) | Constructor left in Java | Missing |
| --- | ---: | ---: | ---: | ---: | ---: |
| `ClassicTest` | 108 | 108 | 0 | 0 | 0 |
| `jdk17` | 82 | 82 | 0 | 0 | 0 |
| `jdk21` | 47 | 47 | 0 | 0 | 0 |
| `jdk25` | 21 | 21 | 0 | 0 | 0 |

The measured fixture inventory has zero leftovers.

## (d) Defaults

Defaults remain `--codegen=legacy`, `--ir-lower=direct`, and `--backend=cpp`.

# 中文

## (a) 仅测量范围

本次仅使用显式 `--codegen=ir`，重新测量仓库内已检入测试样例的 IR 准入情况。改动仅包含生成的清单报告和本交接文件，不修改编译器/运行时源码或 CLI 默认值。

本测量**不是 JDK 支持标志**，**不代表覆盖完整**，也**不是行为或原生端到端验证**。测得零遗留项**不构成**修改 `--codegen` 默认值的依据。

本次重测取代 #199（#198 之后、提交 `4214d7498c4b902d1dbf54f0bc14a3be16649b89` 的快照）。#191 仍是 #190 之后的早期快照，#181 仍是 #180 之后的早期快照。#200–#206 在 #199 之后落地，涉及 catch 表、重定位的前缀处理器、接收者别名系列和相同副本额外值。合并后的样例总数与 #199 相同，因此本报告不宣称这些落地改动了样例遗留项。

## (b) 可发布？

**否。** 本次仅为准入清单测量，不表示生产目标已经完成。

## (c) 门禁与实测证据

本次清单改动未运行聚焦的编译器 Gradle 门禁。测量脚本重新构建了 `:obfuscator:shadowJar`；清单门禁以生成报告中的实测 SHA 和合并总数为准。

- 实测 SHA：`42e52c0076e4a0d3d69be81e47de3c916ca4919e`
- 与 `origin/master` 的合并基点：`42e52c0076e4a0d3d69be81e47de3c916ca4919e`

| 语料 | 清单总数 | IR | 遗留（`legacy-fallback`） | 构造器保留在 Java | 缺失 |
| --- | ---: | ---: | ---: | ---: | ---: |
| `ClassicTest` | 108 | 108 | 0 | 0 | 0 |
| `jdk17` | 82 | 82 | 0 | 0 | 0 |
| `jdk21` | 47 | 47 | 0 | 0 | 0 |
| `jdk25` | 21 | 21 | 0 | 0 | 0 |

实测样例清单中的遗留项为零。

## (d) 默认值

默认值仍为 `--codegen=legacy`、`--ir-lower=direct` 和 `--backend=cpp`。
