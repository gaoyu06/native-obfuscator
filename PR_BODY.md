# compiler: wire evaluator LDIV/LREM to LongDivRem

**(a) Scope / 范围**

Wire the evaluator-only `0x2b` (`LDIV`) and `0x2c` (`LREM`) method-data
instructions to the phase-20 `IrNodes.LongDivRem` nodes. Capability validation
and serialization still complete before `MethodShellEmitter` mutates the
method. No frontend, direct-lowering, interpreter, default-option, or benchmark
changes are included.

仅在 evaluator lowering 中把方法数据指令 `0x2b`（`LDIV`）和
`0x2c`（`LREM`）接到 phase-20 `IrNodes.LongDivRem` 节点。能力检查与序列化
仍在 `MethodShellEmitter` 修改方法前完成。本变更不涉及前端、direct lowering、
interpreter、默认选项或 benchmark。

**(b) Ship-ready? / 可直接发布？**

**No.** The evaluator remains opt-in, default-off compiler infrastructure, and
the #53 evaluator median remains `N/A`.

**否。** evaluator 仍是显式启用、默认关闭的编译器基础设施；#53 evaluator
median 仍为 `N/A`。

**(c) Review focus / 审查重点**

Check the signed C++17 toward-zero divide/remainder, the pre-operation
zero-divisor guard that leaves `java/lang/ArithmeticException` pending through
the trampoline `JNIEnv*`, and the explicit `Long.MIN_VALUE / -1` and `% -1`
guards. A static `(JJ)J` divide without a catch region stays on evaluator
lowering. Any exception edge, including a `try`/`catch` around `LDIV`/`LREM`,
still falls back because evaluator catch dispatch is not represented yet.

请检查 C++17 有符号向零除法/取余、在运算前通过 trampoline 的 `JNIEnv*` 留下
待处理 `java/lang/ArithmeticException` 的除零保护，以及显式处理
`Long.MIN_VALUE / -1` 和 `% -1` 的保护。没有 catch region 的静态 `(JJ)J`
除法继续走 evaluator lowering；包含异常边的情况（包括围绕 `LDIV`/`LREM` 的
`try`/`catch`）仍回退，因为 evaluator 尚未表示 catch dispatch。

**(d) Verification / 验证**

- `CC=gcc CXX=g++ ./gradlew :obfuscator:test --tests
  by.radioegor146.ir.backend.InterpreterStreamStrategyTest --tests
  by.radioegor146.CodegenModeTest`: **17/17 passed**, 0 skipped, 0 failures,
  0 errors.
- The C++17 runtime harness executed normal signed `LDIV`/`LREM`, both
  zero-divisor exception paths, and both `Long.MIN_VALUE / -1` outcomes.
- A generated `(JJ)J` fixture contains opcode decimal `43` (`0x2b`) and
  `evaluate_i64`, with no direct structured-body marker.
- Complete generated outputs for omitted `--ir-lower` and explicit
  `--ir-lower=direct` matched with `diff -r` (exit 0, no output).

- 指定的 GCC/G++ 测试共 **17/17 通过**，0 跳过、0 失败、0 错误。
- C++17 runtime harness 实际执行了普通有符号 `LDIV`/`LREM`、两条除零异常路径，
  以及两种 `Long.MIN_VALUE / -1` 结果。
- 生成的 `(JJ)J` fixture 包含十进制 opcode `43`（`0x2b`）和
  `evaluate_i64`，且不含 direct structured-body 标记。
- 省略 `--ir-lower` 与显式 `--ir-lower=direct` 的完整生成输出经 `diff -r`
  对比一致（退出 0，无输出）。
