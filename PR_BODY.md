# IR compiler phase 5 / IR 编译器第五阶段

Preferred base: `cursor/ir-phase4-fable-review-6d81`

首选基线：`cursor/ir-phase4-fable-review-6d81`

## (a) Scope / 改动范围

- Add typed `IntDivRem` lowering for `IDIV` and `IREM`. Zero divisors raise
  `ArithmeticException`; protected operations enter phase-4 shared catch
  dispatch. Explicit overflow guards preserve JVM results without evaluating
  undefined signed C++ division or remainder.
- Add typed `NewArray` lowering for `NEWARRAY T_INT`, using
  `env->NewIntArray` with negative-length, null-result, and pending-exception
  checks.
- Add `GETSTATIC` / `PUTSTATIC` lowering for descriptor `I`, reusing the
  existing class/field caches and static-int JNI calls.
- Keep whole-method validation before emission. Non-`I` static fields and all
  other unsupported forms fall back before caches, output, metadata, or
  `ACC_NATIVE` are changed.
- Extend the focused tests and the real C++17 syntax smoke; record results in
  `docs/architecture/ir-phase5-status.md`.

- 新增 typed `IntDivRem`，支持 `IDIV` / `IREM`。除数为零时抛出
  `ArithmeticException`；若位于 try 内则进入 phase 4 的共享 catch dispatch。
  对有符号溢出组合做显式保护，保持 JVM 结果且不执行 C++ 未定义运算。
- 新增 `NEWARRAY T_INT` 的 typed `NewArray`，使用 `env->NewIntArray`，并检查负长度、
  空返回值与 pending exception。
- 新增描述符为 `I` 的 `GETSTATIC` / `PUTSTATIC`，复用现有 class/field cache 与静态
  int JNI 调用。
- 保持整方法验证先于发射；非 `I` 静态字段及其他不支持形式在缓存、输出、metadata 或
  `ACC_NATIVE` 发生变化前回退。
- 扩充聚焦测试与真实 C++17 语法冒烟；结果记录于
  `docs/architecture/ir-phase5-status.md`。

## (b) Ship-ready? / 是否可直接上线

**No.** The IR backend remains opt-in and incomplete. Switches, object and most
array creation, constructors, wide carriers, broader invocation/field/array
shapes, and many stack/control instructions still use per-method fallback.
Broader runtime parity remains future work. The default stays `legacy`.

**否。** IR 后端仍为 opt-in 且覆盖不完整。switch、对象与大多数数组创建、构造器、
wide carrier、更广泛的调用/字段/数组形态以及许多栈与控制指令仍按方法回退；更全面的
运行时等价性验证仍待后续完成。默认仍为 `legacy`。

## (c) Review required? / 是否需要 review

**Yes.** Please review the zero/overflow control flow, exceptional-edge routing,
allocation failure routing, static-field cache identity, and
fallback-before-mutation guarantees. The implementation deliberately does not
change constructor policy, the default mode, or existing snippet resources.

**是。** 请重点审阅除零/溢出控制流、异常边路由、数组分配失败路由、静态字段缓存身份，
以及“变更前回退”保证。本实现有意不改变构造器策略、默认模式或现有 snippet 资源。

## (d) Preconditions and evidence / 前置条件与证据

1. Review this branch against `cursor/ir-phase4-fable-review-6d81`.
   请基于 `cursor/ir-phase4-fable-review-6d81` 审阅本分支。
2. Run:
   `./gradlew :obfuscator:test --tests by.radioegor146.ir.IrCompilerTest --tests by.radioegor146.CodegenModeTest`.
   The recorded JUnit XML is `IrCompilerTest` 22/22 and `CodegenModeTest` 2/2:
   24 total, 0 skipped, 0 failures, 0 errors.
   请运行上述命令并核对 JUnit XML：两组分别为 22/22 与 2/2，共 24 个测试，0 跳过、
   0 失败、0 错误。
3. Confirm `generatedCppPassesGppSyntaxCheckWhenToolchainAvailable` is not
   skipped. It compiled an 18-method translation unit containing all four new
   methods with `g++ -std=c++17 -fsyntax-only`; an independent recompile of that
   exact generated file also exited zero.
   请确认 g++ 用例未跳过：包含四个新增方法的 18-method 翻译单元已通过真实
   `g++ -std=c++17 -fsyntax-only`，同一生成文件的独立重编译也返回 0。
4. Retain `rejectsIntStoreIntoInstanceReceiverLocal` and
   `rejectsNonIntStaticFieldBeforeMutation`; both prove rejection precedes
   native-method and shared-cache mutation.
   请保留这两个回归用例；它们证明拒绝发生在 native method 与共享缓存变更之前。
