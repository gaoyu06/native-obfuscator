# IR evaluator lowering benchmark / IR evaluator lowering 基准测试

## English

- **(a) Scope:** Combines the benchmark harness from
  `cursor/bench-ir-vs-legacy-6d81` with the evaluator compiler lineage from
  `cursor/ir-evaluator-review-6d81`, then measures only
  `IrFriendlyIntKernel.run(I)I` on plain JVM, legacy JNI, direct IR JNI, and
  evaluator IR JNI. The report records every sample, median, mean, environment,
  checksum, generated-path evidence, and fallback status.
- **(b) Ship-ready? No.** This is local compiler/benchmark evidence for a narrow
  integer slice, not a release gate or portable performance result.
- **(c) Review required? Yes.** Review the branch integration, mode selection,
  path classifier, equal benchmark settings, checksum validation, raw samples,
  and conclusions.
- **(d) Verification:** All native modes use identical warmup/iteration counts
  and `CC=gcc CXX=g++`. The final commands, environment, path evidence, and
  measured results are recorded in
  `docs/benchmarks/results-ir-eval-lower.md`. No GitHub PR was opened.

## 中文

- **(a) 范围：** 合并 `cursor/bench-ir-vs-legacy-6d81` 的基准框架与
  `cursor/ir-evaluator-review-6d81` 的 evaluator 编译器历史，仅测量
  `IrFriendlyIntKernel.run(I)I` 在 plain JVM、legacy JNI、direct IR JNI 和
  evaluator IR JNI 四种模式下的表现。报告记录全部样本、中位数、平均值、环境、
  校验和、实际生成路径证据及 fallback 状态。
- **(b) 可直接发布？否。** 这只是窄整数切片的本机编译器/基准证据，不是发布门槛，
  也不是可移植性能结果。
- **(c) 是否需要评审？是。** 需评审分支整合、模式选择、路径分类、统一基准参数、
  校验和验证、原始样本及结论。
- **(d) 验证：** 所有 native 模式使用完全相同的预热/迭代次数和
  `CC=gcc CXX=g++`。最终命令、环境、路径证据和实测结果记录在
  `docs/benchmarks/results-ir-eval-lower.md`。本分支未创建 GitHub PR。
