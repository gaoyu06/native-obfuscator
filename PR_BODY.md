# IR evaluator backend compiler review / IR evaluator 后端编译器审阅

This branch reviews
[`cursor/ir-evaluator-backend-6d81-875b` (PR #42)](https://github.com/gaoyu06/native-obfuscator/pull/42)
on top of
[`cursor/ir-phase4-fable-review-6d81` (PR #39)](https://github.com/gaoyu06/native-obfuscator/pull/39).
It adds the review report and this bilingual handoff only; no compiler code
changed because no correctness blocker was found.

本分支审阅基于
[`cursor/ir-phase4-fable-review-6d81`（PR #39）](https://github.com/gaoyu06/native-obfuscator/pull/39)
的 [`cursor/ir-evaluator-backend-6d81-875b`（PR #42）](https://github.com/gaoyu06/native-obfuscator/pull/42)。
本分支仅新增审阅报告与本双语说明；因未发现正确性阻塞项，未修改编译器代码。

## (a) Verdict and scope / 结论与范围

**Accept with nits (`accept-with-nits`).**

The Java serializer, C++ evaluator, trampoline shell, CLI selection, generated
native integration, and requested tests were reviewed against
`docs/architecture/ir-compiler.md` §9.3 and
`docs/architecture/ir-evaluator-backend.md`. The ISA agrees on every opcode and
field; phi edge copies are parallel; integer arithmetic wraps as the JVM
requires; evaluator validation and serialization finish before method mutation;
and the legacy/direct defaults remain unchanged.

**接受，但有非阻塞小问题（accept-with-nits）。**

已按 `ir-compiler.md` §9.3 与 `ir-evaluator-backend.md` 审阅 Java 序列化端、C++
evaluator、trampoline shell、CLI 选择、生成工程集成及指定测试。两端所有 opcode 与
字段一致；phi 边复制为并行复制；整数运算按 JVM 规则回绕；能力检查和序列化早于方法
改写；legacy/direct 默认值保持不变。

## (b) Ship-ready? / 是否可直接上线

**No.** This remains an opt-in, intentionally narrow static-integer lowering,
with established per-method fallback for the rest of the IR. Broader runtime
parity remains a release gate. The reviewed slice itself has no known
correctness blocker.

**否。** 当前仍是可选且有意收窄的静态整数 lowering；其余 IR 继续逐方法回退。更广泛的
运行时一致性仍是发布门槛，但本次已审阅切片没有已知正确性阻塞项。

## (c) Review status / 审阅状态

**This branch IS the review.** The requirement-by-requirement evidence and nits
are in `docs/architecture/ir-evaluator-review.md`. No GitHub PR was opened for
this review branch.

**本分支即为 review。** 逐项证据与非阻塞小问题详见
`docs/architecture/ir-evaluator-review.md`。本审阅分支未创建 GitHub PR。

## (d) Verification / 验证

1. Required Gradle command: **BUILD SUCCESSFUL**.
   JUnit XML: `CodegenModeTest` 4/4, `IrCompilerTest` 17/17,
   `InterpreterStreamStrategyTest` 6/6; total **27/27**, 0 skipped,
   0 failures, 0 errors.
2. All three selected tests that invoke g++ actually ran: direct generated-C++
   syntax, evaluator translation-unit syntax, and the linked evaluator runtime
   harness. None was skipped.
3. Exact CLI generation with `--codegen=ir --ir-lower=eval` produced
   `ir_method_data` + `evaluate_i32` trampolines for both `add` and `sumTo`,
   with no straight-line arithmetic body. The complete generated CMake project
   compiled and linked with g++.
4. Exact default CLI generation produced the legacy body; `--codegen=ir`
   without `--ir-lower` produced the existing direct-IR body. Neither generated
   evaluator runtime files.
5. ISA constants match (`0x01`, `0x02`, `0x10`–`0x12`, `0x20`–`0x22`);
   two-phase temporary staging preserves phi parallel-copy semantics.
   A separate g++ harness confirmed add/subtract/multiply wrap boundaries.
6. No correctness blockers were fixed because none were found. Nits:
   `supports(...)` is not consulted by the compiler, the full generated CMake
   build is not automated, focused branch/wrap boundary coverage is thin, and
   one status sentence describes selection timing too broadly.

1. 指定 Gradle 命令 **BUILD SUCCESSFUL**。JUnit XML：`CodegenModeTest` 4/4、
   `IrCompilerTest` 17/17、`InterpreterStreamStrategyTest` 6/6；合计
   **27/27**，0 skipped、0 failures、0 errors。
2. 三个调用 g++ 的测试均真实执行：direct 生成 C++ 语法检查、evaluator 翻译单元
   语法检查、已链接 evaluator 运行 harness；均未跳过。
3. 精确 CLI 参数 `--codegen=ir --ir-lower=eval` 下，`add` 与 `sumTo` 均生成
   `ir_method_data` + `evaluate_i32` trampoline，不含直线算术函数体；完整生成
   CMake 工程已用 g++ 编译并链接成功。
4. 默认 CLI 仍生成 legacy body；只指定 `--codegen=ir` 时仍生成 direct-IR body；
   两者都不生成 evaluator runtime 文件。
5. ISA 常量完全一致（`0x01`、`0x02`、`0x10`–`0x12`、`0x20`–`0x22`）；
   两阶段临时寄存器 staging 保证 phi 并行复制。额外 g++ harness 已验证加、减、乘
   溢出边界。
6. 未发现正确性阻塞项，因此没有 blocker 修复。小问题为：编译器未调用
   `supports(...)`、完整生成 CMake 编译尚未自动化、分支/溢出边界测试偏少，以及状态
   文档一句话对选择时机描述略宽。
