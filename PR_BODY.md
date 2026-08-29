# English

## (a) Scope

Ports the opt-in shared IR evaluator lowering onto current `master`.
`--codegen=legacy`, `--backend=cpp`, and `--ir-lower=direct` remain the
defaults. `--ir-lower=eval` is consulted only after an `IrMethod` is built on
the `--codegen=ir` path; supported methods become a compact little-endian
method-data stream plus a C++17 evaluator trampoline. Unsupported methods keep
the existing per-method legacy fallback.

The evaluator covers i32 constants, i32 arithmetic/bitwise/shifts and control
flow, i64 parameter/load/store/add/subtract/multiply/bitwise/shifts/return, and
`I2L`/`L2I`. Opcodes `0x2b`/`0x2c` stay reserved: this change does not add
frontend or evaluator support for `LDIV`/`LREM`.

## (b) Ship-ready?

**No.** This is default-off compiler/codegen infrastructure with a narrow
capability slice. It is not a packer, protector, obfuscation product, or
anti-analysis feature. Issue #53 evaluator median remains `N/A`.

## (c) Review required?

**Yes.** Please review:

- exact Java serializer ↔ C++ evaluator opcode agreement;
- capability validation and complete serialization before
  `MethodShellEmitter` can mutate bytecode or registration output;
- the recorded default-off `diff -r` checks;
- preservation of interpreter-first dispatch, constructor restoration,
  classfile versions, existing process overloads, and SDK source copying;
- absence of frontend edits and absence of `LDIV`/`LREM` work owned by the
  parallel IR agent.

## (d) Preconditions

- Re-run the focused Java/C++ evaluator tests.
- Confirm omitted `--ir-lower` matches explicit `direct`, including the
  default legacy generation tree.
- Do not back-fill issue #53 with invented measurements.
- Do not flip the `legacy`, `cpp`, or `direct` defaults.

## Verification

- Focused CLI/evaluator suite: 16/16 passed.
- Existing direct-IR and interpreter-dispatch suites: 96/96 passed.
- Omitted-vs-explicit-direct `diff -r`: exit 0 for default legacy and IR.
- Generated evaluator CMake project: GCC/G++ configure, compile, and link
  completed with exit 0.

# 中文

## (a) 范围

把可选的共享 IR evaluator lowering 移植到当前 `master`。默认值保持
`--codegen=legacy`、`--backend=cpp` 和 `--ir-lower=direct`。
`--ir-lower=eval` 只在 `--codegen=ir` 成功构建 `IrMethod` 后参与选择；
支持的方法会降低为小端紧凑 method-data 和 C++17 evaluator trampoline。
不支持的方法继续逐方法回退到现有 legacy 生成器。

当前切片包括 i32 常量、算术/位运算/移位与控制流，以及 i64 参数装载、复制、
加减乘、位运算、移位、返回和 `I2L`/`L2I`。`0x2b`/`0x2c` 仍保留；
本改动不增加 frontend 或 evaluator 的 `LDIV`/`LREM` 支持。

## (b) 可以发布吗？

**不可以。** 这是默认关闭、能力范围很窄的编译器/codegen 基础设施，不是 packer、
protector、混淆产品或 anti-analysis 功能。#53 的 evaluator median 仍为 `N/A`。

## (c) 需要审阅吗？

**需要。** 请重点检查：

- Java serializer 与 C++ evaluator 的 opcode 是否逐项一致；
- 能力检查和完整序列化是否都发生在 `MethodShellEmitter` 修改字节码或注册输出之前；
- 文档记录的默认关闭 `diff -r` 结果；
- interpreter 优先分派、构造器恢复、classfile 版本、已有 process overload 和 SDK
  源文件复制是否保持不变；
- 是否完全没有修改 frontend，也没有拿走并行 IR agent 的 `LDIV`/`LREM` 工作。

## (d) 前置条件

- 重新运行聚焦 Java/C++ evaluator 测试。
- 确认省略 `--ir-lower` 与显式 `direct` 一致，包括默认 legacy 生成树。
- 不得为 #53 补写虚构测量值。
- 不得改变 `legacy`、`cpp` 或 `direct` 默认值。

## 验证

- 聚焦 CLI/evaluator 测试：16/16 通过。
- 现有 direct-IR 与 interpreter 分派测试：96/96 通过。
- 省略与显式 `direct` 的两组 `diff -r`：退出码均为 0。
- 生成的 evaluator CMake 工程：GCC/G++ 配置、编译、链接退出码为 0。
