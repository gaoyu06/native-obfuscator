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
- rebased onto current `master` after #255 so `ILOAD` indexes and extra-local
  `int[]` `IALOAD` copies both remain

## Verification

- `CC=gcc CXX=g++ ./gradlew :obfuscator:test --rerun-tasks --tests
  by.radioegor146.ir.IrCompilerTest --tests
  by.radioegor146.CodegenModeTest`
- Parent re-run XML on this rebased branch will replace the pre-#254 child
  count. Do not treat the old 438 figure as current.
- combined-JAR runtime parity:
  `threeImmediateExtraLocalIntFamilyArrayLoadsCompileAndRunWithJavaParity`

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
- 已变基到包含 #255 的当前 `master`，`ILOAD` 下标与额外局部 `int[]`
  `IALOAD` 同时保留

## 验证

- `CC=gcc CXX=g++ ./gradlew :obfuscator:test --rerun-tasks --tests
  by.radioegor146.ir.IrCompilerTest --tests
  by.radioegor146.CodegenModeTest`
- 父进程将在变基后的分支上重跑 XML，以替换 #254 之前的子代理计数。
  请勿把旧的 438 当作当前数字。
- 组合 JAR 运行时一致性测试：
  `threeImmediateExtraLocalIntFamilyArrayLoadsCompileAndRunWithJavaParity`

## 发布状态

Ship-ready: No（否）。
