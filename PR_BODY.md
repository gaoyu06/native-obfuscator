<!-- CURSOR_AGENT_PR_BODY_BEGIN -->
## Summary / 摘要

Updates the documentation-only maintainer brief through draft PR #51, using
only claims recorded in the named branch documents. It adds Sol's phase-6
review after Fable was policy-blocked, retains #50's valid live
shared-evaluator result and the corresponding requirement-7 failure, and does
not shrink the written engineering goal to option A.

将仅文档的维护者简报更新至草稿 PR #51，仅采用指定分支文档已经记录的声明。
新增 Fable 因 policy 被阻后由 Sol 完成的 phase-6 审阅；保留 #50 对有效 live
shared-evaluator 样本的结果及 requirement 7 未满足的结论；不把书面工程目标
缩小为选项 A。

## (a) 本次改动范围 / Change scope

- Add #51's recorded **accept with nits** review of phase 6 after the Fable
  policy block.
- Record #51's fix: array-component `ANEWARRAY` now uses `FindClass` with the
  unchanged array descriptor. Its documents claim 27 `IrCompilerTest` plus
  2 `CodegenModeTest`, with 0 skipped/failures/errors.
- Retain #50's recovery-first full recovery of `add`, `sumTo`, `subMul`, and
  `mix` from the valid live shared-evaluator stripped `.so`; together with
  #37's direct-IR result, requirement 7 remains unmet.
- Advance the direct-IR lane from #47 to #51 while keeping the evaluator lane
  #42 → #44 → #48 → #50 separate.
- Update only `docs/architecture/goal-status-and-options.md` and this bilingual
  PR body. PRs #1–#51 remain open drafts; `master` remains `e7ca4c8`.

- 新增 #51 已记录的 phase-6 **accept with nits** 审阅；Fable 审阅此前因
  policy 被阻。
- 记录 #51 的修复：数组组件 `ANEWARRAY` 现在使用未改写的数组 descriptor
  调用 `FindClass`。其文档声称 27 个 `IrCompilerTest` 加 2 个
  `CodegenModeTest`，skipped/failures/errors 均为 0。
- 保留 #50 先恢复、后评分的结果：从有效 live shared-evaluator stripped
  `.so` 完整恢复 `add`、`sumTo`、`subMul`、`mix`；结合 #37 的 direct-IR
  结果，requirement 7 仍未满足。
- 将 direct-IR 路线从 #47 推进至 #51，同时保持 evaluator sibling 路线
  #42 → #44 → #48 → #50 独立。
- 仅更新 `docs/architecture/goal-status-and-options.md` 与本双语 PR body。
  PR #1–#51 仍均为 open draft；`master` 仍为 `e7ca4c8`。

## (b) 是否可直接上线 / Can this ship to production as-is?

No. / 否。

All referenced work remains in draft PRs and `master` contains none of it.
#51 is an accept-with-nits review of an incomplete, opt-in phase-6 slice, not
ship-readiness approval. #37 and #50 fully recovered all four methods from
valid live direct-IR and shared-evaluator subjects, so requirement 7 is not met.

所有相关工作仍在草稿 PR 中，`master` 未包含这些内容。#51 是对不完整、opt-in
phase-6 切片的 accept-with-nits 审阅，不是上线批准。#37 与 #50 分别从有效
live direct-IR 与 shared-evaluator 样本完整恢复全部四个方法，因此
requirement 7 未满足。

## (c) 上线前是否需要 review / Is review required?

Yes. / 是。

Review each statement against the named source document and preserve its
scope. Each implementation stack still requires independent code review,
post-rebase verification, supported-platform/JDK CI, and applicable
product/release approval.

每项陈述均须与指定来源文档核对并保留其范围；每条实现栈仍需独立代码审查、
rebase 后复测、受支持平台/JDK CI，以及适用的产品/发布审批。

## (d) review 的前置条件 / Review preconditions

1. Use #51's `docs/architecture/ir-phase6-review.md` and updated
   `ir-phase6-status.md`: verdict **accept with nits**; array-component
   `ANEWARRAY` was fixed to use descriptor-based `FindClass`; the documents
   claim 27 `IrCompilerTest` plus 2 `CodegenModeTest`.
2. Keep phase 6 opt-in with per-method fallback and default `legacy`; #51
   explicitly says its focused checks are not ship-readiness approval.
3. Use #48's `docs/eval/ir-eval-lower/run.md` and `liveness.md`, then #50's
   `recovery.md` and `scores.md`: recovery preceded source/oracle scoring, all
   four methods scored full, and the subject was valid live evaluator code,
   not DCE.
4. Preserve both #37 and #50 as evidence that requirement 7 is not met.
5. Preserve the complete written goal. Option A remains only the v1 product
   recommendation, not a rewrite of the engineering goal.
6. Preserve the lanes: direct IR through #45 → #47 → #51; evaluator sibling
   #42 → #44 → #48 → #50; SDK #12 → #15 → #46; compatibility
   #6 → #9 → #14 → #41.

中文核对项：

1. 以 #51 的 `docs/architecture/ir-phase6-review.md` 与更新后的
   `ir-phase6-status.md` 为准：结论为 **accept with nits**；数组组件
   `ANEWARRAY` 已修复为使用 descriptor-based `FindClass`；文档声称 27 个
   `IrCompilerTest` 加 2 个 `CodegenModeTest`。
2. 保持 phase 6 为 opt-in、逐方法 fallback、默认 `legacy`；#51 明确说明其
   focused checks 不是上线批准。
3. 先以 #48 的 `docs/eval/ir-eval-lower/run.md` 与 `liveness.md` 为准，再以
   #50 的 `recovery.md` 与 `scores.md` 为准：先提交恢复、后查看 source/oracle
   评分，四个方法均为 full，且样本是有效 live evaluator code，不是 DCE。
4. 保留 #37 与 #50 作为 requirement 7 未满足的证据。
5. 保留完整书面目标；A 仅作为 v1 产品建议，不改写工程目标。
6. 保持各线：direct IR 为 #45 → #47 → #51；evaluator sibling 为
   #42 → #44 → #48 → #50；SDK 为 #12 → #15 → #46；compatibility 为
   #6 → #9 → #14 → #41。

<!-- CURSOR_AGENT_PR_BODY_END -->
