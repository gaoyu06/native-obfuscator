# IR shared evaluator lowering / IR 共享求值器 lowering

## (a) Scope / 改动范围

- Adds `--ir-lower=direct|eval`; defaults remain `--codegen=legacy` and
  `--ir-lower=direct`.
- Implements the §9.3 strategy hook with `DirectCppStrategy` and
  `InterpreterStreamStrategy`.
- Serializes the supported integer SSA/CFG slice to documented per-method data,
  including parallel phi copies on control-flow edges.
- Emits thin JNI trampolines and one shared `native_jvm_eval.cpp` evaluator in
  the existing CMake target.
- Preserves per-method legacy fallback by completing evaluator capability checks
  and serialization before method mutation.
- Adds C++/data inspection tests, generated-project integration coverage,
  evaluator execution for `add` and `sumTo`, and a g++ translation-unit syntax
  smoke.

- 新增 `--ir-lower=direct|eval`；默认值仍是 `--codegen=legacy` 与
  `--ir-lower=direct`。
- 通过 `DirectCppStrategy` 和 `InterpreterStreamStrategy` 落实 §9.3 策略接口。
- 将已支持的整数 SSA/CFG 子集序列化为文档化的逐方法数据，并在控制流边上正确处理
  phi 并行复制。
- 生成精简 JNI trampoline，并把唯一的共享 `native_jvm_eval.cpp` 求值器加入现有
  CMake library target。
- 求值器能力检查和序列化均在方法改写前完成，因此保留逐方法 legacy fallback。
- 新增 C++/数据检查、生成工程集成、`add`/`sumTo` 求值器执行及 g++ 翻译单元语法冒烟测试。

## (b) Ship-ready? / 是否可直接上线

**No.** The evaluator is intentionally limited to static integer methods using
constants, `IADD`/`ISUB`/`IMUL`, integer branches, phi merges, and `IRETURN`.
Other IR nodes still use the established per-method fallback.

**否。** 当前求值器仅覆盖静态整数方法中的常量、`IADD`/`ISUB`/`IMUL`、整数分支、
phi 合并与 `IRETURN`；其他 IR 节点仍使用既有的逐方法 fallback。

## (c) Review required? / 是否需要 review

**Yes.** Review the ISA/runtime agreement, edge-copy serialization, the
fallback-before-mutation boundary, and generated CMake integration.

**是。** 请重点审阅 ISA 与运行时的一致性、控制流边复制的序列化、改写前 fallback 边界，
以及生成 CMake 工程的集成。

## (d) Verification / 验证

1. Run
   `./gradlew :obfuscator:test --tests by.radioegor146.CodegenModeTest --tests by.radioegor146.ir.IrCompilerTest --tests by.radioegor146.ir.backend.InterpreterStreamStrategyTest`.
   Current result: 27/27 passed (`4 + 17 + 6`), 0 skipped, 0 failed,
   0 errors. 运行上述 focused test；当前结果为 27/27 通过、0 跳过、0 失败、
   0 error。
2. Confirm the two g++ tests are not skipped when g++ and JNI headers exist.
   Both executed in the recorded run. 当 g++ 与 JNI headers 存在时，确认两个
   g++ 测试未被跳过；本次记录中二者均已执行。
3. Inspect generated `add` and `sumTo` functions: each must contain
   `ir_method_data` plus one `evaluate_i32` call and no direct arithmetic body.
   检查生成的 `add` 与 `sumTo`：每个函数都应包含 `ir_method_data` 和一次
   `evaluate_i32` 调用，且不包含直接算术函数体。
4. Confirm `docs/architecture/ir-evaluator-backend.md` matches opcode constants
   in both Java serialization and C++ evaluation.
   确认状态文档与 Java 序列化端、C++ 求值端的 opcode 常量一致。
