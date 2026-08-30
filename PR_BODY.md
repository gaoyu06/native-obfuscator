# English

## Scope

- Increment: **measurement only**
- Ship-ready: **No**
- Admitted: **N/A**
- Measured compiler / merge-base: `bca314540a4ded96feb2a670d4fac35651bd70fd` (post-#269 master)
- This remeasurement supersedes #264 on `c0304febc41f1c665fb42ce0947f38bf0c29947a`; #207 remains the earlier post-#206 snapshot at `42e52c0076e4a0d3d69be81e47de3c916ca4919e`.
- Defaults remain unchanged: `--codegen=legacy`, `--ir-lower=direct`, and `--backend=cpp`.

This report only measures IR admission on checked-in fixtures. It does not complete the written production goal, authorize switching the codegen default, or establish coverage completeness. Zero observed leftovers does not change those conclusions.

## Interpretation rules

- Inventory = `javap -p -s -c` methods with a `Code:` body, joined by exact `class + method + descriptor`.
- `// IR codegen:` = IR; `falling back to legacy for this method` = legacy-fallback; `leaving constructor bytecode unchanged` = constructor-left-java.
- This is admission measurement of checked-in fixtures with explicit `--codegen=ir`.
- Not a JDK 17/21/25 support badge. Java 8 is the only version ever called fully supported.

## Verbatim helper totals

```text
corpus	inventory	IR	legacy-fallback	constructor-left-java
ClassicTest	108	108	0	0
jdk17	82	82	0	0
jdk21	47	47	0	0
jdk25	21	21	0	0
```

The helper used `obfuscator/test_data/tests/` from this checkout and did not assemble `TestStringConcatFactory.j`, matching the Java-only 108-method ClassicTest corpus. No historical fixture branches were fetched.

## Measurement

```bash
python3 docs/measurement/ir-leftover-inventory/measure.py --javac-25 /tmp/temurin25/bin/javac
```

The helper completed successfully, including `:obfuscator:shadowJar` and every present fixture. No compiler Gradle test suite was used as an admission gate for this measurement-only increment.

# 中文

## 范围

- 增量类型：**仅测量**
- 可发布（ship-ready）：**否**
- 已准入（admitted）：**不适用**
- 被测编译器 / merge-base：`bca314540a4ded96feb2a670d4fac35651bd70fd`（合入 #269 后的 master）
- 本次复测取代基于 `c0304febc41f1c665fb42ce0947f38bf0c29947a` 的 #264；#207 仍是较早的合入 #206 后快照 `42e52c0076e4a0d3d69be81e47de3c916ca4919e`。
- 默认值保持不变：`--codegen=legacy`、`--ir-lower=direct`、`--backend=cpp`。

本报告只测量仓库内已检入样例的 IR 准入情况。它不表示书面生产目标已经完成，不授权切换代码生成默认值，也不表示覆盖完整。即使观测到零 leftover，这些结论也不改变。

## 解释规则

- Inventory 指 `javap -p -s -c` 输出中带有 `Code:` 方法体的方法，并按精确的 `class + method + descriptor` 连接。
- `// IR codegen:` 表示 IR；`falling back to legacy for this method` 表示 legacy-fallback；`leaving constructor bytecode unchanged` 表示 constructor-left-java。
- 这是对已检入样例显式使用 `--codegen=ir` 的准入测量。
- 这不是 JDK 17/21/25 支持徽章。Java 8 是唯一曾被称为完全支持的版本。

## helper 原样汇总

```text
corpus	inventory	IR	legacy-fallback	constructor-left-java
ClassicTest	108	108	0	0
jdk17	82	82	0	0
jdk21	47	47	0	0
jdk25	21	21	0	0
```

helper 使用当前 checkout 的 `obfuscator/test_data/tests/`，没有组装 `TestStringConcatFactory.j`，因此与仅含 Java 源的 108 方法 ClassicTest 语料一致；也没有获取历史 fixture 分支。

## 测量

```bash
python3 docs/measurement/ir-leftover-inventory/measure.py --javac-25 /tmp/temurin25/bin/javac
```

helper 已成功完成，包括 `:obfuscator:shadowJar` 和所有存在的 fixture。由于本增量仅做测量，没有把编译器 Gradle 测试套件作为准入门槛。
