## Summary / 摘要

Stacked on PR #14 (`cursor/jdk21-25-e2e-6d81`), which is stacked on PR #9 /
#6. Ubuntu's official package repository provided OpenJDK 25.0.4, so this
branch adds four real ClassicTest fixtures compiled with `javac --release 25`:

本分支叠在 PR #14（`cursor/jdk21-25-e2e-6d81`）之上，而 #14 又叠在
PR #9 / #6 上。Ubuntu 官方软件源可安装 OpenJDK 25.0.4，因此本分支新增
四个使用 `javac --release 25` 真实编译的 ClassicTest fixture：

- `CompactSourceModuleImportE2E`
- `FlexibleConstructorBodiesE2E`
- `ScopedValuesE2E`
- `StreamGatherersE2E`

The full suite completed with 24 tests: 23 passed, 1 skipped
(`PullRequest72`, because `krak2` is absent), and 0 failed. All four new
fixtures passed exact stdout comparison after transpilation on `HOTSPOT`,
`STD_JAVA`, and `ANDROID`. Their class-file major version is 69.

完整测试共 24 项：23 通过、1 跳过（缺少 `krak2` 的 `PullRequest72`）、
0 失败。四个新增 fixture 均以 class-file major version 69 编译，并在
`HOTSPOT`、`STD_JAVA`、`ANDROID` 三个平台完成转译后的 stdout 精确对比。

Evidence / 证据：`docs/audit/jdk25-e2e-status.md`

## (a) 本次改动范围 / Change scope

- Add four `obfuscator/test_data/tests/jdk25/**` behavioral fixtures for
  post-Java-21 language and library surfaces.
- Register `jdk25` as release 25 in the test-only ClassicTest harness and
  recognize Java 25 instance `main` methods.
- Record compiler, class-file, oracle, platform, and JUnit evidence.

- 新增四个 `obfuscator/test_data/tests/jdk25/**` 行为测试，覆盖 Java 21
  之后的语言与库特性。
- 在仅测试使用的 ClassicTest harness 中注册 release 25，并识别 Java 25
  实例 `main`。
- 记录编译器、class-file、oracle、平台及 JUnit 的实测证据。

## (b) 是否可直接上线 / Can this ship to production as-is?

No. Tests and evidence only; there is no production implementation change.
The result is deliberately narrower than a blanket “all JDK 25 is supported”
claim.

No（否）。本分支仅包含测试与证据，没有生产实现改动；这些结果也不应被
扩大解释为“完整支持所有 JDK 25 特性”。

## (c) 上线前是否需要 review / Is review required?

Yes. Review the fixture coverage, the instance-main discovery change, the
stdout-oracle evidence, and the narrow claim boundary.

Yes（是）。请 review fixture 覆盖范围、实例 `main` 的发现逻辑、stdout
oracle 证据，以及刻意限定的声明边界。

## (d) review 的前置条件 / Review preconditions

1. Preserve the stack: this branch is based on PR #14, itself based on PR #9 /
   #6.
2. Use a real JDK 25 compiler; verify `javac -version` and rerun
   `CC=gcc CXX=g++ ./gradlew :obfuscator:test --console=plain`.
3. Confirm each new fixture compiles to class-file major version 69 and reaches
   `OK` for `HOTSPOT`, `STD_JAVA`, and `ANDROID`.
4. Do not infer preview-feature coverage or separate JDK 22–24 class-file
   coverage from these release-25 fixtures.

1. 保持堆叠关系：本分支基于 PR #14，而 #14 基于 PR #9 / #6。
2. 使用真实 JDK 25 编译器；确认 `javac -version` 后重新运行
   `CC=gcc CXX=g++ ./gradlew :obfuscator:test --console=plain`。
3. 确认每个新增 fixture 的 class-file major version 为 69，并在
   `HOTSPOT`、`STD_JAVA`、`ANDROID` 上均到达 `OK`。
4. 不要从这些 release-25 fixture 推断 preview 特性覆盖或独立的 JDK
   22–24 class-file 覆盖。
