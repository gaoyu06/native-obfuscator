<!-- CURSOR_AGENT_PR_BODY_BEGIN -->
## Summary / 摘要

Stacks the documentation-only maintainer brief on draft PR #96 (the previous
brief, through #94) and folds in #95/#96/#97 using only claims in their named
branch documents and PR bodies. It preserves the phase-13, phase-12, evaluator
LDIV, AES, benchmark, reader, and complete written-goal conclusions. #95, the
phase-14 scalar float/double implementation stacked on preferred phase-13 tip
#90, is now the preferred direct-IR implementation tip; #90 remains preferred
over its documentation-only reviews #93/#94, #89 over unfixed #84, #81 over
unfixed #80, and #70 over unfixed #66. #97 replaces #92 as the real-fixture
admission measurement on the #90 tip (not #95); neither #92's synthetic 5/6 nor
#97's corpus fractions is a coverage gate.

在草稿 PR #96（覆盖至 #94 的上一份简报）的基础上，将仅文档的维护者简报加入
#95/#96/#97，且仅采用其指定分支文档与 PR body 记录的声明。同时保留 phase-13、
phase-12、evaluator LDIV、AES、benchmark、reader 与完整书面工程目标结论。
叠加在首选 phase-13 tip #90 上的 phase-14 标量 float/double 实现 #95 现为首选
direct-IR 实现 tip；#90 仍优先于其纯文档审阅 #93/#94，#89 仍优先于未修复
#84，#81 仍优先于未修复 #80，#70 仍优先于未修复 #66。#97 取代 #92 成为
#90 tip（而非 #95）上的真实 fixture admission 测量；#92 的合成 5/6 与 #97 的
语料比例都不是覆盖率门槛。

## (a) Change scope / 本次改动范围

- Add #95 on preferred phase-13 tip #90: still-opt-in phase-14 scalar `F`/`D`
  carried as real `F32`/`jfloat` and `F64`/`jdouble` values, not `I32`, with
  `D` kept category-two. It admits load/store/return, raw-bit-pattern constants
  via `memcpy`, instance/static Float/Double JNI fields, the
  static/virtual/interface/special Float/Double invoke families, scalar
  arithmetic with `fmod` remainder and negation,
  `FCMPL`/`FCMPG`/`DCMPL`/`DCMPG`, and I/F/L/D conversions with JVM
  NaN/overflow mapping. Primitive arrays (including `[F`/`[D`),
  `MULTIANEWARRAY`, `POP2`/`DUP2*`, and `invokedynamic` still fall back;
  legacy remains the default. Its status records 68 + 2 = 70 focused tests and
  an unskipped 116-`JNICALL` g++ smoke. #95 is now the preferred direct-IR
  implementation tip; it is partial and not ship-ready.
- Treat #96 as the previous options brief (through #94) and this branch's
  stacking base, not new compiler work.
- Add #97, a measurement-only admission report stacked on #90 (the phase-13
  tip `b5a403f`, not #95). Corpus A, the checked-in ClassicTest fixtures,
  recorded inventory **108 / 69 IR / 37 legacy fallback /
  2 constructor-left-Java**; Corpus B, the fetched JDK 17 E2E fixtures,
  recorded **36 / 20 / 16 / 0**; a separately labeled extra JDK 21 corpus (not
  a JDK 17 result) recorded **38 / 15 / 23 / 0**. The dominant fallback opcode
  is **18** (`LDC`), the top remaining admission gap on that tip. The Krakatau
  fixture was skipped (`krak2` missing); no native library was compiled and no
  behavioral/E2E claim is made. #97 replaces #92 as the honest real-fixture
  corpus measurement; #92's synthetic 5/6 stays on record, and neither is a
  coverage gate.
- Keep #53 unchanged in meaning: eval fell back on `USHR`, so its evaluator
  median remains `N/A`. Do not back-fill it from #59 or any later work.
- Keep #37 and #50 as full recoveries of all four methods from their valid live
  subjects; requirement 7 remains unmet.
- Preserve the lanes:
  - direct IR: #45 → #47 → #51 → #54 → #56 → #62 → #63/#64 → #66 →
    #70/#71 → #73 → #76/#77 → #78 → #82/#83 → #84 → #89/#88 → #90 →
    #93/#94 → #95, with #73 based only on preferred, fixed #70, #90 based on
    preferred #89, #93/#94 as documentation-only reviews of #90, and #95 based
    on #90 as the preferred implementation tip;
  - evaluator: #42 → #44 → #48 → #50, #57/#61, #68/#69 → #85/#87;
  - benchmark: #34 → #53, with #59 stacked on #57; admission measurements #92
    (synthetic, on #89) and #97 (real fixtures, on #90);
  - SDK: #12 → #15 → #46 → #72 → #75 → #80 → #81, with #81 the preferred
    AES tip;
  - compatibility: #6 → #9 → #14 → #41;
  - options: … → #74 → #79 → #86 → #91 → #96 → this branch.
- Preserve the complete written engineering goal. Option A remains only the
  v1 product recommendation.
- Update only `docs/architecture/goal-status-and-options.md` and this bilingual
  PR body. PRs #1–#97 remain open drafts; `master` remains `e7ca4c8`.

- 新增叠加在首选 phase-13 tip #90 上的 #95：仍为 opt-in 的 phase-14 标量
  `F`/`D`，以真实 `F32`/`jfloat` 与 `F64`/`jdouble` 承载，不再是 `I32`，`D`
  保持 category-two。覆盖 load/store/return、以 `memcpy` 保留原始位模式的
  常量、Float/Double 实例与静态 JNI 字段、static/virtual/interface/special
  Float/Double invoke family、标量算术与 `fmod` 求余/取负、
  `FCMPL`/`FCMPG`/`DCMPL`/`DCMPG`，以及带 JVM NaN/溢出映射的 I/F/L/D 转换。
  基元数组（含 `[F`/`[D`）、`MULTIANEWARRAY`、`POP2`/`DUP2*` 与
  invokedynamic 仍 fallback，默认仍为 legacy。其状态记录 68 + 2 = 70 个聚焦
  测试及未跳过的 116-`JNICALL` g++ 烟测。#95 现为首选 direct-IR 实现 tip；
  仍部分且未达上线就绪。
- #96 为上一份 options brief（覆盖至 #94），是本分支的叠加基础，不是新的
  编译器工作。
- 新增叠加在 #90（phase-13 tip `b5a403f`，而非 #95）上、仅测量的 admission
  报告 #97。Corpus A（检入的 ClassicTest fixtures）记录 inventory
  **108 / 69 IR / 37 legacy fallback / 2 constructor-left-Java**；Corpus B
  （拉取的 JDK 17 E2E fixtures）记录 **36 / 20 / 16 / 0**；单独标注为
  extra、不属于 JDK 17 结果的 JDK 21 语料记录 **38 / 15 / 23 / 0**。主导
  fallback opcode 为 **18**（`LDC`），是该 tip 上最主要的剩余 admission
  缺口。Krakatau fixture 因缺少 `krak2` 被跳过；未编译 native 库，不作
  行为/E2E 声明。#97 取代 #92 成为真实 fixture 语料的诚实测量；#92 的合成
  5/6 仍保留在案，两者都不是覆盖率门槛。
- 保持 #53 原意不变：eval 因 `USHR` fallback，故其 evaluator 中位数仍为
  `N/A`。不得用 #59 或任何后续工作回填。
- 保留 #37 与 #50：两者均从各自有效 live 样本完整恢复四个方法，因此
  requirement 7 仍未满足。
- 保持各路线：
  - direct IR：#45 → #47 → #51 → #54 → #56 → #62 → #63/#64 → #66 →
    #70/#71 → #73 → #76/#77 → #78 → #82/#83 → #84 → #89/#88 → #90 →
    #93/#94 → #95，其中 #73 仅基于首选且含修复的 #70，#90 基于首选 #89，
    #93/#94 是 #90 的纯文档审阅，#95 基于 #90 且为首选实现 tip；
  - evaluator：#42 → #44 → #48 → #50、#57/#61、#68/#69 → #85/#87；
  - benchmark：#34 → #53，另有叠加在 #57 上的 #59；admission 测量为叠加在
    #89 上的 #92（合成）与叠加在 #90 上的 #97（真实 fixture）；
  - SDK：#12 → #15 → #46 → #72 → #75 → #80 → #81，其中 #81 为首选
    AES tip；
  - compatibility：#6 → #9 → #14 → #41；
  - options：… → #74 → #79 → #86 → #91 → #96 → 本分支。
- 保留完整书面工程目标；A 仅为 v1 产品建议。
- 仅更新 `docs/architecture/goal-status-and-options.md` 与本双语 PR body。
  PR #1–#97 仍均为 open draft；`master` 仍为 `e7ca4c8`。

## (b) Can this ship to production as-is? / 是否可直接上线

**No. / 否。**

All referenced work remains in draft PRs and `master` contains none of it. The
defaults remain legacy/direct. #95 extends still-opt-in phase 14 to scalar
`F`/`D`, but primitive arrays (including `[F`/`[D`), `MULTIANEWARRAY`,
`POP2`/`DUP2*`, and `invokedynamic` still fall back and legacy remains
default; it is the preferred implementation tip yet partial and not ship
approval. #96 is only the previous options brief. #97 is a measurement-only
admission report on the #90 tip, not #95: its 108/69, 36/20, and extra 38/15
rows are admission counts on those JARs only, with no native compile and no
behavioral E2E; it is not production coverage and not ship approval. #53 still
has no evaluator timing, and #37 and #50 fully recovered all four methods from
valid live subjects, so requirement 7 remains unmet.

所有相关工作仍在草稿 PR 中，`master` 未包含这些内容，默认值仍为 legacy/direct。
#95 将仍为 opt-in 的 phase 14 扩展至标量 `F`/`D`，但基元数组（含
`[F`/`[D`）、`MULTIANEWARRAY`、`POP2`/`DUP2*` 与 invokedynamic 仍 fallback，
默认仍为 legacy；它是首选实现 tip，但仍部分，不是上线批准。#96 只是上一份
options brief。#97 是叠加在 #90 tip（而非 #95）上、仅测量的 admission 报告：
108/69、36/20 与 extra 的 38/15 都只是这些 JAR 上的接纳计数，未编译 native、
无行为 E2E，不代表生产覆盖率，也不是上线批准。#53 仍没有 evaluator timing，
#37 与 #50 均从有效 live 样本完整恢复四个方法，因此 requirement 7 仍未满足。

## (c) Is review required? / 上线前是否需要 review

**Yes. / 是。**

Each implementation stack still requires independent code review, post-rebase
verification, supported-platform/JDK CI, native runtime-parity checks, and
applicable product/release approval. Phase-14 review must start from #95 on
preferred #90, re-run its recorded 70 focused tests with `CC=gcc CXX=g++`, and
keep the exact JNI families, category-two `D` slots, NaN compare polarity,
saturating conversions, constant bit preservation, fallback-before-mutation,
and the `legacy` default. The #92 and #97 admission measurements must stay
scoped to their corpora: #92's 5/6 is synthetic and #97's fractions are counts
on those specific JARs; neither is a coverage gate, and #97 measured #90, not
#95. Do not flip the default.

每条实现栈仍需独立代码审查、rebase 后复测、受支持平台/JDK CI、native runtime
parity 检查，以及适用的产品/发布审批。Phase-14 审阅必须从叠加在首选 #90 上的
#95 开始，用 `CC=gcc CXX=g++` 重跑其记录的 70 个聚焦测试，并保留精确 JNI
family、category-two `D` slot、NaN 比较极性、饱和转换、常量位保留、
fallback-before-mutation 与 `legacy` 默认值。#92 与 #97 的 admission 测量必须
限定在各自语料内：#92 的 5/6 为合成，#97 的比例只是特定 JAR 上的计数；两者都
不是覆盖率门槛，且 #97 测量的是 #90 而非 #95。不得翻转默认值。

## (d) Review preconditions / review 的前置条件

1. Use #95's `docs/architecture/ir-phase14-status.md` for the real `F32`/`F64`
   scalar carriers, the exact Float/Double field and invoke JNI families, the
   `memcpy` raw-bit constants, JVM NaN/overflow conversion mapping,
   `fmod`-based remainder, the still-fallback primitive arrays /
   `MULTIANEWARRAY` / `POP2`/`DUP2*` / `invokedynamic`, the legacy default,
   68 + 2 = 70 tests, the unskipped 116-`JNICALL` g++ smoke, and
   partial/not-ship-ready status. It is stacked on #90 (`b5a403f`), not
   `master`.
2. Use #97's `docs/benchmarks/ir-admission-phase13-corpus.md` (with its
   methods TSV and `measure.py` helper) only for its measurement-only
   admission counts on the #90 tip: 108/69/37/2 on ClassicTest, 36/20/16/0 on
   JDK 17, 38/15/23/0 on the extra JDK 21 corpus, dominant fallback opcode 18
   (`LDC`), the skipped Krakatau fixture, and no native compile / no
   behavioral E2E. Do not generalize these fractions beyond those JARs, do not
   treat them as production coverage, and do not attribute them to #95.
3. Keep #92's `docs/benchmarks/ir-admission-phase12.md` as the synthetic
   six-method 5/6 record on #89; #97 replaces it as the honest real-fixture
   corpus measurement, and neither is a coverage gate.
4. Prefer #95 as the direct-IR implementation tip over #90; keep #90 preferred
   over its documentation-only reviews #93/#94, #89 over unfixed #84, #81 over
   unfixed #80, and #70 over unfixed #66.
5. Treat #96 as the previous options brief, not an evidence source for new
   compiler claims.
6. Keep #53's evaluator median `N/A`; do not back-fill it. Keep #37/#50 as
   evidence that requirement 7 is unmet.
7. Keep the complete written goal and all listed lanes. Option A remains only
   the v1 product recommendation.

中文核对项：

1. 以 #95 的 `docs/architecture/ir-phase14-status.md` 为准：保留真实的
   `F32`/`F64` 标量 carrier、精确 Float/Double 字段与 invoke JNI family、
   `memcpy` 原始位常量、JVM NaN/溢出转换映射、基于 `fmod` 的求余、仍
   fallback 的基元数组 / `MULTIANEWARRAY` / `POP2`/`DUP2*` /
   invokedynamic、legacy 默认值、68 + 2 = 70 个测试、未跳过的
   116-`JNICALL` g++ 烟测，以及仍部分/未达上线就绪的状态；它叠加在 #90
   （`b5a403f`）上，不是 `master`。
2. #97 仅以 `docs/benchmarks/ir-admission-phase13-corpus.md`（含方法级 TSV 与
   `measure.py`）为准：保留其在 #90 tip 上仅测量的接纳计数——ClassicTest
   108/69/37/2、JDK 17 36/20/16/0、extra JDK 21 38/15/23/0、主导 fallback
   opcode 18（`LDC`）、被跳过的 Krakatau fixture，以及未编译 native / 无行为
   E2E 的边界。不得把这些比例外推到这些 JAR 之外，不得当作生产覆盖率，也
   不得归到 #95 名下。
3. 保留 #92 的 `docs/benchmarks/ir-admission-phase12.md` 作为 #89 上合成的
   六方法 5/6 记录；#97 取代它成为真实 fixture 语料的诚实测量，两者都不是
   覆盖率门槛。
4. 将 #95 作为 direct-IR 实现 tip，优先于 #90；保持 #90 优先于其纯文档审阅
   #93/#94、#89 优先于未修复 #84、#81 优先于未修复 #80、#70 优先于未修复
   #66。
5. #96 仅为上一份 options brief，不是新编译器声明的证据来源。
6. 保持 #53 的 evaluator 中位数为 `N/A`，不得回填；保留 #37/#50 作为
   requirement 7 未满足的证据。
7. 保留完整书面目标与所有已列路线；A 仅为 v1 产品建议。

<!-- CURSOR_AGENT_PR_BODY_END -->
