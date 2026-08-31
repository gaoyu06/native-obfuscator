# docs: remeasure IR leftovers after leftover-docs #427

## English

### Summary

- Remeasures the in-tree IR leftover inventory on leftover-docs [#427](https://github.com/gaoyu06/native-obfuscator/pull/427), SHA `96de67dd` (`96de67dd3473d45aa818064d17cc0b3034466e04`).
- Supersedes [#426](https://github.com/gaoyu06/native-obfuscator/pull/426)'s measurement of leftover-docs [#425](https://github.com/gaoyu06/native-obfuscator/pull/425) at `a54d9a28`.
- Latest compiler parent XML at measurement time is **#427 (731)**: `IrCompilerTest` 724 + `CodegenModeTest` 7. This measurement adds no compiler XML.
- Processor changed: **No**. Admitted: **No**. Ship-ready: **No**.
- Defaults are unchanged: `--codegen=legacy`, `--ir-lower=direct`, `--backend=cpp`.
- Zero measured leftovers is not coverage-complete, not a JDK support badge, and does not authorize a default flip.

### Measurement

```bash
python3 docs/measurement/ir-leftover-inventory/measure.py --javac-25 /tmp/temurin25/bin/javac
```

## 中文

### 摘要

- 在 leftover-docs [#427](https://github.com/gaoyu06/native-obfuscator/pull/427) 上重新测量仓库内 IR 剩余项清单；测量 SHA 为 `96de67dd`（`96de67dd3473d45aa818064d17cc0b3034466e04`）。
- 本次测量取代 [#426](https://github.com/gaoyu06/native-obfuscator/pull/426) 对 leftover-docs [#425](https://github.com/gaoyu06/native-obfuscator/pull/425)（`a54d9a28`）的测量。
- 测量时最新编译器父项 XML 为 **#427（731）**：`IrCompilerTest` 724 + `CodegenModeTest` 7。本次测量不增加编译器 XML。
- 处理器有改动：**否**。已准入：**否**。可发布：**否**。
- 默认值不变：`--codegen=legacy`、`--ir-lower=direct`、`--backend=cpp`。
- 测得零剩余项不等于覆盖完整，不是 JDK 支持标志，也不授权切换默认实现。

## Joined totals / 汇总

| Corpus | Inventory / 清单 | IR | Fallback / 回退 | Constructor left / 构造器保留 | Missing / 缺失 |
| --- | ---: | ---: | ---: | ---: | ---: |
| `ClassicTest` | 108 | 108 | 0 | 0 | 0 |
| `jdk17` | 82 | 82 | 0 | 0 | 0 |
| `jdk21` | 47 | 47 | 0 | 0 | 0 |
| `jdk25` | 21 | 21 | 0 | 0 | 0 |
