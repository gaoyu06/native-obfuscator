# Post-#281 IR leftover inventory / post-#281 IR 遗留项清单

## English

- Measurement only. This changes only the generated inventory report and PR body.
- Measured compiler SHA: `c9e4d6e7d0fc3da9fd2e6071006762e16cead65f`.
- Zero leftovers is not a JDK support badge and does not authorize a default flip.
- Defaults stay `--codegen=legacy`, `--ir-lower=direct`, `--backend=cpp`.
- Ship-ready: No.

## 中文

- 仅进行测量。本次变更只包含生成的遗留项清单报告和 PR 正文。
- 测量的编译器 SHA：`c9e4d6e7d0fc3da9fd2e6071006762e16cead65f`。
- 遗留项为零并不代表已获得 JDK 支持标志，也不授权切换默认配置。
- 默认配置保持为 `--codegen=legacy`、`--ir-lower=direct`、`--backend=cpp`。
- 可发布：否（Ship-ready: No）。

## Joined totals / 汇总总数

```text
corpus	inventory	IR	legacy-fallback	constructor-left-java
ClassicTest	108	108	0	0
jdk17	82	82	0	0
jdk21	47	47	0	0
jdk25	21	21	0	0
```
