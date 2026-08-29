# Phase 12 IR admission measurement / Phase 12 IR 接纳率测量

## (a) Change scope / 改动范围

**English**

This is documentation and measurement only. It adds a two-class,
Java-8-targeted corpus under `docs/measurement/ir-admission-phase12/` and the
reproducible report `docs/benchmarks/ir-admission-phase12.md`. The corpus
exercises a constructor, integer/long/reference fields, an interface default
method, an interface call, and an unsupported float-conversion opcode.

On the compiler/runtime tip from draft PR #89, the exact six-method input
produced five IR markers and one logged per-method fallback. The report records
every method and descriptor, the exact fallback diagnostic, commands,
toolchain, date, input JAR hash, and the one-tip scope limitation. It makes no
speedup or production-coverage claim. No compiler/runtime source, default, or
snippet resource is changed.

**中文**

本改动仅包含文档与测量。在
`docs/measurement/ir-admission-phase12/` 下新增一个以 Java 8 为目标的双类语料，
并新增可复现报告 `docs/benchmarks/ir-admission-phase12.md`。语料覆盖构造函数、
整数/长整数/引用字段、接口 default 方法、接口调用，以及不受支持的浮点转换
opcode。

在草稿 PR #89 的 compiler/runtime tip 上，这个包含六个方法的精确输入产生了
五个 IR marker 与一条逐方法 fallback 日志。报告逐项记录方法与 descriptor、
原始 fallback 诊断、命令、工具链、日期、输入 JAR hash 及单一 tip 的范围限制。
本文不声称存在加速或生产覆盖率，也未修改 compiler/runtime 源码、默认值或
snippet resource。

## (b) Can this ship to production as-is? / 是否可直接上线？

**No / 否。**

**English**

This is a controlled measurement, not a production compiler change or a
production-readiness gate. The observed five-to-one split applies only to the
named two-class JAR on one draft PR #89 tip and does not establish wider
coverage, runtime parity, or performance.

**中文**

这是受控测量，并非生产编译器改动或生产就绪门禁。观测到的五比一结果仅适用于
草稿 PR #89 单一 tip 上指定的双类 JAR，不能证明更广泛的覆盖率、运行时等价性
或性能。

## (c) Is review required? / 是否需要 review？

**Yes / 是。**

**English**

Review should confirm that the inventory contains exactly the six input
methods, each table row matches its JVM descriptor, all five IR classifications
have generated IR markers, and the one fallback reason is copied exactly from
the compiler log. Review should also confirm that generated `<clinit>` methods
are excluded and that no production source or default changed.

**中文**

Review 应确认输入清单恰好包含六个方法、表中每行与其 JVM descriptor 一致、
五项 IR 分类均有生成的 IR marker，且唯一 fallback 原因逐字复制自 compiler
日志。还应确认工具生成的 `<clinit>` 未计入输入方法，并且生产源码与默认值
均未改变。

## (d) Review evidence / Review 证据

**English**

1. Compare against `cursor/ir-phase12-sol-review-6d81` at
   `481b7b108388380bfbbdf94703ee56eb4b601b02`.
2. Run the build, corpus compilation, `javap`, opt-in CLI, marker count, and
   fallback count exactly as listed in
   `docs/benchmarks/ir-admission-phase12.md`.
3. Confirm the observed counts are six input methods, five IR methods, and one
   per-method fallback.
4. Confirm the fallback log names
   `measurement/phase12/AdmissionTarget#unsupported(I)I` and reports
   `Unsupported instruction for phase-two IR at bytecode instruction 3 (opcode 134)`.
5. Inspect the branch diff and confirm it contains only this PR body, the
   benchmark report, and the two measurement-only Java sources.

**中文**

1. 与 `cursor/ir-phase12-sol-review-6d81` 的
   `481b7b108388380bfbbdf94703ee56eb4b601b02` 比较。
2. 严格按照 `docs/benchmarks/ir-admission-phase12.md` 中列出的命令运行构建、
   语料编译、`javap`、显式 IR CLI、marker 计数与 fallback 计数。
3. 确认观测计数为六个输入方法、五个 IR 方法和一个逐方法 fallback。
4. 确认 fallback 日志指向
   `measurement/phase12/AdmissionTarget#unsupported(I)I`，且报告
   `Unsupported instruction for phase-two IR at bytecode instruction 3 (opcode 134)`。
5. 检查分支 diff，确认仅包含本 PR body、benchmark 报告和两个仅用于测量的
   Java 源文件。
