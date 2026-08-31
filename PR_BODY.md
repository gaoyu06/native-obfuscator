## English

Remeasures the joined in-tree IR leftover inventory on the leftover-docs #399 tree. This is measurement-only and changes no compiler/runtime source, tests, or CLI defaults.

- Measurement SHA: `454af2687204a17204d2a60c511d019582a14774`
- Merge-base with `origin/master` at measurement: `454af2687204a17204d2a60c511d019582a14774`
- Supersedes #399's measurement of leftover-docs #397 at `9675616` (`9675616f6d2e84442d7465232c2705dfd152c11d`)
- Latest published compiler parent XML at measurement time: **#398 (689)** (`IrCompilerTest` 682 + `CodegenModeTest` 7)

### Joined corpus totals

| Corpus | Inventory | IR | Legacy fallback | Constructor left in Java | Missing |
| --- | ---: | ---: | ---: | ---: | ---: |
| `ClassicTest` | 108 | 108 | 0 | 0 | 0 |
| `jdk17` | 82 | 82 | 0 | 0 | 0 |
| `jdk21` | 47 | 47 | 0 | 0 | 0 |
| `jdk25` | 21 | 21 | 0 | 0 | 0 |

- Processor changed: **No**
- Admitted: **No**
- Ship-ready: **No**
- Zero leftovers is not coverage-complete, not a JDK support badge, and does not authorize a default flip.
- Defaults remain `--codegen=legacy`, `--ir-lower=direct`, and `--backend=cpp`.

Measured with:

```bash
python3 docs/measurement/ir-leftover-inventory/measure.py --javac-25 /tmp/temurin25/bin/javac
```

## 中文

在 leftover-docs #399 树上重新测量仓库内 IR leftover 的联合清单。本次仅做测量，不修改编译器/运行时源码、测试或 CLI 默认值。

- 测量 SHA：`454af2687204a17204d2a60c511d019582a14774`
- 测量时与 `origin/master` 的 merge-base：`454af2687204a17204d2a60c511d019582a14774`
- 本次结果取代 #399 对 leftover-docs #397 `9675616`（`9675616f6d2e84442d7465232c2705dfd152c11d`）的测量
- 测量时最新发布的编译器父级 XML：**#398 (689)**（`IrCompilerTest` 682 + `CodegenModeTest` 7）

联合语料总数见上表。

- Processor changed：**No**
- Admitted：**No**
- Ship-ready：**No**
- leftover 为零不代表覆盖完整，不是 JDK 支持徽章，也不授权切换默认值。
- 默认值仍为 `--codegen=legacy`、`--ir-lower=direct` 和 `--backend=cpp`。
