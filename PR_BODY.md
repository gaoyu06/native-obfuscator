## English

### (a) Scope

Admit bounded constructor splits whose 2–8 path-id-selected, nonempty
`RETURN`-terminated suffix ranges include both an identical pair and at least
one CFG-distinct pair. This uses the existing #189–#195 single hidden bridge
and trailing path id. Repeated suffix ranges keep separate path ids; this does
not combine join normalization with path selection. All-identical
straight-line copies continue to use the earlier one-join normalizer.

### (b) Ship-ready?

No.

### (c) Review and gate

Executed the focused `IrCompilerTest` + `CodegenModeTest` gate. No stacked
Fable/Sol review is included.

### (d) Preconditions

Remaining constructor-split leftovers continue to reject before mutation.
`--codegen` remains `legacy`; no production default changes in this increment.

## 中文

### (a) 范围

允许一种有界构造函数拆分：由路径编号选择的 2–8 个非空、以 `RETURN`
结束的后缀范围中，既有 CFG 相同的一对，也至少有一对 CFG 不同。该实现继续
使用现有 #189–#195 的单个隐藏桥接方法和末尾路径编号。重复后缀仍保留各自
的路径编号；本改动不把连接点归一化与路径选择组合起来。全部相同的直线后缀
副本仍优先使用已有的单连接点归一化流程。

### (b) 可发布？

否。

### (c) 审查与门禁

已执行聚焦的 `IrCompilerTest` + `CodegenModeTest` 门禁；不包含堆叠的
Fable/Sol 审查。

### (d) 前置条件

其余构造函数拆分遗留形状仍在修改前拒绝。`--codegen` 仍为 `legacy`；
本增量没有修改生产默认值。
