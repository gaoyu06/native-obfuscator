# Post-#336 IR leftover inventory remeasurement / #336 后 IR 遗留清单重新测量

## English

This measurement-only update remeasures the in-tree IR leftover inventory on the post-[#336](https://github.com/gaoyu06/native-obfuscator/pull/336) leftover-docs tree, after an extra-local `int` value was admitted as all four `Insets` `NEW` arguments.

- Measured SHA: `d5faac91be7829915aad7a5ac25fbb043b70297e`
- Merge-base with `origin/master`: `d5faac91be7829915aad7a5ac25fbb043b70297e`
- Processor changed: **No**
- Ship-ready: **No**
- Admitted: **No** (measurement only)
- Latest compiler parent XML: **[#336](https://github.com/gaoyu06/native-obfuscator/pull/336) (593)**
- Compiler XML added by this update: **No**

### Joined corpus totals

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

The focused Gradle gate was skipped because this update is measurement-only. The measurement helper's own `:obfuscator:shadowJar` build completed successfully.

This remeasurement supersedes [#335](https://github.com/gaoyu06/native-obfuscator/pull/335) on `c99c0f942f338e0c53b1d7cbff3df53600da4742` (post-#334). [#333](https://github.com/gaoyu06/native-obfuscator/pull/333), [#331](https://github.com/gaoyu06/native-obfuscator/pull/331), and [#329](https://github.com/gaoyu06/native-obfuscator/pull/329) remain the earlier post-#332, post-#330, and post-#328 snapshots.

Zero leftovers is not coverage-complete, not a JDK support badge, and not authorization to flip `--codegen` off `legacy`. Defaults remain `--codegen=legacy`, `--ir-lower=direct`, and `--backend=cpp`. This update does not mark the production goal complete.

## 中文

本次仅测量的更新在 [#336](https://github.com/gaoyu06/native-obfuscator/pull/336) 之后的 leftover-docs 树上重新测量仓内 IR 遗留清单；该树已允许将一个额外局部 `int` 值作为全部四个 `Insets` `NEW` 参数。

- 测量 SHA：`d5faac91be7829915aad7a5ac25fbb043b70297e`
- 与 `origin/master` 的合并基点：`d5faac91be7829915aad7a5ac25fbb043b70297e`
- Processor 是否变更：**否**
- 可发布：**否**
- 已准入：**否**（仅测量）
- 最新编译器父 XML：**[#336](https://github.com/gaoyu06/native-obfuscator/pull/336) (593)**
- 本次更新新增编译器 XML：**否**

### 合并后的语料总计

| 语料 | 清单方法数 | IR | 回退到 legacy | 构造器保留在 Java | 缺失 |
| --- | ---: | ---: | ---: | ---: | ---: |
| `ClassicTest` | 108 | 108 | 0 | 0 | 0 |
| `jdk17` | 82 | 82 | 0 | 0 | 0 |
| `jdk21` | 47 | 47 | 0 | 0 | 0 |
| `jdk25` | 21 | 21 | 0 | 0 | 0 |

实际运行的命令：

```bash
python3 docs/measurement/ir-leftover-inventory/measure.py --javac-25 /tmp/temurin25/bin/javac
```

由于本次更新仅做测量，未运行单独的聚焦 Gradle 门禁；测量脚本自身执行的 `:obfuscator:shadowJar` 构建已成功完成。

本次重新测量取代位于 `c99c0f942f338e0c53b1d7cbff3df53600da4742`（#334 之后）的 [#335](https://github.com/gaoyu06/native-obfuscator/pull/335)。[#333](https://github.com/gaoyu06/native-obfuscator/pull/333)、[#331](https://github.com/gaoyu06/native-obfuscator/pull/331) 和 [#329](https://github.com/gaoyu06/native-obfuscator/pull/329) 分别继续作为 #332、#330 和 #328 之后的较早快照。

遗留项为零并不代表覆盖完整，不是 JDK 支持标志，也不授权将 `--codegen` 默认值从 `legacy` 切换出去。默认值仍为 `--codegen=legacy`、`--ir-lower=direct` 和 `--backend=cpp`。本次更新不将生产目标标记为完成。
