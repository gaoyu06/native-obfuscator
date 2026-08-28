# Review the IR evaluator integer ISA extension / 审阅 IR evaluator 整数 ISA 扩展

## (a) Change scope / 改动范围

Independently review the opt-in shared evaluator support for `IAND`, `IOR`,
`IXOR`, `ISHL`, `ISHR`, and `IUSHR`. The review covers Java/C++ opcode
agreement (`0x13`–`0x18`), JVM shift and 32-bit semantics,
fallback-before-mutation, unchanged defaults, and per-method legacy fallback.
The findings are recorded in
`docs/architecture/ir-evaluator-ushr-review.md`. No compiler code or benchmark
claims are changed by this review branch.

独立审阅可选共享 evaluator 对 `IAND`、`IOR`、`IXOR`、`ISHL`、`ISHR` 和
`IUSHR` 的支持。审阅范围包括 Java/C++ opcode 一致性（`0x13`–`0x18`）、JVM
移位与 32 位语义、改写前回退、默认值不变，以及逐方法 legacy 回退。审阅结论记录在
`docs/architecture/ir-evaluator-ushr-review.md`。本审阅分支不修改编译器代码，也不
改动任何基准测试结论。

## (b) Ship-ready? / 是否可直接上线

**No.** The technical verdict is accept, but the evaluator remains opt-in via
`--codegen=ir --ir-lower=eval`, and the parent has not yet accepted this review.
The defaults remain `legacy` codegen and `direct` IR lowering.

**否。** 技术结论为 accept，但 evaluator 仍需通过
`--codegen=ir --ir-lower=eval` 显式启用，且父级审阅尚未接受本次结论。默认值仍为
`legacy` codegen 与 `direct` IR lowering。

## (c) Review required? / 是否需要 review

**Yes.** Parent review is required for the evidence and accept verdict. In
particular, confirm the cross-language ISA mapping, shift semantics, mutation
boundary, defaults, and fallback path described in the review document.

**是。** 证据与 accept 结论仍需父级审阅，尤其需要确认审阅文档中的跨语言 ISA
映射、移位语义、改写边界、默认值和回退路径。

## (d) Review preconditions / 审阅前提

- The review branch starts from
  `21f474d3983cbcb015bb787615112135740a048b`.
- Re-run command:
  `CC=gcc CXX=g++ ./gradlew :obfuscator:test --tests by.radioegor146.CodegenModeTest --tests by.radioegor146.ir.IrCompilerTest --tests by.radioegor146.ir.backend.InterpreterStreamStrategyTest --rerun-tasks`.
- Actual result: **28/28 passed**, 0 skipped, 0 failures, 0 errors
  (`CodegenModeTest` 4/4, `IrCompilerTest` 17/17,
  `InterpreterStreamStrategyTest` 7/7). Both g++-gated evaluator tests ran.
- Unsupported evaluator IR must continue to reject before method, output,
  registration, or cache mutation, then use the existing per-method legacy
  fallback.
- Parent acceptance is still required before changing the ship-ready answer.

- 审阅分支基于 `21f474d3983cbcb015bb787615112135740a048b`。
- 复跑命令：
  `CC=gcc CXX=g++ ./gradlew :obfuscator:test --tests by.radioegor146.CodegenModeTest --tests by.radioegor146.ir.IrCompilerTest --tests by.radioegor146.ir.backend.InterpreterStreamStrategyTest --rerun-tasks`。
- 实际结果：**28/28 通过**，0 skipped、0 failures、0 errors
  （`CodegenModeTest` 4/4、`IrCompilerTest` 17/17、
  `InterpreterStreamStrategyTest` 7/7）；两个依赖 g++ 的 evaluator 测试均实际运行。
- 不支持的 evaluator IR 必须继续在修改方法、输出、注册信息或缓存之前拒绝，随后使用
  现有逐方法 legacy 回退。
- 在父级接受审阅结论之前，ship-ready 仍保持 No。
