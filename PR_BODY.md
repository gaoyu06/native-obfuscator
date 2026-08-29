# English

## Scope

This change admits one narrow constructor-split shape on opt-in
`--codegen=ir`: exactly two reachable direct this/super constructor calls,
each immediately followed by `RETURN`. The existing identical-suffix proof
normalizes the first return to `GOTO` the canonical return, so the retained
bytecode wrapper keeps both chain-call paths and invokes one hidden bridge.
The independent IR body contains only `RETURN`; no uninitialized receiver is
lowered to IR.

Three-or-more separate returns, unequal suffixes, post-call work before a join,
paths with two chain calls, paths with no chain call, non-identity `ASTORE 0`,
and unsupported exception-region placements remain rejected.

## Release and review

- **Ship-ready: No.** This is one opt-in compiler admission increment, not a
  production-readiness claim.
- **No stacked review.** The change is based directly on current `master`
  after #172. The review gate is the focused compiler test command below.
- `--codegen` remains `legacy`; this change does not flip `--ir-lower` or
  `--backend`.

## Preconditions

Remaining constructor leftovers, unsafe `ConstantDynamic` forms, and
`jsr`/`ret` stay rejected before mutation. Those gaps must remain fail-closed,
and no default may change until their separate admission and release gates are
satisfied.

## Validation

Focused JUnit/CMake/g++ gate results will be recorded here after execution.

# 中文

## 范围

本变更仅在显式选择 `--codegen=ir` 时接纳一种严格限定的构造函数拆分形态：
恰好两个可达的直接 this/super 构造调用，并且每个调用后都立即执行
`RETURN`。现有的相同后缀证明会把第一个返回改写为跳转到规范返回点，因此保留
的字节码包装器继续包含两条构造调用路径，并且只调用一个隐藏桥接方法。独立
的 IR 方法体只包含 `RETURN`，不会把未初始化的接收者下沉到 IR。

三个或更多独立返回、不相同的后缀、调用后到汇合点前仍有工作、同一路径执行
两次构造调用、跳过所有构造调用、非恒等 `ASTORE 0`，以及不支持的异常区域
布局仍然拒绝。

## 发布与审查

- **可直接上线：否。** 这只是显式启用的编译器接纳增量，不代表生产就绪。
- **不采用堆叠审查。** 本变更直接基于包含 #172 的当前 `master`；审查门槛
  是下方的聚焦编译器测试命令。
- `--codegen` 默认值仍为 `legacy`，也不切换 `--ir-lower` 或 `--backend`。

## 前置条件

其余构造函数遗留形态、不安全的 `ConstantDynamic` 形态以及 `jsr`/`ret`
继续在修改前拒绝。这些缺口必须保持失败关闭；在分别完成接纳与发布门槛前，
不得切换任何默认值。

## 验证

聚焦的 JUnit/CMake/g++ 门槛结果将在实际执行后记录于此。
