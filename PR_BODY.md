# EN

## Suggested title

`docs: remeasure IR leftovers on post-#326 master`

## Summary

- Remeasures the checked-in fixture inventory with explicit `--codegen=ir` on post-#326 master.
- Measured SHA: `0894b1609410b98b91db0d69a34adfa99cc4b090`.
- Merge-base with `origin/master`: `0894b1609410b98b91db0d69a34adfa99cc4b090`.
- This post-#326 measurement supersedes #325 on `4f8612a6e75911c7cbd91bad5d424763f8b5b460` (post-#324). #323 remains the earlier post-#322 snapshot; #321 remains the earlier post-#320 snapshot; #318 remains the earlier post-#317 snapshot.
- Latest compiler parent XML remains **#326 (578)**; this measurement adds no compiler XML.

| Corpus | Inventory | IR | Legacy fallback | Constructor left in Java | Missing |
| --- | ---: | ---: | ---: | ---: | ---: |
| ClassicTest | 108 | 108 | 0 | 0 | 0 |
| jdk17 | 82 | 82 | 0 | 0 | 0 |
| jdk21 | 47 | 47 | 0 | 0 | 0 |
| jdk25 | 21 | 21 | 0 | 0 | 0 |

- Processor changed: **No**
- Ship-ready: **No**
- Admitted: **No** (measurement only)

Command actually run:

```bash
python3 docs/measurement/ir-leftover-inventory/measure.py --javac-25 /tmp/temurin25/bin/javac
```

This is measurement only: it is not a JDK support badge, not coverage-complete, and not a behavioral/native E2E claim. Zero measured leftovers does not complete the production goal or authorize changing `--codegen=legacy`, `--ir-lower=direct`, or `--backend=cpp`.

# 中文

## 建议标题

`docs: remeasure IR leftovers on post-#326 master`

## 摘要

- 在 post-#326 master 上使用显式 `--codegen=ir`，重新测量仓库内已签入测试夹具的清单。
- 实测 SHA：`0894b1609410b98b91db0d69a34adfa99cc4b090`。
- 与 `origin/master` 的 merge-base：`0894b1609410b98b91db0d69a34adfa99cc4b090`。
- 本次 post-#326 测量取代基于 `4f8612a6e75911c7cbd91bad5d424763f8b5b460`（post-#324）的 #325。#323 仍是较早的 post-#322 快照；#321 仍是较早的 post-#320 快照；#318 仍是较早的 post-#317 快照。
- 最新编译器父 XML 仍为 **#326 (578)**；本次测量没有新增编译器 XML。

| 语料 | 清单方法数 | IR | 回退到 legacy | 构造器保留在 Java | 缺失 |
| --- | ---: | ---: | ---: | ---: | ---: |
| ClassicTest | 108 | 108 | 0 | 0 | 0 |
| jdk17 | 82 | 82 | 0 | 0 | 0 |
| jdk21 | 47 | 47 | 0 | 0 | 0 |
| jdk25 | 21 | 21 | 0 | 0 | 0 |

- Processor 是否变更：**否**
- 可直接发布：**否**
- 已接纳：**否**（仅测量）

实际运行的命令：

```bash
python3 docs/measurement/ir-leftover-inventory/measure.py --javac-25 /tmp/temurin25/bin/javac
```

本次仅进行测量：它不是 JDK 支持标志，不代表覆盖完整，也不是行为/native E2E 结论。实测遗留项为零并不表示生产目标已完成，也不授权修改 `--codegen=legacy`、`--ir-lower=direct` 或 `--backend=cpp` 默认值。
