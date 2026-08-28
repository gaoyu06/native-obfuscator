<!-- CURSOR_AGENT_PR_BODY_BEGIN -->
## Summary / 摘要

Updates the maintainer goal-status and human-options brief through draft PR
#36. The document now records #34's narrowly scoped local IR benchmark, #35's
live direct-IR artifact without a reader pass, #36's opt-in exception-edge
compiler slice, and why #31 remains invalid reader-bar evidence.

更新维护者的目标状态与人工选项简报至草稿 PR #36：记录 #34 的窄范围本地 IR
基准、#35 尚未 reader pass 的 live direct-IR artifact、#36 的 opt-in 异常边
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
opt-in, and legacy remains the default. #34 is local diagnostic evidence, #35
has no reader pass, and #36 is not ship-ready.

所有相关工作仍在草稿 PR 中，`master` 未变；IR 仍需显式选择，默认仍为 legacy。
#34 仅是本地诊断，#35 没有 reader pass，#36 尚不可上线。

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
2. Do not count #31 or #35 as requirement-7 reader evidence: #31's `mix` was
   dead-code-eliminated, while #35 explicitly performed no reader pass.
3. Confirm #36 remains opt-in, default legacy, and not ship-ready; its status
   document records 17/17 `IrCompilerTest` and 2/2 `CodegenModeTest`.
4. Keep option A as the prior v1 product recommendation, not a recommendation
   to shrink the written goal. Continue toward a live-kernel reader using #35
   and wider IR coverage through #36.
5. Confirm #34 and #36 are sibling branches stacked on #33, while #35 is an
   eval-only sibling and not an implementation prerequisite.

中文核对项：

1. 阅读 #34 的 `docs/benchmarks/results-ir-vs-legacy.md`；只有
   `IrFriendlyIntKernel.run(I)I` 是有效 IR 对比，本地中位数不可移植。
2. 不得把 #31 或 #35 计入 requirement-7 reader 证据：#31 的 `mix` 被 DCE，
   #35 则明确没有 reader pass。
3. 确认 #36 仍为 opt-in、默认 legacy、尚不可上线；其状态文档记录
   17/17 `IrCompilerTest` 与 2/2 `CodegenModeTest`。
4. A 仍是先前对 v1 产品范围的建议，不是缩小书面目标的建议；工程继续以
   #35 的 live kernel 推进 reader，并通过 #36 扩大 IR coverage。
5. 确认 #34 与 #36 都基于 #33；#35 是 eval-only sibling，不是实现前置。

<!-- CURSOR_AGENT_PR_BODY_END -->
