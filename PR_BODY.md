# EN

## (a) Scope

This change admits one narrow opt-in IR constructor-split shape after #177:

- exactly two direct this/super constructor-chain calls;
- after the first call, one direct `ILOAD` of a declared int-family constructor
  argument immediately feeds `TABLESWITCH` or `LOOKUPSWITCH`;
- every case/default targets the exact shared initialized suffix, an immediate
  prefix `RETURN`, or a prefix label whose first executable instruction is a
  direct `GOTO` to that suffix;
- the second chain call falls through to the same suffix;
- the exception table is empty, chain-entry stacks contain no older values,
  both calls consume the original receiver, and the existing CFG proof requires
  exactly one chain call at every suffix/return edge.

Both switch opcodes have split-admission, JVM-verification, negative, and full
CMake/g++ compile-and-run parity coverage under `java -Xverify:all
-Xcheck:jni`. Computed/non-argument keys, work before target returns, nonempty
exception tables, and skip-super paths remain rejected.

## (b) Ship-ready?

**No.** This is one bounded admission rule on the opt-in IR path; it does not
complete the production goal.

## (c) Review and gate

**No stacked review.** The gate is the executed focused test suite, including
the constructor switch runtime harness.

## (d) Preconditions

Remaining constructor leftovers, unsafe/unproven constant-dynamic forms, and
`jsr`/`ret` remain rejected before mutation. `--codegen` remains `legacy`; this
change does not flip `--ir-lower`, `--backend`, or any default.

# 中文

## (a) 范围

本改动在 #177 之后，仅为可选 IR 构造器拆分路径接纳一种边界明确的形态：

- 恰好有两个直接的 this/super 构造器链调用；
- 第一个调用之后，必须由声明的 int 族构造器参数通过一次直接 `ILOAD`
  立即提供 `TABLESWITCH` 或 `LOOKUPSWITCH` 的键；
- 每个 case/default 只能指向同一个已初始化共享后缀、前缀中的立即
  `RETURN`，或首条可执行指令为直接跳往该后缀的 `GOTO` 的前缀标签；
- 第二个构造器链调用直接落入同一后缀；
- 异常表必须为空，调用入口栈在 receiver 与参数之下不得有旧值，两个调用
  都必须使用原始 receiver，且既有 CFG 证明要求每条后缀/返回边都恰好
  执行一次构造器链调用。

两个 switch 操作码都覆盖了拆分接纳、JVM 验证、负例，以及在
`java -Xverify:all -Xcheck:jni` 下完整的 CMake/g++ 编译运行一致性测试。
计算得到的键、非参数键、目标返回前的额外工作、非空异常表和跳过 super
的路径仍会被拒绝。

## (b) 是否可直接上线？

**否。** 这只是可选 IR 路径上的一条有限接纳规则，不代表生产目标完成。

## (c) 审查与门禁

**不做叠加审查。** 门禁是已实际执行的聚焦测试套件，其中包括构造器
switch 的运行时测试。

## (d) 前置条件

其余构造器缺口、不安全或未经证明的 constant-dynamic 形态，以及
`jsr`/`ret` 仍在修改前拒绝。`--codegen` 继续默认为 `legacy`；本改动
不切换 `--ir-lower`、`--backend` 或任何默认值。
