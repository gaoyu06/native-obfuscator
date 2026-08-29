# IR phase 11 compiler review / IR 编译器第十一阶段审阅

Review branch / 审阅分支: `cursor/ir-phase11-fable-review-6d81`, based on
`cursor/ir-compiler-phase11-6d81` (draft PR #78, code commit `a8f3b1f`),
stacked on `cursor/ir-compiler-phase10-6d81`
(`b8cdb8efb09c135e7d119249f48feba22cf7e8f4`).

审阅分支 `cursor/ir-phase11-fable-review-6d81`，基于
`cursor/ir-compiler-phase11-6d81`（草稿 PR #78，代码提交 `a8f3b1f`），
堆叠于 `cursor/ir-compiler-phase10-6d81`
(`b8cdb8efb09c135e7d119249f48feba22cf7e8f4`)。

## Verdict / 结论

**accept.** No correctness defect was found; this review is documentation-only
and changed no compiler code.

**accept（通过）。** 未发现正确性缺陷；本次审阅仅为文档，未改动任何编译器代码。

Full findings: `docs/architecture/ir-phase11-fable-review.md`.
完整结论见 `docs/architecture/ir-phase11-fable-review.md`。

## Summary / 摘要

Phase 11 admits `INVOKEINTERFACE` and non-constructor `INVOKESPECIAL` into the
optional Java bytecode → typed CFG IR → C++/JNI path, restricted to the exact
`I`, exact `J`, object/array reference, and `V` invoke carriers. The review
confirms the lowering, typing, exceptional-exit, and reject-before-mutation
behavior and finds them consistent with the JNI contract. The default remains
`legacy`.

第十一阶段将 `INVOKEINTERFACE` 与非构造器 `INVOKESPECIAL` 纳入可选的
Java 字节码 → typed CFG IR → C++/JNI 路径，仅限精确 `I`、精确 `J`、
对象/数组引用及 `V` invoke carrier。审阅确认其 lowering、类型化、异常出口与
mutation 前拒绝行为均与 JNI 约定一致。默认值仍为 `legacy`。

## (a) Change scope / 本次改动范围

This review branch adds documentation only:

- `docs/architecture/ir-phase11-fable-review.md`: the review with verdict,
  checklist findings, non-blocking observations, and real test counts.
- `PR_BODY.md`: this bilingual review summary.

No files under `src/main` or `src/test` were modified by this branch.

本审阅分支仅新增文档：

- `docs/architecture/ir-phase11-fable-review.md`：含结论、逐项核对、非阻塞
  观察及真实测试计数的审阅报告。
- `PR_BODY.md`：本双语审阅摘要。

本分支未修改 `src/main` 或 `src/test` 下的任何文件。

Reviewed compiler behavior on the base (unchanged by this branch):
本分支未改动、仅审阅的基线编译器行为：

- `INVOKEINTERFACE` uses `GetMethodID` on the interface owner and the
  `Call[Int|Long|Object|Void]Method` families.
  `INVOKEINTERFACE` 对接口 owner 使用 `GetMethodID`，并调用
  `Call[Int|Long|Object|Void]Method` family。
- Non-constructor `INVOKESPECIAL` emits
  `CallNonvirtual[Int|Long|Object|Void]Method` with receiver, declaring class,
  method ID, then descriptor arguments.
  非构造器 `INVOKESPECIAL` 生成
  `CallNonvirtual[Int|Long|Object|Void]Method`，依次传入 receiver、声明类、
  method ID 及描述符参数。
- `<init>` stays on `CallNonvirtualVoidMethod`; constructor bodies stay excluded.
  `<init>` 继续走 `CallNonvirtualVoidMethod`；构造器方法体继续被排除。
- Null interface/special receivers take the shared exceptional exit;
  `invokedynamic` and primitive descriptors `Z/B/C/S/F/D` are rejected before
  mutation.
  null 接口/special receiver 走共享异常出口；`invokedynamic` 与 primitive
  描述符 `Z/B/C/S/F/D` 在 mutation 前被拒绝。

## (b) Can this ship to production as-is? / 是否可直接上线？

**No / 否。**

Phase 11 remains a partial, opt-in compiler slice. Unsupported bytecodes and
descriptors still fall back per method, including other primitive invoke
carriers, float/double operations, `MULTIANEWARRAY`, non-`int` primitive array
operations, invokedynamic, constructor method bodies, and category-two stack
manipulation. Focused unit tests plus a C++ syntax check do not replace
supported-platform native runtime-parity gates.

第十一阶段仍是部分、可选的编译器增量。不支持的字节码与描述符仍按方法
fallback，包括其他 primitive invoke carrier、float/double 操作、
`MULTIANEWARRAY`、非 `int` primitive array 操作、invokedynamic、构造器方法体
及 category-two stack manipulation。聚焦单测加 C++ 语法检查不能替代受支持平台上
的 native 运行时等价性门禁。

## (c) Is review required? / 上线前是否需要 review？

**Yes / 是。**

Even with the accept verdict, promotion beyond the opt-in path requires
confirming descriptor-exact IR typing, interface lookup and virtual JNI-family
selection, nonvirtual receiver/class/method-ID ordering, null-receiver exception
routing, and fallback-before-mutation. The stacked phase-9 array-return and
phase-10 field regressions must remain present.

即便结论为 accept，将该路径推广到 opt-in 之外仍需确认描述符精确的 IR typing、
接口 lookup 与 virtual JNI family 选择、nonvirtual receiver/class/method-ID
顺序、null receiver 异常路由，以及 mutation 前 fallback。堆叠基线中的 phase-9
数组返回与 phase-10 字段回归必须保留。

## (d) Review preconditions / Review 前置条件

1. Compare against `cursor/ir-compiler-phase10-6d81` at `b8cdb8e…`, not
   `master` or docs-only branches.
   基于 `cursor/ir-compiler-phase10-6d81` 的 `b8cdb8e…` 比较，勿改用 `master`
   或 docs-only 分支。
2. Re-run the focused Gradle command with `CC=gcc CXX=g++ --rerun-tasks` and
   read the actual JUnit XML counts. This review's run: `IrCompilerTest` 53 and
   `CodegenModeTest` 2, total 55; zero skipped, failures, or errors.
   使用 `CC=gcc CXX=g++ --rerun-tasks` 重跑聚焦 Gradle 命令并读取实际 JUnit XML
   计数。本次审阅运行结果：53 + 2，共 55；跳过、失败、错误均为零。
3. With g++ and JNI headers present, confirm
   `generatedCppPassesGppSyntaxCheckWhenToolchainAvailable` is not skipped. In
   this run it executed (0.22 s) with no `<skipped>` element.
   当 g++ 与 JNI headers 存在时，确认
   `generatedCppPassesGppSyntaxCheckWhenToolchainAvailable` 未跳过。本次运行其
   已执行（0.22 s），无 `<skipped>` 元素。
4. Inspect generated C++ for exact `Call[Int|Long|Object|Void]Method` and
   `CallNonvirtual[Int|Long|Object|Void]Method` families; verify interface
   argument count matches the descriptor and nonvirtual calls pass receiver,
   declaring class, method ID, then arguments.
   检查生成 C++ 是否使用精确的 `Call[Int|Long|Object|Void]Method` 与
   `CallNonvirtual[Int|Long|Object|Void]Method` family；确认接口实参数量与描述符
   一致，且 nonvirtual 调用依次传入 receiver、声明类、method ID 及参数。
5. Verify a null interface receiver takes the shared exceptional exit and keeps
   the `NullPointerException` pending.
   确认 null 接口 receiver 进入共享异常出口并保持 `NullPointerException` pending。
6. During conflict resolution, retain fallback-before-mutation, the phase-9
   `jarray` cast, phase-10 field coverage, constructor-method exclusion, the
   `legacy` default, and all existing snippets.
   解决冲突时保留 mutation 前 fallback、phase-9 `jarray` 转换、phase-10 字段
   覆盖、构造器方法体排除、`legacy` 默认值及全部现有 snippets。
