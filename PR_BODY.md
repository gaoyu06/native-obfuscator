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

1. **PASS:** Java-oracle and native-jar stdout are byte-identical (`cmp` exit
   0).
2. **PASS:** six `mix` cases produced six distinct, nonzero outputs.
3. **PASS:** the stripped dynamic symbol table retains native `mix` and
   `evaluate_i32`; disassembly shows a single evaluator trampoline call.
4. **PASS:** the 497-byte blob decodes to live IADD/ISUB/IMUL, branch, and
   return opcodes; evaluator disassembly retains the matching handlers.
5. Treat this branch only as compiler-artifact preparation; it supplies no
   reader or recovery result.

1. **通过：** Java oracle 与 native jar 的 stdout 逐字节一致（`cmp` 退出码 0）。
2. **通过：** 六组 `mix` 输入得到六个不同且非零的输出。
3. **通过：** 全剥离动态符号表仍含 native `mix` 与 `evaluate_i32`；反汇编显示 `mix`
   仅通过一个 evaluator trampoline 调用执行。
4. **通过：** 497 字节 blob 可解码出活跃 IADD/ISUB/IMUL、分支与返回 opcode；
   evaluator 反汇编保留对应 handler。
5. 本分支只能视为编译器产物准备，不提供 reader 或 recovery 结论。
