# PR body / PR 描述

## English

### Scope

- Extends the opt-in IR constructor split after #176 with the five remaining
  unary int-zero branches (`IFEQ`, `IFLT`, `IFGE`, `IFGT`, `IFLE`) and all six
  binary declared-argument branches (`IF_ICMPxx`).
- Requires direct `ILOAD` operands from declared int-family constructor
  arguments, a jump to the exact shared suffix, and an immediate `RETURN` on
  the other edge.
- Retains the existing empty-exception-table, empty chain-entry-stack,
  original-receiver, and exactly-one-chain-call CFG proofs.
- Adds split, JVM-verification, and CMake/g++ Java-parity coverage for every new
  compare and all three relevant paths.

### Readiness and review

- Ship-ready: **No**. This is a narrow opt-in admission, not completion of the
  production goal.
- Stacked review: **No**. This branch is based directly on `origin/master` at
  or after #176; the gate is the focused test suite below.
- Gate result: 177 tests, 0 failures, 0 errors, 0 skipped
  (`IrCompilerTest` 170 + `CodegenModeTest` 7).

### Preconditions and unchanged rejects

- Remaining constructor leftovers, unsafe constant-dynamic forms, and
  `jsr`/`ret` remain rejected.
- General prefix-to-suffix jumps and switches, skip-super paths, non-declared
  compare operands, and other unproven forms remain rejected.
- `--codegen` remains `legacy` by default. There is no `--ir-lower` or backend
  default change and no silent fallback.

## 中文

### 范围

- 在 #176 之后的可选 IR 构造函数拆分路径中，新增其余五个单操作数整数零值
  分支（`IFEQ`、`IFLT`、`IFGE`、`IFGT`、`IFLE`）以及全部六个双操作数
  `IF_ICMPxx` 分支。
- 操作数必须由构造函数声明的 int-family 参数通过直接 `ILOAD` 取得；分支必须
  跳到精确的共享后缀，另一条边必须立即 `RETURN`。
- 保留现有证明条件：空异常表、构造函数链调用入口空栈、原始 receiver，以及
  CFG 中每条成功路径恰好一次构造函数链调用。
- 为每个新增比较及三条相关路径加入拆分、JVM 验证和 CMake/g++ Java 输出一致性
  测试。

### 就绪状态与审查

- 可发布（Ship-ready）：**No**。这是窄范围的可选准入，不代表生产目标完成。
- 堆叠审查（Stacked review）：**No**。本分支直接基于包含 #176 的
  `origin/master`；准入门槛是下述聚焦测试。
- 测试结果：177 个测试，0 failure、0 error、0 skipped
  （`IrCompilerTest` 170 + `CodegenModeTest` 7）。

### 前置条件与保持拒绝的范围

- 其余构造函数遗留形态、不安全的 constant-dynamic 形态以及 `jsr`/`ret`
  继续拒绝。
- 通用 prefix-to-suffix 跳转或 switch、跳过 super 的路径、非声明参数比较
  操作数及其他未证明形态继续拒绝。
- `--codegen` 默认值仍为 `legacy`；不改变 `--ir-lower` 或 backend 默认值，
  也不引入静默回退。
