<!-- CURSOR_AGENT_PR_BODY_BEGIN -->
## Summary / 摘要

Stacks the documentation-only maintainer brief on draft PR #100 (the previous
brief, through #97) and folds in #98/#99/#101/#102/#103 using only claims in
their named branch documents and PR bodies. It preserves the phase-14,
phase-13, phase-12, evaluator LDIV, AES, benchmark, reader, and complete
written-goal conclusions. #99, the phase-15 LDC String / object-array Class /
Long implementation stacked on #95, is now the preferred direct-IR
implementation tip; #95 remains preferred over #90, #90 over its
documentation-only reviews #93/#94, #89 over unfixed #84, #81 over unfixed
#80, and #70 over unfixed #66. #103 replaces #97 as the honest admission
measurement on the current IR tip (#97 stays as the #90-tip baseline: #97
measured #90, #103 measured #99); neither #92's synthetic 5/6 nor #97's or
#103's corpus fractions is a coverage gate.

在草稿 PR #100（覆盖至 #97 的上一份简报）的基础上，将仅文档的维护者简报加入
#98/#99/#101/#102/#103，且仅采用其指定分支文档与 PR body 记录的声明。同时
保留 phase-14、phase-13、phase-12、evaluator LDIV、AES、benchmark、reader 与
完整书面工程目标结论。叠加在 #95 上的 phase-15 LDC String/对象-数组
Class/Long 实现 #99 现为首选 direct-IR 实现 tip；#95 仍优先于 #90，#90 仍
优先于其纯文档审阅 #93/#94，#89 仍优先于未修复 #84，#81 仍优先于未修复
#80，#70 仍优先于未修复 #66。#103 取代 #97 成为当前 IR tip 上的诚实
admission 测量（#97 保留为 #90 tip 基线：#97 测的是 #90，#103 测的是
#99）；#92 的合成 5/6 与 #97/#103 的语料比例都不是覆盖率门槛。

## (a) Change scope / 本次改动范围

- Add #98, Sol's documentation-only **accept** review of phase-14 #95: no
  compiler change, a 68 + 2 = 70 focused-test rerun (70/70), the unskipped
  116-`JNICALL` g++ smoke, an independent syntax-only check, and a
  UBSan/float-cast-overflow harness on the conversion branches. It is not a
  compiler fix.
- Add #101, Fable's documentation-only **accept-with-nits** review of the
  same #95: no compiler change and 70/70; its nits are cosmetic (verbose SSA
  re-referencing, per-constant IIFEs) plus carried-forward earlier items,
  with no correctness blocker. It is not a compiler fix.
- Add #99 on #95 (`ece69f5`): still-opt-in IR phase 15 admits the common
  `LDC` forms — String through the existing `StringPool`/`cstrings` tables
  and `NewStringUTF` (empty, ASCII, non-ASCII, embedded-NUL modified UTF-8),
  object/array Class through the existing `cclasses` cache (object classes
  via the defining loader, `[I` and `[Ljava/lang/String;` via `FindClass`),
  and Long through the existing `LongConst`/`I64` path. Primitive Class `LDC`
  is conservatively rejected before mutation rather than emitted wrong;
  `MethodType`/`Handle`/`ConstantDynamic` `LDC` still fall back; legacy
  remains the default. Its status records 73 + 2 = **75** focused tests and
  an unskipped 119-`JNICALL` g++ smoke. **#99 is now the preferred direct-IR
  implementation tip**; it is partial and not ship-ready.
- Add #102, Sol's documentation-only **accept** review of phase-15 #99: no
  compiler change, 75/75, the unskipped 119-`JNICALL` g++ smoke, and an
  independent syntax-only check. Fable was **policy-blocked twice** on this
  slice, so #102 is its only independent review; no Fable verdict exists and
  none is invented.
- Add #103, a measurement-only admission report stacked on #99 (`f46c3eae`)
  that reruns the same corpora as #97 (which measured #90 at `b5a403f`):
  Corpus A ClassicTest records **108 / 97 IR / 11 fallback /
  0 constructor-left-Java** (ΔIR **+28** versus #97); Corpus B JDK 17 records
  **36 / 23 / 13 / 0** (ΔIR **+3**); the separately labeled extra JDK 21
  corpus records **38 / 17 / 21 / 0** (ΔIR **+2**). Opcode 18 (`LDC`) is no
  longer the dominant fallback; the new top reasons are opcode **50**
  (`AALOAD`) on ClassicTest and opcode **95** (`SWAP`) on the JDK 17/21
  corpora. Krakatau was again skipped; no native compile, no behavioral E2E.
  **#103 replaces #97 as the honest measurement on the current IR tip**; #97
  stays as the #90-tip baseline, and neither is a coverage gate.
- Treat #100 as the previous options brief (through #97) and this branch's
  stacking base, not new compiler work.
- Keep #53 unchanged in meaning: eval fell back on `USHR`, so its evaluator
  median remains `N/A`. Do not back-fill it from #59 or any later work.
- Keep #37 and #50 as full recoveries of all four methods from their valid
  live subjects; requirement 7 remains unmet.
- Preserve the lanes:
  - direct IR: #45 → #47 → #51 → #54 → #56 → #62 → #63/#64 → #66 →
    #70/#71 → #73 → #76/#77 → #78 → #82/#83 → #84 → #89/#88 → #90 →
    #93/#94 → #95 → #98/#101 → #99 → #102, with #73 based only on
    preferred, fixed #70, #90 based on preferred #89, #93/#94 as
    documentation-only reviews of #90, #98/#101 as documentation-only reviews
    of #95, #102 as the documentation-only review of #99, and #99 based on
    #95 as the preferred implementation tip;
  - evaluator: #42 → #44 → #48 → #50, #57/#61, #68/#69 → #85/#87;
  - benchmark: #34 → #53, with #59 stacked on #57; admission measurements
    #92 (synthetic, on #89), #97 (real fixtures, on #90), and #103 (real
    fixtures, on #99; the preferred admission measurement);
  - SDK: #12 → #15 → #46 → #72 → #75 → #80 → #81, with #81 the preferred
    AES tip;
  - compatibility: #6 → #9 → #14 → #41;
  - options: … → #74 → #79 → #86 → #91 → #96 → #100 → this branch.
- Preserve the complete written engineering goal. Option A remains only the
  v1 product recommendation.
- Update only `docs/architecture/goal-status-and-options.md` and this
  bilingual PR body. PRs #1–#103 remain open drafts; `master` remains
  `e7ca4c8`.

- 新增 #98：Sol 对 phase-14 #95 的纯文档 **accept** 审阅——无编译器改动、
  68 + 2 = 70 个聚焦测试重跑（70/70）、未跳过的 116-`JNICALL` g++ 烟测、
  独立 syntax-only 检查，以及转换分支上的 UBSan/float-cast-overflow 校验。
  它不是编译器修复。
- 新增 #101：Fable 对同一 #95 的纯文档 **accept-with-nits** 审阅——无编译器
  改动、70/70；nit 均为表述层面（冗长 SSA 重引用、每常量 IIFE）加此前已知
  事项，无正确性阻塞。它不是编译器修复。
- 新增叠加在 #95（`ece69f5`）上的 #99：仍为 opt-in 的 IR phase 15 接纳常见
  `LDC`——String 复用既有 `StringPool`/`cstrings` 并经 `NewStringUTF`
  （空串、ASCII、非 ASCII、含内嵌 NUL 的 modified UTF-8），对象/数组 Class
  复用既有 `cclasses` 缓存（对象类经 defining loader，`[I` 与
  `[Ljava/lang/String;` 经 `FindClass`），Long 复用既有 `LongConst`/`I64`。
  primitive Class LDC 在 mutation 前保守拒绝而非错误输出；
  `MethodType`/`Handle`/`ConstantDynamic` LDC 仍 fallback；默认仍为
  legacy。其状态记录 73 + 2 = **75** 个聚焦测试及未跳过的 119-`JNICALL`
  g++ 烟测。**#99 现为首选 direct-IR 实现 tip**；仍部分且未达上线就绪。
- 新增 #102：Sol 对 phase-15 #99 的纯文档 **accept** 审阅——无编译器改动、
  75/75、未跳过的 119-`JNICALL` g++ 烟测及独立 syntax-only 检查。Fable 在
  该切片上**两次被策略拦截**，故 #102 是其唯一独立审阅；不存在也不虚构
  Fable 结论。
- 新增叠加在 #99（`f46c3eae`）上、仅测量的 admission 报告 #103，复测与 #97
  （其测量对象为 #90 `b5a403f`）相同的语料：Corpus A ClassicTest 记录
  **108 / 97 IR / 11 fallback / 0 constructor-left-Java**（ΔIR 相对 #97
  **+28**）；Corpus B JDK 17 记录 **36 / 23 / 13 / 0**（ΔIR **+3**）；单独
  标注为 extra 的 JDK 21 语料记录 **38 / 17 / 21 / 0**（ΔIR **+2**）。
  opcode 18（`LDC`）不再是主导 fallback；新的首要原因是 ClassicTest 上的
  opcode **50**（`AALOAD`）与 JDK 17/21 语料上的 opcode **95**（`SWAP`）。
  Krakatau 仍被跳过；未编译 native、无行为 E2E。**#103 取代 #97 成为当前
  IR tip 上的诚实测量**；#97 保留为 #90 tip 基线，两者都不是覆盖率门槛。
- #100 为上一份 options brief（覆盖至 #97），是本分支的叠加基础，不是新的
  编译器工作。
- 保持 #53 原意不变：eval 因 `USHR` fallback，故其 evaluator 中位数仍为
  `N/A`。不得用 #59 或任何后续工作回填。
- 保留 #37 与 #50：两者均从各自有效 live 样本完整恢复四个方法，因此
  requirement 7 仍未满足。
- 保持各路线：
  - direct IR：#45 → #47 → #51 → #54 → #56 → #62 → #63/#64 → #66 →
    #70/#71 → #73 → #76/#77 → #78 → #82/#83 → #84 → #89/#88 → #90 →
    #93/#94 → #95 → #98/#101 → #99 → #102，其中 #73 仅基于首选且含修复
    的 #70，#90 基于首选 #89，#93/#94 是 #90 的纯文档审阅，#98/#101 是
    #95 的纯文档审阅，#102 是 #99 的纯文档审阅，#99 基于 #95 且为首选实现
    tip；
  - evaluator：#42 → #44 → #48 → #50、#57/#61、#68/#69 → #85/#87；
  - benchmark：#34 → #53，另有叠加在 #57 上的 #59；admission 测量为叠加在
    #89 上的 #92（合成）、叠加在 #90 上的 #97（真实 fixture）与叠加在 #99
    上的 #103（真实 fixture，首选的 admission 测量）；
  - SDK：#12 → #15 → #46 → #72 → #75 → #80 → #81，其中 #81 为首选
    AES tip；
  - compatibility：#6 → #9 → #14 → #41；
  - options：… → #74 → #79 → #86 → #91 → #96 → #100 → 本分支。
- 保留完整书面工程目标；A 仅为 v1 产品建议。
- 仅更新 `docs/architecture/goal-status-and-options.md` 与本双语 PR body。
  PR #1–#103 仍均为 open draft；`master` 仍为 `e7ca4c8`。

## (b) Can this ship to production as-is? / 是否可直接上线

**No. / 否。**

All referenced work remains in draft PRs and `master` contains none of it.
The defaults remain legacy/direct. #99 extends still-opt-in phase 15 to
String / object-array Class / Long `LDC`, but primitive Class `LDC`,
`MethodType`/`Handle`/`ConstantDynamic`, arrays, `POP2`, and `invokedynamic`
still fall back and legacy remains default; it is the preferred
implementation tip yet partial and not ship approval. #98/#101 (of #95) and
#102 (of #99) are documentation-only reviews, not compiler fixes and not
ship approval; Fable was policy-blocked twice on the phase-15 slice, so #102
is its only independent review. #100 is only the previous options brief.
#103 is a measurement-only admission report on the #99 tip: its 108/97,
36/23, and extra 38/17 rows are admission counts on those JARs only, with no
native compile and no behavioral E2E; it is not production coverage and not
ship approval. #53 still has no evaluator timing, and #37 and #50 fully
recovered all four methods from valid live subjects, so requirement 7
remains unmet.

所有相关工作仍在草稿 PR 中，`master` 未包含这些内容，默认值仍为
legacy/direct。#99 将仍为 opt-in 的 phase 15 扩展至 String/对象-数组
Class/Long 的 `LDC`，但 primitive Class LDC、
`MethodType`/`Handle`/`ConstantDynamic`、数组、`POP2` 与 invokedynamic 仍
fallback，默认仍为 legacy；它是首选实现 tip，但仍部分，不是上线批准。
#98/#101（对 #95）与 #102（对 #99）均为纯文档审阅，不是编译器修复，也不是
上线批准；Fable 在 phase-15 切片上两次被策略拦截，#102 是其唯一独立审阅。
#100 只是上一份 options brief。#103 是叠加在 #99 tip 上、仅测量的
admission 报告：108/97、36/23 与 extra 的 38/17 都只是这些 JAR 上的接纳
计数，未编译 native、无行为 E2E，不代表生产覆盖率，也不是上线批准。#53 仍
没有 evaluator timing，#37 与 #50 均从有效 live 样本完整恢复四个方法，因此
requirement 7 仍未满足。

## (c) Is review required? / 上线前是否需要 review

**Yes. / 是。**

Each implementation stack still requires independent code review, post-rebase
verification, supported-platform/JDK CI, native runtime-parity checks, and
applicable product/release approval. Phase-15 review must start from #99 on
#95, re-run its recorded 75 focused tests with `CC=gcc CXX=g++`, and keep the
`StringPool`/`NewStringUTF` path, the `cclasses`/`FindClass` object/array
Class handling with pending-exception routing, the `LongConst` path,
primitive-Class rejection before mutation, and the `legacy` default. The
#92, #97, and #103 admission measurements must stay scoped to their corpora:
#92's 5/6 is synthetic, and #97's and #103's fractions are counts on those
specific JARs; none is a coverage gate, #97 measured #90, and #103 measured
#99. Do not flip the default.

每条实现栈仍需独立代码审查、rebase 后复测、受支持平台/JDK CI、native
runtime parity 检查，以及适用的产品/发布审批。Phase-15 审阅必须从叠加在
#95 上的 #99 开始，用 `CC=gcc CXX=g++` 重跑其记录的 75 个聚焦测试，并保留
`StringPool`/`NewStringUTF` 路径、带 pending-exception 路由的
`cclasses`/`FindClass` 对象/数组 Class 处理、`LongConst` 路径、mutation 前
的 primitive Class 拒绝与 `legacy` 默认值。#92、#97 与 #103 的 admission
测量必须限定在各自语料内：#92 的 5/6 为合成，#97 与 #103 的比例只是特定
JAR 上的计数；三者都不是覆盖率门槛，且 #97 测量的是 #90，#103 测量的是
#99。不得翻转默认值。

## (d) Review preconditions / review 的前置条件

1. Use #99's `docs/architecture/ir-phase15-status.md` for the String /
   object-array Class / Long `LDC` admission, the modified UTF-8
   `NewStringUTF` handling, the `cclasses`/`FindClass` class loading with
   pending-exception routing, the primitive-Class rejection before mutation,
   the still-fallback `MethodType`/`Handle`/`ConstantDynamic` `LDC`, the
   legacy default, 73 + 2 = 75 tests, the unskipped 119-`JNICALL` g++ smoke,
   and partial/not-ship-ready status. It is stacked on #95 (`ece69f5`), not
   `master`.
2. Use #98's `docs/architecture/ir-phase14-review.md` and #101's
   `docs/architecture/ir-phase14-fable-review.md` only as documentation-only
   reviews of #95 (Sol **accept** and Fable **accept-with-nits**, both
   70/70, no compiler change). Use #102's
   `docs/architecture/ir-phase15-review.md` only as the documentation-only
   Sol **accept** review of #99 (75/75, no compiler change); Fable was
   policy-blocked twice on that slice, so do not cite a Fable verdict for it.
3. Use #103's `docs/benchmarks/ir-admission-phase15-corpus.md` (with its
   methods TSV and `measure.py` helper) only for its measurement-only
   admission counts on the #99 tip: 108/97/11/0 on ClassicTest (ΔIR +28),
   36/23/13/0 on JDK 17 (ΔIR +3), 38/17/21/0 on the extra JDK 21 corpus
   (ΔIR +2), the new top fallback reasons opcode 50 (`AALOAD`) on
   ClassicTest and opcode 95 (`SWAP`) on JDK 17/21, the skipped Krakatau
   fixture, and no native compile / no behavioral E2E. Keep #97's
   `docs/benchmarks/ir-admission-phase13-corpus.md` as the #90-tip baseline
   and #92's `docs/benchmarks/ir-admission-phase12.md` as the synthetic 5/6
   record. Do not generalize any fraction beyond those JARs, do not treat
   #103 as a coverage gate, and do not attribute #97's counts to #99 or
   #103's counts to #90.
4. Prefer #99 as the direct-IR implementation tip over #95; keep #95
   preferred over #90, #90 over its documentation-only reviews #93/#94, #89
   over unfixed #84, #81 over unfixed #80, and #70 over unfixed #66.
5. Treat #100 as the previous options brief, not an evidence source for new
   compiler claims.
6. Keep #53's evaluator median `N/A`; do not back-fill it. Keep #37/#50 as
   evidence that requirement 7 is unmet.
7. Keep the complete written goal and all listed lanes. Option A remains
   only the v1 product recommendation.

中文核对项：

1. 以 #99 的 `docs/architecture/ir-phase15-status.md` 为准：保留 String/
   对象-数组 Class/Long 的 `LDC` 接纳、modified UTF-8 的 `NewStringUTF`
   处理、带 pending-exception 路由的 `cclasses`/`FindClass` 类加载、
   mutation 前的 primitive Class 拒绝、仍 fallback 的
   `MethodType`/`Handle`/`ConstantDynamic` LDC、legacy 默认值、
   73 + 2 = 75 个测试、未跳过的 119-`JNICALL` g++ 烟测，以及仍部分/未达
   上线就绪的状态；它叠加在 #95（`ece69f5`）上，不是 `master`。
2. #98 的 `docs/architecture/ir-phase14-review.md` 与 #101 的
   `docs/architecture/ir-phase14-fable-review.md` 仅作为 #95 的纯文档审阅
   （Sol **accept** 与 Fable **accept-with-nits**，均 70/70、无编译器
   改动）。#102 的 `docs/architecture/ir-phase15-review.md` 仅作为 #99 的
   纯文档 Sol **accept** 审阅（75/75、无编译器改动）；Fable 在该切片上两次
   被策略拦截，不得引用任何 Fable 结论。
3. #103 仅以 `docs/benchmarks/ir-admission-phase15-corpus.md`（含方法级
   TSV 与 `measure.py`）为准：保留其在 #99 tip 上仅测量的接纳计数——
   ClassicTest 108/97/11/0（ΔIR +28）、JDK 17 36/23/13/0（ΔIR +3）、extra
   JDK 21 38/17/21/0（ΔIR +2）、新的首要 fallback 原因（ClassicTest 上
   opcode 50 `AALOAD`、JDK 17/21 上 opcode 95 `SWAP`）、被跳过的 Krakatau
   fixture，以及未编译 native / 无行为 E2E 的边界。保留 #97 的
   `docs/benchmarks/ir-admission-phase13-corpus.md` 作为 #90 tip 基线、
   #92 的 `docs/benchmarks/ir-admission-phase12.md` 作为合成 5/6 记录。
   不得把任何比例外推到这些 JAR 之外，不得把 #103 当作覆盖率门槛，也不得
   把 #97 的计数归到 #99 或把 #103 的计数归到 #90 名下。
4. 将 #99 作为 direct-IR 实现 tip，优先于 #95；保持 #95 优先于 #90、#90
   优先于其纯文档审阅 #93/#94、#89 优先于未修复 #84、#81 优先于未修复
   #80、#70 优先于未修复 #66。
5. #100 仅为上一份 options brief，不是新编译器声明的证据来源。
6. 保持 #53 的 evaluator 中位数为 `N/A`，不得回填；保留 #37/#50 作为
   requirement 7 未满足的证据。
7. 保留完整书面目标与所有已列路线；A 仅为 v1 产品建议。

<!-- CURSOR_AGENT_PR_BODY_END -->
