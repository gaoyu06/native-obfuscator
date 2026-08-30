# Post-#338 IR leftover inventory / #338 后 IR 遗留清单

## English

This measurement refreshes the in-tree IR leftover inventory on the post-[#338](https://github.com/gaoyu06/native-obfuscator/pull/338) leftover-docs tree, after extra-local `int` values were admitted as the first and second `Insets` `NEW` arguments.

- Measured SHA: `f7446f5a6bbd3bb316897896a7f8cfee0532d2d7`
- Merge-base with `origin/master`: `f7446f5a6bbd3bb316897896a7f8cfee0532d2d7`
- Processor changed: **No**
- Ship-ready: **No**
- Admitted: **No** (measurement only)
- Latest compiler parent XML: **[#338](https://github.com/gaoyu06/native-obfuscator/pull/338) (596)**
- Compiler XML added by this measurement: **No**

### Joined corpus totals / 合并语料总计

| Corpus | Inventory | IR | Legacy fallback | Constructor left in Java | Missing |
| --- | ---: | ---: | ---: | ---: | ---: |
| `ClassicTest` | 108 | 108 | 0 | 0 | 0 |
| `jdk17` | 82 | 82 | 0 | 0 | 0 |
| `jdk21` | 47 | 47 | 0 | 0 | 0 |
| `jdk25` | 21 | 21 | 0 | 0 | 0 |

Command actually run:

```bash
python3 docs/measurement/ir-leftover-inventory/measure.py --javac-25 /tmp/temurin25/bin/javac
```

The focused Gradle gate was skipped because this is measurement-only. The measurement helper's own `:obfuscator:shadowJar` build completed successfully.

This remeasurement supersedes [#337](https://github.com/gaoyu06/native-obfuscator/pull/337) on `d5faac91be7829915aad7a5ac25fbb043b70297e` (post-#336). [#335](https://github.com/gaoyu06/native-obfuscator/pull/335), [#333](https://github.com/gaoyu06/native-obfuscator/pull/333), and [#331](https://github.com/gaoyu06/native-obfuscator/pull/331) remain the earlier post-#334, post-#332, and post-#330 snapshots.

Zero measured leftovers is not coverage-complete, not a JDK support badge, not production-goal completion, and not authorization to change defaults. Defaults remain `--codegen=legacy`, `--ir-lower=direct`, and `--backend=cpp`.

## 中文

本次测量在 [#338](https://github.com/gaoyu06/native-obfuscator/pull/338) 合入后的 leftover-docs 树上更新仓库内 IR 遗留清单；#338 接纳了把额外局部 `int` 值用作 `Insets` `NEW` 的第一个和第二个参数。

- 测量 SHA：`f7446f5a6bbd3bb316897896a7f8cfee0532d2d7`
- 与 `origin/master` 的 merge-base：`f7446f5a6bbd3bb316897896a7f8cfee0532d2d7`
- Processor 是否变更：**No**
- Ship-ready：**No**
- Admitted：**No**（仅测量）
- 最新编译器父 XML：**[#338](https://github.com/gaoyu06/native-obfuscator/pull/338) (596)**
- 本次测量新增编译器 XML：**No**

实际运行的命令：

```bash
python3 docs/measurement/ir-leftover-inventory/measure.py --javac-25 /tmp/temurin25/bin/javac
```

由于本次仅进行测量，未运行聚焦 Gradle gate；测量脚本自身执行的 `:obfuscator:shadowJar` 构建已成功完成。

本次重新测量取代 [#337](https://github.com/gaoyu06/native-obfuscator/pull/337) 在 `d5faac91be7829915aad7a5ac25fbb043b70297e`（post-#336）上的结果。[#335](https://github.com/gaoyu06/native-obfuscator/pull/335)、[#333](https://github.com/gaoyu06/native-obfuscator/pull/333) 和 [#331](https://github.com/gaoyu06/native-obfuscator/pull/331) 分别保留为更早的 post-#334、post-#332 和 post-#330 快照。

测得零遗留并不表示覆盖完整，不是 JDK 支持徽章，不表示生产目标完成，也不授权修改默认选项。默认值仍为 `--codegen=legacy`、`--ir-lower=direct` 和 `--backend=cpp`。
