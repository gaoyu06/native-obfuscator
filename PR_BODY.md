# Re-measure evaluator lowering after `IUSHR` / `IUSHR` 后重新测量 evaluator lowering

## (a) Change scope / 改动范围

Integrate the classified benchmark harness onto the evaluator-`IUSHR` tip and
measure `IrFriendlyIntKernel.run(I)I` on plain JVM, legacy JNI, direct IR JNI,
and evaluator IR JNI. Add the complete raw samples, medians, means,
environment, checksums, and path/fallback evidence in
`docs/benchmarks/results-ir-eval-ushr.md`.

在 evaluator-`IUSHR` 最新版本上集成带路径分类的基准框架，并测量
`IrFriendlyIntKernel.run(I)I` 的 plain JVM、legacy JNI、direct IR JNI 与
evaluator IR JNI 路径。`docs/benchmarks/results-ir-eval-ushr.md` 记录全部原始
样本、中位数、均值、环境、校验和以及路径/回退证据。

## (b) Ship-ready? / 是否可直接上线

**No.** This is one local diagnostic measurement on one VM. It is not a
portable performance result, and the evaluator remains opt-in.

**否。** 这是单台 VM 上的一次本地诊断测量，不是可移植的性能结论；evaluator 仍为
可选路径。

## (c) Review required? / 是否需要 review

**Yes.** Review the harness integration, identical native warmup/iteration
counts, method-path evidence, raw sample transcription, and the absence of a
target-method `IUSHR` fallback.

**是。** 需要审阅基准框架集成、所有原生模式一致的 warmup/iteration 次数、方法路径
证据、原始样本抄录，以及目标方法不存在 `IUSHR` 回退。

## (d) Verification / 验证

With `BENCH_WARMUP=5 BENCH_ITERATIONS=10 CC=gcc CXX=g++`, all native
pipelines passed transpilation, CMake/g++ compilation, JNI execution, and
checksum comparison. Every JVM/native run returned checksum `2,038,221,507`.
Recorded medians were:

使用 `BENCH_WARMUP=5 BENCH_ITERATIONS=10 CC=gcc CXX=g++` 时，所有原生路径均
通过 transpile、CMake/g++ 编译、JNI 执行与校验和比对。全部 JVM/原生运行的校验和
均为 `2,038,221,507`。实测中位数如下：

- JVM: `10,017,146.0 ns`
- legacy JNI: `167,870,311.5 ns`
- direct IR JNI: `10,021,957.0 ns`
- evaluator IR JNI: `411,875,537.5 ns`

The generated evaluator source contains
`// IR evaluator data: benchmarks/kernels/IrFriendlyIntKernel.run(I)I`.
The eval transpile log has no fallback for that target method and no
`Unsupported evaluator binary operation USHR` record. No speedup or
portability claim is made.

生成的 evaluator 源码包含
`// IR evaluator data: benchmarks/kernels/IrFriendlyIntKernel.run(I)I`。
eval transpile 日志对该目标方法没有回退，也没有
`Unsupported evaluator binary operation USHR` 记录。本次结果不作加速或可移植性
声明。

`python3 -m py_compile benchmarks/run.py` passed. The focused
`CodegenModeTest`, `IrCompilerTest`, and `InterpreterStreamStrategyTest`
selection passed 28/28 with no failure or skip.

`python3 -m py_compile benchmarks/run.py` 通过。聚焦的 `CodegenModeTest`、
`IrCompilerTest` 与 `InterpreterStreamStrategyTest` 共 28/28 通过，无失败或跳过。
