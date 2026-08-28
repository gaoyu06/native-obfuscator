# IR phase 8 independent review / IR 第八阶段独立审查

Review stack / 审查堆叠:
`cursor/ir-phase8-sol-review-6d81` reviews
`cursor/ir-compiler-phase8-6d81` /
[PR #62](https://github.com/gaoyu06/native-obfuscator/pull/62), which is
stacked on `cursor/ir-phase7-sol-review-6d81-f29d` /
[PR #56](https://github.com/gaoyu06/native-obfuscator/pull/56).

`cursor/ir-phase8-sol-review-6d81` 审查
`cursor/ir-compiler-phase8-6d81` /
[PR #62](https://github.com/gaoyu06/native-obfuscator/pull/62)；后者堆叠在
`cursor/ir-phase7-sol-review-6d81-f29d` /
[PR #56](https://github.com/gaoyu06/native-obfuscator/pull/56) 之上。

## Summary / 摘要

Verdict: **Accept / 接受**. The independent review found no correctness bug in
the requested phase-8 scope and therefore changes documentation only. Detailed
findings and verification evidence are in
`docs/architecture/ir-phase8-review.md`.

结论：**接受**。独立审查未在指定的第八阶段范围内发现正确性缺陷，因此本审查分支只
修改文档。详细发现及验证证据见
`docs/architecture/ir-phase8-review.md`。

## (a) Change scope / 本次改动范围

- Reviewed typed `NEW`, existing class-cache resolution,
  `JNIEnv::AllocObject`, and protected/unprotected exceptional exits.
- Reviewed constructor-only `INVOKESPECIAL <init>`, including verified
  `NEW`/`DUP`/constructor stack behavior and
  `CallNonvirtualVoidMethod(receiver, class, method ID, arguments...)`.
- Reviewed exact `I`, `J`, and reference arguments plus `V`, exact `I`, `J`,
  and reference returns for static/virtual high-level invokes, including every
  selected JNI call family.
- Reviewed fallback-before-mutation after newly admitted operations,
  constructor-method exclusion, the opt-in `--codegen=ir` mode, the `legacy`
  default, and retention of existing snippets.
- Added the phase-8 review record and refreshed this bilingual handoff; no
  compiler implementation was changed.

- 审查 typed `NEW`、现有类缓存解析、`JNIEnv::AllocObject`，以及受保护/未受保护
  情况下的异常出口。
- 审查仅限构造器的 `INVOKESPECIAL <init>`，包括经验证的
  `NEW`/`DUP`/构造器栈行为，以及
  `CallNonvirtualVoidMethod(receiver, class, method ID, arguments...)`。
- 审查静态/虚高层调用的精确 `I`、`J`、引用参数及 `V`、精确 `I`、`J`、引用
  返回，包括所选择的全部 JNI 调用族。
- 审查新接纳操作后的 mutation 前 fallback、构造器方法体排除策略、可选
  `--codegen=ir` 模式、`legacy` 默认值及现有 snippets 保留情况。
- 新增第八阶段审查记录并更新本双语交接说明；未修改编译器实现。

## (b) Can this ship to production as-is? / 是否可直接上线？

**No / 否。**

Phase 8 is still a partial, opt-in compiler slice. Unsupported bytecodes,
descriptor sorts, interface/dynamic calls, non-constructor special calls, and
reference-returning method bodies still fall back. Focused unit and C++ syntax
evidence does not replace supported-platform native runtime-parity gates.

第八阶段仍是部分、可选的编译器增量。不支持的字节码与描述符类型、接口/动态调用、
非构造器 special 调用及引用返回方法体仍会 fallback。聚焦单测与 C++ 语法证据不能
替代受支持平台上的 native 运行时等价性门禁。

## (c) Is review required? / 是否需要 review？

**Yes / 是。**

This branch supplies an independent compiler review, but the stacked change
still requires normal maintainer review and disposition. Reviewers should
preserve allocation failure routing, constructor receiver/argument order,
invoke carrier-to-JNI mapping, and fallback atomicity.

本分支提供独立编译器审查，但该堆叠改动仍需维护者按正常流程审查并决定是否接收。
审查者应保留分配失败路由、构造器 receiver/参数顺序、invoke carrier 到 JNI
调用族的映射，以及 fallback 原子性。

## (d) Review preconditions / Review 前置条件

1. Review this branch against `cursor/ir-compiler-phase8-6d81` (PR #62), and
   keep PR #62 stacked on `cursor/ir-phase7-sol-review-6d81-f29d` (PR #56);
   do not rebase the compiler sequence onto `master`.
   本分支必须相对 `cursor/ir-compiler-phase8-6d81`（PR #62）审查，同时 PR #62
   必须继续堆叠在 `cursor/ir-phase7-sol-review-6d81-f29d`（PR #56）之上；
   不得将编译器序列改基到 `master`。
2. Re-run the focused Gradle command and inspect JUnit XML. This review reran
   it with `CC=gcc CXX=g++` and recorded `IrCompilerTest` 36 plus
   `CodegenModeTest` 2, total 38; zero skips, failures, or errors.
   重跑聚焦 Gradle 命令并检查 JUnit XML。本审查使用 `CC=gcc CXX=g++` 重跑，
   记录为 `IrCompilerTest` 36 加 `CodegenModeTest` 2，共 38 个用例；跳过、
   失败、错误均为零。
3. When g++ and JNI headers are present, require the g++ testcase to be
   unskipped and independently syntax-check its retained 34-method translation
   unit with `g++ -std=c++17 -fsyntax-only`. This review confirmed both with
   g++ 13.3.0 and OpenJDK 21.0.10 JNI headers.
   当 g++ 与 JNI 头文件存在时，必须确认 g++ 用例未跳过，并使用
   `g++ -std=c++17 -fsyntax-only` 独立语法检查其保留的 34-method 翻译单元。
   本审查已使用 g++ 13.3.0 与 OpenJDK 21.0.10 JNI 头文件确认两项检查。
4. Require supported-platform/JDK CI and native runtime-parity checks before
   any production decision.
   任何生产决策前都必须通过受支持平台/JDK 的 CI 及 native 运行时等价性检查。
5. During conflict resolution, retain exception routing, constructor-method
   exclusion, exact JNI carriers, fallback-before-mutation, the `legacy`
   default, and existing snippets.
   解决冲突时必须保留异常路由、构造器方法体排除策略、精确 JNI carrier、mutation
   前 fallback、`legacy` 默认值及现有 snippets。
