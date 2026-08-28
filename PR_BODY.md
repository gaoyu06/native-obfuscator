# IR shared-evaluator compiler artifact / IR 共享 evaluator 编译器产物

This branch prepares only a live, stripped compiler artifact from
`--codegen=ir --ir-lower=eval` on top of
[`cursor/ir-evaluator-review-6d81` (PR #44)](https://github.com/gaoyu06/native-obfuscator/pull/44).
It contains no packing and no reader, recovery, or scoring pass.

本分支基于
[`cursor/ir-evaluator-review-6d81`（PR #44）](https://github.com/gaoyu06/native-obfuscator/pull/44)，
仅准备由 `--codegen=ir --ir-lower=eval` 生成的活跃、全剥离编译器产物；不含 packing，
也不含 reader、recovery 或评分工作。

## (a) Change scope / 改动范围

- Publish `published.so`, `published.jar`, `run.md`, and `liveness.md` under
  `docs/eval/ir-eval-lower/`.
- Keep `mix(II)I` on the shared evaluator with serialized method data and an
  `evaluate_i32` trampoline.
- Build the ordinary generated JNI/C++ project as GCC Release and run
  `strip --strip-all`.
- Do not change compiler code, pack the artifact, or write a recovery report.

- 在 `docs/eval/ir-eval-lower/` 下发布 `published.so`、`published.jar`、
  `run.md` 与 `liveness.md`。
- 保证 `mix(II)I` 以序列化 method data 与 `evaluate_i32` trampoline 的形式留在
  shared evaluator 上。
- 用 GCC Release 构建普通生成的 JNI/C++ 工程，并执行 `strip --strip-all`。
- 不修改编译器、不打包产物，也不撰写 recovery 报告。

## (b) Can this ship to production as-is? / 是否可直接上线

**No.** This is a controlled evaluation subject, not a production release.

**否。** 这是受控评估样本，不是生产发布版本。

## (c) Is review required? / 是否需要 review

**Yes.** A later independent reader must first confirm the published bytes pass
the documented stdout and liveness gates before drawing recovery conclusions.

**是。** 后续独立 reader 必须先确认已发布字节通过文档中的 stdout 与存活性门槛，再得出
任何 recovery 结论。

## (d) Review preconditions and evidence / Review 前置条件与证据

1. Require byte-identical Java-oracle and native-jar stdout (`cmp` exit 0).
2. Require six `mix` cases, at least four distinct outputs, and a nonzero output.
3. Confirm the native `mix` symbol is a trampoline and that the evaluator plus
   serialized blob retain live integer arithmetic and branches.
4. Treat this branch only as compiler-artifact preparation; it supplies no
   reader or recovery result.

1. Java oracle 与 native jar 的 stdout 必须逐字节一致（`cmp` 退出码 0）。
2. 必须有六组 `mix` 输入、至少四个不同输出，且输出不能全为零。
3. 必须确认 native `mix` 符号是 trampoline，且 evaluator 与序列化 blob 中保留活跃整数
   算术和分支。
4. 本分支只能视为编译器产物准备，不提供 reader 或 recovery 结论。
