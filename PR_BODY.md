## English

This is a measurement-only post-#278 IR leftover-inventory refresh at compiler SHA `27414d011fc69e769ea48a3e1a40f007d172b4a5`. It changes no compiler, runtime, tests, CLI behavior, or defaults.

### Joined totals (verbatim helper output)

```text
corpus	inventory	IR	legacy-fallback	constructor-left-java
ClassicTest	108	108	0	0
jdk17	82	82	0	0
jdk21	47	47	0	0
jdk25	21	21	0	0
```

Zero measured leftovers is not a JDK support badge, is not coverage-complete, and does not authorize a default flip. Defaults remain `--codegen=legacy`, `--ir-lower=direct`, and `--backend=cpp`.

Ship-ready: **No**

## 中文

这是一次仅测量的 post-#278 IR leftover inventory 更新，测量的编译器 SHA 为 `27414d011fc69e769ea48a3e1a40f007d172b4a5`。本次不修改编译器、运行时、测试、CLI 行为或默认值。

上方 Joined totals 区块逐字复制自测量 helper 的输出。

测得的 leftover 为零不代表获得 JDK 支持标志，不代表覆盖完整，也不授权切换默认值。默认值仍为 `--codegen=legacy`、`--ir-lower=direct` 和 `--backend=cpp`。

Ship-ready（可发布）：**No**
