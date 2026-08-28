<!-- CURSOR_AGENT_PR_BODY_BEGIN -->
## Summary / 摘要

Updates the maintainer goal-status and human-options brief through draft PR
#37. The document now records #34's narrowly scoped local IR benchmark, #35's
live direct-IR artifact and #37 reader, #36's opt-in exception-edge compiler
slice, and why #31 remains invalid reader-bar evidence.

更新维护者的目标状态与人工选项简报至草稿 PR #37：记录 #34 的窄范围本地 IR
基准、#35 的 live direct-IR artifact 及其 #37 reader、#36 的 opt-in 异常边
编译器切片，以及 #31 仍不能作为 reader bar 证据的原因。

## (a) 本次改动范围 / Change scope

- Documentation only: `docs/architecture/goal-status-and-options.md`
- Bilingual maintainer brief with (a)–(d)
- Updated draft status, benchmark boundaries, reader-evidence boundaries,
  human options A/B/C, and suggested stack order

仅修改 `docs/architecture/goal-status-and-options.md`，增加双语 (a)–(d)，
并更新草稿状态、benchmark/reader 证据边界、A/B/C 人工选项和建议 stack
顺序；不实现编译器代码，不合并任何草稿。

## (b) 是否可直接上线 / Can this ship to production as-is?

No. / 否。

All referenced work remains in draft PRs, `master` is unchanged, IR remains
opt-in, and legacy remains the default. #37 fully recovered all four methods
from #35's valid live subject in one blinded run, so requirement 7 is not met;
#36 is also not ship-ready.

所有相关工作仍在草稿 PR 中，`master` 未变；IR 仍需显式选择，默认仍为 legacy。
#37 在一次盲读中完整恢复了 #35 有效存活样本的四个方法，因此 requirement 7
未满足；#36 同样尚不可上线。

## (c) 上线前是否需要 review / Is review required?

Yes. Review the documentation against the source branch records and preserve
their kernel, artifact, and method-level limits. Each implementation stack
still requires its own code review and post-rebase verification.

是。需依据各来源分支记录核对本文，并保留其 kernel、artifact 与方法级边界；
每条实现栈仍需独立代码审查和 rebase 后复测。

## (d) review 的前置条件 / Review preconditions

1. Read #34's `docs/benchmarks/results-ir-vs-legacy.md`; only
   `IrFriendlyIntKernel.run(I)I` is a valid IR comparison, and its local
   medians are not portable.
2. Keep #31 excluded because its `mix` was dead-code-eliminated. Record #37 as
   #35's recovery-first reader: all four methods scored full on a valid live
   subject, so requirement 7 is not met.
3. Confirm #36 remains opt-in, default legacy, and not ship-ready; its status
   document records 17/17 `IrCompilerTest` and 2/2 `CodegenModeTest`.
4. Keep option A as the prior v1 product recommendation, not a recommendation
   to shrink the written goal. The next reader-bar engineering needs a lowering
   that is not straight-line readable native output, not encoding tweaks.
5. Confirm #34 and #36 are sibling branches stacked on #33, while #35 is an
   eval-only sibling and #37 is its reader. Continue #36 IR coverage and the
   JDK/SDK stacks as separate lanes.

中文核对项：

1. 阅读 #34 的 `docs/benchmarks/results-ir-vs-legacy.md`；只有
   `IrFriendlyIntKernel.run(I)I` 是有效 IR 对比，本地中位数不可移植。
2. #31 的 `mix` 被 DCE，仍须排除；#37 是 #35 的 recovery-first reader，
   有效存活样本上的四个方法均为 full，因此 requirement 7 未满足。
3. 确认 #36 仍为 opt-in、默认 legacy、尚不可上线；其状态文档记录
   17/17 `IrCompilerTest` 与 2/2 `CodegenModeTest`。
4. A 仍是先前对 v1 产品范围的建议，不是缩小书面目标的建议；下一步需要不会
   产生直线可读 native 源算法的 lowering，而不是继续调整 encoding。
5. 确认 #34 与 #36 都基于 #33；#35 是 eval-only sibling，#37 是其 reader。
   #36 的 IR coverage 与 JDK/SDK stacks 作为独立工程线继续。

<!-- CURSOR_AGENT_PR_BODY_END -->
