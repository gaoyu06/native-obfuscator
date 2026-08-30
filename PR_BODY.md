# English

## Summary

- admit constant-index `LALOAD`, `FALOAD`, and `DALOAD` constructor chain-input
  leaves sourced from one proven prefix extra-local `ALOAD`/`ASTORE` copy of an
  unchanged declared `[J`, `[F`, or `[D` argument
- retain the array copy and every array load in JVM bytecode, preserving null,
  bounds, and category-two behavior while sharing one hidden bridge
- keep computed or `ILOAD` indexes, combined extra-array plus extra-index forms,
  computed stores, overwritten copies or declared sources, prior array stores,
  mismatched sources, skip-super forms, and seventeen-level trees rejected
- leave the CLI `--codegen` default on `legacy`

## Rebase

- No rebase was needed; the branch was created from `origin/master` at
  `af0b0d8` after #257 landed.

## Verification

- Pending final scoped Gradle verification and JUnit XML totals.

## Release status

Ship-ready: No.

# 中文

## 摘要

- 允许构造器链调用输入使用常量索引的 `LALOAD`、`FALOAD` 与 `DALOAD` 叶子；
  其来源必须是未修改的已声明 `[J`、`[F` 或 `[D` 参数经一次已证明的前缀
  `ALOAD`/`ASTORE` 额外局部复制
- 数组复制和全部数组读取继续保留在 JVM 字节码中，从而保留空指针、越界及
  category-two 行为，并共享一个隐藏桥接方法
- 继续拒绝计算索引或 `ILOAD` 索引、额外数组与额外索引组合、计算式存储、
  被覆盖的复制或声明来源、之前的数组写入、来源类型不匹配、跳过父构造器及
  十七层树
- CLI 的 `--codegen` 默认值仍为 `legacy`

## 变基

- 无需变基；该分支在 #257 合入后从 `origin/master` 的 `af0b0d8` 创建。

## 验证

- 最终限定 Gradle 验证及 JUnit XML 汇总待完成。

## 发布状态

Ship-ready: No（否）。
