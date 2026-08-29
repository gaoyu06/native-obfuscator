# IR phase 13 independent review / IR 编译器第十三阶段独立审查

Required base / 必须基于:
`cursor/ir-compiler-phase13-6d81`
(`b5a403fd398961870eb6aadafb50b882bc17f273`,
[draft PR #90](https://github.com/gaoyu06/native-obfuscator/pull/90)).
PR #90 is stacked on
`cursor/ir-phase12-sol-review-6d81`
(`481b7b108388380bfbbdf94703ee56eb4b601b02`,
[draft PR #89](https://github.com/gaoyu06/native-obfuscator/pull/89)). /
必须基于
`cursor/ir-compiler-phase13-6d81`
（`b5a403fd398961870eb6aadafb50b882bc17f273`，
[草稿 PR #90](https://github.com/gaoyu06/native-obfuscator/pull/90)）。
PR #90 堆叠在
`cursor/ir-phase12-sol-review-6d81`
（`481b7b108388380bfbbdf94703ee56eb4b601b02`，
[草稿 PR #89](https://github.com/gaoyu06/native-obfuscator/pull/89)）之上。

## Summary / 摘要

Independent review verdict: **Pass**. The review found no correctness bug, so
this branch changes documentation only. Phase 13 uses the exact
Boolean/Byte/Char/Short JNI field and invoke families while retaining IR
`I32` carriers, rejection-before-mutation for `F`/`D`, the phase-9 through
phase-12 regressions, and the `legacy` default.

独立审查结论：**通过**。本次审查未发现正确性缺陷，因此该分支只修改文档。
Phase 13 在保留 IR `I32` carrier 的同时，使用精确的
Boolean/Byte/Char/Short JNI 字段与调用 family；`F`/`D` 仍在 mutation 前被
拒绝，phase-9 至 phase-12 回归与 `legacy` 默认值均保留。

## (a) Change scope / 本次改动范围

- Reviewed all instance/static `Z`/`B`/`C`/`S` field paths. They select
  `Get/Set[Static]Boolean/Byte/Char/ShortField`, never an `IntField` accessor.
- Confirmed read widening: byte/short sign-extend and boolean/char zero-extend
  to `I32`. Writes retain the boolean low bit or truncate through
  `jbyte`/`jshort`/`jchar`.
- Reviewed static, virtual, interface, and special invokes. Return descriptors
  select `Call[Static|Nonvirtual]Boolean/Byte/Char/ShortMethod`, and arguments
  are narrowed in descriptor order.
- Confirmed the constructor special-void path still uses
  `CallNonvirtualVoidMethod`.
- Confirmed `F`/`D` field and invoke descriptors are rejected before mutation.
- Rechecked the retained phase-9 `jarray` return cast, phase-10 fields,
  phase-11 invokes, phase-12 constructor regressions, and `legacy` defaults.
- Added `docs/architecture/ir-phase13-review.md`; no compiler source changed.

- 审查全部实例/静态 `Z`/`B`/`C`/`S` 字段路径；它们选择
  `Get/Set[Static]Boolean/Byte/Char/ShortField`，不会使用 `IntField`
  accessor。
- 确认读取扩展：byte/short 符号扩展，boolean/char 零扩展到 `I32`。写入时
  boolean 保留低位，byte/short/char 分别通过 `jbyte`/`jshort`/`jchar`
  截断。
- 审查 static、virtual、interface、special 调用；返回描述符选择
  `Call[Static|Nonvirtual]Boolean/Byte/Char/ShortMethod`，参数按描述符顺序
  窄化。
- 确认构造函数 special-void 路径仍使用 `CallNonvirtualVoidMethod`。
- 确认 `F`/`D` 字段及调用描述符在 mutation 前被拒绝。
- 复核保留的 phase-9 `jarray` 返回转换、phase-10 字段、phase-11 调用、
  phase-12 构造函数回归以及 `legacy` 默认值。
- 新增 `docs/architecture/ir-phase13-review.md`；未修改编译器源码。

## (b) Can this ship to production as-is? / 是否可直接上线？

**No / 否。**

This remains a partial, opt-in compiler slice. Float/double operations,
non-`int` primitive arrays, `MULTIANEWARRAY`, `POP2`, `invokedynamic`, and
other operations outside the admitted subset still fall back. Focused unit
tests and C++ syntax checks do not establish runtime parity on every supported
platform.

这仍是部分、可选的编译器增量。float/double 运算、非 `int` primitive array、
`MULTIANEWARRAY`、`POP2`、`invokedynamic` 及已接纳子集之外的其他操作仍会
fallback。聚焦单测与 C++ 语法检查不能证明全部受支持平台上的运行时等价性。

## (c) Is review required? / 上线前是否需要 review？

**Yes / 是。**

This branch records an independent source, regression, XML-result, and g++
smoke review of PR #90. Further integration review should preserve the stacked
base and the documented fallback and default-mode invariants.

该分支记录了对 PR #90 的独立源码、回归、XML 结果及 g++ smoke 审查。后续集成
审查仍须保留堆叠基线，以及文档所列 fallback 与默认模式不变量。

## (d) Review preconditions and evidence / Review 前置条件与证据

1. Compare with PR #90 head
   `b5a403fd398961870eb6aadafb50b882bc17f273`, retaining PR #89 at
   `481b7b108388380bfbbdf94703ee56eb4b601b02` as its base.
   必须与 PR #90 head
   `b5a403fd398961870eb6aadafb50b882bc17f273` 比较，并保留
   `481b7b108388380bfbbdf94703ee56eb4b601b02` 的 PR #89 作为其基线。
2. Re-run:

   ```text
   CC=gcc CXX=g++ ./gradlew :obfuscator:test \
     --tests by.radioegor146.ir.IrCompilerTest \
     --tests by.radioegor146.CodegenModeTest \
     --rerun-tasks
   ```

   The new JUnit XML records `IrCompilerTest` 62 and `CodegenModeTest` 2:
   64 total, with zero skipped, failures, or errors.
   新生成的 JUnit XML 记录 `IrCompilerTest` 62、`CodegenModeTest` 2：
   共 64 项，跳过、失败、错误均为零。
3. g++ 13.3.0, OpenJDK 21.0.10, and JNI headers were present.
   `generatedCppPassesGppSyntaxCheckWhenToolchainAvailable` ran unskipped in
   0.283 s. The retained
   `/tmp/ir-compile-smoke14043444023380144353/ir-smoke.cpp` unit contains 87
   `JNICALL` functions and also passed an independent
   `g++ -std=c++17 -fsyntax-only` check with empty diagnostics.
   环境中存在 g++ 13.3.0、OpenJDK 21.0.10 与 JNI header。
   `generatedCppPassesGppSyntaxCheckWhenToolchainAvailable` 未跳过，耗时
   0.283 秒。保留的
   `/tmp/ir-compile-smoke14043444023380144353/ir-smoke.cpp` 含 87 个
   `JNICALL` 函数，并通过独立的 `g++ -std=c++17 -fsyntax-only` 检查，
   无诊断输出。
4. Detailed review evidence is in
   `docs/architecture/ir-phase13-review.md`. No compiler bug was fixed and no
   compiler source changed.
   详细审查证据见 `docs/architecture/ir-phase13-review.md`。本次未修复编译器
   缺陷，也未修改编译器源码。
