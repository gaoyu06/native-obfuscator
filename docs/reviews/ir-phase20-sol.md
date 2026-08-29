# IR phase 20 independent review / IR phase 20 独立审查

Reviewed tip: `257b1531cfbfd83f2ff0b322a2331ae90ea4001f`

Reviewed base: `76ebeddb005e01033523384275c8c0c1641ada81`

## Verdict / 结论

**Accept (docs-only review note).** The complete phase-20 compiler diff has no
correctness defect in the `LDIV`, `LREM`, or `LNEG` lowering. No compiler
change is requested.

**接受（仅增加审查文档）。** 对 phase 20 完整编译器差异的独立审查未发现
`LDIV`、`LREM` 或 `LNEG` lowering 的正确性缺陷，因此不要求修改编译器。

## Review evidence / 审查证据

- `LDIV` and `LREM` lower to the dedicated `LongDivRem` node. Its constructor
  requires an `I64` result and two `I64` operands. `LongBinary` remains limited
  to exception-free add/subtract/multiply/bitwise operations and has no divide
  or remainder operation.
- `LNEG` lowers to the dedicated `LongUnary.NEGATE` node, whose constructor
  requires an `I64` result and operand. The existing `Unary` node remains
  strictly `I32`.
- The frontend pops the right `I64` operand before the left `I64` operand for
  both `LDIV` and `LREM`, then pushes an `I64` result. Abstract stack simulation
  applies the same two-operand `I64` shape.
- `CfgBuilder.mayThrow` contains `LDIV` and `LREM`, but not `LNEG`. Splitting
  after the potentially throwing operation leaves the divide block with its
  ordered handler edge.
- `emitLongDivRem` checks the divisor first, calls the existing
  `utils::throw_re`, and transfers through `exceptionalExit`. It guards
  `Long.MIN_VALUE / -1`, producing `Long.MIN_VALUE` for division and zero for
  remainder, before using signed `int64_t` `/` or `%` in the ordinary path.
  `emitLongUnary` negates a `uint64_t` carrier before converting back to
  `jlong`, preserving two's-complement wrap for `Long.MIN_VALUE`.
- The `try`/`catch` fixture around `LDIV` obtains an
  `ArithmeticException` edge, and emitted zero-divisor control flow reaches
  the shared catch dispatch before ordinary division.
- `rejectsUnsupportedWideOperationBeforeMutation` now uses `LCMP`. `LCMP` is
  absent from the frontend admission set, so the test still exercises a real
  unsupported opcode and verifies that no method or compiler context mutation
  occurred.
- The complete diff changes only IR nodes/dump, frontend CFG/lowering, emitter,
  focused tests, and phase documentation. It does not edit `Main`,
  `NativeObfuscator`, interpreter, or evaluator code. `Main` still declares
  `--codegen` with `defaultValue = "legacy"`.

- `LDIV` 与 `LREM` lowering 到独立的 `LongDivRem` 节点；其构造器要求结果、
  左操作数及右操作数均为 `I64`。`LongBinary` 仍只包含不抛异常的加、减、
  乘和位运算，不含除法或余数操作。
- `LNEG` lowering 到独立的 `LongUnary.NEGATE` 节点，其结果与操作数均被
  强制为 `I64`；既有 `Unary` 节点仍严格限定为 `I32`。
- frontend 对 `LDIV`/`LREM` 先弹出右侧 `I64`，再弹出左侧 `I64`，最后压入
  `I64` 结果；抽象栈模拟使用相同的双 `I64` 形状。
- `CfgBuilder.mayThrow` 包含 `LDIV` 与 `LREM`，但不包含 `LNEG`。潜在抛出
  指令之后的 block split 使除法 block 保留有序 handler edge。
- `emitLongDivRem` 先检查除数，调用既有 `utils::throw_re`，再经
  `exceptionalExit` 转移；随后保护 `Long.MIN_VALUE / -1`，除法产生
  `Long.MIN_VALUE`、余数产生零，普通路径使用有符号 `int64_t` `/` 或 `%`。
  `emitLongUnary` 在 `uint64_t` carrier 上取负后转回 `jlong`，保留
  `Long.MIN_VALUE` 的二进制补码回绕。
- `LDIV` 的 `try`/`catch` fixture 获得 `ArithmeticException` edge；生成的
  除零控制流在普通除法之前进入共享 catch dispatch。
- `rejectsUnsupportedWideOperationBeforeMutation` 改用 `LCMP`；frontend
  admission set 并不包含 `LCMP`，因此该测试仍以真实的不支持 opcode 验证
  method 与 compiler context 均未被修改。
- 完整差异仅修改 IR node/dump、frontend CFG/lowering、emitter、聚焦测试及
  phase 文档；未修改 `Main`、`NativeObfuscator`、interpreter 或 evaluator。
  `Main` 的 `--codegen` 仍显式使用 `defaultValue = "legacy"`。

## Focused verification / 聚焦验证

The required independent rerun will use:

```text
CC=gcc CXX=g++ ./gradlew :obfuscator:test \
  --tests by.radioegor146.ir.IrCompilerTest \
  --tests by.radioegor146.CodegenModeTest
```

Independent result and JUnit XML counts: pending the post-commit rerun.

独立复跑结果及 JUnit XML 数量：等待提交后的聚焦复跑。

## Ship-readiness / 交付准备度

- **(a) Scope / 范围:** Accept the opt-in typed-CFG compiler lowering of
  `LDIV`, `LREM`, and `LNEG`, including divide-by-zero exception transfer and
  long overflow/wrap semantics. /
  接受 opt-in typed-CFG 编译器对 `LDIV`、`LREM`、`LNEG` 的 lowering，包括
  除零异常转移与 long 溢出/回绕语义。
- **(b) Ship-ready? / 可直接发布？:** **No.** This is one opt-in compiler
  increment and does not establish broad runtime or JDK support. /
  **否。** 这是单个 opt-in 编译器增量，不代表广泛 runtime 或 JDK 支持。
- **(c) Review result / 审查结果:** No compiler fix requested after checking
  node typing, JVM stack order, exceptional CFG construction, emitted
  arithmetic semantics, unsupported-opcode rejection, and defaults. /
  审查 node typing、JVM stack 顺序、异常 CFG 构造、生成算术语义、不支持
  opcode 拒绝及默认值后，不要求编译器修复。
- **(d) Integration / 集成:** Stack this review on
  `cursor/ir-compiler-phase20-6d81`; keep `legacy` as the default and do not
  combine it with evaluator, interpreter, or CLI-default changes. /
  本审查仅堆叠在 `cursor/ir-compiler-phase20-6d81`；保持 `legacy` 默认值，
  且不与 evaluator、interpreter 或 CLI 默认值变更合并。
