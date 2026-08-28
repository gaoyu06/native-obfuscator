# IR compiler phase 8 / IR 编译器第八阶段

Preferred base / 首选基线:
`cursor/ir-phase7-sol-review-6d81-f29d`
(`2a36df34bb8a5a7a09e1c2c870037622c6c5ac80`).

## Summary / 摘要

Opt-in IR (`--codegen=ir`) now lowers ordinary `NEW` through
`JNIEnv::AllocObject` and high-level `INVOKESPECIAL <init>` through
`CallNonvirtualVoidMethod`. Static and virtual invokes now cover exact `I`,
`J`, and reference arguments plus `V`, exact `I`, `J`, and reference returns
with the matching JNI call families. The default remains `legacy`. Detailed
evidence is in `docs/architecture/ir-phase8-status.md`.

可选 IR（`--codegen=ir`）现可通过 `JNIEnv::AllocObject` 降低普通 `NEW`，并将
高层 `INVOKESPECIAL <init>` 降为 `CallNonvirtualVoidMethod`。静态与虚调用现支持
精确 `I`、`J` 及引用参数，并支持 `V`、精确 `I`、`J` 及引用返回，使用对应 JNI
调用族。默认值仍为 `legacy`。详细证据见
`docs/architecture/ir-phase8-status.md`。

## (a) Change scope / 本次改动范围

- Added a typed `NEW` IR node, existing-cache class resolution,
  `AllocObject`, and exceptional-exit handling.
- Added constructor-only `INVOKESPECIAL` with receiver/arguments represented
  in the high-level invoke node and emitted by `CallNonvirtualVoidMethod`.
- Expanded static/virtual high-level invokes to long arguments, and
  void/long/object returns. Tests exercise long, String-returning, and static
  void shapes.
- Added three regressions, including fallback-before-mutation after newly
  admitted allocation/constructor operations, and expanded the real g++ smoke
  from 29 to 34 methods.
- Kept constructor method bodies excluded, the default `legacy`, and all
  existing snippets.

- 新增 typed `NEW` IR 节点、基于现有缓存的类解析、`AllocObject` 及异常出口处理。
- 新增仅限构造器的 `INVOKESPECIAL`；receiver/参数保留在高层 invoke 节点中，并由
  `CallNonvirtualVoidMethod` 发射。
- 将静态/虚高层调用扩展到 long 参数及 void/long/object 返回；测试覆盖 long、
  String 返回及静态 void 形状。
- 新增 3 个回归，包括新接纳分配/构造器操作后的 mutation 前 fallback，并将真实
  g++ 烟测从 29 个方法扩展到 34 个方法。
- 构造器方法体仍不处理，默认值仍为 `legacy`，全部现有 snippets 均保留。

## (b) Can this ship to production as-is? / 是否可直接上线？

**No / 否。**

Phase 8 is still a partial, opt-in compiler slice. Unsupported bytecodes,
descriptor sorts, interface/dynamic calls, non-constructor special calls, and
reference-returning method bodies still fall back. Focused unit and C++ syntax
evidence does not replace supported-platform native runtime-parity gates.

第八阶段仍是部分、可选的编译器增量。不支持的字节码与描述符 sort、接口/动态调用、
非构造器 special 调用及引用返回方法体仍会 fallback。聚焦单测与 C++ 语法证据不能
替代受支持平台上的 native 运行时等价性门禁。

## (c) Is review required? / 上线前是否需要 review？

**Yes / 是。**

Review class-cache/allocation failure routing, verified `NEW`/`DUP`/constructor
stack behavior, `CallNonvirtualVoidMethod` argument order, invoke carrier-to-JNI
family mapping, and fallback-before-mutation.

需要审查类缓存/分配失败路由、经验证的 `NEW`/`DUP`/构造器 stack 行为、
`CallNonvirtualVoidMethod` 参数顺序、invoke carrier 到 JNI 调用族的映射，以及
mutation 前 fallback。

## (d) Review preconditions / Review 前置条件

1. Compare and land on `cursor/ir-phase7-sol-review-6d81-f29d` (PR #56,
   reviewing PR #54), not `master`, preserving the stack order.
   必须基于 `cursor/ir-phase7-sol-review-6d81-f29d`（PR #56，审查 PR #54）
   比较与落地，不得改用 `master`，并保持堆叠顺序。
2. Re-run the focused Gradle command and inspect JUnit XML. Recorded final
   result: `IrCompilerTest` 36 plus `CodegenModeTest` 2, total 38; zero
   skipped, failures, or errors.
   重跑聚焦 Gradle 命令并检查 JUnit XML。最终记录为 36 + 2，共 38 个测试；跳过、
   失败、错误均为零。
3. Confirm the g++ testcase remains unskipped when g++ and JNI headers are
   present, and independently syntax-check the retained 34-method translation
   unit with `g++ -std=c++17 -fsyntax-only`.
   当 g++ 与 JNI 头文件存在时，确认该测试未跳过，并使用
   `g++ -std=c++17 -fsyntax-only` 独立语法检查保留的 34-method 翻译单元。
4. Require supported-platform/JDK CI and native runtime-parity checks before
   any production decision.
   任何生产决策前都必须通过受支持平台/JDK 的 CI 及 native 运行时等价性检查。
5. During conflict resolution, retain exception routing, constructor-method
   exclusion, exact JNI carriers, fallback-before-mutation, the `legacy`
   default, and existing snippets.
   解决冲突时必须保留异常路由、构造器方法体排除策略、精确 JNI carrier、mutation
   前 fallback、`legacy` 默认值及现有 snippets。
