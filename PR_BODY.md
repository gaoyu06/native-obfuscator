# docs: Sol review of #139 eval LDIV/LREM

**(a) Scope / 范围**

Add the independent Sol review of draft PR #139 at `ad27a76`. The review covers
only evaluator lowering: serializer/runtime opcode agreement, JVM
divide/remainder semantics, fallback ordering, focused tests, and unchanged
defaults. No compiler fix was needed.

新增对 draft PR #139（`ad27a76`）的独立 Sol 审查。审查仅覆盖 evaluator
lowering：serializer/runtime opcode 一致性、JVM 除法与余数语义、fallback
顺序、聚焦测试及默认值不变。本审查无需修改编译器。

**(b) Ship-ready? / 可直接发布？**

**No.** This is a narrow, opt-in evaluator compiler increment and does not
establish broad runtime or JDK support. The #53 evaluator median remains
`N/A`.

**否。** 这是范围有限、需显式启用的 evaluator 编译器增量，不代表广泛的
runtime 或 JDK 支持；#53 evaluator median 仍为 `N/A`。

**(c) Result / 结论**

**Accept.** Java and C++ agree on `LDIV=0x2b`, `LREM=0x2c`, and
`dst/lhs/rhs` order. Zero is checked before signed `/` or `%`, leaves
`ArithmeticException` pending through `JNIEnv*`, and exits immediately.
`Long.MIN_VALUE / -1` and `% -1` are guarded. Static `(JJ)J` stays on eval;
methods with exception edges retain per-method fallback before shell mutation.
Defaults remain `legacy`, `direct`, and `cpp`.

**接受。** Java 与 C++ 的 `LDIV=0x2b`、`LREM=0x2c` 及 `dst/lhs/rhs`
顺序一致。除数为零时，在有符号 `/` 或 `%` 前通过 `JNIEnv*` 留下待处理的
`ArithmeticException` 并立即退出；`Long.MIN_VALUE / -1` 与 `% -1`
均有显式保护。静态 `(JJ)J` 继续走 eval；带异常边的方法在 shell 修改前保留
逐方法 fallback。默认值仍为 `legacy`、`direct` 和 `cpp`。

**(d) Verification / 验证**

- Required focused run: **17/17 passed** — `InterpreterStreamStrategyTest`
  10 and `CodegenModeTest` 7; 0 skipped, 0 failures, 0 errors.
- The C++17 runtime harness ran normal signed cases, both minimum/-one cases,
  and both zero-divisor exception paths.
- A fresh generated `(JJ)J` method uses opcode decimal `43` and
  `evaluate_i64`; its evaluator CMake target builds with GCC/G++.
- Sequential omitted-vs-direct output comparison differed only in 14 ZIP
  timestamp bytes in the output JAR. The complete C++ trees and extracted JAR
  contents matched (`diff -r` exit 0).

- 指定聚焦测试共 **17/17 通过**：`InterpreterStreamStrategyTest` 10 个、
  `CodegenModeTest` 7 个；0 skipped、0 failures、0 errors。
- C++17 runtime harness 实际运行了普通有符号用例、两种 minimum/-one 用例及
  两条除零异常路径。
- 新生成的 `(JJ)J` 方法使用十进制 opcode `43` 与 `evaluate_i64`；
  evaluator CMake target 由 GCC/G++ 成功构建。
- 顺序运行的省略参数与显式 direct 输出仅在 output JAR 的 14 个 ZIP 时间戳
  字节上不同；完整 C++ 目录及解压后的 JAR 内容均一致（`diff -r` 退出 0）。
