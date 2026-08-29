# IR phase 13 — Fable review / IR 编译器第十三阶段 —— Fable 评审

Review branch / 评审分支: `cursor/ir-phase13-fable-review-6d81`.
Subject / 评审对象: `cursor/ir-compiler-phase13-6d81` at
`b5a403fd398961870eb6aadafb50b882bc17f273`
([draft PR #90](https://github.com/gaoyu06/native-obfuscator/pull/90)),
based on / 基于 `cursor/ir-phase12-sol-review-6d81`
(`481b7b108388380bfbbdf94703ee56eb4b601b02`,
[draft PR #89](https://github.com/gaoyu06/native-obfuscator/pull/89)).

## Summary / 摘要

Fable review of the phase-13 slice that admits the `Z`/`B`/`C`/`S` (boolean,
byte, char, short) field and invoke descriptors into the optional Java bytecode
→ typed CFG IR → C++/JNI compiler path. Verdict: **accept**. The exact JNI
Boolean/Byte/Char/Short families, the JVM widen-on-read / narrow-on-write on the
`I32` stack carrier, invoke argument order and return widening, continued `F`/`D`
rejection, the unchanged constructor void path, reject-before-mutation, and the
`legacy` default all hold. No correctness defect was found, so the review is
documentation-only: **no compiler code changed** on this branch.

对第十三阶段增量的 Fable 评审：该增量将 `Z`/`B`/`C`/`S`（boolean、byte、char、
short）字段与调用描述符纳入可选的 Java 字节码 → typed CFG IR → C++/JNI 编译路径。
结论：**通过（accept）**。精确的 JNI Boolean/Byte/Char/Short family、在 `I32`
栈 carrier 上的「读取扩展 / 写入窄化」JVM 语义、invoke 参数顺序与返回值扩展、
`F`/`D` 继续拒绝、构造函数 void 路径不变、mutation 前拒绝，以及 `legacy` 默认值
均成立。未发现正确性缺陷，故本评审仅为文档：本分支 **未修改任何编译器代码**。

## (a) Change scope / 本次改动范围

- Adds `docs/architecture/ir-phase13-fable-review.md`: the Fable review with the
  accept verdict, per-point correctness assessment, real test counts, and one
  non-blocking fidelity nit.
- Rewrites this `PR_BODY.md` as the bilingual review body.
- No source, test, build, or resource files were modified; the phase-13
  compiler and its tests are reviewed as submitted.

- 新增 `docs/architecture/ir-phase13-fable-review.md`：包含 accept 结论、逐点
  正确性评估、真实测试计数，以及一个非阻断的 fidelity 小问题。
- 将本 `PR_BODY.md` 重写为双语评审正文。
- 未修改任何源码、测试、构建或资源文件；按提交原样评审 phase-13 编译器及其测试。

## (b) Can this ship to production as-is? / 是否可直接上线？

**No / 否。**

The verdict on the phase-13 *code generation* is accept, but the feature it
extends is still a partial, opt-in compiler slice. Float/double operations and
descriptors, non-`int` primitive arrays, `MULTIANEWARRAY`, `POP2`,
`invokedynamic`, and other operations outside the documented subset still fall
back to legacy. Focused unit tests and a C++ syntax check do not replace native
runtime-parity gates on every supported platform.

phase-13 的 *代码生成* 评审结论为通过，但其所扩展的特性仍是部分、可选的编译器
增量。float/double 运算及描述符、非 `int` primitive array、`MULTIANEWARRAY`、
`POP2`、`invokedynamic` 及文档范围外的其他操作仍会回退到 legacy。聚焦单测与 C++
语法检查不能替代全部受支持平台上的 native 运行时等价性门禁。

## (c) Is review required? / 上线前是否需要 review？

**Yes / 是。**

This document *is* that independent review, and it accepts the slice. Any future
promotion of the IR path toward default/production must still re-confirm
descriptor-driven JNI family selection, JVM-compatible narrowing (byte/short
sign extension, char zero extension, boolean low-bit masking), exact invoke
argument order and return widening, null-receiver exceptional exits, and
rejection before mutation, and must gate on native runtime parity.

本文件即为该独立评审，并对该增量给出通过结论。未来若将 IR 路径推进为默认/生产
路径，仍须重新确认由描述符驱动的 JNI family 选择、符合 JVM 的窄化（byte/short
符号扩展、char 零扩展、boolean 低位掩码）、精确的 invoke 参数顺序与返回值扩展、
空接收者异常出口、mutation 前拒绝，并以 native 运行时等价性作为门禁。

## (d) Review preconditions / Review 前置条件

1. Compare against `cursor/ir-compiler-phase13-6d81` at
   `b5a403fd398961870eb6aadafb50b882bc17f273`
   ([draft PR #90](https://github.com/gaoyu06/native-obfuscator/pull/90)),
   based on `cursor/ir-phase12-sol-review-6d81`
   (`481b7b108388380bfbbdf94703ee56eb4b601b02`, draft PR #89). Do not use
   `master`.
   与 `cursor/ir-compiler-phase13-6d81` 的
   `b5a403fd398961870eb6aadafb50b882bc17f273`
   （[草稿 PR #90](https://github.com/gaoyu06/native-obfuscator/pull/90)）比较，
   其基于 `cursor/ir-phase12-sol-review-6d81`
   （`481b7b108388380bfbbdf94703ee56eb4b601b02`，草稿 PR #89）；不使用 `master`。
2. Re-run the focused suite and read counts from JUnit XML:

   ```text
   CC=gcc CXX=g++ ./gradlew :obfuscator:test \
     --tests by.radioegor146.ir.IrCompilerTest \
     --tests by.radioegor146.CodegenModeTest \
     --rerun-tasks
   ```

   Recorded result: `IrCompilerTest` 62 plus `CodegenModeTest` 2, total 64;
   zero skipped, failures, or errors; the real-g++ syntax-check unit
   (`generatedCppPassesGppSyntaxCheckWhenToolchainAvailable`) ran, not skipped.
   记录结果：`IrCompilerTest` 62 加 `CodegenModeTest` 2，共 64；跳过、失败、错误
   均为零；真实 g++ 语法检查单测
   （`generatedCppPassesGppSyntaxCheckWhenToolchainAvailable`）已运行，未跳过。
3. Confirm the accept rationale in `docs/architecture/ir-phase13-fable-review.md`:
   exact JNI Boolean/Byte/Char/Short families, widen/narrow on the `I32` carrier,
   invoke argument order and return widening, `F`/`D` rejection, unchanged
   constructor void path, reject-before-mutation, and the `legacy` default.
   核对 `docs/architecture/ir-phase13-fable-review.md` 中的通过依据：精确的 JNI
   Boolean/Byte/Char/Short family、`I32` carrier 上的扩展/窄化、invoke 参数顺序
   与返回值扩展、`F`/`D` 拒绝、构造函数 void 路径不变、mutation 前拒绝、`legacy`
   默认值。

Detailed evidence / 详细证据:
`docs/architecture/ir-phase13-fable-review.md` (this review) and
`docs/architecture/ir-phase13-status.md` (implementation status).
