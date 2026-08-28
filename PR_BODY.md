# IR vs legacy JNI benchmark / IR 与 legacy JNI 基准测试

## English

- **(a) Scope:** Extends the existing benchmark harness to run the same
  kernels, warmups, iterations, and checksum checks on plain JVM,
  `--codegen=legacy`, and `--codegen=ir`. It captures transpiler logs,
  classifies each measured method as IR or legacy fallback, adds one small
  deterministic int-only kernel because all original kernels fell back, and
  records the measured local samples, medians, means, environment, and native
  build status in `docs/benchmarks/results-ir-vs-legacy.md`.
- **(b) Ship-ready? No.** This is local diagnostic benchmark evidence, not a
  release gate or portable result.
- **(c) Review required? Yes.** Review the harness changes, checksum coverage,
  method-path evidence, and interpretation of the measurements.
- **(d) Preconditions:** Re-run on controlled hardware before using the
  results. Do not treat these numbers as portable. Confirm IR-versus-legacy
  performance only for methods whose generated bodies actually stayed on the
  IR path; fallback timings are not IR performance.

Both recorded native builds used GCC/g++ and passed CMake compilation, JNI
execution, and checksum validation. In this run only
`IrFriendlyIntKernel.run(I)I` stayed on IR.

## 中文

- **(a) 范围：** 扩展现有基准框架，以相同内核、预热次数、测量次数和校验和检查运行
  plain JVM、`--codegen=legacy` 与 `--codegen=ir`。框架会保存转译日志，判定每个被测
  方法实际走 IR 还是 legacy fallback。由于原有内核全部 fallback，本次增加了一个小型、
  确定性的纯 `int` 内核，并在 `docs/benchmarks/results-ir-vs-legacy.md` 中记录本机实测的
  全部样本、中位数、平均值、环境信息和原生构建状态。
- **(b) 可直接发布？否。** 这些数据只是本地诊断证据，不是发布门槛，也不具备跨机器
  可移植性。
- **(c) 是否需要评审？是。** 需要评审基准框架改动、校验和覆盖、方法路径证据以及对
  测量结果的解释。
- **(d) 前置条件：** 使用结果前必须在受控硬件上重新运行；不得把这些数字视为可移植
  结论；只有确认生成的方法体确实保持在 IR 路径上，才可进行 IR 与 legacy 的性能比较，
  fallback 的时间不能视作 IR 性能。

两次记录的原生构建均使用 GCC/g++，并通过 CMake 编译、JNI 执行和校验和验证。本次运行中
只有 `IrFriendlyIntKernel.run(I)I` 保持在 IR 路径。
