# JDK 21–25 E2E status / JDK 21–25 端到端状态

Run date / 运行日期: 2026-08-28

Branch / 分支: `cursor/jdk21-25-e2e-6d81`, based on / 基于
`cursor/jdk17-classfile-metadata-6d81` (`74eed44`)

## Environment / 环境

```text
openjdk version "21.0.10" 2026-01-20
OpenJDK Runtime Environment (build 21.0.10+7-Ubuntu-124.04)
javac 21.0.10
cmake version 3.28.3
gcc/g++ (Ubuntu 13.3.0-6ubuntu2~24.04.1) 13.3.0
krak2: not installed
```

The available compiler supports final Java 21 features. It cannot compile
Java 22–25 language or class-file fixtures, so none are claimed or marked as
passing here.

当前编译器支持 Java 21 的正式特性，但不能编译 Java 22–25 的语言或类文件
fixture，因此本文不添加、也不声称这些版本通过。

## Fixtures / 测试用例

All three fixtures are compiled by the existing `ClassicTest` flow with
`javac --release 21`. The harness packages and runs the unmodified jar as the
stdout oracle, transforms the jar, compiles the generated C++, and requires
the transformed native run's stdout to match the oracle exactly on each
platform.

三个 fixture 均由现有 `ClassicTest` 流程通过 `javac --release 21` 编译。测试
先运行未转换 jar 作为 stdout oracle，再转换 jar、编译生成的 C++，并要求
每个平台上的 native 运行 stdout 与 oracle 完全一致。

| Case | Coverage / 覆盖内容 | Real result / 实测结果 |
|---|---|---|
| `PatternSwitchE2E` | `instanceof` binding, guarded type patterns, null case, exhaustive sealed-hierarchy switch, `SwitchBootstraps.typeSwitch` / `instanceof` 绑定、带守卫类型模式、null 分支、sealed 层次穷尽 switch | **PASS** — `HOTSPOT`, `STD_JAVA`, `ANDROID` |
| `RecordPatternsE2E` | Nested record patterns in `switch` and `instanceof`, guards, generated record methods / switch 与 `instanceof` 中的嵌套 record pattern、守卫、record 生成方法 | **PASS** — `HOTSPOT`, `STD_JAVA`, `ANDROID` |
| `SequencedCollectionsE2E` | Java 21 `List` first/last/reversed operations and `SequencedMap` first/last/reversed views invoked from transformed methods, exercising generated JNI calls to the new library APIs / 从转换后方法调用 Java 21 新集合 API，覆盖对应 JNI 调用 | **PASS** — `HOTSPOT`, `STD_JAVA`, `ANDROID` |

## Command and real result / 命令与实测结果

```sh
CC=gcc CXX=g++ ./gradlew :obfuscator:test --console=plain
```

Result / 结果: **BUILD SUCCESSFUL in 3m 26s**.

```text
20 tests total
19 passed
1 skipped
0 failed

RecordPatternsE2E       SUCCESS in 27400ms
SequencedCollectionsE2E SUCCESS in 5978ms
PatternSwitchE2E        SUCCESS in 19966ms
```

JUnit XML reports `failures="0"` and `errors="0"` for the generated E2E suite.
For every new fixture, its captured log contains `OK` after the native run on
each of `HOTSPOT`, `STD_JAVA`, and `ANDROID`; in `ClassicTest`, `OK` is only
reached after exact stdout equality.

JUnit XML 对动态 E2E 测试报告 `failures="0"`、`errors="0"`。每个新 fixture
在三个平台的 native 运行后均记录 `OK`；`ClassicTest` 只有在 stdout
完全相等后才会到达该结果。

The sole skip is the pre-existing `PullRequest72`, because `krak2` is absent.
All three new JDK 21 fixtures compiled and ran; no fixture was skipped, no
writer/preprocessor defect was observed, and no production fix was needed.

唯一跳过项是既有 `PullRequest72`，原因是环境缺少 `krak2`。三个新增
JDK 21 fixture 均成功编译并运行；没有新增 fixture 被跳过，也未发现需要
修复的 writer/preprocessor 缺陷。
