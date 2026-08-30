# English

## Scope

Fail-closed, no admit.

Path-selected constructor suffixes use one singular `MethodContext.proxyMethod`
and one fixed hidden-bridge descriptor. If any selected suffix needs an extra,
every bridge-taking call site must satisfy that descriptor. A path where the
extra is unassigned therefore cannot omit the argument, and this change does
not synthesize or forward a placeholder value.

This increment:

- makes the distinct-suffix rejection fixture verifier-valid: the assigned path
  reads the extra, while the unassigned bridge-taking path does not;
- proves both original Java 8 paths load and execute under JVM verification;
- keeps that shape rejected before constructor instructions, generated source,
  hidden methods, or `MethodContext.proxyMethod` are mutated;
- strengthens the identical-suffix rejection with instruction-object identity
  checks; and
- documents why only the existing non-bridge immediate-prefix-return exception
  may leave a forwarded extra unassigned.

Skip-super remains rejected. The default `--codegen` mode remains `legacy`.

## Verification

```text
CC=gcc CXX=g++ ./gradlew :obfuscator:test --rerun-tasks \
  --tests by.radioegor146.ir.IrCompilerTest \
  --tests by.radioegor146.CodegenModeTest
```

Rebased onto `origin/master` `3d39c17` (post-#262 wide extra-array plus
extra-index). Parent-verified JUnit XML after rebase: `IrCompilerTest`
459/459 and `CodegenModeTest` 7/7, with zero failures, errors, or skips
(total 466). The verification test is
`unassignedExtraUnusedOnOneDistinctSuffixPassesJvmVerification`.

Ship-ready: **No**

# 中文

## 范围

保持失败关闭，不新增放行形状。

按路径选择的构造器后缀共用唯一的 `MethodContext.proxyMethod` 和固定的隐藏桥
描述符。只要任一后缀需要额外局部变量，每个会调用该桥的路径都必须满足同一
描述符。因此，额外局部变量未赋值的路径不能省略该参数；本次修改也不会合成
或转发占位值。

本增量：

- 将不同后缀的拒绝样例改为可通过 JVM 验证的合法字节码：已赋值路径读取该
  额外局部变量，未赋值但仍调用桥的路径不读取它；
- 验证原始 Java 8 类的两条路径都能在 JVM 验证后加载并执行；
- 保持该形状在修改构造器指令、生成源码、隐藏方法或
  `MethodContext.proxyMethod` 之前被拒绝；
- 用指令对象身份检查加强相同后缀拒绝测试；以及
- 记录只有现有的不调用桥的立即前缀返回例外，才允许转发的额外局部变量在
  该退出路径上未赋值。

跳过 super 的路径仍然拒绝。`--codegen` 默认模式仍为 `legacy`。

## 验证

已变基到 `origin/master` `3d39c17`（#262 宽数组额外源加额外索引之后）。
父代理变基后聚焦 JUnit XML：`IrCompilerTest` 459/459、`CodegenModeTest` 7/7，
失败、错误和跳过均为 0（合计 466）。验证测试为
`unassignedExtraUnusedOnOneDistinctSuffixPassesJvmVerification`。

可发布：**否**
