# Review evaluator LDIV/LREM / 复核 evaluator LDIV/LREM

## (a) Scope and verdict / 范围与结论

**Verdict: Accept.** The review found no correctness defect, so the review
branch is documentation-only. Java serialization and the C++ evaluator agree
on `LDIV=0x2b` and `LREM=0x2c`. A zero divisor requests a pending
`ArithmeticException` and exits the evaluator immediately. The
`Long.MIN_VALUE / -1` and remainder cases return `Long.MIN_VALUE` and zero
without executing overflowing signed C++ arithmetic.

**结论：接受。** 本次复核未发现正确性缺陷，因此 review 分支仅修改文档。Java
序列化端与 C++ evaluator 对 `LDIV=0x2b`、`LREM=0x2c` 保持一致。除数为零时，
实现会产生 pending `ArithmeticException` 并立即退出 evaluator；
`Long.MIN_VALUE / -1` 及对应余数分别得到 `Long.MIN_VALUE` 与零，且不会执行
会溢出的 C++ 有符号运算。

Generated `divide(JJ)J` and `remainder(JJ)J` methods remain on
`evaluate_i64`. The shared frontend admits both operations for direct IR, where
the structured emitter applies the same JVM edge cases and existing exception
dispatch. Unsupported evaluator methods are rejected before method, output,
registration, or cache mutation. Defaults remain `--codegen=legacy` and
`--ir-lower=direct`.

生成的 `divide(JJ)J` 与 `remainder(JJ)J` 仍走 `evaluate_i64`。共享 frontend
也允许 direct IR 使用这两个操作；结构化 emitter 使用相同 JVM 边界规则和现有异常
分发。不支持的 evaluator 方法会在方法、输出、注册或缓存发生修改之前被拒绝。默认值
仍为 `--codegen=legacy` 与 `--ir-lower=direct`。

This review adds no benchmark numbers and does not rewrite #53 or #59.

本复核不新增 benchmark 数字，也不改写 #53 或 #59。

## (b) Ship-ready? / 是否可直接上线

**No.** The evaluator remains an opt-in, deliberately limited lowering with
per-method legacy fallback outside its supported IR subset.

**否。** evaluator 仍是可选且范围受限的 lowering；超出支持 IR 子集的方法继续逐方法
回退到 legacy。

## (c) Findings and review notes / 发现与复核说明

No correctness blocker was found and compiler code was not changed. One
non-blocking coverage note remains: the evaluator has a linked runtime harness
for division edges and pending exceptions, while direct IR checks generated
source and g++ syntax rather than executing an exception-catching native
fixture. Code inspection confirms that direct lowering exits or dispatches
before reaching its division expression.

未发现正确性阻塞项，也未修改编译器代码。唯一的非阻塞覆盖说明是：evaluator 已有
链接后运行的 harness 覆盖除法边界与 pending exception；direct IR 则检查生成源码及
g++ 语法，尚未执行可捕获异常的原生 fixture。代码检查确认 direct lowering 会在到达
除法表达式之前退出或进入异常分发。

## (d) Verification / 验证

The independent `CC=gcc CXX=g++` focused rerun is pending in this pre-test
commit. The final revision will report counts from JUnit XML for
`CodegenModeTest`, `IrCompilerTest`, and `InterpreterStreamStrategyTest`, and
will state whether each toolchain-gated native test actually ran.

本次独立 `CC=gcc CXX=g++` 聚焦重跑将在该 pre-test 提交之后执行。最终版本会从
JUnit XML 记录 `CodegenModeTest`、`IrCompilerTest` 与
`InterpreterStreamStrategyTest` 的真实计数，并确认每个受 toolchain 条件控制的
原生测试是否实际运行。
