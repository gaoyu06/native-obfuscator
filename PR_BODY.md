<!-- CURSOR_AGENT_PR_BODY_BEGIN -->
## Summary / 摘要

Stacks the documentation-only maintainer brief on draft PR #91 and folds in
#90/#92/#93/#94 using only claims in their named branch documents. It preserves
#91's phase-12, evaluator LDIV, AES, benchmark, reader, and complete
written-goal conclusions. #90, the phase-13 implementation stacked on preferred
phase-12 tip #89, is preferred over its documentation-only reviews #93/#94; #89
remains preferred over unfixed #84, #81 over unfixed #80, and #70 over unfixed
#66. #92 is a measurement-only admission report and is not a coverage gate.

在草稿 PR #91 的基础上，将仅文档的维护者简报加入 #90/#92/#93/#94，且仅采用其
指定分支文档记录的声明。同时保留 #91 的 phase-12、evaluator LDIV、AES、
benchmark、reader 与完整书面工程目标结论。叠加在首选 phase-12 tip #89 上的
phase-13 实现 #90 优先于其纯文档审阅 #93/#94；#89 仍优先于未修复 #84，#81 仍
优先于未修复 #80，#70 仍优先于未修复 #66。#92 仅为测量报告，不是覆盖率门槛。

## (a) Change scope / 本次改动范围

- Add #90 on preferred phase-12 tip #89: still-opt-in phase-13 `Z`/`B`/`C`/`S`
  field and invoke descriptors with the exact Boolean/Byte/Char/Short JNI
  families. Stack/local carriers stay `I32` with explicit widen/narrow. `F`/`D`
  still fall back and legacy remains the default. Its source records
  62 + 2 = 64 focused tests and an unskipped 87-`JNICALL` g++ smoke. It is
  partial and not ship-ready.
- Add #93, Sol's documentation-only **pass/accept** review of #90, with 64/64
  and no compiler change.
- Add #94, Fable's documentation-only **accept** review of #90, with 64/64 and
  no compiler change. Its sole non-blocking nit is that boolean *invoke
  arguments* are masked `& 1` while the JVM does not mask at the call site;
  this is unobservable for javac output and is a docs-only note, not a code
  change.
- Add #92, a measurement-only six-method admission report stacked on #89. A
  two-class Java 8 corpus recorded **5 IR / 1 fallback**; the one fallback is
  `AdmissionTarget.unsupported(I)I` at opcode 134 (`I2F`), and `<clinit>` is
  excluded. Its 5/6 (83.3%) is not production IR coverage or a speedup claim and
  changes no compiler or runtime code.
- Keep #53 unchanged in meaning: eval fell back on `USHR`, so its evaluator
  median remains `N/A`. Do not back-fill it from #59 or any later work.
- Keep #37 and #50 as full recoveries of all four methods from their valid live
  subjects; requirement 7 remains unmet.
- Preserve the lanes:
  - direct IR: #45 → #47 → #51 → #54 → #56 → #62 → #63/#64 → #66 →
    #70/#71 → #73 → #76/#77 → #78 → #82/#83 → #84 → #89/#88 → #90 →
    #93/#94, with #73 based only on preferred, fixed #70, #90 based on preferred
    #89, and #93/#94 as documentation-only reviews of #90;
  - evaluator: #42 → #44 → #48 → #50, #57/#61, #68/#69 → #85/#87;
  - benchmark: #34 → #53, with #59 stacked on #57; #92 measurement-only on #89;
  - SDK: #12 → #15 → #46 → #72 → #75 → #80 → #81, with #81 the preferred
    AES tip;
  - compatibility: #6 → #9 → #14 → #41;
  - options: … → #74 → #79 → #86 → #91 → this branch.
- Preserve the complete written engineering goal. Option A remains only the
  v1 product recommendation.
- Update only `docs/architecture/goal-status-and-options.md` and this bilingual
  PR body. PRs #1–#94 remain open drafts; `master` remains `e7ca4c8`.

- 新增叠加在首选 phase-12 tip #89 上的 #90：仍为 opt-in 的 phase-13
  `Z`/`B`/`C`/`S` 字段与 invoke descriptor 的精确 Boolean/Byte/Char/Short JNI
  family。stack/local carrier 保持 `I32` 并显式 widen/narrow，`F`/`D` 仍
  fallback，默认仍为 legacy。其来源记录 62 + 2 = 64 个聚焦测试及未跳过的
  87-`JNICALL` g++ 烟测；该阶段仍部分且未达上线就绪。
- 新增 #93，即 Sol 对 #90 的纯文档 **pass/accept** 审阅，记录 64/64 且无编译器
  改动。
- 新增 #94，即 Fable 对 #90 的纯文档 **accept** 审阅，记录 64/64 且无编译器
  改动。其唯一非阻塞 nit 是 boolean *invoke 参数* 被 `& 1` 掩码，而 JVM 不在
  调用点做掩码；这对 javac 输出不可观察，仅为文档说明，不是代码改动。
- 新增叠加在 #89 上、仅测量的六方法接纳率报告 #92。两个类的 Java 8 语料记录
  **5 IR / 1 fallback**，唯一 fallback 为 `AdmissionTarget.unsupported(I)I`，
  opcode 134（`I2F`），且 `<clinit>` 已排除。该 5/6（83.3%）不是生产 IR 覆盖率
  或加速结论，且不改编译器或运行时代码。
- 保持 #53 原意不变：eval 因 `USHR` fallback，故其 evaluator 中位数仍为
  `N/A`。不得用 #59 或任何后续工作回填。
- 保留 #37 与 #50：两者均从各自有效 live 样本完整恢复四个方法，因此
  requirement 7 仍未满足。
- 保持各路线：
  - direct IR：#45 → #47 → #51 → #54 → #56 → #62 → #63/#64 → #66 →
    #70/#71 → #73 → #76/#77 → #78 → #82/#83 → #84 → #89/#88 → #90 →
    #93/#94，其中 #73 仅基于首选且含修复的 #70，#90 基于首选 #89，
    #93/#94 是 #90 的纯文档审阅；
  - evaluator：#42 → #44 → #48 → #50、#57/#61、#68/#69 → #85/#87；
  - benchmark：#34 → #53，另有叠加在 #57 上的 #59；#92 为叠加在 #89 上的仅
    测量报告；
  - SDK：#12 → #15 → #46 → #72 → #75 → #80 → #81，其中 #81 为首选
    AES tip；
  - compatibility：#6 → #9 → #14 → #41；
  - options：… → #74 → #79 → #86 → #91 → 本分支。
- 保留完整书面工程目标；A 仅为 v1 产品建议。
- 仅更新 `docs/architecture/goal-status-and-options.md` 与本双语 PR body。
  PR #1–#94 仍均为 open draft；`master` 仍为 `e7ca4c8`。

## (b) Can this ship to production as-is? / 是否可直接上线

**No. / 否。**

All referenced work remains in draft PRs and `master` contains none of it. The
defaults remain legacy/direct. #90 extends still-opt-in phase 13 to
`Z`/`B`/`C`/`S` field and invoke families, but `F`/`D`, non-int arrays, `POP2`,
and invokedynamic still fall back and legacy remains default; it is partial and
not ship approval. #93's pass and #94's accept are documentation-only reviews of
#90 that change no compiler code and are not ship approval. #92 is a
measurement-only six-method 5/6 admission report on #89; it is not production IR
coverage and not ship approval. #53 still has no evaluator timing, and #37 and
#50 fully recovered all four methods from valid live subjects, so requirement 7
remains unmet.

所有相关工作仍在草稿 PR 中，`master` 未包含这些内容，默认值仍为 legacy/direct。
#90 将仍为 opt-in 的 phase 13 扩展至 `Z`/`B`/`C`/`S` 字段与 invoke，但 `F`/`D`、
非 int 数组、`POP2` 与 invokedynamic 仍 fallback，默认仍为 legacy；它仍部分，
不是上线批准。#93 的 pass 与 #94 的 accept 均为 #90 的纯文档审阅，不改编译器，
也不是上线批准。#92 是叠加在 #89 上、仅测量的六方法 5/6 接纳率报告，不代表生产
IR 覆盖率，也不是上线批准。#53 仍没有 evaluator timing，#37 与 #50 均从有效
live 样本完整恢复四个方法，因此 requirement 7 仍未满足。

## (c) Is review required? / 上线前是否需要 review

**Yes. / 是。**

Each implementation stack still requires independent code review, post-rebase
verification, supported-platform/JDK CI, native runtime-parity checks, and
applicable product/release approval. Phase-13 review must start from #90 on
preferred #89 and must not treat #93/#94 as compiler fixes. Keep the exact
Boolean/Byte/Char/Short JNI families (never Int), the constructor void path, and
the `legacy` default. The #92 admission measurement must stay scoped to its
six-method corpus; its 5/6 is not a coverage gate. Do not flip the default.

每条实现栈仍需独立代码审查、rebase 后复测、受支持平台/JDK CI、native runtime
parity 检查，以及适用的产品/发布审批。Phase-13 审阅必须从叠加在首选 #89 上的
#90 开始，且不得把 #93/#94 当作编译器修复。保留精确的 Boolean/Byte/Char/Short
JNI family（绝不用 Int）、构造器 void 路径与 `legacy` 默认值。#92 的 admission
测量必须限定在其六方法语料内，其 5/6 不是覆盖率门槛。不得翻转默认值。

## (d) Review preconditions / review 的前置条件

1. Use #90's `docs/architecture/ir-phase13-status.md` for the exact
   Boolean/Byte/Char/Short JNI families on `Z`/`B`/`C`/`S` field and invoke
   descriptors, the `I32` widen/narrow carriers, `F`/`D` fallback, legacy
   default, 62 + 2 = 64 tests, unskipped 87-`JNICALL` g++ smoke, and
   partial/not-ship-ready status. It is stacked on preferred #89.
2. Use #93's `docs/architecture/ir-phase13-review.md` only for its
   documentation-only **pass/accept** verdict, no compiler change, and 64/64
   result. It is not a compiler fix.
3. Use #94's `docs/architecture/ir-phase13-fable-review.md` only for its
   documentation-only **accept** verdict, no compiler change, 64/64 result, and
   the boolean invoke-argument `& 1` nit (unobservable for javac output). It is
   not a compiler fix.
4. Use #92's `docs/benchmarks/ir-admission-phase12.md` only for its
   measurement-only six-method **5 IR / 1 fallback** result on the #89 tip, the
   `AdmissionTarget.unsupported(I)I` fallback at opcode 134 (`I2F`), the
   excluded `<clinit>`, and no compiler/runtime change. Do not generalize its
   5/6 into production coverage.
5. Prefer #90 as the phase-13 implementation tip over its reviews #93/#94, keep
   #89 preferred over unfixed #84, #81 over unfixed #80, and #70 over unfixed
   #66.
6. Keep #53's evaluator median `N/A`; do not back-fill it. Keep #37/#50 as
   evidence that requirement 7 is unmet.
7. Keep the complete written goal and all listed lanes. Option A remains only
   the v1 product recommendation.

中文核对项：

1. 以 #90 的 `docs/architecture/ir-phase13-status.md` 为准：保留 `Z`/`B`/`C`/`S`
   字段与 invoke descriptor 的精确 Boolean/Byte/Char/Short JNI family、`I32`
   widen/narrow carrier、`F`/`D` fallback、legacy 默认值、62 + 2 = 64 个测试、
   未跳过的 87-`JNICALL` g++ 烟测，以及仍部分/未达上线就绪的状态；它叠加在
   首选 #89 上。
2. #93 仅以 `docs/architecture/ir-phase13-review.md` 为准：保留纯文档
   **pass/accept**、无编译器改动与 64/64；它不是编译器修复。
3. #94 仅以 `docs/architecture/ir-phase13-fable-review.md` 为准：保留纯文档
   **accept**、无编译器改动、64/64，以及 boolean invoke 参数的 `& 1` nit
   （对 javac 输出不可观察）；它不是编译器修复。
4. #92 仅以 `docs/benchmarks/ir-admission-phase12.md` 为准：保留其在 #89 tip 上
   仅测量的六方法 **5 IR / 1 fallback** 结果、`AdmissionTarget.unsupported(I)I`
   于 opcode 134（`I2F`）的 fallback、被排除的 `<clinit>`，以及不改编译器/
   运行时的边界。不得把其 5/6 外推为生产覆盖率。
5. 将 #90 作为 phase-13 实现 tip，优先于其审阅 #93/#94；保持 #89 优先于未修复
   #84、#81 优先于未修复 #80、#70 优先于未修复 #66。
6. 保持 #53 的 evaluator 中位数为 `N/A`，不得回填；保留 #37/#50 作为
   requirement 7 未满足的证据。
7. 保留完整书面目标与所有已列路线；A 仅为 v1 产品建议。

<!-- CURSOR_AGENT_PR_BODY_END -->
