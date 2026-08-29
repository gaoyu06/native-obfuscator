# EN

## (a) Scope

This change admits one constructor-split leftover only: a try range wholly
inside one of 2–8 path-id distinct suffixes may target an already-proven
isolated prefix handler. The independent typed-CFG IR body receives the cloned
handler, optional isolated return block, and remapped exception-table entry.
The wrapper omits them and continues to use one hidden bridge with unchanged
path-id dispatch. Identical-copy normalization remains prefix-table-only.

## (b) Ship-ready?

**No.** The production migration is not complete.

## (c) Review and gate

The parent will re-run the focused Gradle suite for `IrCompilerTest` and
`CodegenModeTest`. Added tests:

- `admitsRelocatedPrefixReturnHandlersOnTwoDistinctSuffixes()`
- `rejectsRelocatedPrefixHandlerSpanningDistinctSuffixesBeforeMutation()`
- `relocatedPrefixHandlerDistinctMultiSuperCompilesAndRunsWithJavaParity()`

## (d) Preconditions

All remaining constructor leftovers stay reject-before-mutation. `--codegen`
stays `legacy`.

# 中文

## (a) 范围

本变更只接纳一个构造器拆分遗留形状：在 2–8 个 path-id 独立后缀中，完全位于同一
后缀内的 try 区间，可以指向已经证明安全的隔离前缀 handler。独立的 typed-CFG IR
方法会克隆该 handler、可选的隔离 `RETURN` 块，并重映射异常表；字节码 wrapper
不会保留它们，仍通过原有 path-id 分派调用同一个隐藏 bridge。相同后缀副本的
单-join 归一化仍只接纳纯前缀异常表。

## (b) 是否可发布？

**否。** 生产迁移目标尚未完成。

## (c) 审查与门禁

父代理将重新运行包含 `IrCompilerTest` 与 `CodegenModeTest` 的聚焦 Gradle
测试。新增测试：

- `admitsRelocatedPrefixReturnHandlersOnTwoDistinctSuffixes()`
- `rejectsRelocatedPrefixHandlerSpanningDistinctSuffixesBeforeMutation()`
- `relocatedPrefixHandlerDistinctMultiSuperCompilesAndRunsWithJavaParity()`

## (d) 前置条件

其余构造器遗留形状继续在修改前拒绝；`--codegen` 保持为 `legacy`。
