<!-- CURSOR_AGENT_PR_BODY_BEGIN -->
## Summary / 摘要

Updates the documentation-only maintainer brief through draft PR #42. It
records #39's Fable accept-with-nits phase-4 review, #40's opt-in phase-5
slice, #41's narrowly scoped JDK 25 fixtures, and #42's opt-in shared-evaluator
lowering while preserving #37's requirement-7 result.

将仅文档的维护者简报更新至草稿 PR #42：记录 #39 对 phase 4 的 Fable
accept-with-nits 审阅、#40 的 opt-in phase-5 切片、#41 的窄范围 JDK 25
fixtures 与 #42 的 opt-in shared-evaluator lowering，并保留 #37 对
requirement 7 的结论。

## (a) 本次改动范围 / Change scope

- Documentation only: update
  `docs/architecture/goal-status-and-options.md` and this bilingual PR body.
- Record all PRs through #42 as open drafts while `master` remains unchanged.
- Quote the named branch documents for #39–#42; do not merge or implement them.
- Preserve option A as the v1 product recommendation without shrinking the
  written engineering goal.

- 仅更新 `docs/architecture/goal-status-and-options.md` 与本双语 PR body。
- 记录截至 #42 的 PR 均为 open draft，且 `master` 未变。
- 仅引用 #39–#42 指定分支文档中的记录；不合并或实现这些草稿。
- 保留 A 作为 v1 产品建议，但不缩小书面工程目标。

## (b) 是否可直接上线 / Can this ship to production as-is?

No. / 否。

All referenced work remains in draft PRs and `master` contains none of it.
#40 remains opt-in and incomplete; #41 is not a blanket full-JDK-25 claim; and
#42 is limited, opt-in, defaults to `--ir-lower=direct`, and has no reader
evaluation. #37 fully recovered all four methods from #35's valid live
direct-IR stripped `.so`, so requirement 7 is not met.

所有相关工作仍在草稿 PR 中，`master` 未包含这些内容。#40 仍为 opt-in 且覆盖
不完整；#41 不能解释为完整 JDK 25 支持；#42 覆盖有限、需显式选择、默认仍为
`--ir-lower=direct`，且没有 reader evaluation。#37 从 #35 的有效 live
direct-IR stripped `.so` 完整恢复四个方法，因此 requirement 7 未满足。

## (c) 上线前是否需要 review / Is review required?

Yes. Review every statement against the named source document and preserve its
scope. Each implementation stack still requires independent code review,
post-rebase verification, and applicable product/release approval.

是。每项陈述均须与指定来源文档核对并保留其范围；每条实现栈仍需独立代码审查、
rebase 后复测及适用的产品/发布审批。

## (d) review 的前置条件 / Review preconditions

1. Use #39's `docs/architecture/ir-phase4-fable-review.md`: the verdict is
   accept with nits, the branch is documentation only, and no compiler code
   changed.
2. Use #40's `docs/architecture/ir-phase5-status.md`: it records
   `IDIV`/`IREM`, `NEWARRAY T_INT`, and static descriptor-`I`
   `GETSTATIC`/`PUTSTATIC`; 22 `IrCompilerTest` plus 2 `CodegenModeTest`; and
   opt-in IR with default legacy and per-method fallback.
3. Use #41's `docs/audit/jdk25-e2e-status.md`: four ClassicTest fixtures were
   compiled with `javac --release 25`; the full suite recorded 23 passed,
   1 pre-existing skip, and 0 failed. Preserve its explicit boundary: this is
   not blanket JDK 25 support. The branch stacks on #14.
4. Use #42's `docs/architecture/ir-evaluator-backend.md`: `--ir-lower=eval`
   selects the limited shared evaluator and `direct` remains the default.
   #42 is a sibling of #40 on #39 and is not requirement-7 evidence.
5. Preserve #37: all four methods were fully recovered from #35's valid live
   subject, so requirement 7 is not met. Keep option A only as the v1 product
   recommendation; do not shrink the written engineering goal.
6. Preserve the suggested lanes: compatibility #6 → #9 → #14 → #41; direct IR
   #8 through #39, then #40; #42 as a separate sibling; and #35/#37 as a
   separate evaluation lane.

中文核对项：

1. 以 #39 的 `docs/architecture/ir-phase4-fable-review.md` 为准：结论为
   accept with nits；该分支仅含文档，未改编译器代码。
2. 以 #40 的 `docs/architecture/ir-phase5-status.md` 为准：记录
   `IDIV`/`IREM`、`NEWARRAY T_INT`、描述符 `I` 的静态
   `GETSTATIC`/`PUTSTATIC`、22 个 `IrCompilerTest` 加 2 个
   `CodegenModeTest`；IR 仍为 opt-in，默认 legacy，并保留逐方法 fallback。
3. 以 #41 的 `docs/audit/jdk25-e2e-status.md` 为准：四个 ClassicTest fixture
   使用 `javac --release 25` 编译；完整 suite 记录 23 通过、1 个既有 skip、
   0 失败。不得扩大为完整 JDK 25 支持；该分支叠在 #14 上。
4. 以 #42 的 `docs/architecture/ir-evaluator-backend.md` 为准：
   `--ir-lower=eval` 选择有限的 shared evaluator，默认仍为 `direct`。#42
   与 #40 同为基于 #39 的 sibling，且不是 requirement 7 证据。
5. 保留 #37：#35 的有效 live 样本中四个方法均被完整恢复，因此 requirement 7
   未满足。A 仅作为 v1 产品建议；不得缩小书面工程目标。
6. 保持建议顺序：compatibility #6 → #9 → #14 → #41；direct IR 从 #8
   依序至 #39，再到 #40；#42 为独立 sibling；#35/#37 为独立 evaluation 线。

<!-- CURSOR_AGENT_PR_BODY_END -->
