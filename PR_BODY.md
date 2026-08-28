<!-- CURSOR_AGENT_PR_BODY_BEGIN -->
## Summary / 摘要

Updates the documentation-only maintainer brief through draft PR #54 using
only claims recorded in the named branch documents. It adds #53's scoped local
benchmark and #54's still-opt-in IR phase 7, retains #37/#50 as evidence that
requirement 7 is not met, and does not shrink the written engineering goal to
option A.

将仅文档的维护者简报更新至草稿 PR #54，仅采用指定分支文档已经记录的声明。
新增 #53 的限定范围本地 benchmark 与 #54 仍为 opt-in 的 IR phase 7；保留
#37/#50 作为 requirement 7 未满足的证据；不把书面工程目标缩小为选项 A。

## (a) 本次改动范围 / Change scope

- Add #53's `IrFriendlyIntKernel.run(I)I` benchmark of JVM, legacy, direct IR,
  and eval selection. Its recorded local medians are 12,207,144.5 ns,
  202,090,247.0 ns, and 11,311,481.5 ns for JVM, legacy, and direct IR,
  respectively. Direct IR stayed on IR; eval rejected `USHR` and fell back to
  legacy, so its median is `N/A` and no eval timing is claimed. The results are
  not portable.
- Add #54's still-opt-in phase-7 `CHECKCAST`/`INSTANCEOF` and initial two-slot
  `I64` slice. Its status document claims 33 `IrCompilerTest` plus
  2 `CodegenModeTest`, all with 0 skipped/failures/errors. Default remains
  `legacy`.
- Retain #37 and #50: each reports full recovery of all four methods from its
  valid live subject, so requirement 7 remains unmet.
- Advance the direct-IR lane #47 → #51 → #54 and benchmark lane #34 → #53
  while keeping the evaluator lane #42 → #44 → #48 → #50 separate.
- Update only `docs/architecture/goal-status-and-options.md` and this bilingual
  PR body. PRs #1–#54 remain open drafts; `master` remains `e7ca4c8`.

- 新增 #53 对 `IrFriendlyIntKernel.run(I)I` 的 JVM、legacy、direct IR 与
  eval 选择 benchmark。文档记录的本地中位数依次为 12,207,144.5 ns、
  202,090,247.0 ns 与 11,311,481.5 ns；direct IR 保持在 IR 路径，eval 因
  `USHR` 回退到 legacy，故 eval 中位数为 `N/A`，不声称 eval timing，结果
  不可移植。
- 新增 #54 仍为 opt-in 的 phase-7 `CHECKCAST`/`INSTANCEOF` 与初始两槽
  `I64` 切片。其状态文档声称 33 个 `IrCompilerTest` 加 2 个
  `CodegenModeTest`，skipped/failures/errors 均为 0；默认仍为 `legacy`。
- 保留 #37 与 #50：两者均报告从各自有效 live 样本完整恢复四个方法，因此
  requirement 7 仍未满足。
- 将 direct-IR 路线推进为 #47 → #51 → #54，benchmark 路线推进为
  #34 → #53，同时保持 evaluator sibling 路线 #42 → #44 → #48 → #50 独立。
- 仅更新 `docs/architecture/goal-status-and-options.md` 与本双语 PR body。
  PR #1–#54 仍均为 open draft；`master` 仍为 `e7ca4c8`。

## (b) 是否可直接上线 / Can this ship to production as-is?

No. / 否。

All referenced work remains in draft PRs and `master` contains none of it.
#54 is an incomplete, opt-in phase-7 slice, not ship-readiness evidence. #53
is a one-VM diagnostic with no valid eval timing, not a portable performance
claim. #37 and #50 fully recovered all four methods from valid live direct-IR
and shared-evaluator subjects, so requirement 7 is not met.

所有相关工作仍在草稿 PR 中，`master` 未包含这些内容。#54 是不完整、opt-in
的 phase-7 切片，不是上线证据。#53 是单一 VM 的诊断且没有有效 eval timing，
不是可移植性能声明。#37 与 #50 分别从有效 live direct-IR 与
shared-evaluator 样本完整恢复全部四个方法，因此 requirement 7 未满足。

## (c) 上线前是否需要 review / Is review required?

Yes. / 是。

Review each statement against the named source document and preserve its
scope. Each implementation stack still requires independent code review,
post-rebase verification, supported-platform/JDK CI, and applicable
product/release approval.

每项陈述均须与指定来源文档核对并保留其范围；每条实现栈仍需独立代码审查、
rebase 后复测、受支持平台/JDK CI，以及适用的产品/发布审批。

## (d) review 的前置条件 / Review preconditions

1. Use #53's `docs/benchmarks/results-ir-eval-lower.md`. Quote only its local
   medians, preserve the actual-path evidence, and never cite the eval-selected
   legacy-fallback observation as an eval timing.
2. Use #54's `docs/architecture/ir-phase7-status.md`. Preserve opt-in
   per-method fallback and default `legacy`; treat 33 `IrCompilerTest` plus
   2 `CodegenModeTest` as status-document claims, not ship-readiness approval.
3. Use #48's `docs/eval/ir-eval-lower/run.md` and `liveness.md`, then #50's
   `recovery.md` and `scores.md`: recovery preceded source/oracle scoring, all
   four methods scored full, and the subject was valid live evaluator code,
   not DCE.
4. Preserve both #37 and #50 as evidence that requirement 7 is not met.
5. Preserve the complete written goal. Option A remains only the v1 product
   recommendation, not a rewrite of the engineering goal.
6. Preserve the lanes: direct IR through #45 → #47 → #51 → #54; benchmark
   #34 → #53; evaluator sibling #42 → #44 → #48 → #50; SDK
   #12 → #15 → #46; compatibility #6 → #9 → #14 → #41.

中文核对项：

1. 以 #53 的 `docs/benchmarks/results-ir-eval-lower.md` 为准；仅引用其本地
   中位数，保留实际路径证据，不得把 eval-selected legacy fallback
   observation 引为 eval timing。
2. 以 #54 的 `docs/architecture/ir-phase7-status.md` 为准；保持 opt-in、
   逐方法 fallback、默认 `legacy`，并将 33 个 `IrCompilerTest` 加 2 个
   `CodegenModeTest` 视为状态文档声明，而非上线批准。
3. 先以 #48 的 `docs/eval/ir-eval-lower/run.md` 与 `liveness.md` 为准，再以
   #50 的 `recovery.md` 与 `scores.md` 为准：先提交恢复、后查看 source/oracle
   评分，四个方法均为 full，且样本是有效 live evaluator code，不是 DCE。
4. 保留 #37 与 #50 作为 requirement 7 未满足的证据。
5. 保留完整书面目标；A 仅作为 v1 产品建议，不改写工程目标。
6. 保持各线：direct IR 为 #45 → #47 → #51 → #54；benchmark 为
   #34 → #53；evaluator sibling 为 #42 → #44 → #48 → #50；SDK 为
   #12 → #15 → #46；compatibility 为 #6 → #9 → #14 → #41。

<!-- CURSOR_AGENT_PR_BODY_END -->
