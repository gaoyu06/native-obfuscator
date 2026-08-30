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

- `CC=gcc CXX=g++ ./gradlew :obfuscator:test --rerun-tasks --tests
  by.radioegor146.ir.IrCompilerTest --tests
  by.radioegor146.CodegenModeTest`
- JUnit XML: `IrCompilerTest` 446 tests and `CodegenModeTest` 7 tests; total
  453 tests, 0 failures, 0 errors, 0 skipped.
- `threeImmediateExtraLocalWidePrimitiveArrayLoadsCompileAndRunWithJavaParity()`
  passed, including CMake/g++ JNI generation and plain-Java stdout parity.

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

- 已通过 `CC=gcc CXX=g++ ./gradlew :obfuscator:test --rerun-tasks --tests
  by.radioegor146.ir.IrCompilerTest --tests
  by.radioegor146.CodegenModeTest`。
- JUnit XML：`IrCompilerTest` 446 项，`CodegenModeTest` 7 项；共 453 项，
  0 失败、0 错误、0 跳过。
- `threeImmediateExtraLocalWidePrimitiveArrayLoadsCompileAndRunWithJavaParity()`
  已通过，包括 CMake/g++ JNI 生成及与普通 Java 标准输出的一致性。

## 发布状态

Ship-ready: No（否）。
