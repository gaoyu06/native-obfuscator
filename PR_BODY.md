# compiler: IR phase 20 LDIV, LREM, and LNEG

## English

Phase 20 extends the opt-in direct Java bytecode → typed CFG → C++/JNI compiler
(`--codegen=ir`) with the remaining JVM long arithmetic that can trap or wrap:
`LDIV`, `LREM`, and `LNEG`. Base: `origin/master` at
`76ebeddb005e01033523384275c8c0c1641ada81` (phase 19 + interpreter ISA v2).

- **(a) Scope:** Direct typed-CFG IR admission and lowering for `LDIV`,
  `LREM`, and `LNEG`.
- **(b) Ship-ready?** **No.** This is one compiler increment; `--codegen`
  still defaults to `legacy` with per-method fallback, and the production goal
  is incomplete.
- **(c) Review focus:** zero-divisor exceptional path, the
  `Long.MIN_VALUE / -1` wrap, the split of the new `LongDivRem` node from the
  exception-free wrapping `LongBinary`, and the `CfgBuilder.mayThrow` addition
  of `LDIV`/`LREM`.
- **(d) Do not combine** this increment with evaluator / interpreter /
  `--ir-lower` work; keep the `legacy` default.

### What changed

- `IrNodes`: added `LongDivRem` (I64 result/left/right, `DIVIDE`/`REMAINDER`,
  with `bytecodeOffset` + `sourceLine`) modeled on `IntDivRem`, and `LongUnary`
  (i64-typed `NEGATE`) rather than overloading the i32 `Unary`. `LongBinary`
  is left untouched (no `DIVIDE`/`REMAINDER`).
- `AsmToIr`: admits `LDIV`/`LREM`/`LNEG`, type-checks the abstract stack
  (`LDIV`/`LREM` pop right then left, both `I64`, push `I64`; `LNEG` pops and
  pushes `I64`), and lowers to the new nodes.
- `CfgBuilder.mayThrow`: now includes `LDIV`/`LREM` so a zero divisor inside a
  `try` gets the same catch-block exception edges as `IDIV`/`IREM`.
- `IrCppEmitter`: `emitLongDivRem` mirrors `emitIntDivRem` — divisor-zero
  `utils::throw_re` (`LDIV / by 0` / `LREM % by 0`) then the existing
  `exceptionalExit`; `Long.MIN_VALUE` && `-1` guard yielding `Long.MIN_VALUE`
  (LDIV) / `0` (LREM); otherwise signed `int64_t` `/` or `%` assigned to
  `jlong`. `emitLongUnary` negates on `uint64_t` and assigns `jlong` so
  `Long.MIN_VALUE` wraps to itself.
- `IrMethod`: dump/text for `LongDivRem` (`ldiv`/`lrem`) and `LongUnary`
  (`lneg`).
- `IrCompilerTest`: new frontend/emitter cases plus corpus admission and the
  `g++ -fsyntax-only` smoke gate.

### Verification

```text
CC=gcc CXX=g++ ./gradlew :obfuscator:test \
  --tests by.radioegor146.ir.IrCompilerTest \
  --tests by.radioegor146.CodegenModeTest
```

`BUILD SUCCESSFUL` on 2026-08-29. Gradle JUnit XML:

```text
IrCompilerTest: tests=97, skipped=0, failures=0, errors=0
CodegenModeTest: tests=5, skipped=0, failures=0, errors=0
Total: 102 tests, 0 skipped, 0 failures, 0 errors
```

`CodegenModeTest` confirms `--codegen` still defaults to `legacy`. No JDK
17/21/25 corpus support is claimed and no benchmark numbers are reported.

## 中文

第 20 阶段为可选的直接 Java 字节码 → 类型化 CFG → C++/JNI 编译器
（`--codegen=ir`）补齐会抛异常或回绕的剩余 JVM long 运算：`LDIV`、`LREM`
和 `LNEG`。基线：`origin/master`，提交
`76ebeddb005e01033523384275c8c0c1641ada81`（第 19 阶段 + 解释器 ISA v2）。

- **(a) 范围：** 直接 typed-CFG IR 接纳并下降 `LDIV`、`LREM`、`LNEG`。
- **(b) 可直接发布？** **否。** 这是单个编译器增量；`--codegen` 仍默认
  `legacy` 且保留逐方法 fallback，生产目标尚未完成。
- **(c) 审查重点：** 除零异常路径、`Long.MIN_VALUE / -1` 回绕、新增
  `LongDivRem` 节点与无异常回绕 `LongBinary` 的拆分，以及
  `CfgBuilder.mayThrow` 对 `LDIV`/`LREM` 的加入。
- **(d) 请勿合并** 本增量与 evaluator / interpreter / `--ir-lower` 工作；
  保持 `legacy` 默认值。

### 改动内容

- `IrNodes`：新增 `LongDivRem`（I64 结果/左/右，`DIVIDE`/`REMAINDER`，带
  `bytecodeOffset` + `sourceLine`），仿照 `IntDivRem`；新增 `LongUnary`
  （i64 类型的 `NEGATE`），而非复用 i32 `Unary`。`LongBinary` 保持不变
  （不添加 `DIVIDE`/`REMAINDER`）。
- `AsmToIr`：接纳 `LDIV`/`LREM`/`LNEG`，对抽象栈做类型检查（`LDIV`/`LREM`
  先弹右后弹左，均为 `I64`，压入 `I64`；`LNEG` 弹出并压入 `I64`），并下降为
  新节点。
- `CfgBuilder.mayThrow`：加入 `LDIV`/`LREM`，使 `try` 内的零除数获得与
  `IDIV`/`IREM` 相同的 catch 异常边。
- `IrCppEmitter`：`emitLongDivRem` 仿照 `emitIntDivRem` —— 除零调用
  `utils::throw_re`（`LDIV / by 0` / `LREM % by 0`）后走既有 `exceptionalExit`；
  `Long.MIN_VALUE` && `-1` 保护，`LDIV` 得 `Long.MIN_VALUE`、`LREM` 得 `0`；
  否则按有符号 `int64_t` 做 `/` 或 `%` 并赋给 `jlong`。`emitLongUnary` 在
  `uint64_t` 上取负后赋 `jlong`，使 `Long.MIN_VALUE` 回绕到自身。
- `IrMethod`：为 `LongDivRem`（`ldiv`/`lrem`）与 `LongUnary`（`lneg`）
  增加文本转储。
- `IrCompilerTest`：新增前端/发射用例，以及语料接纳与 `g++ -fsyntax-only`
  冒烟门禁。

### 验证

2026-08-29 结果 `BUILD SUCCESSFUL`。Gradle JUnit XML：

```text
IrCompilerTest: tests=97, skipped=0, failures=0, errors=0
CodegenModeTest: tests=5, skipped=0, failures=0, errors=0
Total: 102 tests, 0 skipped, 0 failures, 0 errors
```

`CodegenModeTest` 确认 `--codegen` 仍默认 `legacy`。未声称 JDK 17/21/25
语料支持，也未报告任何基准数值。
