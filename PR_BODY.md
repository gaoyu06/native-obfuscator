<!-- CURSOR_AGENT_PR_BODY_BEGIN -->
## Summary / 摘要

Updates the documentation-only maintainer brief through draft PR #48, using
only the records committed on the named draft branches. It preserves #37's
conclusion that requirement 7 is not met and keeps option A as a v1 product
recommendation only, without shrinking the written engineering goal.

将仅文档的维护者简报更新至草稿 PR #48，仅采用指定草稿分支已提交的记录。保留
#37 对 requirement 7 未满足的结论，并仅将 A 保留为 v1 产品建议，不缩小书面
工程目标。

## (a) 本次改动范围 / Change scope

- Record #44's accept-with-nits review of evaluator #42 and #45's Fable
  accept-with-nits review of phase 5 #40.
- Record #46's clean `NativeStrings` stack on SDK v1 #12: no general benchmark
  harness duplication, and the local remeasurement was slower than Java.
- Record #47's still-opt-in switches plus object `ANEWARRAY`; its status
  document claims 26 `IrCompilerTest` plus 2 `CodegenModeTest`.
- Record #48's live stripped `--ir-lower=eval` `.so`; it has no reader/recovery
  pass and is not requirement-7 evidence.
- Update only `docs/architecture/goal-status-and-options.md` and this bilingual
  PR body. All PRs through #48 remain drafts; `master` remains `e7ca4c8`.

- 记录 #44 对 evaluator #42 的 accept-with-nits 审阅，以及 #45 对 phase 5
  #40 的 Fable accept-with-nits 审阅。
- 记录 #46 干净叠在 SDK v1 #12 上的 `NativeStrings`：未复制通用 benchmark
  harness，且本地复测慢于 Java。
- 记录 #47 仍为 opt-in 的 switches 与对象 `ANEWARRAY`；其状态文档声称
  26 个 `IrCompilerTest` 加 2 个 `CodegenModeTest`。
- 记录 #48 的 live stripped `--ir-lower=eval` `.so`；它没有 reader/recovery
  pass，不能作为 requirement 7 证据。
- 仅更新 `docs/architecture/goal-status-and-options.md` 与本双语 PR body。
  截至 #48 的 PR 均为草稿；`master` 仍为 `e7ca4c8`。

## (b) 是否可直接上线 / Can this ship to production as-is?

No. / 否。

All referenced work remains in draft PRs and `master` contains none of it.
#47 remains partial and opt-in; #46's local result was slower than Java and is
not portable; and #48 is an artifact without a reader. #37 fully recovered all
four methods from #35's valid live direct-IR stripped `.so`, so requirement 7
is not met.

所有相关工作仍在草稿 PR 中，`master` 未包含这些内容。#47 仍为不完整的 opt-in
切片；#46 的本地结果慢于 Java 且不可外推；#48 只是没有 reader 的 artifact。
#37 从 #35 的有效 live direct-IR stripped `.so` 完整恢复四个方法，因此
requirement 7 未满足。

## (c) 上线前是否需要 review / Is review required?

Yes. / 是。

Review every statement against the named source document and preserve its
scope. Each implementation stack still requires independent code review,
post-rebase verification, and applicable product/release approval.

每项陈述均须与指定来源文档核对并保留其范围；每条实现栈仍需独立代码审查、
rebase 后复测及适用的产品/发布审批。

## (d) review 的前置条件 / Review preconditions

1. Use #44's `docs/architecture/ir-evaluator-review.md`: verdict
   accept-with-nits, no compiler change, and no requirement-7 evidence.
2. Use #45's `docs/architecture/ir-phase5-fable-review.md`: Fable's verdict is
   accept-with-nits; no compiler code changed.
3. Use #46's `docs/sdk/v1-status.md`: `NativeStrings` is cleanly stacked on
   #12 without the general benchmark harness; its local diagnostic was slower
   than Java and is neither portable nor a speedup claim.
4. Use #47's `docs/architecture/ir-phase6-status.md`: switches and general
   object `ANEWARRAY`; 26 `IrCompilerTest` plus 2 `CodegenModeTest`; opt-in IR,
   per-method fallback, and default legacy.
5. Use #48's `docs/eval/ir-eval-lower/run.md` and `liveness.md`: the evaluator
   artifact is live and stripped, but no reader/recovery pass occurred.
6. Preserve #37 and the complete written goal. Option A remains only the v1
   product recommendation.
7. Preserve the lanes: direct IR through #45 then #47; evaluator
   #42 → #44 → #48 as a sibling; SDK #12 → #15 → #46; compatibility
   #6 → #9 → #14 → #41.

中文核对项：

1. 以 #44 的 `docs/architecture/ir-evaluator-review.md` 为准：结论为
   accept-with-nits，未改编译器，且不是 requirement 7 证据。
2. 以 #45 的 `docs/architecture/ir-phase5-fable-review.md` 为准：Fable
   结论为 accept-with-nits，未改编译器代码。
3. 以 #46 的 `docs/sdk/v1-status.md` 为准：`NativeStrings` 干净叠在 #12
   上，未复制通用 benchmark harness；本地诊断慢于 Java，不能外推，也不是
   speedup 声明。
4. 以 #47 的 `docs/architecture/ir-phase6-status.md` 为准：switches 与
   通用对象 `ANEWARRAY`；26 个 `IrCompilerTest` 加 2 个
   `CodegenModeTest`；IR 仍为 opt-in，逐方法 fallback，默认 legacy。
5. 以 #48 的 `docs/eval/ir-eval-lower/run.md` 与 `liveness.md` 为准：
   evaluator artifact 有效且已 strip，但没有 reader/recovery pass。
6. 保留 #37 与完整书面目标；A 仅作为 v1 产品建议。
7. 保持各线：direct IR 依序至 #45，再到 #47；evaluator sibling
   #42 → #44 → #48；SDK #12 → #15 → #46；compatibility
   #6 → #9 → #14 → #41。

<!-- CURSOR_AGENT_PR_BODY_END -->
