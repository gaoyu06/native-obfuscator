# docs: Fable review of IR phase 20

## English

Independent review of PR #134 (`cursor/ir-compiler-phase20-6d81` at
`257b153`, base `master` at `76ebedd`): the phase-20 compiler increment that
admits `LDIV`, `LREM`, and `LNEG` into the opt-in direct Java bytecode →
typed CFG → C++/JNI compiler (`--codegen=ir`). The full verdict is in
`docs/reviews/ir-phase20-fable.md`.

- **(a) Scope:** Review-only. Verdict note for the phase-20 typed-CFG
  admission and structured C++ lowering of `LDIV`/`LREM`/`LNEG`. No compiler
  code was changed on this branch; the review found no correctness bug
  requiring a fix.
- **(b) Ship-ready?** **No.** Phase 20 is one compiler increment;
  `--codegen` still defaults to `legacy` with per-method fallback, and the
  production goal is incomplete. No JDK support badge is claimed.
- **(c) Review focus:** The verdict is **accept with nits** (docs/test-shape
  observations only). Verified: the `LongDivRem`/`LongUnary` node split
  (`LongBinary` gains no `DIVIDE`/`REMAINDER`), `AsmToIr` popping right then
  left with both operands `I64`, `CfgBuilder.mayThrow` adding `LDIV`/`LREM`
  only (`LNEG` cannot throw), the zero-divisor `utils::throw_re`
  ArithmeticException (`LDIV / by 0` / `LREM % by 0`) followed by the
  existing `exceptionalExit`, the `Long.MIN_VALUE / -1` if/else guard that
  keeps signed C++ overflow unreachable, the signed `int64_t` `/`/`%`
  ordinary path, and the `lneg` negation on the `uint64_t` carrier so
  `Long.MIN_VALUE` wraps. A differential check of the exact emitted patterns
  under `g++ -std=c++17 -O2 -fsanitize=undefined` matched Java semantics on
  all cases.
- **(d) Do not combine** this review branch with evaluator, interpreter, or
  `--ir-lower` work; keep the `legacy` default.

### Verification (run by the reviewer at the reviewed tip)

```text
CC=gcc CXX=g++ ./gradlew :obfuscator:test \
  --tests by.radioegor146.ir.IrCompilerTest \
  --tests by.radioegor146.CodegenModeTest
```

`BUILD SUCCESSFUL` on 2026-08-29 (OpenJDK 21.0.10, gcc/g++ 13.3.0). Gradle
JUnit XML:

```text
IrCompilerTest:  tests=97, skipped=0, failures=0, errors=0
CodegenModeTest: tests=5,  skipped=0, failures=0, errors=0
Total: 102 tests, 0 skipped, 0 failures, 0 errors
```

These counts match the phase-20 PR's claims exactly.

## 中文

对 PR #134（`cursor/ir-compiler-phase20-6d81`，提交 `257b153`，基线
`master` `76ebedd`）的独立审查：第 20 阶段编译器增量，将 `LDIV`、`LREM`、
`LNEG` 接纳进可选的直接 Java 字节码 → 类型化 CFG → C++/JNI 编译器
（`--codegen=ir`）。完整结论见 `docs/reviews/ir-phase20-fable.md`。

- **(a) 范围：** 仅审查。针对第 20 阶段 `LDIV`/`LREM`/`LNEG` 的 typed-CFG
  接纳与结构化 C++ 下降的结论文档。本分支未修改任何编译器代码；审查未发现
  需要修复的正确性缺陷。
- **(b) 可直接发布？** **否。** 第 20 阶段只是一个编译器增量；`--codegen`
  仍默认 `legacy` 并保留逐方法 fallback，生产目标尚未完成。不声称任何 JDK
  支持徽章。
- **(c) 审查重点：** 结论为**接受（附非阻塞小问题）**（仅文档/测试形态
  观察）。已核实：`LongDivRem`/`LongUnary` 节点拆分（`LongBinary` 未添加
  `DIVIDE`/`REMAINDER`）、`AsmToIr` 先弹右后弹左且两操作数均为 `I64`、
  `CfgBuilder.mayThrow` 仅加入 `LDIV`/`LREM`（`LNEG` 不会抛异常）、除零时
  `utils::throw_re` ArithmeticException（`LDIV / by 0` / `LREM % by 0`）后走
  既有 `exceptionalExit`、`Long.MIN_VALUE / -1` 的 if/else 保护使有符号
  C++ 溢出不可达、常规路径按有符号 `int64_t` 做 `/`/`%`，以及 `lneg` 在
  `uint64_t` 载体上取负使 `Long.MIN_VALUE` 回绕。在
  `g++ -std=c++17 -O2 -fsanitize=undefined` 下对发射模式做的差分验证与
  Java 语义在全部用例上一致。
- **(d) 请勿合并** 本审查分支与 evaluator、interpreter 或 `--ir-lower`
  工作；保持 `legacy` 默认值。

### 验证（审查者在被审查提交上运行）

2026-08-29 结果 `BUILD SUCCESSFUL`（OpenJDK 21.0.10，gcc/g++ 13.3.0）。
Gradle JUnit XML：

```text
IrCompilerTest:  tests=97, skipped=0, failures=0, errors=0
CodegenModeTest: tests=5,  skipped=0, failures=0, errors=0
Total: 102 tests, 0 skipped, 0 failures, 0 errors
```

以上计数与第 20 阶段 PR 的声明完全一致。
