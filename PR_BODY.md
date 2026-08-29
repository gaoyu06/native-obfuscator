<!-- CURSOR_AGENT_PR_BODY_BEGIN -->
## Summary / 摘要

Stacks the documentation-only maintainer brief on draft PR #106 (the previous
brief, through #103) and folds in #104/#105/#107 using only claims in their
named branch documents and PR bodies. It preserves the phase-15, phase-14,
phase-13, phase-12, evaluator LDIV, AES, benchmark, reader, and complete
written-goal conclusions. #104, the phase-16 `SWAP` + `AALOAD`/`AASTORE`
implementation stacked on #99, is now the preferred direct-IR implementation
tip; #99 remains preferred over #95, #95 over #90, #90 over its
documentation-only reviews #93/#94, #89 over unfixed #84, #81 over unfixed
#80, and #70 over unfixed #66. #107 replaces #103 as the honest admission
measurement on the current IR tip (#103 stays as the #99-tip baseline: #97
measured #90, #103 measured #99, #107 measured #104); neither #92's synthetic
5/6 nor #97's, #103's, or #107's corpus fractions is a coverage gate.

在草稿 PR #106（覆盖至 #103 的上一份简报）的基础上，将仅文档的维护者简报加入
#104/#105/#107，且仅采用其指定分支文档与 PR body 记录的声明。同时保留
phase-15、phase-14、phase-13、phase-12、evaluator LDIV、AES、benchmark、
reader 与完整书面工程目标结论。叠加在 #99 上的 phase-16
`SWAP` + `AALOAD`/`AASTORE` 实现 #104 现为首选 direct-IR 实现 tip；#99 仍
优先于 #95，#95 仍优先于 #90，#90 仍优先于其纯文档审阅 #93/#94，#89 仍优先
于未修复 #84，#81 仍优先于未修复 #80，#70 仍优先于未修复 #66。#107 取代
#103 成为当前 IR tip 上的诚实 admission 测量（#103 保留为 #99 tip 基线：
#97 测的是 #90，#103 测的是 #99，#107 测的是 #104）；#92 的合成 5/6 与
#97/#103/#107 的语料比例都不是覆盖率门槛。

## (a) Change scope / 本次改动范围

- Add #104 on #99 (`f46c3eae`): still-opt-in IR phase 16 admits the
  category-one stack reorder and reference-array operations that dominated
  the remaining measured fallback. `SWAP` validates both operands in the
  complete stack-type pass (each must be single-slot; either operand being
  `I64` or `F64` rejects the method at opcode 95 before mutation) and then
  only exchanges the two existing SSA values, creating no IR instruction,
  temporary, or JNI call. `AALOAD`/`AASTORE` lower to
  `GetObjectArrayElement`/`SetObjectArrayElement`: a null array takes the
  pending-`NullPointerException` exceptional exit, the JNI bounds check is
  routed as a pending `ArrayIndexOutOfBoundsException` via `ExceptionCheck`,
  and `AASTORE`'s component-compatibility check leaves the JNI-raised
  `ArrayStoreException` pending. `NEWARRAY` forms outside the retained
  `int[]` slice, `MULTIANEWARRAY`, `POP2`/`DUP2*`, and `invokedynamic` still
  fall back; legacy remains the default. Its status records 78 + 2 = **80**
  focused tests and an unskipped 128-`JNICALL` g++ smoke. **#104 is now the
  preferred direct-IR implementation tip**; it is partial and not
  ship-ready.
- Add #105, Sol's documentation-only **accept** review of phase-16 #104: no
  compiler change, an 80/80 focused-test rerun, the unskipped 128-`JNICALL`
  g++ smoke, and an independent syntax-only check. It is not a compiler fix.
- Add #107, a measurement-only admission report stacked on #104 (`dbfeb78`)
  that reruns the same corpora as #103 (which measured #99 at `f46c3eae`):
  Corpus A ClassicTest records **108 / 102 IR / 6 fallback /
  0 constructor-left-Java** (ΔIR **+5** versus #103); Corpus B JDK 17
  records **36 / 24 / 12 / 0** (ΔIR **+1**); the separately labeled extra
  JDK 21 corpus records **38 / 18 / 20 / 0** (ΔIR **+1**). The 33 methods
  that fell back at opcode 95 (`SWAP`) on #99 pass that instruction on #104
  and now fall back at opcode **93** (`DUP2_X1`); the other remaining first
  fallback reasons are `NEWARRAY` ×2, `MULTIANEWARRAY` ×2, and one `ISTORE`
  rejected for the logged type mismatch. Krakatau was again skipped; no
  native compile, no behavioral E2E. **#107 replaces #103 as the honest
  measurement on the current IR tip**; #103 stays as the #99-tip baseline,
  and neither is a coverage gate.
- Treat #106 as the previous options brief (through #103) and this branch's
  stacking base, not new compiler work.
- Keep #53 unchanged in meaning: eval fell back on `USHR`, so its evaluator
  median remains `N/A`. Do not back-fill it from #59 or any later work.
- Keep #37 and #50 as full recoveries of all four methods from their valid
  live subjects; requirement 7 remains unmet.
- Preserve the lanes:
  - direct IR: #45 → #47 → #51 → #54 → #56 → #62 → #63/#64 → #66 →
    #70/#71 → #73 → #76/#77 → #78 → #82/#83 → #84 → #89/#88 → #90 →
    #93/#94 → #95 → #98/#101 → #99 → #102 → #104 → #105, with #73 based
    only on preferred, fixed #70, #90 based on preferred #89, #93/#94 as
    documentation-only reviews of #90, #98/#101 as documentation-only
    reviews of #95, #102 as the documentation-only review of #99, #105 as
    the documentation-only review of #104, and #104 based on #99 as the
    preferred implementation tip;
  - evaluator: #42 → #44 → #48 → #50, #57/#61, #68/#69 → #85/#87;
  - benchmark: #34 → #53, with #59 stacked on #57; admission measurements
    #92 (synthetic, on #89), #97 (real fixtures, on #90), #103 (real
    fixtures, on #99), and #107 (real fixtures, on #104; the preferred
    admission measurement);
  - SDK: #12 → #15 → #46 → #72 → #75 → #80 → #81, with #81 the preferred
    AES tip;
  - compatibility: #6 → #9 → #14 → #41;
  - options: … → #74 → #79 → #86 → #91 → #96 → #100 → #106 → this branch.
- Preserve the complete written engineering goal. Option A remains only the
  v1 product recommendation.
- Update only `docs/architecture/goal-status-and-options.md` and this
  bilingual PR body. PRs #1–#107 remain open drafts; `master` remains
  `e7ca4c8`.

- 新增叠加在 #99（`f46c3eae`）上的 #104：仍为 opt-in 的 IR phase 16 接纳
  主导剩余测得 fallback 的 category-one 栈重排与引用数组操作。`SWAP` 在
  完整栈型校验中检查两个操作数（均须为单槽；任一为 `I64`/`F64` 即在
  mutation 前于 opcode 95 拒绝）后仅交换既有 SSA 值，不产生 IR 指令、
  临时量或 JNI 调用。`AALOAD`/`AASTORE` 经
  `GetObjectArrayElement`/`SetObjectArrayElement` 降低：null 数组走
  pending `NullPointerException` 异常出口，JNI 边界检查经 `ExceptionCheck`
  路由为 pending `ArrayIndexOutOfBoundsException`，`AASTORE` 的组件兼容性
  检查保留 JNI 抛出的 pending `ArrayStoreException`。retained `int[]` 切片
  之外的 `NEWARRAY`、`MULTIANEWARRAY`、`POP2`/`DUP2*` 与 invokedynamic 仍
  fallback；默认仍为 legacy。其状态记录 78 + 2 = **80** 个聚焦测试及未跳过
  的 128-`JNICALL` g++ 烟测。**#104 现为首选 direct-IR 实现 tip**；仍部分
  且未达上线就绪。
- 新增 #105：Sol 对 phase-16 #104 的纯文档 **accept** 审阅——无编译器改动、
  80/80 聚焦测试重跑、未跳过的 128-`JNICALL` g++ 烟测及独立 syntax-only
  检查。它不是编译器修复。
- 新增叠加在 #104（`dbfeb78`）上、仅测量的 admission 报告 #107，复测与
  #103（其测量对象为 #99 `f46c3eae`）相同的语料：Corpus A ClassicTest 记录
  **108 / 102 IR / 6 fallback / 0 constructor-left-Java**（ΔIR 相对 #103
  **+5**）；Corpus B JDK 17 记录 **36 / 24 / 12 / 0**（ΔIR **+1**）；单独
  标注为 extra 的 JDK 21 语料记录 **38 / 18 / 20 / 0**（ΔIR **+1**）。
  在 #99 上于 opcode 95（`SWAP`）fallback 的 33 个方法在 #104 上通过该指令
  后改在 opcode **93**（`DUP2_X1`）fallback；其余剩余首因为 `NEWARRAY`
  ×2、`MULTIANEWARRAY` ×2 与一处按日志记录为类型不匹配拒绝的 `ISTORE` ×1。
  Krakatau 仍被跳过；未编译 native、无行为 E2E。**#107 取代 #103 成为当前
  IR tip 上的诚实测量**；#103 保留为 #99 tip 基线，两者都不是覆盖率门槛。
- #106 为上一份 options brief（覆盖至 #103），是本分支的叠加基础，不是新的
  编译器工作。
- 保持 #53 原意不变：eval 因 `USHR` fallback，故其 evaluator 中位数仍为
  `N/A`。不得用 #59 或任何后续工作回填。
- 保留 #37 与 #50：两者均从各自有效 live 样本完整恢复四个方法，因此
  requirement 7 仍未满足。
- 保持各路线：
  - direct IR：#45 → #47 → #51 → #54 → #56 → #62 → #63/#64 → #66 →
    #70/#71 → #73 → #76/#77 → #78 → #82/#83 → #84 → #89/#88 → #90 →
    #93/#94 → #95 → #98/#101 → #99 → #102 → #104 → #105，其中 #73 仅
    基于首选且含修复的 #70，#90 基于首选 #89，#93/#94 是 #90 的纯文档
    审阅，#98/#101 是 #95 的纯文档审阅，#102 是 #99 的纯文档审阅，#105 是
    #104 的纯文档审阅，#104 基于 #99 且为首选实现 tip；
  - evaluator：#42 → #44 → #48 → #50、#57/#61、#68/#69 → #85/#87；
  - benchmark：#34 → #53，另有叠加在 #57 上的 #59；admission 测量为叠加在
    #89 上的 #92（合成）、叠加在 #90 上的 #97（真实 fixture）、叠加在 #99
    上的 #103（真实 fixture）与叠加在 #104 上的 #107（真实 fixture，首选
    的 admission 测量）；
  - SDK：#12 → #15 → #46 → #72 → #75 → #80 → #81，其中 #81 为首选
    AES tip；
  - compatibility：#6 → #9 → #14 → #41；
  - options：… → #74 → #79 → #86 → #91 → #96 → #100 → #106 → 本分支。
- 保留完整书面工程目标；A 仅为 v1 产品建议。
- 仅更新 `docs/architecture/goal-status-and-options.md` 与本双语 PR body。
  PR #1–#107 仍均为 open draft；`master` 仍为 `e7ca4c8`。

## (b) Can this ship to production as-is? / 是否可直接上线

**No. / 否。**

All referenced work remains in draft PRs and `master` contains none of it.
The defaults remain legacy/direct. #104 extends still-opt-in phase 16 to
`SWAP` and `AALOAD`/`AASTORE`, but `NEWARRAY` forms outside the retained
`int[]` slice, `MULTIANEWARRAY`, `POP2`/`DUP2*`, and `invokedynamic` still
fall back and legacy remains default; it is the preferred implementation tip
yet partial and not ship approval. #105 (of #104) is a documentation-only
review, not a compiler fix and not ship approval. #106 is only the previous
options brief. #107 is a measurement-only admission report on the #104 tip:
its 108/102, 36/24, and extra 38/18 rows are admission counts on those JARs
only, with no native compile and no behavioral E2E; it is not production
coverage and not ship approval. #53 still has no evaluator timing, and #37
and #50 fully recovered all four methods from valid live subjects, so
requirement 7 remains unmet.

所有相关工作仍在草稿 PR 中，`master` 未包含这些内容，默认值仍为
legacy/direct。#104 将仍为 opt-in 的 phase 16 扩展至 `SWAP` 与
`AALOAD`/`AASTORE`，但 retained `int[]` 切片之外的 `NEWARRAY`、
`MULTIANEWARRAY`、`POP2`/`DUP2*` 与 invokedynamic 仍 fallback，默认仍为
legacy；它是首选实现 tip，但仍部分，不是上线批准。#105（对 #104）为纯文档
审阅，不是编译器修复，也不是上线批准。#106 只是上一份 options brief。#107
是叠加在 #104 tip 上、仅测量的 admission 报告：108/102、36/24 与 extra 的
38/18 都只是这些 JAR 上的接纳计数，未编译 native、无行为 E2E，不代表生产
覆盖率，也不是上线批准。#53 仍没有 evaluator timing，#37 与 #50 均从有效
live 样本完整恢复四个方法，因此 requirement 7 仍未满足。

## (c) Is review required? / 上线前是否需要 review

**Yes. / 是。**

Each implementation stack still requires independent code review, post-rebase
verification, supported-platform/JDK CI, native runtime-parity checks, and
applicable product/release approval. Phase-16 review must start from #104 on
#99, re-run its recorded 80 focused tests with `CC=gcc CXX=g++`, and keep the
single-slot `SWAP` validation with rejection before mutation, the
`GetObjectArrayElement`/`SetObjectArrayElement` lowering with
pending-exception routing for `NullPointerException`,
`ArrayIndexOutOfBoundsException`, and `ArrayStoreException`, and the
`legacy` default. The #92, #97, #103, and #107 admission measurements must
stay scoped to their corpora: #92's 5/6 is synthetic, and #97's, #103's, and
#107's fractions are counts on those specific JARs; none is a coverage gate,
#97 measured #90, #103 measured #99, and #107 measured #104. Do not flip the
default.

每条实现栈仍需独立代码审查、rebase 后复测、受支持平台/JDK CI、native
runtime parity 检查，以及适用的产品/发布审批。Phase-16 审阅必须从叠加在
#99 上的 #104 开始，用 `CC=gcc CXX=g++` 重跑其记录的 80 个聚焦测试，并保留
单槽 `SWAP` 校验及 mutation 前拒绝、带 `NullPointerException`/
`ArrayIndexOutOfBoundsException`/`ArrayStoreException` pending-exception
路由的 `GetObjectArrayElement`/`SetObjectArrayElement` 降低，以及 `legacy`
默认值。#92、#97、#103 与 #107 的 admission 测量必须限定在各自语料内：
#92 的 5/6 为合成，#97、#103 与 #107 的比例只是特定 JAR 上的计数；四者都
不是覆盖率门槛，且 #97 测量的是 #90，#103 测量的是 #99，#107 测量的是
#104。不得翻转默认值。

## (d) Review preconditions / review 的前置条件

1. Use #104's `docs/architecture/ir-phase16-status.md` for the `SWAP` /
   `AALOAD` / `AASTORE` admission, the single-slot `SWAP` validation with
   rejection at opcode 95 before mutation, the SSA-only `SWAP` lowering, the
   `GetObjectArrayElement`/`SetObjectArrayElement` lowering with pending
   `NullPointerException`, `ArrayIndexOutOfBoundsException`, and
   `ArrayStoreException` routing, the still-fallback `NEWARRAY` forms
   outside the retained `int[]` slice plus `MULTIANEWARRAY`, `POP2`/`DUP2*`,
   and `invokedynamic`, the legacy default, 78 + 2 = 80 tests, the unskipped
   128-`JNICALL` g++ smoke, and partial/not-ship-ready status. It is stacked
   on #99 (`f46c3eae`), not `master`.
2. Use #105's `docs/architecture/ir-phase16-review.md` only as the
   documentation-only Sol **accept** review of #104 (80/80, no compiler
   change, independent syntax-only check). Keep #102 as the
   documentation-only Sol **accept** review of #99; Fable was policy-blocked
   twice on the phase-15 slice, so do not cite a Fable verdict for it.
3. Use #107's `docs/benchmarks/ir-admission-phase16-corpus.md` (with its
   methods TSV and `measure.py` helper) only for its measurement-only
   admission counts on the #104 tip: 108/102/6/0 on ClassicTest (ΔIR +5
   versus #103), 36/24/12/0 on JDK 17 (ΔIR +1), 38/18/20/0 on the extra
   JDK 21 corpus (ΔIR +1), the 33 former `SWAP` fallbacks now failing at
   opcode 93 (`DUP2_X1`), the remaining `NEWARRAY` ×2, `MULTIANEWARRAY` ×2,
   and `ISTORE` type-mismatch ×1 first reasons, the skipped Krakatau
   fixture, and no native compile / no behavioral E2E. Keep #103's
   `docs/benchmarks/ir-admission-phase15-corpus.md` as the #99-tip baseline,
   #97's `docs/benchmarks/ir-admission-phase13-corpus.md` as the #90-tip
   baseline, and #92's `docs/benchmarks/ir-admission-phase12.md` as the
   synthetic 5/6 record. Do not generalize any fraction beyond those JARs,
   do not treat #107 as a coverage gate, and do not attribute any report's
   counts to another tip.
4. Prefer #104 as the direct-IR implementation tip over #99; keep #99
   preferred over #95, #95 over #90, #90 over its documentation-only reviews
   #93/#94, #89 over unfixed #84, #81 over unfixed #80, and #70 over unfixed
   #66.
5. Treat #106 as the previous options brief, not an evidence source for new
   compiler claims.
6. Keep #53's evaluator median `N/A`; do not back-fill it. Keep #37/#50 as
   evidence that requirement 7 is unmet.
7. Keep the complete written goal and all listed lanes. Option A remains
   only the v1 product recommendation.

中文核对项：

1. 以 #104 的 `docs/architecture/ir-phase16-status.md` 为准：保留 `SWAP`/
   `AALOAD`/`AASTORE` 接纳、单槽 `SWAP` 校验及 mutation 前于 opcode 95 的
   拒绝、仅交换 SSA 值的 `SWAP` 降低、带 pending `NullPointerException`/
   `ArrayIndexOutOfBoundsException`/`ArrayStoreException` 路由的
   `GetObjectArrayElement`/`SetObjectArrayElement` 降低、仍 fallback 的
   retained `int[]` 切片之外 `NEWARRAY` 及 `MULTIANEWARRAY`、
   `POP2`/`DUP2*` 与 invokedynamic、legacy 默认值、78 + 2 = 80 个测试、
   未跳过的 128-`JNICALL` g++ 烟测，以及仍部分/未达上线就绪的状态；它叠加
   在 #99（`f46c3eae`）上，不是 `master`。
2. #105 的 `docs/architecture/ir-phase16-review.md` 仅作为 #104 的纯文档
   Sol **accept** 审阅（80/80、无编译器改动、独立 syntax-only 检查）。#102
   保留为 #99 的纯文档 Sol **accept** 审阅；Fable 在 phase-15 切片上两次被
   策略拦截，不得引用任何 Fable 结论。
3. #107 仅以 `docs/benchmarks/ir-admission-phase16-corpus.md`（含方法级
   TSV 与 `measure.py`）为准：保留其在 #104 tip 上仅测量的接纳计数——
   ClassicTest 108/102/6/0（ΔIR 相对 #103 +5）、JDK 17 36/24/12/0
   （ΔIR +1）、extra JDK 21 38/18/20/0（ΔIR +1）、33 个原 `SWAP` fallback
   方法现于 opcode 93（`DUP2_X1`）拒绝、其余首因（`NEWARRAY` ×2、
   `MULTIANEWARRAY` ×2、类型不匹配 `ISTORE` ×1）、被跳过的 Krakatau
   fixture，以及未编译 native / 无行为 E2E 的边界。保留 #103 的
   `docs/benchmarks/ir-admission-phase15-corpus.md` 作为 #99 tip 基线、
   #97 的 `docs/benchmarks/ir-admission-phase13-corpus.md` 作为 #90 tip
   基线、#92 的 `docs/benchmarks/ir-admission-phase12.md` 作为合成 5/6
   记录。不得把任何比例外推到这些 JAR 之外，不得把 #107 当作覆盖率门槛，
   也不得把任一报告的计数归到其他 tip 名下。
4. 将 #104 作为 direct-IR 实现 tip，优先于 #99；保持 #99 优先于 #95、#95
   优先于 #90、#90 优先于其纯文档审阅 #93/#94、#89 优先于未修复 #84、#81
   优先于未修复 #80、#70 优先于未修复 #66。
5. #106 仅为上一份 options brief，不是新编译器声明的证据来源。
6. 保持 #53 的 evaluator 中位数为 `N/A`，不得回填；保留 #37/#50 作为
   requirement 7 未满足的证据。
7. 保留完整书面目标与所有已列路线；A 仅为 v1 产品建议。

<!-- CURSOR_AGENT_PR_BODY_END -->
