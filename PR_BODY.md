# Summary / 摘要

## English

- Remeasure the joined IR leftover inventory on the exact post-[#377](https://github.com/gaoyu06/native-obfuscator/pull/377) leftover-docs commit `de3f430bd01f225bb293dccd2e7898fcf6254f51`; the merge-base with `origin/master` and the measured commit are both that SHA.
- Supersede the post-[#376](https://github.com/gaoyu06/native-obfuscator/pull/376) snapshot on `1b48c8510395a11d1baf972d6061cab9dc2aea40` (post-#375).
- The latest compiler parent XML is [#377](https://github.com/gaoyu06/native-obfuscator/pull/377) (656): `IrCompilerTest` 649 + `CodegenModeTest` 7. This measurement adds no compiler XML.
- Processor changed: **No**. Admitted: **No**. Ship-ready: **No**.

## 中文

- 在 post-[#377](https://github.com/gaoyu06/native-obfuscator/pull/377) 的精确 leftover-docs 提交 `de3f430bd01f225bb293dccd2e7898fcf6254f51` 上重新测量 joined IR leftover inventory；与 `origin/master` 的 merge-base 和被测提交均为该 SHA。
- 本快照取代 post-[#376](https://github.com/gaoyu06/native-obfuscator/pull/376) 的 `1b48c8510395a11d1baf972d6061cab9dc2aea40`（post-#375）快照。
- 最新 compiler parent XML 为 [#377](https://github.com/gaoyu06/native-obfuscator/pull/377)（656）：`IrCompilerTest` 649 + `CodegenModeTest` 7。本次测量不增加 compiler XML。
- Processor changed：**No**。Admitted：**No**。Ship-ready：**No**。

## Joined totals / 汇总

| Corpus | Inventory | IR | Legacy fallback | Constructor left in Java | Missing |
| --- | ---: | ---: | ---: | ---: | ---: |
| `ClassicTest` | 108 | 108 | 0 | 0 | 0 |
| `jdk17` | 82 | 82 | 0 | 0 | 0 |
| `jdk21` | 47 | 47 | 0 | 0 | 0 |
| `jdk25` | 21 | 21 | 0 | 0 | 0 |

## Boundaries / 边界

Zero measured leftovers is not coverage-complete, not a JDK support badge, and does not authorize a default flip. Defaults remain `--codegen=legacy`, `--ir-lower=direct`, and `--backend=cpp`.

测得的零 leftover 不代表覆盖完整，不是 JDK 支持标志，也不授权切换默认值。默认值仍为 `--codegen=legacy`、`--ir-lower=direct` 和 `--backend=cpp`。

## Verification / 验证

```bash
python3 docs/measurement/ir-leftover-inventory/measure.py --javac-25 /tmp/temurin25/bin/javac
```
