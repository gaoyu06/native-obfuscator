<!-- CURSOR_AGENT_PR_BODY_BEGIN -->
## Summary / 摘要

Updates the documentation-only maintainer brief through draft PR #50, using
only the records committed on the named draft branches. It records recovery
of all four methods from both valid live direct-IR and shared-evaluator
stripped `.so` subjects, preserves the conclusion that requirement 7 is not
met, and keeps option A as a v1 product recommendation only without shrinking
the written engineering goal.

将仅文档的维护者简报更新至草稿 PR #50，仅采用指定草稿分支已提交的记录。记录
有效 live direct-IR 与 shared-evaluator stripped `.so` 的四个方法均被完整恢复，
保留 requirement 7 未满足的结论，并仅将 A 保留为 v1 产品建议，不缩小书面工程
目标。

## (a) 本次改动范围 / Change scope

- Record #44's accept-with-nits review of evaluator #42 and #45's Fable
  accept-with-nits review of phase 5 #40.
- Record #46's clean `NativeStrings` stack on SDK v1 #12: no general benchmark
  harness duplication, and the local remeasurement was slower than Java.
- Record #47's still-opt-in switches plus object `ANEWARRAY`; its status
  document claims 26 `IrCompilerTest` plus 2 `CodegenModeTest`.
- Record #48's live stripped `--ir-lower=eval` `.so` and #50's recovery-first
  blinded reader: `add`, `sumTo`, `subMul`, and `mix` all scored **full** on
  the valid live evaluator subject.
- Update only `docs/architecture/goal-status-and-options.md` and this bilingual
  PR body. All PRs through #50 remain drafts; `master` remains `e7ca4c8`.

- 记录 #44 对 evaluator #42 的 accept-with-nits 审阅，以及 #45 对 phase 5
  #40 的 Fable accept-with-nits 审阅。
- 记录 #46 干净叠在 SDK v1 #12 上的 `NativeStrings`：未复制通用 benchmark
  harness，且本地复测慢于 Java。
- 记录 #47 仍为 opt-in 的 switches 与对象 `ANEWARRAY`；其状态文档声称
  26 个 `IrCompilerTest` 加 2 个 `CodegenModeTest`。
- 记录 #48 的 live stripped `--ir-lower=eval` `.so` 与 #50 先恢复、后评分的
  blinded reader：有效 live evaluator 样本中的 `add`、`sumTo`、`subMul`、
  `mix` 均为 **full**。
- 仅更新 `docs/architecture/goal-status-and-options.md` 与本双语 PR body。
  截至 #50 的 PR 均为草稿；`master` 仍为 `e7ca4c8`。

## (b) 是否可直接上线 / Can this ship to production as-is?

No. / 否。

All referenced work remains in draft PRs and `master` contains none of it.
#47 remains partial and opt-in; #46's local result was slower than Java and is
not portable. #37 and #50 respectively recovered all four methods from valid
live direct-IR and shared-evaluator stripped `.so` subjects, so requirement 7
is not met.

所有相关工作仍在草稿 PR 中，`master` 未包含这些内容。#47 仍为不完整的 opt-in
切片；#46 的本地结果慢于 Java 且不可外推。#37 与 #50 分别从有效 live
direct-IR 与 shared-evaluator stripped `.so` 完整恢复四个方法，因此
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
5. Use #48's `docs/eval/ir-eval-lower/run.md` and `liveness.md`, then #50's
   `recovery.md` and `scores.md`: recovery was committed before source/oracle
   scoring, all four methods scored full, and the subject was valid live
   evaluator code rather than DCE.
6. Preserve both #37 and #50 as evidence that requirement 7 is not met, and
   preserve the complete written goal. Option A remains only the v1 product
   recommendation.
7. Preserve the lanes: direct IR through #45 then #47; evaluator
   #42 → #44 → #48 → #50 as a sibling; SDK #12 → #15 → #46; compatibility
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
5. 先以 #48 的 `docs/eval/ir-eval-lower/run.md` 与 `liveness.md` 为准，再以
   #50 的 `recovery.md` 与 `scores.md` 为准：先提交恢复、后查看 source/oracle
   评分，四个方法均为 full，且样本是有效 live evaluator code，不是 DCE。
6. 保留 #37 与 #50 作为 requirement 7 未满足的证据，并保留完整书面目标；
   A 仅作为 v1 产品建议。
7. 保持各线：direct IR 依序至 #45，再到 #47；evaluator sibling
   #42 → #44 → #48 → #50；SDK #12 → #15 → #46；compatibility
   #6 → #9 → #14 → #41。

<!-- CURSOR_AGENT_PR_BODY_END -->
