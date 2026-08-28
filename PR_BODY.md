# IR phase 6 compiler review / IR 编译器第六阶段审查

Preferred base / 首选基线:
`cursor/ir-phase5-fable-review-6d81`
(`b72e3cf0d1cbf128a7f98508d98cbf1f63de1217`).

Review verdict / 审查结论: **Accept with nits / 有附带条件地接受**.

## (a) Scope / 范围

- This branch is the substitute compiler review of
  [PR #47](https://github.com/gaoyu06/native-obfuscator/pull/47), stacked on
  [PR #45](https://github.com/gaoyu06/native-obfuscator/pull/45). Every changed
  IR implementation file and the added tests were read.
- `TABLESWITCH` and `LOOKUPSWITCH` were checked from CFG leader/successor
  construction through typed IR and structured C++ emission. Every case and
  default performs parallel phi copies and then `goto`.
- String and Object `ANEWARRAY` paths were checked for negative lengths,
  component resolution, `NewObjectArray`, null allocation, `ExceptionCheck()`,
  and protected/unprotected exceptional exits.
- One blocker was fixed: array-typed components such as
  `[Ljava/lang/String;` now use `JNIEnv::FindClass` with the unchanged
  descriptor instead of the ordinary-name resolver. A regression and the g++
  smoke now cover this path.
- Fallback-before-mutation, the `legacy` default, and retained snippet resources
  were independently confirmed. Detailed evidence is in
  `docs/architecture/ir-phase6-review.md`.

- 本分支是对
  [PR #47](https://github.com/gaoyu06/native-obfuscator/pull/47) 的替代编译器审查，
  基于 [PR #45](https://github.com/gaoyu06/native-obfuscator/pull/45)。已逐一阅读全部
  IR 改动文件及新增测试。
- 已从 CFG leader/后继构建、typed IR 一直核验到 `TABLESWITCH` /
  `LOOKUPSWITCH` 的结构化 C++ 发射；每个 case 与 default 都先完成并行 phi copy，
  再执行 `goto`。
- 已核验 String/Object `ANEWARRAY` 的负长度、组件类解析、`NewObjectArray`、空
  allocation、`ExceptionCheck()` 以及受保护/不受保护异常出口。
- 已修复一个阻断项：`[Ljava/lang/String;` 等数组组件现在保留原描述符并通过
  `JNIEnv::FindClass` 解析，不再误走普通类名解析器；新增回归及 g++ 冒烟覆盖。
- 已独立确认 mutation 前 fallback、默认 `legacy` 及 snippet 资源保留。详细证据见
  `docs/architecture/ir-phase6-review.md`。

## (b) Ship-ready? / 可直接发布？

**No / 否。**

The reviewed phase-6 delta has no remaining compiler correctness blocker, but
this focused review provides unit and C++ syntax evidence rather than the full
supported-platform native runtime-parity gate. The stacked base also still
requires normal human disposition. Keep `legacy` as the default.

已审查的 phase-6 增量不存在剩余编译器正确性阻断项，但本次聚焦审查提供的是单元测试
与 C++ 语法证据，不能替代全部受支持平台上的 native 运行时等价性门禁；堆叠基线也仍需
人工正常处理。默认值应继续保持为 `legacy`。

## (c) This IS the review / 本次提交即为审查

**Yes / 是。**

This document and `docs/architecture/ir-phase6-review.md` constitute the
requested compiler review; they are not a request for another substitute
review. The verdict is accept-with-nits after the blocker fix, with no remaining
blocker found in scope.

本文件与 `docs/architecture/ir-phase6-review.md` 即为所要求的编译器审查，并非再次
请求替代审查。修复阻断项后的结论为“有附带条件地接受”，审查范围内未发现剩余阻断项。

## (d) Human preconditions / 人工前置条件

1. Compare and land this work on `cursor/ir-phase5-fable-review-6d81`, not
   `master`, preserving the stack order.
   必须基于 `cursor/ir-phase5-fable-review-6d81` 比较与落地，不得改用 `master`，
   并保持堆叠顺序。
2. Require final supported-platform/JDK CI and native runtime-parity checks.
   最终提交必须通过受支持平台/JDK 的 CI 及 native 运行时等价性检查。
3. Re-run the focused command and inspect JUnit XML. Recorded final result:
   `IrCompilerTest` 27 plus `CodegenModeTest` 2, total 29; zero skipped,
   failures, or errors.
   重跑聚焦命令并检查 JUnit XML。最终记录为 27 + 2，共 29 个测试；跳过、失败、
   错误均为零。
4. Confirm the g++ testcase remains unskipped when g++ and JNI headers are
   present, and independently syntax-check the retained 23-method translation
   unit.
   当 g++ 与 JNI 头文件存在时，确认该测试未跳过，并独立语法检查保留的 23-method
   翻译单元。
5. During conflict resolution, retain the array-component `FindClass` fix,
   switch default edges, parallel phi transfers, JNI failure routing, mutation
   regressions, `legacy` default, and snippet resources.
   解决冲突时必须保留数组组件 `FindClass` 修复、switch default 边、并行 phi 传递、
   JNI 失败路由、mutation 回归、`legacy` 默认值及 snippet 资源。
