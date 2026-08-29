# IR phase 12 — Fable review (docs only) / IR 编译器第十二阶段 —— Fable 审阅（仅文档）

Review branch: `cursor/ir-phase12-fable-review-6d81`.
Subject under review: `cursor/ir-compiler-phase12-6d81` (draft PR #84),
comparing against base `cursor/ir-compiler-phase11-6d81` at
`6fc64927a53c777a36c38e54aaed01b1bd696ed3` (draft PR #78).
This branch adds only `docs/architecture/ir-phase12-fable-review.md` and this
PR body. No compiler code changed.

审阅分支：`cursor/ir-phase12-fable-review-6d81`。
审阅对象：`cursor/ir-compiler-phase12-6d81`（草稿 PR #84），基于
`cursor/ir-compiler-phase11-6d81` 的 `6fc6492…`（草稿 PR #78）比较。
本分支仅新增 `docs/architecture/ir-phase12-fable-review.md` 与本 PR 说明，
未改动任何编译器代码。

## (a) Change scope / 本次改动范围

- Documents an independent Fable review of the phase-12 constructor lowering:
  Java bytecode → typed IR → C++/JNI, focused on the verifier-safe split.
- Confirms `<init>` is never marked `ACC_NATIVE`; only the hidden static helper
  is native.
- Confirms exactly one linear direct `this(...)`/`super(...)` call stays in the
  Java constructor and the native suffix starts after it (not executed twice).
- Confirms the hidden bridge receives the initialized receiver as local 0 /
  `REFERENCE` / `jobject obj`, with descriptor arguments after it.
- Confirms full-body admission before any output, cache, method-flag, bridge,
  or bytecode mutation; a rejected constructor stays unchanged Java bytecode.
- Confirms the CLI/API default stays `legacy` and the phase-9/10/11 regressions
  and `cppsnippets.properties` remain present.
- Records real focused-test counts and an independent g++ recompile of the
  retained translation unit.

- 记录对 phase-12 构造函数降级（Java 字节码 → typed IR → C++/JNI）的独立 Fable
  审阅，聚焦 verifier-safe split。
- 确认 `<init>` 绝不被标记 `ACC_NATIVE`；仅 hidden static helper 为 native。
- 确认唯一的线性直接 `this(...)`/`super(...)` 调用保留在 Java 构造函数中，
  native 后缀从该调用之后开始（不重复执行）。
- 确认 hidden bridge 以 local 0 / `REFERENCE` / `jobject obj` 接收已初始化的
  receiver，描述符参数随其后。
- 确认 mutation 前完成完整方法体 admission；被拒构造函数保持原 Java 字节码。
- 确认 CLI/API 默认仍为 `legacy`，phase-9/10/11 回归与 `cppsnippets.properties`
  均保留。
- 记录真实聚焦测试计数，及对保留翻译单元的独立 g++ 重编。

## (b) Can this ship to production as-is? / 是否可直接上线？

**No / 否。**

This branch is documentation only. The reviewed phase-12 implementation is
still a partial, opt-in compiler slice: constructors with unsupported
operations, non-linear initialization prefixes, cross-split exception regions,
or suffix dependencies on prefix-only locals remain Java bytecode, and the
default stays `legacy`. Focused unit tests plus a C++ syntax check do not
replace native runtime-parity gates on every supported platform.

本分支仅含文档。所审阅的 phase-12 实现仍是部分、可选的编译器增量：含不支持
操作、非线性初始化前缀、跨 split 异常区域，或后缀依赖仅在前缀初始化的局部变量
的构造函数仍保留为 Java 字节码，默认仍为 `legacy`。聚焦单测加 C++ 语法检查
不能替代全部受支持平台上的 native 运行时等价性门禁。

## (c) Is review required? / 上线前是否需要 review？

**Yes / 是。**

This document is that review. Verdict: **accept**. It verifies the
verifier-safe split, full-body admission before mutation, the local-0 receiver
mapping, the bridge descriptor and argument order, the retained single
`this(...)`/`super(...)` bytecode call, the I/J/reference field carriers, the
JNI `void` exceptional exits, the unchanged-constructor fallback, and the
retained phase-9 through phase-11 regressions. No correctness bug was found, so
no compiler code was changed.

本文档即为该 review。结论：**接受**。审阅确认了 verifier-safe split、mutation
前的完整方法体 admission、local-0 receiver 映射、bridge 描述符与参数顺序、保留
的唯一 `this(...)`/`super(...)` 字节码调用、I/J/引用字段 carrier、JNI `void`
异常出口、构造函数保持不变的 fallback，以及保留的 phase-9 至 phase-11 回归。
未发现正确性缺陷，故未改动任何编译器代码。

## (d) Review preconditions and evidence / Review 前置条件与证据

1. Compare against `cursor/ir-compiler-phase11-6d81` at `6fc6492…` (draft
   PR #78), not `master`.
   基于 `cursor/ir-compiler-phase11-6d81` 的 `6fc6492…`（草稿 PR #78）比较，
   而非 `master`。
2. Focused command re-run with `CC=gcc CXX=g++ … --rerun-tasks`; counts read
   from JUnit XML: `IrCompilerTest` 57 + `CodegenModeTest` 2 = **59**; zero
   skipped, failures, or errors.
   以 `CC=gcc CXX=g++ … --rerun-tasks` 重跑聚焦命令；从 JUnit XML 读取计数：
   `IrCompilerTest` 57 + `CodegenModeTest` 2 = **59**；跳过、失败、错误均为零。
3. `generatedCppPassesGppSyntaxCheckWhenToolchainAvailable` ran unskipped
   (`time="0.268"`, no `<skipped>`); an independent
   `g++ -std=c++17 -fsyntax-only` on the retained unit (61 `JNICALL`
   functions, 2 `special_init` bridges) exited 0 with empty diagnostics.
   `generatedCppPassesGppSyntaxCheckWhenToolchainAvailable` 未跳过运行
   （`time="0.268"`，无 `<skipped>`）；对保留 unit（61 个 `JNICALL` 函数、
   2 个 `special_init` bridge）独立运行 `g++ -std=c++17 -fsyntax-only`，退出 0
   且无诊断输出。
4. Full evidence: `docs/architecture/ir-phase12-fable-review.md`.
   完整证据见 `docs/architecture/ir-phase12-fable-review.md`。

Compiler code changed: **No**. Verdict: **accept**.
编译器代码改动：**否**。结论：**接受**。
