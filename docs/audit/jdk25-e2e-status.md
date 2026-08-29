# JDK 25 E2E status / JDK 25 端到端状态

Run date / 运行日期: 2026-08-28

Branch / 分支: `cursor/jdk25-e2e-6d81`, based on / 基于
`cursor/jdk21-25-e2e-6d81` (`23e4cc1`, PR #14; stacked on PR #9 / #6)

## Compiler discovery / 编译器探测

Before refreshing the standard Ubuntu package indexes, the VM exposed only:

刷新 Ubuntu 标准软件包索引之前，VM 只提供：

```text
Ubuntu 24.04.4 LTS
openjdk version "21.0.10" 2026-01-20
javac 21.0.10
update-java-alternatives:
  java-1.21.0-openjdk-amd64 /usr/lib/jvm/java-1.21.0-openjdk-amd64
sdk: not found
```

`apt-get update` made the official `noble-updates/universe` package
`openjdk-25-jdk-headless` available. It was installed without additional
credentials:

执行 `apt-get update` 后，官方 `noble-updates/universe` 软件源提供了
`openjdk-25-jdk-headless`，且无需额外凭据即可安装：

```text
openjdk-25-jdk-headless 25.0.4+7-1~24.04
openjdk-25-jre-headless 25.0.4+7-1~24.04
openjdk version "25.0.4" 2026-07-21
OpenJDK Runtime Environment (build 25.0.4+7-1-24.04-Ubuntu)
javac 25.0.4
javac path: /usr/lib/jvm/java-25-openjdk-amd64/bin/javac
```

After installation, `update-java-alternatives --list` reported both the JDK
21 and JDK 25 installations. All results below used JDK 25.0.4.

安装后，`update-java-alternatives --list` 同时列出 JDK 21 和 JDK 25。
下述结果均使用 JDK 25.0.4。

## Fixtures / 测试用例

`ClassicTest` now maps the `jdk25` directory to `javac --release 25` and
recognizes both static and instance `main` methods. Test discovery remains
automatic through `TestsGenerator`.

`ClassicTest` 现在将 `jdk25` 目录映射到 `javac --release 25`，并同时识别
静态和实例 `main` 方法；测试仍由 `TestsGenerator` 自动发现。

| Case | Post-21 surface / Java 21 后的新特性 | Behavioral oracle / 行为 oracle |
|---|---|---|
| `CompactSourceModuleImportE2E` | Java 25 compact source file, instance `main`, and module import declaration / Java 25 紧凑源文件、实例 `main` 与模块导入声明 | Uppercase stream result and deterministic date arithmetic / 大写 stream 结果与确定性日期运算 |
| `FlexibleConstructorBodiesE2E` | Java 25 flexible constructor bodies with validation before `super(...)` and parsing before `this(...)` / 在 `super(...)` 前校验、在 `this(...)` 前解析 | Constructed values and rejected-zero message / 构造值与拒绝零值的消息 |
| `ScopedValuesE2E` | Final Java 25 `ScopedValue` API with nested bindings / Java 25 正式版 `ScopedValue` API 与嵌套绑定 | `unbound → outer → inner → outer → unbound` |
| `StreamGatherersE2E` | Final post-21 Stream Gatherers API (`windowFixed`, `scan`, `fold`) / Java 21 后正式版 Stream Gatherers API | Fixed windows, running totals, and folded total / 固定窗口、累计值与折叠总和 |

Each source compiled independently with `javac --release 25`. `javap -verbose`
reported the same class-file header for every fixture:

每个源文件都通过 `javac --release 25` 独立编译；`javap -verbose` 对所有
fixture 均报告：

```text
minor version: 0
major version: 69
```

The four unmodified JVM oracles also ran successfully before the full suite.

四个未转换的 JVM oracle 在完整测试前也均成功运行。

## Full suite result / 完整测试结果

Command / 命令：

```sh
CC=gcc CXX=g++ ./gradlew :obfuscator:test --console=plain
```

Real result / 实测结果：

```text
BUILD SUCCESSFUL in 3m 46s

24 tests total
23 passed
1 skipped
0 failed

StreamGatherersE2E              SUCCESS in 8587ms
CompactSourceModuleImportE2E   SUCCESS in 6088ms
ScopedValuesE2E                SUCCESS in 6823ms
FlexibleConstructorBodiesE2E   SUCCESS in 6302ms
```

The generated `TestsGenerator` JUnit XML reports `tests="20"`, `skipped="1"`,
`failures="0"`, and `errors="0"`. The other two JUnit suites contain four
passing unit tests, giving the 24/23/1/0 aggregate above. The sole skip is the
pre-existing `PullRequest72`, because `krak2` is absent.

生成的 `TestsGenerator` JUnit XML 报告 `tests="20"`、`skipped="1"`、
`failures="0"`、`errors="0"`；另外两个 JUnit suite 共含四个通过的单元
测试，因此总计为 24/23/1/0。唯一跳过项仍是既有 `PullRequest72`，原因是
环境缺少 `krak2`。

For each new fixture, the captured log reaches `OK` after the native run on
all three `HOTSPOT`, `STD_JAVA`, and `ANDROID` targets. `ClassicTest` reaches
`OK` only after the transformed run's stdout exactly equals the unmodified
JVM oracle.

每个新增 fixture 的日志在 `HOTSPOT`、`STD_JAVA`、`ANDROID` 三个目标的
native 运行后均到达 `OK`；`ClassicTest` 只有在转换后 stdout 与原始 JVM
oracle 完全一致时才会到达 `OK`。

## Claim boundary / 声明边界

This is evidence for class-file major version 69 and the four listed surfaces
on this VM. It is **not** a blanket claim that every JDK 25 language feature,
library API, runtime mode, or generated class shape is supported. In
particular, no preview feature was enabled or tested, and no separate class
files for JDK 22, 23, or 24 were compiled. The Stream Gatherers fixture covers
an API finalized after 21, but the fixture itself was compiled for release 25.

这些结果只证明本 VM 上 class-file major version 69 与上述四类特性通过。
它**不**代表所有 JDK 25 语言特性、库 API、运行模式或 class 形态均受支持。
本次未启用或测试 preview 特性，也未单独编译 JDK 22、23、24 class 文件。
Stream Gatherers fixture 覆盖 Java 21 后正式发布的 API，但该 fixture 本身
以 release 25 编译。

Only test code and evidence documentation changed. No production fix was
needed, and this branch does not configure CI or developer machines to install
JDK 25.

本分支只修改测试代码与证据文档；无需生产代码修复，也不负责为 CI 或开发
环境安装 JDK 25。
