# English

## Summary

- widen the proven constructor-prefix array-copy map from reference arrays and
  `int[]` to matching `byte[]`, `boolean[]`, `char[]`, and `short[]` arguments
- admit constant-index `BALOAD`, `CALOAD`, and `SALOAD` chain-input leaves from
  one dominating direct `ALOAD`/`ASTORE` extra-local copy
- retain every array load in JVM bytecode and preserve exact opcode/type
  pairing, overwrite, array-store, computed-source, and index rejection gates
- add admit, reject-before-mutation, Java 8 verifier, and combined-JAR
  Java/native parity coverage for all four primitive array types

## Verification

- Pending final scoped Gradle verification and JUnit XML totals.

## Release status

Ship-ready: No.

# 中文

## 摘要

- 将构造器前缀中已证明的数组复制映射，从引用数组和 `int[]` 扩展到类型匹配的
  `byte[]`、`boolean[]`、`char[]` 与 `short[]` 参数
- 允许通过一次支配所有路径的直接 `ALOAD`/`ASTORE` 额外局部复制，使用常量索引
  的 `BALOAD`、`CALOAD` 与 `SALOAD` 作为链调用输入叶子
- 所有数组读取仍保留在 JVM 字节码中，并继续严格拒绝类型不匹配、覆盖、数组写入、
  计算来源和非常量索引
- 为四种原始数组类型补充准入、拒绝前不变异、Java 8 校验器，以及组合 JAR 的
  Java/native 一致性覆盖

## 验证

- 最终限定 Gradle 验证及 JUnit XML 汇总待完成。

## 发布状态

Ship-ready: No（否）。
