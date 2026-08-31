# Summary / 摘要

## English

- Remeasure the joined IR leftover inventory on the post-[#370](https://github.com/gaoyu06/native-obfuscator/pull/370) leftover-docs tree.
- Both the measured compiler base and measurement commit are `28f3f15c4e1131cc0d29ecee64aff0eab286b312`, after admitting an extra-local `int` as the **first and third** five-argument `GregorianCalendar` `NEW` arguments.
- This snapshot supersedes [#369](https://github.com/gaoyu06/native-obfuscator/pull/369) on `215397a01fc67f7077bc0814e830c4e7197b6c72` (post-#368).
- Latest compiler parent XML remains **#370 (644)**: `IrCompilerTest` 637 + `CodegenModeTest` 7. This measurement adds no compiler XML.
- Measurement only. Processor changed: **No**. Admitted: **No**. Ship-ready: **No**.

## 中文

- 在 [#370](https://github.com/gaoyu06/native-obfuscator/pull/370) 之后的 leftover-docs 树上重新测量合并后的 IR 剩余项清单。
- 被测编译器基线与测量提交均为 `28f3f15c4e1131cc0d29ecee64aff0eab286b312`；该树已接纳五参数 `GregorianCalendar` `NEW` 的**第一和第三**个参数使用额外局部 `int`。
- 本快照取代基于 `215397a01fc67f7077bc0814e830c4e7197b6c72`（post-#368）的 [#369](https://github.com/gaoyu06/native-obfuscator/pull/369)。
- 最新编译器父级 XML 仍为 **#370（644）**：`IrCompilerTest` 637 + `CodegenModeTest` 7。本次测量不增加编译器 XML。
- 仅测量。处理器变更：**否**。接纳：**否**。可发布：**否**。

## Joined corpus / 合并语料

| Corpus / 语料库 | Inventory / 总数 | IR | Legacy fallback / 旧后端回退 | Constructor left in Java / 构造器保留 Java | Missing / 缺失 |
| --- | ---: | ---: | ---: | ---: | ---: |
| `ClassicTest` | 108 | 108 | 0 | 0 | 0 |
| `jdk17` | 82 | 82 | 0 | 0 | 0 |
| `jdk21` | 47 | 47 | 0 | 0 | 0 |
| `jdk25` | 21 | 21 | 0 | 0 | 0 |

## Measurement / 测量

```bash
python3 docs/measurement/ir-leftover-inventory/measure.py --javac-25 /tmp/temurin25/bin/javac
```

The defaults remain `--codegen=legacy`, `--ir-lower=direct`, and `--backend=cpp`. Zero measured leftovers is not coverage-complete, is not a JDK support badge, and does not authorize a default flip.

默认值仍为 `--codegen=legacy`、`--ir-lower=direct` 和 `--backend=cpp`。测得零剩余项不代表覆盖完整，不是 JDK 支持标志，也不授权切换默认值。
