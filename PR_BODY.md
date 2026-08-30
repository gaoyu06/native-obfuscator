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
fail-closed. Extra-array plus extra-index for `LALOAD`/`FALOAD`/`DALOAD`
stays rejected. Extra-local wide-array sources (#258) and wide `ILOAD`
indexes (#259) stay admitted.

The default `--codegen` mode remains `legacy`.

## Rebase

Rebased onto `origin/master` `6d454f6` (post-#259). Child XML on the
pre-#258 merge-base is stale and is not the parent number.

## Validation

Parent will re-run the full focused `IrCompilerTest` + `CodegenModeTest`
suite on this rebased branch and report JUnit XML from
`obfuscator/build/test-results/test/`. Runtime name to confirm:
`threeImmediateExtraArrayExtraIndexCompileAndRunWithJavaParity()`.

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
以及其他未列出的输入仍然保持失败关闭。`LALOAD`/`FALOAD`/`DALOAD` 的
额外数组加额外索引组合仍拒绝。#258 的 extra-local 宽数组源与 #259 的
宽数组 `ILOAD` 索引保持接纳。

默认 `--codegen` 模式仍为 `legacy`。

## 变基

已变基到 `origin/master` `6d454f6`（#259 之后）。变基前的子代理 XML
不是父代理数字。

## 验证

父代理将在变基后的分支上重跑完整聚焦套件，并从
`obfuscator/build/test-results/test/` 读取 JUnit XML。运行时名称：
`threeImmediateExtraArrayExtraIndexCompileAndRunWithJavaParity()`。

可发布：**否（No）**
