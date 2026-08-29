# English

## (a) Scope

Admit one closed `TABLESWITCH` or `LOOKUPSWITCH` inside each pairwise-distinct
constructor suffix when its key is an immediately preceding proven int-family
`ILOAD`, every case/default target stays forward inside that suffix, and every
complete path reaches `RETURN`. This extends the existing distinct-suffix
path-id bridge from #189–#194 and keeps one hidden method with one trailing path
id.

This is not a loosening of #178's shared-join post-chain switch. Its computed
key, target-side work, and exception-table rejection rules remain unchanged.

## (b) Ship-ready?

No.

## (c) Review and gate

Executed the focused `IrCompilerTest` and `CodegenModeTest` gate, including the
real CMake/g++ JNI parity test. No stacked Fable/Sol review is requested.

## (d) Preconditions

All remaining constructor-split leftovers stay reject-before-mutation.
`--codegen` remains `legacy`; the `--ir-lower` and `--backend` defaults are
unchanged.

# 中文

## (a) 范围

当开关键是紧邻其前且已证明为 int-family 的 `ILOAD`、所有 case/default
目标都向前并位于同一后缀内、且每条完整路径都到达 `RETURN` 时，允许每个互不
相同的构造器后缀包含一个闭合的 `TABLESWITCH` 或 `LOOKUPSWITCH`。此变更扩展
#189–#194 已有的不同后缀 path-id 桥接路径，仍只使用一个隐藏方法和一个尾部
path id。

这不是放宽 #178 的调用后共享汇合开关；其计算键、目标侧额外工作和异常表变体
仍保持拒绝。

## (b) 可发布？

否。

## (c) 评审与门禁

已执行聚焦的 `IrCompilerTest` 与 `CodegenModeTest` 门禁，其中包括真实的
CMake/g++ JNI 一致性测试。不请求堆叠的 Fable/Sol 评审。

## (d) 前置条件

其余构造器拆分遗留形状继续在修改前拒绝。`--codegen` 仍为 `legacy`；
`--ir-lower` 与 `--backend` 默认值保持不变。
