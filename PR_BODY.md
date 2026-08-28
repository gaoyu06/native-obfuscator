# IR compiler phase 7 / IR 编译器第七阶段

Preferred base / 首选基线:
`cursor/ir-phase6-sol-review-6d81`
(`ac01e555aaa0109f61e98472dedd20f481643cf7`).

## (a) Scope / 范围

- Add dedicated typed IR nodes and C++ emission for `CHECKCAST` and
  `INSTANCEOF`. Null follows JVM semantics; failed casts raise
  `ClassCastException` and protected failures enter the phase-4 shared catch
  dispatcher.
- Resolve ordinary targets through the existing class cache and preserve array
  descriptors for `JNIEnv::FindClass`, including a
  `[Ljava/lang/String;` regression.
- Add `I64`/`jlong` support for `LLOAD`, `LSTORE`, `LCONST_0/1`, `LADD`,
  `LSUB`, `LMUL`, `LRETURN`, `I2L`, and `L2I`. Wide locals reserve both JVM
  slots, stack phi indices use physical slot widths, and arithmetic wraps
  through standard `uint64_t`.
- Keep whole-method admission ahead of all mutation. A mixed method containing
  supported long arithmetic and unsupported `LDIV` proves clean fallback.
- Extend the real generated-C++ smoke to 29 methods and run
  `g++ -std=c++17 -fsyntax-only` with JNI headers.

- 为 `CHECKCAST` 与 `INSTANCEOF` 新增专用 typed IR 节点及 C++ 发射。空引用遵循
  JVM 语义；失败 cast 抛出 `ClassCastException`，受保护失败进入 phase-4 共享
  catch dispatcher。
- 普通目标继续使用现有类缓存；数组描述符保持原样并交给 `JNIEnv::FindClass`，
  包含 `[Ljava/lang/String;` 回归。
- 新增 `I64`/`jlong` 支持：`LLOAD`、`LSTORE`、`LCONST_0/1`、`LADD`、
  `LSUB`、`LMUL`、`LRETURN`、`I2L` 与 `L2I`。宽局部变量占用两个 JVM
  slot，stack phi 按物理 slot 宽度编号，算术通过标准 `uint64_t` 实现回绕。
- 保持整方法能力判定先于任何 mutation；包含受支持 long 算术及不受支持 `LDIV`
  的混合方法证明可干净 fallback。
- 将真实生成 C++ 冒烟扩展至 29 个方法，并使用 JNI headers 执行
  `g++ -std=c++17 -fsyntax-only`。

## (b) Ship-ready? / 可直接发布？

**No / 否。**

This phase-7 slice passes focused unit and C++ syntax checks, but it is still an
opt-in staged subset. It does not replace supported-platform native
runtime-parity testing, and many bytecodes still fall back. Keep `legacy` as
the default.

本 phase-7 增量已通过聚焦单元测试及 C++ 语法检查，但仍是 opt-in 的阶段性子集，
不能替代受支持平台上的 native 运行时等价性测试，且仍有大量字节码需要 fallback。
默认值必须保持为 `legacy`。

## (c) Requested phase-7 slice complete? / 所要求的 phase-7 增量已完成？

**Yes / 是。**

The requested type-test nodes, protected cast failure routing, array-target
`FindClass`, two-slot long slice, fallback atomicity regression, focused tests,
real g++ smoke, status document, and recorded JUnit XML counts are all present.

所要求的类型测试节点、受保护 cast 失败路由、数组目标 `FindClass`、双 slot long
增量、fallback 原子性回归、聚焦测试、真实 g++ 冒烟、状态文档及 JUnit XML
计数均已提供。

## (d) Human preconditions / 人工前置条件

1. Compare and land this branch on `cursor/ir-phase6-sol-review-6d81`, not
   `master`, preserving the stacked branch order.
   必须基于 `cursor/ir-phase6-sol-review-6d81` 比较与落地，不得改用 `master`，
   并保持堆叠顺序。
2. Re-run the focused command and inspect JUnit XML. Recorded result:
   `IrCompilerTest` 33 plus `CodegenModeTest` 2, total 35; zero skipped,
   failures, or errors.
   重跑聚焦命令并检查 JUnit XML。记录结果为 33 + 2，共 35 个测试；跳过、失败、
   错误均为零。
3. Confirm the g++ testcase remains unskipped when g++ and JNI headers are
   present. The retained 29-method translation unit also passed an independent
   C++17 syntax-only invocation.
   当 g++ 与 JNI headers 存在时，确认该测试未跳过；保留的 29-method 翻译单元
   也已独立通过 C++17 syntax-only 调用。
4. Require final supported-platform/JDK CI and native runtime-parity checks
   before release.
   发布前必须通过最终受支持平台/JDK CI 及 native 运行时等价性检查。
5. During conflict resolution, retain the phase-6 array-component fix,
   type-test null semantics and catch routing, wide-slot invariants,
   fallback-before-mutation regressions, the `legacy` default, and all snippet
   resources.
   解决冲突时必须保留 phase-6 数组组件修复、类型测试空引用语义与 catch 路由、
   宽 slot 不变量、mutation 前 fallback 回归、`legacy` 默认值及全部 snippet
   资源。
