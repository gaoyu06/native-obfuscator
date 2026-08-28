<!-- CURSOR_AGENT_PR_BODY_BEGIN -->
## Summary / 摘要

Updates the documentation-only maintainer brief through draft PR #57 using
only claims recorded in the named branch documents. It adds #56's Sol accept
review and #57's evaluator ISA extension while preserving #53's `N/A` eval
timing, #37/#50's unmet requirement 7, and the complete written goal.

将仅文档的维护者简报更新至草稿 PR #57，仅采用指定分支文档已经记录的声明。
新增 #56 的 Sol accept 审阅与 #57 的 evaluator ISA 扩展，同时保留 #53 的
eval timing `N/A`、#37/#50 对 requirement 7 未满足的结论，以及完整书面目标。

## (a) 本次改动范围 / Change scope

- Add #56, Sol's documentation-only review of phase 7 (#54). Its recorded
  verdict is **accept**, with no compiler change, and its review document
  records a 35/35 focused-test rerun.
- Add #57's evaluator ISA support for `IAND`, `IOR`, `IXOR`, `ISHL`, `ISHR`,
  and `IUSHR`. Its record says an `IrFriendlyIntKernel`-equivalent stream
  stayed on eval and claims 28/28 focused tests. #57 adds no benchmark timing.
- Preserve #53 exactly as prior evidence: eval rejected `USHR` and used legacy
  fallback, so its eval median remains `N/A`. Do not back-fill #53 from #57.
- Retain #37 and #50: each reports full recovery of all four methods from its
  valid live subject, so requirement 7 remains unmet.
- Advance the direct-IR coverage/review lane #47 → #51 → #54 → #56 and keep
  evaluator experiment #42 → #44 → #48 → #50 with #57 as an ISA sibling.
  Keep benchmark lane #34 → #53 separate.
- Preserve the complete written engineering goal; option A remains only the
  v1 product recommendation.
- Update only `docs/architecture/goal-status-and-options.md` and this bilingual
  PR body. PRs #1–#57 remain open drafts; `master` remains `e7ca4c8`.

- 新增 #56：Sol 对 phase 7（#54）的纯文档审阅。记录结论为 **accept**，未改
  编译器；审阅文档记录重新运行 35/35 个聚焦测试。
- 新增 #57 对 `IAND`、`IOR`、`IXOR`、`ISHL`、`ISHR` 与 `IUSHR` 的
  evaluator ISA 支持。其记录称 `IrFriendlyIntKernel` 等价数据流保持在 eval
  路径，并声称 28/28 个聚焦测试通过。#57 不新增 benchmark timing。
- 原样保留 #53 的既有证据：eval 因 `USHR` 使用 legacy fallback，故其 eval
  中位数仍为 `N/A`；不得用 #57 回填 #53。
- 保留 #37 与 #50：两者均报告从各自有效 live 样本完整恢复四个方法，因此
  requirement 7 仍未满足。
- 将 direct-IR coverage/review 路线推进为 #47 → #51 → #54 → #56；保持
  evaluator 实验 #42 → #44 → #48 → #50，并将 #57 作为 ISA sibling；另行
  保持 benchmark 路线 #34 → #53。
- 保留完整书面工程目标；A 仅为 v1 产品建议。
- 仅更新 `docs/architecture/goal-status-and-options.md` 与本双语 PR body。
  PR #1–#57 仍均为 open draft；`master` 仍为 `e7ca4c8`。

## (b) 是否可直接上线 / Can this ship to production as-is?

No. / 否。

All referenced work remains in draft PRs and `master` contains none of it.
#56 is a scoped review, not ship-readiness approval; #57 remains an opt-in,
narrow evaluator lowering with per-method fallback. #53 still has no valid
eval timing, and #57 adds no benchmark result. #37 and #50 fully recovered all
four methods from valid live direct-IR and shared-evaluator subjects, so
requirement 7 is not met.

所有相关工作仍在草稿 PR 中，`master` 未包含这些内容。#56 是限定范围审阅，
不是上线批准；#57 仍是 opt-in、窄范围且逐方法 fallback 的 evaluator lowering。
#53 仍没有有效 eval timing，#57 也不新增 benchmark 结果。#37 与 #50 分别从
有效 live direct-IR 与 shared-evaluator 样本完整恢复全部四个方法，因此
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

1. Use #56's `docs/architecture/ir-phase7-review.md`: preserve the **accept**
   verdict, documentation-only scope, absence of compiler changes, and recorded
   35/35 focused-test rerun.
2. Use #57's `docs/architecture/ir-evaluator-backend.md` and bilingual
   `PR_BODY.md`: preserve the six named operations, opt-in/fallback boundaries,
   recorded eval-path result, and claimed 28/28 focused tests. Do not derive or
   add benchmark numbers.
3. Keep #53's `docs/benchmarks/results-ir-eval-lower.md` unchanged in meaning:
   eval fell back on `USHR`, its median remains `N/A`, and #57 cannot be used
   to back-fill a #53 timing.
4. Use #48's `docs/eval/ir-eval-lower/run.md` and `liveness.md`, then #50's
   `recovery.md` and `scores.md`: all four methods scored full on valid live
   evaluator code. Preserve #37 and #50 as evidence that requirement 7 is not
   met.
5. Preserve the complete written goal. Option A remains only the v1 product
   recommendation, not a rewrite of the engineering goal.
6. Preserve the lanes: direct IR coverage/review #45 → #47 → #51 → #54 → #56;
   benchmark #34 → #53; evaluator experiment #42 → #44 → #48 → #50 with #57
   as an ISA sibling; SDK #12 → #15 → #46; compatibility
   #6 → #9 → #14 → #41.

中文核对项：

1. 以 #56 的 `docs/architecture/ir-phase7-review.md` 为准：保留
   **accept** 结论、纯文档范围、未改编译器，以及记录的 35/35 聚焦测试复跑。
2. 以 #57 的 `docs/architecture/ir-evaluator-backend.md` 与双语
   `PR_BODY.md` 为准：保留六个具名操作、opt-in/fallback 边界、记录的 eval
   路径结果与声称的 28/28 聚焦测试；不得推导或新增 benchmark 数字。
3. 保持 #53 的 `docs/benchmarks/results-ir-eval-lower.md` 原意不变：eval 因
   `USHR` fallback，其中位数仍为 `N/A`；不得用 #57 回填 #53 timing。
4. 先以 #48 的 `docs/eval/ir-eval-lower/run.md` 与 `liveness.md` 为准，再以
   #50 的 `recovery.md` 与 `scores.md` 为准：有效 live evaluator code 的四个
   方法均为 full。保留 #37 与 #50 作为 requirement 7 未满足的证据。
5. 保留完整书面目标；A 仅作为 v1 产品建议，不改写工程目标。
6. 保持各线：direct IR coverage/review 为 #45 → #47 → #51 → #54 → #56；
   benchmark 为 #34 → #53；evaluator 实验为 #42 → #44 → #48 → #50，
   #57 为 ISA sibling；SDK 为 #12 → #15 → #46；compatibility 为
   #6 → #9 → #14 → #41。

<!-- CURSOR_AGENT_PR_BODY_END -->
