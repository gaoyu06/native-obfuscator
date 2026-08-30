# English

## Summary

- Admits the composition of the already-proven constructor chain-input leaves
  for `AALOAD`, `IALOAD`, `BALOAD`, `CALOAD`, and `SALOAD`.
- The exact `(II[X)V` fixture shape is:
  `ALOAD 3; ASTORE 4; ILOAD 2; ISTORE 5`, followed at each chain call by
  `ALOAD 4; ILOAD 5; xALOAD`.
- The retained JVM prefix still executes both copies and every array load.
  Each constructor rewrites to one hidden bridge and one native method.
- Adds admission, reject-before-mutation, JVM-verification, and combined
  CMake/g++ Java-parity coverage for `AALOAD` and `IALOAD`; unit admission
  also covers `BALOAD` (byte/boolean), `CALOAD`, and `SALOAD`.

## Still rejected

Computed extra-local stores, overwritten copies, overwritten declared arrays,
prior array stores, wrong array sources, computed or `INEG` indexes,
unproven indexes, skip-super paths, and all other unlisted inputs remain
fail-closed. This does not admit extra-local `LALOAD`/`FALOAD`/`DALOAD`
sources or wide extra-index shapes.

The default `--codegen` mode remains `legacy`.

## Validation

The focused Gradle gate passed. JUnit XML records:

- `IrCompilerTest`: 446 tests, 0 failures, 0 errors, 0 skipped.
- `CodegenModeTest`: 7 tests, 0 failures, 0 errors, 0 skipped.
- Total: 453 tests, 0 failures, 0 errors, 0 skipped.

The passing runtime test is
`threeImmediateExtraArrayExtraIndexCompileAndRunWithJavaParity`.

Ship-ready: **No**

# 中文

## 摘要

- 组合放行构造器链调用中已经分别证明安全的 `AALOAD`、`IALOAD`、
  `BALOAD`、`CALOAD` 和 `SALOAD` 叶子。
- 精确的 `(II[X)V` 测试形状为：
  `ALOAD 3; ASTORE 4; ILOAD 2; ISTORE 5`，每个链调用随后执行
  `ALOAD 4; ILOAD 5; xALOAD`。
- 两次局部变量复制和所有数组读取仍由保留的 JVM 前缀执行；每个构造器
  只重写为一个隐藏桥接和一个 native 方法。
- 新增放行、修改前拒绝、JVM 验证，以及覆盖 `AALOAD` 和 `IALOAD` 的
  CMake/g++ Java 一致性测试；单元放行还覆盖 byte/boolean `BALOAD`、
  `CALOAD` 和 `SALOAD`。

## 仍然拒绝

计算得到的额外局部变量写入、被覆盖的复制、被覆盖的声明数组、先前数组
写入、错误数组来源、计算或 `INEG` 索引、未证明索引、跳过 super 的路径，
以及其他未列出的输入仍然保持失败关闭。本增量不放行额外局部变量来源的
`LALOAD`/`FALOAD`/`DALOAD`，也不放行宽类型数组的额外索引形状。

默认 `--codegen` 模式仍为 `legacy`。

## 验证

聚焦 Gradle 测试已通过。JUnit XML 记录：

- `IrCompilerTest`：446 个测试，0 失败，0 错误，0 跳过。
- `CodegenModeTest`：7 个测试，0 失败，0 错误，0 跳过。
- 合计：453 个测试，0 失败，0 错误，0 跳过。

通过的运行时测试为
`threeImmediateExtraArrayExtraIndexCompileAndRunWithJavaParity`。

可发布：**否（No）**
