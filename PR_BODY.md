# English

## (a) Scope

Admit only the constructor-split exception-table leftover for proven multi-super
forms:

- prefix-only tables on identical-copy normalization and bounded path-selected
  suffixes; and
- tables wholly owned by one nonempty path-selected suffix.

The wrapper retains prefix tables. The independent typed CFG IR body receives
only tables whose start, end, and handler labels are cloned from the same
suffix. Mixed, spanning, chain-covering, method-end-handler, and relocated
prefix-handler forms remain rejected before mutation.

## (b) Ship-ready?

**No.** This is one structural IR leftover only. The production migration is
not complete.

## (c) Review and gate

The parent will re-run:

```text
CC=gcc CXX=g++ ./gradlew :obfuscator:test --rerun-tasks \
  --tests by.radioegor146.ir.IrCompilerTest \
  --tests by.radioegor146.CodegenModeTest
```

Local JUnit XML from this branch records 242 `IrCompilerTest` cases and 7
`CodegenModeTest` cases, with zero failures, errors, or skips.

New tests:

- `admitsPrefixOnlyTryCatchOnThreeImmediateReturns()`
- `admitsPrefixOnlyTryCatchOnThreeDistinctSuffixes()`
- `admitsPrefixOnlyTryCatchOnTwoDistinctSuffixes()`
- `admitsWhollyInOneSuffixTryCatchOnTwoDistinctSuffixes()`
- `rejectsCrossSuffixAndChainCoveringMultiSuperTryCatchBeforeMutation()`
- `prefixOnlyMultiSuperTryCatchCompilesAndRunsWithJavaParity()`
- `suffixOnlyDistinctMultiSuperTryCatchCompilesAndRunsWithJavaParity()`

## (d) Preconditions

All remaining constructor leftovers continue to reject before compiler state,
hidden-method pools, or constructor bytecode are mutated. `--codegen` remains
`legacy`; no CLI default changes are included.

# 中文

## (a) 范围

本次只接纳构造方法拆分中的一个异常表遗留，并且仅限已经证明安全的多
this/super 调用形式：

- 相同后缀副本归一化和有界路径选择后缀中的纯前缀异常表；
- 完全属于一个非空路径选择后缀的异常表。

纯前缀异常表保留在字节码包装方法中。只有 `start`、`end`、`handler`
三个标签都从同一个后缀克隆时，异常表才进入独立的 typed CFG IR 方法。
前后缀混合、跨后缀、覆盖 this/super 调用、方法末尾 handler，以及需要
搬迁前缀 handler 的形式，仍会在任何修改发生前拒绝。

## (b) 可发布？

**否。** 这只是一个结构性 IR 遗留增量，生产迁移目标尚未完成。

## (c) 审查与门禁

父代理会重新运行上面的 Gradle 聚焦测试。新增测试名称如下：

本分支本地生成的 JUnit XML 记录为：`IrCompilerTest` 242 项、
`CodegenModeTest` 7 项，失败、错误及跳过均为 0。

- `admitsPrefixOnlyTryCatchOnThreeImmediateReturns()`
- `admitsPrefixOnlyTryCatchOnThreeDistinctSuffixes()`
- `admitsPrefixOnlyTryCatchOnTwoDistinctSuffixes()`
- `admitsWhollyInOneSuffixTryCatchOnTwoDistinctSuffixes()`
- `rejectsCrossSuffixAndChainCoveringMultiSuperTryCatchBeforeMutation()`
- `prefixOnlyMultiSuperTryCatchCompilesAndRunsWithJavaParity()`
- `suffixOnlyDistinctMultiSuperTryCatchCompilesAndRunsWithJavaParity()`

## (d) 前置条件

其余构造方法遗留仍必须在修改编译器状态、隐藏方法池或构造方法字节码之前
拒绝。`--codegen` 继续保持 `legacy`，本次不修改任何 CLI 默认值。
