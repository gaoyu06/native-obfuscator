# IR phase 8 compiler review / IR 编译器第八阶段审查

Preferred base / 首选基线:
`cursor/ir-compiler-phase8-6d81`
(`95eb5ffc...`, [PR #62](https://github.com/gaoyu06/native-obfuscator/pull/62)).

Review verdict / 审查结论: **Accept / 接受**.

## (a) Scope / 范围

- This branch is the compiler review of
  [PR #62](https://github.com/gaoyu06/native-obfuscator/pull/62), stacked on
  [PR #56](https://github.com/gaoyu06/native-obfuscator/pull/56). The full
  phase-8 diff and every changed IR file were read.
- `NEW` lowering was checked end to end: array descriptors stay excluded,
  the class is resolved through the existing cache path, `AllocObject` runs
  without a constructor, and a null class slot, null result, or pending
  exception each routes to the exceptional exit.
- `INVOKESPECIAL <init>` is admitted only as a void constructor call and is
  emitted as `env->CallNonvirtualVoidMethod(receiver, cclasses[id],
  cmethods[id], args...)` with a `GetMethodID` instance lookup — correct JNI
  argument order and method-id kind.
- `NEW`/`DUP`/constructor stack behavior was verified: `DUP` aliases the SSA
  reference, the void constructor consumes one copy, and the surviving copy is
  the allocated object.
- Static and virtual invokes were checked for widening to `J` and reference
  arguments and to `V`/`I`/`J`/reference returns, mapping to the correct
  `CallStatic*`/`Call*`/`CallNonvirtual*` JNI families; `String.length()` stays
  on its dedicated intrinsic.
- The `unsupportedAfterNew` regression proves rejection before method, output,
  native-metadata, or cache mutation. The CLI and API defaults remain `legacy`.
- Constructor method bodies remain out of scope. No correctness blocker was
  found. Detailed evidence is in
  `docs/architecture/ir-phase8-fable-review.md`.

- 本分支是对
  [PR #62](https://github.com/gaoyu06/native-obfuscator/pull/62) 的编译器审查，
  基于 [PR #56](https://github.com/gaoyu06/native-obfuscator/pull/56)。已阅读完整
  phase-8 diff 及全部改动的 IR 文件。
- 已完整核验 `NEW` 降低：仍排除数组描述符，类经现有缓存路径解析，`AllocObject`
  不运行构造器；类槽为空、结果为空或存在挂起异常时均路由到异常出口。
- `INVOKESPECIAL <init>` 仅作为 void 构造器调用被接纳，并发射为
  `env->CallNonvirtualVoidMethod(receiver, cclasses[id], cmethods[id], args...)`，
  method id 走 `GetMethodID` 实例查找——JNI 参数顺序与 method-id 种类均正确。
- 已核验 `NEW`/`DUP`/构造器 stack 行为：`DUP` 别名同一 SSA 引用，void 构造器消耗
  一份副本，存活副本即为已分配对象。
- 已核验静态与虚调用扩展到 `J` 及引用参数、`V`/`I`/`J`/引用返回，并映射到正确的
  `CallStatic*`/`Call*`/`CallNonvirtual*` JNI 调用族；`String.length()` 仍走专用
  intrinsic。
- `unsupportedAfterNew` 回归证明拒绝先于方法、输出、native 元数据或缓存 mutation；
  CLI 与 API 默认值仍为 `legacy`。
- 构造器方法体仍不在范围内。未发现正确性阻断项。详细证据见
  `docs/architecture/ir-phase8-fable-review.md`。

## (b) Ship-ready? / 可直接发布？

**No / 否。**

The reviewed phase-8 delta has no compiler correctness blocker, but this
focused review provides unit and C++ syntax evidence rather than the full
supported-platform native runtime-parity gate. The stacked base also still
requires normal human disposition. Keep `legacy` as the default.

已审查的 phase-8 增量不存在编译器正确性阻断项，但本次聚焦审查提供的是单元测试与
C++ 语法证据，不能替代全部受支持平台上的 native 运行时等价性门禁；堆叠基线也仍需
人工正常处理。默认值应继续保持为 `legacy`。

## (c) This IS the review / 本次提交即为审查

**Yes / 是。**

This document and `docs/architecture/ir-phase8-fable-review.md` constitute the
requested compiler review; they are not a request for another substitute
review. The verdict is accept, with no blocker or nit found in scope (one
non-blocking observation: a harmless, never-taken receiver null check on the
constructor invoke path).

本文件与 `docs/architecture/ir-phase8-fable-review.md` 即为所要求的编译器审查，并非
再次请求替代审查。结论为“接受”，审查范围内未发现阻断项或附带问题（仅一条不阻断的
观察：构造器 invoke 路径上有一处无害且永不触发的 receiver 空检查）。

## (d) Human preconditions / 人工前置条件

1. Compare and land this work on `cursor/ir-compiler-phase8-6d81`, not
   `master`, preserving the stack order.
   必须基于 `cursor/ir-compiler-phase8-6d81` 比较与落地，不得改用 `master`，
   并保持堆叠顺序。
2. Require final supported-platform/JDK CI and native runtime-parity checks.
   最终提交必须通过受支持平台/JDK 的 CI 及 native 运行时等价性检查。
3. Re-run the focused command and inspect JUnit XML. Recorded final result:
   `IrCompilerTest` 36 plus `CodegenModeTest` 2, total 38; zero skipped,
   failures, or errors.
   重跑聚焦命令并检查 JUnit XML。最终记录为 36 + 2，共 38 个测试；跳过、失败、
   错误均为零。
4. Confirm the g++ testcase remains unskipped when g++ and JNI headers are
   present, and independently syntax-check the retained 34-method translation
   unit with `g++ -std=c++17 -fsyntax-only`.
   当 g++ 与 JNI 头文件存在时，确认该测试未跳过，并使用
   `g++ -std=c++17 -fsyntax-only` 独立语法检查保留的 34-method 翻译单元。
5. During conflict resolution, retain cached-class allocation and its failure
   routing, `NEW`/`DUP`/constructor stack behavior, the
   `CallNonvirtualVoidMethod` argument order, the invoke carrier-to-JNI family
   mapping, constructor-method-body exclusion, reject-before-mutation coverage,
   and the `legacy` default.
   解决冲突时必须保留缓存类分配及其失败路由、`NEW`/`DUP`/构造器 stack 行为、
   `CallNonvirtualVoidMethod` 参数顺序、invoke carrier 到 JNI 调用族的映射、
   构造器方法体排除策略、mutation 前拒绝覆盖及 `legacy` 默认值。
