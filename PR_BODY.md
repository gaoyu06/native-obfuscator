# EN

## Summary

- Admits one fixture-only constructor-split shape: the same proven prefix extra-local float copy is loaded as both initializer arguments of an isolated two-argument `Point2D.Float` `NEW`.
- Adds admission, rewritten-JVM-verification, and Java/native parity coverage for `new-constructor-extra-local-float-both-arguments`.
- Processor changed: No.

## Scope

- This compose admits only `FLOAD 3; FLOAD 3` as the two `(FF)V` initializer inputs.
- It does not change codegen defaults or authorize a default flip.
- Leftover inventory #310 is planning inventory, not a JDK support badge.

## Test accounting

- Expected parent XML after landing: 554 tests total (`IrCompilerTest` 547 + `CodegenModeTest` 7).
- Ship-ready: **No**.

# 中文

## 摘要

- 仅通过测试夹具新增一种构造器拆分形态：将同一个已证明的前缀额外局部 `float` 副本，分别加载为隔离的双参数 `Point2D.Float` `NEW` 的两个初始化参数。
- 为 `new-constructor-extra-local-float-both-arguments` 增加入选、重写后 JVM 验证以及 Java/原生输出一致性测试。
- 是否修改处理器：否。

## 范围

- 本次组合仅准入两个 `(FF)V` 初始化输入均为 `FLOAD 3` 的情况。
- 不修改代码生成默认值，也不授权切换默认后端。
- 剩余项清单 #310 只是规划清单，不是 JDK 支持徽章。

## 测试计数

- 合入后父分支预期 XML 总数：554（`IrCompilerTest` 547 + `CodegenModeTest` 7）。
- 可发布：**否**。
