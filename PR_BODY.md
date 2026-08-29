# English

## (a) Scope

This is the count lift of #193's closed intra-suffix int-branch proof onto the
existing #190 path-id bridge for 3–8 pairwise-distinct constructor suffixes.
The independent IR body continues to use one hidden bridge with a trailing
path-id `int`; three or more paths continue to dispatch through the existing
exact-range `TABLESWITCH` with a throwing default.

The existing CFG and operand proof remains unchanged: each suffix admits at
most one closed unary `IF*` or binary `IF_ICMP*`, while switches, back edges,
cross-suffix or suffix-to-prefix targets, and unproven inputs remain rejected.

## (b) Ship-ready?

**No.**

## (c) Review and gate

Executed the focused `IrCompilerTest` and `CodegenModeTest` gate, including JVM
verification and the CMake/g++ JNI parity test. No stacked Fable/Sol review is
part of this increment.

## (d) Preconditions

All remaining constructor leftovers continue to reject before mutation.
`--codegen` remains `legacy`; compiler defaults and the existing backend and
lowering selections are unchanged.

# 中文

## (a) 范围

本次改动把 #193 已有的后缀内闭合整数条件分支证明扩展到 #190 已有的
3–8 路构造器不同后缀 path-id 桥接形式。独立 IR 方法仍只使用一个隐藏桥接，
末尾参数仍为 path-id `int`；三路及以上仍通过已有的精确范围
`TABLESWITCH` 分派，默认分支仍抛出异常。

现有 CFG 和操作数证明保持不变：每个后缀最多允许一个闭合的一元 `IF*` 或
二元 `IF_ICMP*`；后缀内 switch、回边、跨后缀或后缀到前缀的跳转，以及未经
证明的输入仍然拒绝。

## (b) 可发布？

**否。**

## (c) 审查与门禁

已执行聚焦的 `IrCompilerTest` 与 `CodegenModeTest` 门禁，其中包含 JVM 验证
以及 CMake/g++ JNI 一致性测试。本次增量不包含堆叠的 Fable/Sol 审查。

## (d) 前置条件

其余构造器遗留形态继续在任何修改发生前拒绝。`--codegen` 仍为 `legacy`；
编译器默认值以及现有后端和 lowering 选择均未改变。
