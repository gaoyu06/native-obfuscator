## (a) 本次改动范围 / Change scope

本 PR 在 `--codegen=ir` 的 typed CFG 路径中加入 phase 4 最小异常支持：

- CFG 将 try 边界、handler 入口和潜在抛出点切成合法基本块，`ATHROW` 成为终结符；
- protected block 保存按 JVM exception table 顺序排列的异常边，含 `type == null`
  的 catch-all，并在可达性分析中跟随异常边；
- SSA handler 入口由 typed `CaughtException` 提供唯一 `Ref` 栈值，locals 通过异常边
  连接 phi 并重新校验 carrier；
- 新增 typed `Throw`，并最小支持 catch 序言所需的 `ASTORE`；
- structured C++ emitter 在 protected JNI 操作后把 pending exception 导向按
  handler-set 去重的共享 dispatch，执行 `ExceptionOccurred`、`ExceptionClear`、
  有序 `IsInstanceOf`，未匹配时 `env->Throw`；
- phase-3 array-region 异常在 try 内不再以 `return 0` 绕过 catch；
- 新增匹配 catch、未匹配 rethrow、显式 `ATHROW`、catch-all、空 exception table、
  fallback-before-mutation 与包含新方法的 g++ translation-unit smoke 覆盖。

This PR adds the phase-4 minimum exception slice to the opt-in typed CFG path:
first-class ordered exception edges, handler stack seeding and exceptional phi
inputs, typed `CaughtException`/`Throw`, and shared structured JNI catch dispatch.
The default remains `legacy`, unsupported methods still fall back independently,
and the snippet-based compiler remains intact.

Detailed implementation and test evidence:
`docs/architecture/ir-phase4-status.md`.

## (b) 是否可直接上线 / Can this ship to production as-is?

**No**

这是有意受限的编译器切片，尚未覆盖完整 JVM opcode、复杂 handler 布局、monitor、
wide carrier 与完整运行时异常等价性。

This is intentionally a limited compiler slice and does not yet provide full JVM
opcode or exception parity.

## (c) 上线前是否需要 review / Is review required?

**Yes**

需要逐项审查 CFG exception edge、SSA carrier/phi、JNI pending-exception 生命周期、
有序 handler 选择、rethrow 语义和 C++ `goto` 作用域合法性。

Review must cover exceptional CFG/SSA correctness, JNI pending-exception
lifetime, ordered handler selection, rethrow behavior, and C++ goto scope safety.

## (d) review 的前置条件 / Review preconditions

1. 以 `cursor/ir-phase3-fable-review-6d81`
   (`ebc6ffc973a91662de7f402a60d392e8f34f1961`) 为 base；本 PR 堆叠在
   [#33](https://github.com/gaoyu06/native-obfuscator/pull/33) /
   [#29](https://github.com/gaoyu06/native-obfuscator/pull/29)。
   Use that branch as the merge base for this stacked review.
2. 重新运行：
   `./gradlew :obfuscator:test --tests by.radioegor146.ir.IrCompilerTest --tests by.radioegor146.CodegenModeTest`，
   并确认 JUnit 为 17 + 2、零 skipped/failure/error。
   Re-run the exact focused test command and inspect the JUnit XML.
3. 确认所有 capability rejection 都在 `AsmToIr.build(...)` 内发生，早于 cache ID
   分配、`output` / `nativeMethods` 修改和 `ACC_NATIVE` 设置；保留
   `rejectsIntStoreIntoInstanceReceiverLocal` 的 clean-state 断言。
   Confirm fallback-before-mutation rather than relying only on successful cases.
4. 检查 generated C++：protected `IALOAD` 的 `ExceptionCheck` 必须跳到共享
   `IR_CATCH_n`，随后执行 catch；不得用静默 `return 0` 吞掉可捕获异常。
   Confirm the catch runs instead of swallowing a catchable exception.
5. 在工具链可用时确认 g++ smoke 未 skip，并实际执行
   `g++ -std=c++17 -fsyntax-only`；同时检查 catch landing、type-test order、
   catch-all 和 unmatched rethrow。
