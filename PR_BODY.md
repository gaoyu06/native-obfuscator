# IR phase 9 — Fable review / IR 编译器第九阶段 —— Fable 审阅

Review branch / 审阅分支: `cursor/ir-phase9-fable-review-6d81`.
Subject / 被审对象: `cursor/ir-compiler-phase9-6d81` (PR #66), tip `32ac47d`.
Preferred base / 首选基线: `cursor/ir-compiler-phase8-6d81`
(`95eb5ffd2fc5a9515af65c1d15403e7c983c64a5`).

Verdict / 结论: **Accept with nits / 接受（有小瑕疵）.**
Compiler code changed on this branch / 本分支是否改动编译器代码: **No / 否**
(documentation only — no correctness bug found /
仅文档——未发现正确性缺陷).

Full review / 完整审阅: `docs/architecture/ir-phase9-fable-review.md`.

This is a compiler/transpiler review only — typed IR, CFG and exception edges,
structured C++ emission, JNI carrier selection, and pending-exception lifetime.
本次审阅仅针对编译器/转译器——typed IR、CFG 与异常边、结构化 C++ 发射、JNI carrier
选择与 pending-exception 生命周期。

## (a) Change scope / 本次改动范围

Phase 9 adds four isolated lowerings to the opt-in typed CFG compiler, all
verified correct against JVM semantics:

- `ARETURN` through the existing `IrType.REFERENCE` / `jobject` carrier, admitted
  only when the descriptor's return sort is a reference; unprotected JNI failures
  return `nullptr` with the exception pending.
- A typed `ACONST_NULL` (`NullReference`, `ref`) that emits `nullptr` as a
  reference, not an integer zero.
- `IFNULL` / `IFNONNULL` as a dedicated `ReferenceBranch` comparing against
  `nullptr` with `==` / `!=`, never as integer compares.
- `POP` for a single category-one operand only; `POP2` and category-two `POP`
  still fall back.

This review branch adds only `docs/architecture/ir-phase9-fable-review.md` and
this PR body. No `main/**` compiler code was modified.

第九阶段为可选的 typed CFG 编译器新增四项独立 lowering，均已核对符合 JVM 语义：

- 经现有 `IrType.REFERENCE` / `jobject` carrier 的 `ARETURN`，仅当描述符返回引用时
  接纳；未受保护的 JNI 失败返回 `nullptr` 且异常保持 pending。
- typed `ACONST_NULL`（`NullReference`，`ref`）发射 `nullptr`，是引用而非整数零。
- `IFNULL` / `IFNONNULL` 作为专用 `ReferenceBranch`，对 `nullptr` 做 `==` / `!=`，
  绝不表示为整数比较。
- `POP` 仅接纳单个 category-one 操作数；`POP2` 与 category-two `POP` 仍 fallback。

本审阅分支仅新增 `docs/architecture/ir-phase9-fable-review.md` 与本 PR body，未改动
任何 `main/**` 编译器代码。

## (b) Can this ship to production as-is? / 是否可直接上线？

**No / 否。**

Phase 9 remains a partial, opt-in compiler slice. Unsupported bytecodes and
descriptors still fall back — float/double, `MULTIANEWARRAY`, non-`int` primitive
arrays, reference fields, `INVOKEINTERFACE`, invokedynamic, non-constructor
`INVOKESPECIAL`, constructor method bodies, and category-two stack manipulation.
Focused unit tests plus a g++ syntax smoke do not replace supported-platform
native runtime-parity gates.

第九阶段仍是部分、可选的编译器增量。不支持的字节码与描述符仍会 fallback——
float/double、`MULTIANEWARRAY`、非 `int` primitive array、reference field、
`INVOKEINTERFACE`、invokedynamic、非构造器 `INVOKESPECIAL`、构造器方法体及
category-two stack manipulation。聚焦单测与 g++ 语法冒烟不能替代受支持平台上的
native 运行时等价性门禁。

## (c) Is review required before shipping? / 上线前是否需要 review？

**Yes / 是.**

The changes are correct as reviewed, but any production decision must still gate
on reference-return descriptor/carrier matching, JNI default returns on
exceptional exits, explicit reference-null branch typing and control flow,
category-one `POP` validation, fallback-before-mutation, and supported-platform
CI plus native runtime-parity checks.

改动经审阅正确，但任何生产决策仍须审查引用返回描述符/carrier 匹配、异常出口的 JNI
默认返回、显式 reference-null 分支 typing 与控制流、category-one `POP` 校验、
mutation 前 fallback，以及受支持平台的 CI 与 native 运行时等价性检查。

## (d) Evidence and re-run counts / 证据与重跑计数

Environment / 环境: `gcc`/`g++ 13.3.0`, OpenJDK `21.0.10`, JNI headers at
`/usr/lib/jvm/java-21-openjdk-amd64/include{,/linux}`.

1. Focused suite, `CC=gcc CXX=g++ ./gradlew :obfuscator:test --tests
   …ir.IrCompilerTest --tests …CodegenModeTest` — BUILD SUCCESSFUL. JUnit XML:
   `IrCompilerTest` `tests="42" skipped="0" failures="0" errors="0"` (0.604 s)
   and `CodegenModeTest` `tests="2" skipped="0" failures="0" errors="0"`
   (0.097 s); **total 44, 0 skipped, 0 failures, 0 errors.**
   聚焦测试以 `CC=gcc CXX=g++` 重跑，BUILD SUCCESSFUL；JUnit XML 为 42 + 2，共 44 个，
   跳过 / 失败 / 错误均为 0。
2. `generatedCppPassesGppSyntaxCheckWhenToolchainAvailable` really ran (suite XML
   has zero `<skipped>`, case time 0.249 s); the g++ syntax check of the
   39-method TU exited 0. I independently re-ran `g++ -std=c++17 -fsyntax-only`
   on the exact file the test wrote (`/tmp/ir-compile-smoke*/ir-smoke.cpp`,
   69 270 bytes, empty `gpp-output.txt`) — exit 0.
   该 g++ 冒烟真实运行（XML 无 `<skipped>`，用时 0.249 s），39-method 翻译单元语法检查
   退出 0；我对测试写出的同一文件独立重跑 `g++ -std=c++17 -fsyntax-only`，退出 0。
3. All seven review points hold, read out of the g++-accepted TU: `ARETURN`
   returns a `jobject` value from a `jobject` function; unprotected exits
   `return nullptr;` with the exception pending; `ACONST_NULL` is `v0 = nullptr;`
   on a `jobject`; `IFNULL`/`IFNONNULL` emit `if (arg0 == nullptr)` /
   `if (arg0 != nullptr)`; `POP` drops a category-one invoke result while keeping
   the call; `rejectsUnsupportedAfterPhaseNineOpsBeforeMutation` proves
   fallback-before-mutation; default stays `legacy` and `<init>` stays excluded.
   七个审阅要点全部成立，均取自被 g++ 接受的翻译单元：`ARETURN` 从 `jobject` 函数返回
   `jobject` 值；未受保护出口 `return nullptr;` 且异常 pending；`ACONST_NULL` 为
   `jobject` 上的 `v0 = nullptr;`；`IFNULL`/`IFNONNULL` 发射 `if (arg0 == nullptr)` /
   `if (arg0 != nullptr)`；`POP` 丢弃 category-one invoke 结果但保留调用；
   `rejectsUnsupportedAfterPhaseNineOpsBeforeMutation` 证明 mutation 前 fallback；
   默认仍为 `legacy` 且 `<init>` 仍被排除。

No benchmark numbers are claimed. This branch does not merge to `master` and does
not open a pull request.
未声称任何基准数值。本分支不合并至 `master`，也不开启 pull request。
