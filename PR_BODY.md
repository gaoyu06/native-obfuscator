## English

### Summary

- Remeasures the in-tree IR leftover inventory on post-#272 master at `e1b07a860c3e50ce2ee48c605af069a37a195388`.
- This is **measurement only**. It supersedes #270 on `bca314540a4ded96feb2a670d4fac35651bd70fd`; #264 remains the earlier post-#263 snapshot at `c0304febc41f1c665fb42ce0947f38bf0c29947a`, and #207 remains the earlier post-#206 snapshot at `42e52c0076e4a0d3d69be81e47de3c916ca4919e`.
- Zero leftovers in these checked-in fixtures is **not coverage-complete**, does **not** complete the written production goal, and does **not** authorize changing the `--codegen` default.

### Exact joined totals

```text
corpus	inventory	IR	legacy-fallback	constructor-left-java
ClassicTest	108	108	0	0
jdk17	82	82	0	0
jdk21	47	47	0	0
jdk25	21	21	0	0
```

### Interpretation rules

- Inventory = `javap -p -s -c` methods with a `Code:` body, joined by exact `class + method + descriptor`.
- `// IR codegen:` = IR; `falling back to legacy for this method` = legacy-fallback; `leaving constructor bytecode unchanged` = constructor-left-java.
- This is admission measurement of checked-in fixtures with explicit `--codegen=ir`.
- Not a JDK 17/21/25 support badge. Java 8 is the only version ever called fully supported.

### Readiness and defaults

- Measurement only: **Yes**
- Ship-ready: **No**
- Admitted: **N/A**
- Defaults unchanged: `--codegen=legacy`, `--ir-lower=direct`, `--backend=cpp`

### Measurement command

```bash
python3 docs/measurement/ir-leftover-inventory/measure.py --javac-25 /tmp/temurin25/bin/javac
```

The helper built `:obfuscator:shadowJar`, compiled all present checked-in fixtures, and generated `docs/benchmarks/ir-leftover-inventory.md`. No historical fixture branch was fetched, and `TestStringConcatFactory.j` was not assembled.

## 中文

### 摘要

- 在合入 #272 后的 master（`e1b07a860c3e50ce2ee48c605af069a37a195388`）上重新测量仓库内 IR 遗留项清单。
- 本次变更**仅为测量**。它取代 `bca314540a4ded96feb2a670d4fac35651bd70fd` 上的 #270 测量；#264 仍是 `c0304febc41f1c665fb42ce0947f38bf0c29947a` 上合入 #263 后的较早快照，#207 仍是 `42e52c0076e4a0d3d69be81e47de3c916ca4919e` 上合入 #206 后的较早快照。
- 这些已检入 fixture 中的遗留项为零，**不代表覆盖完整**，不代表书面的生产目标已经完成，也不能作为修改 `--codegen` 默认值的依据。

### 精确关联汇总

```text
corpus	inventory	IR	legacy-fallback	constructor-left-java
ClassicTest	108	108	0	0
jdk17	82	82	0	0
jdk21	47	47	0	0
jdk25	21	21	0	0
```

### 解释规则

- Inventory 指 `javap -p -s -c` 输出中带有 `Code:` 方法体的方法，并按精确的 `class + method + descriptor` 进行关联。
- `// IR codegen:` 表示 IR；`falling back to legacy for this method` 表示 legacy-fallback；`leaving constructor bytecode unchanged` 表示 constructor-left-java。
- 这是对已检入 fixture 使用显式 `--codegen=ir` 进行的准入测量。
- 这不是 JDK 17/21/25 支持徽章。Java 8 是唯一曾被称为完全支持的版本。

### 就绪状态与默认值

- 仅测量：**是**
- 可发布：**否**
- 准入：**不适用**
- 默认值不变：`--codegen=legacy`、`--ir-lower=direct`、`--backend=cpp`

### 测量命令

```bash
python3 docs/measurement/ir-leftover-inventory/measure.py --javac-25 /tmp/temurin25/bin/javac
```

辅助脚本构建了 `:obfuscator:shadowJar`，编译了所有现有的已检入 fixture，并生成 `docs/benchmarks/ir-leftover-inventory.md`。没有获取历史 fixture 分支，也没有组装 `TestStringConcatFactory.j`。
