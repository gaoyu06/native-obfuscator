## English

### Summary

- Remeasures the checked-in IR fixture inventory on the post-[#360](https://github.com/gaoyu06/native-obfuscator/pull/360) leftover-docs tree at `7feb4c09ea91085b68f3e9e7b44a1ce9d0d29aa0`.
- This tree follows admission of an extra-local `int` as the third five-argument `GregorianCalendar` `NEW` argument.
- Inventory methods are `javap -p -s -c` methods with a `Code:` body, joined to output by exact `class + method + descriptor`.
- Latest compiler parent XML remains **[#360](https://github.com/gaoyu06/native-obfuscator/pull/360) (629)** (`IrCompilerTest` 622 + `CodegenModeTest` 7). This measurement adds no compiler XML.
- Supersedes [#359](https://github.com/gaoyu06/native-obfuscator/pull/359) on `49b2e8ca1edfa3990e202f81f1a15ddafb5f0920` (post-#358).

### Joined corpus totals

| Corpus | Inventory | IR | Legacy fallback | Constructor left in Java | Missing |
| --- | ---: | ---: | ---: | ---: | ---: |
| `ClassicTest` | 108 | 108 | 0 | 0 | 0 |
| `jdk17` | 82 | 82 | 0 | 0 | 0 |
| `jdk21` | 47 | 47 | 0 | 0 | 0 |
| `jdk25` | 21 | 21 | 0 | 0 | 0 |

### Caveats

- Measurement only: not coverage-complete, not a JDK support badge, and not a behavioral/JNI E2E claim.
- Zero measured leftovers does not complete the production goal or authorize a default flip.
- No compiler/runtime source or defaults changed. `--codegen=legacy`, `--ir-lower=direct`, and `--backend=cpp` remain the defaults.
- Processor changed: **No**. Ship-ready: **No**. Admitted: **No**.

## 中文

### 摘要

- 在 `7feb4c09ea91085b68f3e9e7b44a1ce9d0d29aa0` 的 post-[#360](https://github.com/gaoyu06/native-obfuscator/pull/360) leftover-docs 树上重新测量已检入的 IR fixture inventory。
- 该树位于“把一个 extra-local `int` 作为五参数 `GregorianCalendar` `NEW` 的第三个参数纳入 IR”之后。
- Inventory 统计 `javap -p -s -c` 中带 `Code:` body 的方法，并按精确的 `class + method + descriptor` 与输出连接。
- 最新 compiler parent XML 仍为 **[#360](https://github.com/gaoyu06/native-obfuscator/pull/360) (629)**（`IrCompilerTest` 622 + `CodegenModeTest` 7）；本次测量不增加 compiler XML。
- 本次结果取代 `49b2e8ca1edfa3990e202f81f1a15ddafb5f0920`（post-#358）上的 [#359](https://github.com/gaoyu06/native-obfuscator/pull/359)。

### Joined corpus 总计

| Corpus | Inventory | IR | Legacy fallback | Constructor left in Java | Missing |
| --- | ---: | ---: | ---: | ---: | ---: |
| `ClassicTest` | 108 | 108 | 0 | 0 | 0 |
| `jdk17` | 82 | 82 | 0 | 0 | 0 |
| `jdk21` | 47 | 47 | 0 | 0 | 0 |
| `jdk25` | 21 | 21 | 0 | 0 | 0 |

### 限制说明

- 这只是测量：不代表覆盖完整，不是 JDK 支持标志，也不是行为/JNI E2E 结论。
- 测得零 leftover 不代表生产目标完成，也不授权切换默认值。
- 未修改 compiler/runtime 源码或默认值；`--codegen=legacy`、`--ir-lower=direct` 和 `--backend=cpp` 仍为默认值。
- Processor changed：**No**。Ship-ready：**No**。Admitted：**No**。
