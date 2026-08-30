# Post-#283 IR leftover inventory / post-#283 IR 剩余项清单

## English

This is measurement only. It remeasures the post-#283 `origin/master` compiler at `164fed155f865d5610b4353d9d54667a9cf1c3b0`; no compiler, runtime, test, or CLI-default behavior changes.

Joined totals (verbatim helper output):

```text
corpus	inventory	IR	legacy-fallback	constructor-left-java
ClassicTest	108	108	0	0
jdk17	82	82	0	0
jdk21	47	47	0	0
jdk25	21	21	0	0
```

Zero leftovers is not a JDK support badge and does not authorize a default flip. Defaults stay `--codegen=legacy`, `--ir-lower=direct`, and `--backend=cpp`.

Ship-ready: **No**

## 中文

本次变更仅用于测量。它重新测量了 post-#283 的 `origin/master` 编译器，测量提交为 `164fed155f865d5610b4353d9d54667a9cf1c3b0`；不修改编译器、运行时、测试或 CLI 默认行为。

合并总计（逐字复制自测量辅助程序输出）：

```text
corpus	inventory	IR	legacy-fallback	constructor-left-java
ClassicTest	108	108	0	0
jdk17	82	82	0	0
jdk21	47	47	0	0
jdk25	21	21	0	0
```

剩余项为零并不代表获得 JDK 支持认证，也不授权切换默认值。默认值保持为 `--codegen=legacy`、`--ir-lower=direct` 和 `--backend=cpp`。

可发布：**否（Ship-ready: No）**
