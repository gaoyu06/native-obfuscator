# IR compiler phase 6 / IR 编译器第六阶段

Preferred base / 首选基线:
`cursor/ir-phase5-fable-review-6d81`
(`b72e3cf0d1cbf128a7f98508d98cbf1f63de1217`).

## (a) Scope / 改动范围

- Add typed `TABLESWITCH` and `LOOKUPSWITCH` terminators. Every case and the
  required default are explicit CFG successors; stack/local carrier analysis
  and phi connection cover all of them. Structured C++ emission performs
  parallel phi copies and a `goto` in every arm.
- Add general object `ANEWARRAY` lowering through the existing class cache and
  `JNIEnv::NewObjectArray`. Negative lengths raise
  `NegativeArraySizeException`; class-resolution, null-allocation, and pending
  exception failures use the phase-5 exceptional routing.
- Add switch-default carrier validation, String/Object array coverage,
  fallback-before-mutation regression coverage, and a real 22-method
  `g++ -std=c++17 -fsyntax-only` smoke.
- Add `docs/architecture/ir-phase6-status.md` with results copied from the
  generated JUnit XML. The default remains `legacy`; constructor policy and
  snippet resources are unchanged.

- 新增 typed `TABLESWITCH` / `LOOKUPSWITCH` 终结器。每个 case 与必需的 default
  都是显式 CFG 后继；栈/局部变量 carrier 分析及 phi 连接覆盖全部后继。结构化 C++
  发射会在每个分支执行并行 phi copy 后 `goto`。
- 新增通用对象 `ANEWARRAY` lowering，复用现有 class cache 并调用
  `JNIEnv::NewObjectArray`。负长度抛出 `NegativeArraySizeException`；类解析失败、
  allocation 返回空值及 pending exception 都沿用 phase-5 异常路由。
- 新增 switch default carrier 校验、String/Object 数组覆盖、mutation 前 fallback
  回归，以及真实的 22-method `g++ -std=c++17 -fsyntax-only` 冒烟测试。
- 新增 `docs/architecture/ir-phase6-status.md`，其中计数直接来自 JUnit XML。
  默认仍为 `legacy`；未改变构造器策略，也未删除 snippet 资源。

## (b) Ship-ready? / 是否可直接上线

**No.**

Phase 6 is still an opt-in staged subset. Wide carriers, constructors and object
creation, reference fields/returns/stores, broader invokes and arrays,
`CHECKCAST`/`INSTANCEOF`, and many stack/control opcodes still fall back per
method. Keep `legacy` as the default while coverage and runtime-parity testing
continue.

**否。**

Phase 6 仍是 opt-in 的阶段性子集。wide carrier、构造器与对象创建、引用字段/返回/写入、
更广的调用与数组形态、`CHECKCAST`/`INSTANCEOF` 及许多栈/控制 opcode 仍按方法回退。
在继续扩大覆盖及运行时等价性验证期间，应保持 `legacy` 为默认值。

## (c) Review required? / 是否需要 review

**Yes.**

Please review switch CFG construction (especially default and duplicate
destinations), phi edge transfers, object-component class-cache lifetime, and
pending-exception routing. The new default-only carrier mismatch regression and
the emitted C++ excerpts in the status document are the main evidence.

**是。**

请重点 review switch CFG 构建（尤其 default 与重复目标）、phi 边传递、对象 component
class cache 生命周期及 pending-exception 路由。新增的“仅经 default 到达”的 carrier
不匹配回归，以及状态文档中的生成 C++ 证据，是主要核验点。

## (d) Preconditions for a human / 人工前置条件

1. Review this branch on top of
   `cursor/ir-phase5-fable-review-6d81` at `b72e3cf0…`; do not use `master` as
   the comparison base.
   请基于 `cursor/ir-phase5-fable-review-6d81`（`b72e3cf0…`）审阅，不要以
   `master` 作为比较基线。
2. Reproduce the focused command and inspect XML rather than relying only on
   console output:
   请复现指定命令并直接核对 XML，而不只依赖控制台输出：

   ```text
   ./gradlew :obfuscator:test \
     --tests by.radioegor146.ir.IrCompilerTest \
     --tests by.radioegor146.CodegenModeTest
   ```

   Recorded result / 已记录结果:
   `IrCompilerTest` 26, `CodegenModeTest` 2, total 28; 0 skipped, 0 failures,
   0 errors.
3. Confirm
   `generatedCppPassesGppSyntaxCheckWhenToolchainAvailable` has no `<skipped>`
   child and that the emitted 22-method TU contains `tableSelect`,
   `lookupSelect`, `allocateStrings`, and `allocateObjects`. Re-run the real
   C++17 syntax command with JNI include paths.
   确认该测试没有 `<skipped>`，22-method 翻译单元包含上述四个新增方法，并使用 JNI
   include 路径真实重跑 C++17 syntax-only 命令。
4. Inspect generated switch arms: cases and default must each copy incoming
   phi carriers then jump to the selected block. Inspect object-array emission:
   `< 0` raises `NegativeArraySizeException`; class resolution, null result,
   and `ExceptionCheck()` all take the correct exceptional exit.
   检查生成的 switch：每个 case/default 都必须复制 phi carrier 后跳转；检查对象数组：
   `< 0` 抛 `NegativeArraySizeException`，类解析、空结果及 `ExceptionCheck()` 均走正确
   的异常出口。
5. Reconfirm fallback atomicity with
   `rejectsUnsupportedInstructionAfterAnewarrayBeforeMutation`,
   `rejectsNonIntStaticFieldBeforeMutation`, and
   `rejectsIntStoreIntoInstanceReceiverLocal`; confirm CLI default `legacy`,
   `<init>` policy, and snippet resources are unchanged.
   用上述三个测试复核 mutation 前 fallback；同时确认 CLI 默认 `legacy`、`<init>` 策略
   及 snippet 资源均未变化。
