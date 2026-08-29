<!-- CURSOR_AGENT_PR_BODY_BEGIN -->
## Summary / 摘要

Stacks the documentation-only maintainer brief on draft PR #74 and adds
#72/#73/#75/#76/#77 using only claims in their named branch documents. It
preserves the #66/#70/#71 phase-9 story, #53's `N/A` evaluator timing,
#37/#50's unmet requirement 7, and the complete written engineering goal. #70,
which contains the reviewed `jarray`-return compiler fix, remains the preferred
phase-9 tip and is #73's base.

在草稿 PR #74 的基础上，将仅文档的维护者简报加入 #72/#73/#75/#76/#77，
且仅采用其指定分支文档记录的声明。同时保留 #66/#70/#71 的 phase-9 叙事、
#53 的 evaluator timing `N/A`、#37/#50 对 requirement 7 未满足的结论，以及
完整书面工程目标。包含已审阅 `jarray` 返回编译器修复的 #70 仍是首选
phase-9 tip，也是 #73 的 base。

## (a) Change scope / 本次改动范围

- Add #66's still-opt-in direct-IR phase 9: `ARETURN`, `ACONST_NULL`,
  `IFNULL`/`IFNONNULL`, and category-one `POP`. `POP2` remains per-method
  fallback and legacy remains the default. Its source records 42 + 2 = 44
  focused tests and a 39-method g++ smoke. It is partial and not ship-ready.
- Add #70, Sol's phase-9 review and compiler fix. The review found that the
  coarsened `jobject` SSA carrier did not match an array-returning JNI
  function's `jarray` carrier. It adds an explicit cast at that boundary and
  an array-return regression. The post-fix verdict is **accept**, with
  43 + 2 = 45 focused tests and a 40-method g++ smoke. This is the preferred
  phase-9 tip, not a ship-readiness finding.
- Add #71, Fable's documentation-only **accept-with-nits** review of unfixed
  #66 at `32ac47d`. It records 44 tests and no compiler change. It does not
  include #70's `jarray` fix and is not ship-ready.
- Add #68, stacked on #57, with evaluator `LLOAD`, `LSTORE`, `LADD`, `LSUB`,
  `LMUL`, `LRETURN`, `I2L`, and `L2I` at opcodes `0x23`–`0x2a`. Its source
  records `(J)J` staying on eval, `LDIV`/`LREM` remaining fallback, 31/31
  focused tests, and no new benchmark numbers.
- Add #69, Sol's documentation-only **accept** review of #68, with no
  review-branch compiler change and 31/31 focused tests. The reviewed stack
  necessarily edits sibling direct-IR files because frontend i64 admission is
  shared across lowering strategies. This is not a ship-readiness finding.
- Add #72 on #46: Java `NativePrimitives.hmacSha256`, C ABI
  `no_sdk_hmac_sha256_v1`, and RFC 2104 HMAC built from the in-tree SHA-256.
  Its source records published vectors, a 13/13 module suite, and no HMAC
  benchmark. This is a review-stage API, not a shipped SDK.
- Add #75, Sol's documentation-only **PASS** review of #72. It records a 1/1
  focused test and 13/13 full suite, both with 0 failed and 0 skipped; the full
  suite comprises 8 generated fixtures, 2 `StringPoolTest`, 2
  `ClassMethodListTest`, and 1 SDK integration test. It also records a 4/4
  independent vector cross-check and 11/11 C ABI probe. Its generated-library
  verifier records 2 SHA-256 vectors, 4 HMAC vectors, 9 `MessageDigest` cases,
  5 equality cases, 5 string vectors, and 4 concatenations. No HMAC benchmark
  was run; this is not a shipped SDK.
- Add #73 on preferred #70: `GETFIELD`, `PUTFIELD`, `GETSTATIC`, and
  `PUTSTATIC` for exact `I`, exact `J`, and object/array descriptors. A null
  instance receiver takes the exceptional exit with a pending
  `NullPointerException`; `Z`/`B`/`C`/`S`/`F`/`D` remain fallback, and legacy
  remains default. It records 47 + 2 = 49 tests and a 50-method g++ smoke.
- Add #76, Fable's documentation-only **accept-with-nits** review of #73, and
  #77, Sol's documentation-only **PASS** review. Both record 49/49; neither is
  a ship-readiness finding.
- Keep #53 unchanged in meaning: eval fell back on `USHR`, so its evaluator
  median remains `N/A`. Do not back-fill it from #59, #68, or any later work.
- Keep #37 and #50 as full recoveries of all four methods from their valid
  live subjects; requirement 7 remains unmet.
- Preserve the lanes:
  - direct IR: #45 → #47 → #51 → #54 → #56 → #62 → #63/#64 → #66 →
    #70/#71 → #73 → #76/#77, with #73 based only on preferred, fixed #70;
  - evaluator: #42 → #44 → #48 → #50, #57/#61, #68/#69;
  - benchmark: #34 → #53, with #59 stacked on #57;
  - SDK: #12 → #15 → #46 → #72 → #75;
  - compatibility: #6 → #9 → #14 → #41;
  - options: … → #67 → #74 → this branch.
- Preserve the complete written engineering goal. Option A remains only the
  v1 product recommendation.
- Update only `docs/architecture/goal-status-and-options.md` and this bilingual
  PR body. PRs #1–#77 remain open drafts; `master` remains `e7ca4c8`.

- 新增 #66 的仍为 opt-in 的 direct-IR phase 9：`ARETURN`、`ACONST_NULL`、
  `IFNULL`/`IFNONNULL` 与 category-one `POP`。`POP2` 仍逐方法 fallback，
  默认仍为 legacy。其来源记录 42 + 2 = 44 个聚焦测试及 39-method g++ 烟测；
  该阶段仍部分且未达上线就绪。
- 新增 #70，即 Sol 的 phase-9 审阅与编译器修复。审阅发现粗化后的 `jobject`
  SSA carrier 与数组返回 JNI 函数的 `jarray` carrier 不匹配，并在该边界增加
  显式 cast 与数组返回回归。修复后结论为 **accept**，记录 43 + 2 = 45 个
  聚焦测试及 40-method g++ 烟测。它是首选 phase-9 tip，但不是上线就绪结论。
- 新增 #71，即 Fable 对未修复 #66 `32ac47d` 的纯文档
  **accept-with-nits** 审阅。它记录 44 个测试且未改编译器，不包含 #70 的
  `jarray` 修复，也未达上线就绪。
- 新增叠加在 #57 上的 #68：evaluator 在 opcode `0x23`–`0x2a` 加入
  `LLOAD`、`LSTORE`、`LADD`、`LSUB`、`LMUL`、`LRETURN`、`I2L` 与 `L2I`。
  来源记录 `(J)J` 保持在 eval、`LDIV`/`LREM` 仍 fallback、31/31 个聚焦测试
  通过，且不新增 benchmark 数字。
- 新增 #69，即 Sol 对 #68 的纯文档 **accept** 审阅；审阅分支未改编译器，
  记录 31/31。由于 frontend 的 i64 准入由各 lowering strategy 共享，被审阅栈
  必须同步修改 sibling direct-IR 文件；这不是隔离缺陷。该结论不是上线就绪声明。
- 新增叠加在 #46 上的 #72：Java `NativePrimitives.hmacSha256`、C ABI
  `no_sdk_hmac_sha256_v1`，以及使用仓内 SHA-256 的 RFC 2104 HMAC。其来源
  记录公开向量、13/13 module suite，且未运行 HMAC benchmark。这是 review-stage
  API，不是已交付 SDK。
- 新增 #75，即 Sol 对 #72 的纯文档 **PASS** 审阅。它记录 focused 1/1 与完整
  suite 13/13，二者均为 0 failed、0 skipped；完整 suite 包含 8 个 generated
  fixture、2 个 `StringPoolTest`、2 个 `ClassMethodListTest` 与 1 个 SDK
  integration test。它还记录独立向量核对 4/4 与 C ABI probe 11/11；
  generated-library verifier 记录 2 个 SHA-256 向量、4 个 HMAC 向量、9 个
  `MessageDigest` case、5 个 equality case、5 个 string vector 与 4 个
  concatenation。未运行 HMAC benchmark；这不是已交付 SDK。
- 新增叠加在首选 #70 上的 #73：为 exact `I`、exact `J` 与 object/array
  descriptor 增加 `GETFIELD`、`PUTFIELD`、`GETSTATIC`、`PUTSTATIC`。实例字段
  null receiver 留下 pending `NullPointerException` 并走异常出口；
  `Z`/`B`/`C`/`S`/`F`/`D` 仍 fallback，默认仍为 legacy。来源记录
  47 + 2 = 49 个测试及 50-method g++ 烟测。
- 新增 #76（Fable 对 #73 的纯文档 **accept-with-nits** 审阅）与 #77（Sol 的
  纯文档 **PASS** 审阅）。两者均记录 49/49，均不是上线就绪结论。
- 保持 #53 原意不变：eval 因 `USHR` fallback，故其 evaluator 中位数仍为
  `N/A`。不得用 #59、#68 或任何后续工作回填。
- 保留 #37 与 #50：两者均从各自有效 live 样本完整恢复四个方法，因此
  requirement 7 仍未满足。
- 保持各路线：
  - direct IR：#45 → #47 → #51 → #54 → #56 → #62 → #63/#64 → #66 →
    #70/#71 → #73 → #76/#77，其中 #73 仅基于首选且含修复的 #70；
  - evaluator：#42 → #44 → #48 → #50、#57/#61、#68/#69；
  - benchmark：#34 → #53，另有叠加在 #57 上的 #59；
  - SDK：#12 → #15 → #46 → #72 → #75；
  - compatibility：#6 → #9 → #14 → #41；
  - options：… → #67 → #74 → 本分支。
- 保留完整书面工程目标；A 仅为 v1 产品建议。
- 仅更新 `docs/architecture/goal-status-and-options.md` 与本双语 PR body。
  PR #1–#77 仍均为 open draft；`master` 仍为 `e7ca4c8`。

## (b) Can this ship to production as-is? / 是否可直接上线

**No. / 否。**

All referenced work remains in draft PRs and `master` contains none of it.
The defaults remain legacy/direct. #66 is partial; #70 fixes one correctness
bug and accepts the reviewed phase but is not production approval; #71 reviews
the unfixed tip. #73 is based on preferred #70 but remains partial, opt-in, and
legacy-default; #76/#77 are documentation reviews, not ship approval. #68
remains a narrow opt-in evaluator slice, and #69 is a scoped review rather than
release approval. #72/#75 remain a review-stage HMAC SDK surface/review, not a
shipped SDK. #53 still has no evaluator timing, and #68 adds no benchmark
numbers. #37 and #50 fully recovered all four methods from valid live subjects,
so requirement 7 remains unmet.

所有相关工作仍在草稿 PR 中，`master` 未包含这些内容，默认值仍为
legacy/direct。#66 仍部分；#70 修复一项正确性问题并接受其审阅范围，但不是生产
批准；#71 审阅的是未修复 tip。#73 基于首选 #70，但仍部分、opt-in 且默认
legacy；#76/#77 是文档审阅，不是上线批准。#68 仍是窄范围 opt-in evaluator
slice，#69 只是限定范围审阅而非发布批准。#72/#75 仍是 review-stage HMAC SDK
接口/审阅，而不是已交付 SDK。#53 仍没有 evaluator timing，#68 不新增 benchmark
数字。#37 与 #50 均从有效 live 样本完整恢复四个方法，因此 requirement 7 仍未满足。

## (c) Is review required? / 上线前是否需要 review

**Yes. / 是。**

Each implementation stack still requires independent code review, post-rebase
verification, supported-platform/JDK CI, native runtime-parity checks, and
applicable product/release approval. Review must retain #70's array-return fix
and must not treat #71 as evidence that the unfixed tip contains that fix.
Phase-10 review must start from #73 on #70, and HMAC still needs product and
security approval beyond #75's technical PASS.

每条实现栈仍需独立代码审查、rebase 后复测、受支持平台/JDK CI、native runtime
parity 检查，以及适用的产品/发布审批。审阅必须保留 #70 的数组返回修复，且不得
把 #71 当作未修复 tip 已包含该修复的证据。Phase-10 审阅必须从叠加在 #70 上的
#73 开始；HMAC 在 #75 的技术 PASS 之外仍需产品与安全审批。

## (d) Review preconditions / review 的前置条件

1. Use #66's `docs/architecture/ir-phase9-status.md` for its four named
   operation families, fallback/default boundaries, 42 + 2 = 44 tests,
   39-method g++ smoke, and partial/not-ship-ready status.
2. Use #70's `docs/architecture/ir-phase9-review.md` for the
   `jobject`/`jarray` mismatch, cast and regression fix, **accept** verdict,
   43 + 2 = 45 tests, 40-method smoke, and non-ship-readiness boundary. Treat
   #70 as the preferred phase-9 tip.
3. Use #71's `docs/architecture/ir-phase9-fable-review.md` only for its
   documentation-only **accept-with-nits** review of #66 at `32ac47d`, its
   44-test record, and its nits. It does not contain #70's fix.
4. Use #68's `docs/architecture/ir-evaluator-backend.md` and bilingual
   `PR_BODY.md` for the eight exact operations/opcodes, `(J)J` eval path,
   `LDIV`/`LREM` fallback, 31/31 result, and absence of benchmark numbers.
5. Use #69's `docs/architecture/ir-evaluator-i64-review.md` for **accept**,
   documentation-only review scope, no review-branch implementation change,
   31/31 result, and the shared-frontend/direct-IR integration explanation.
6. Use #72's bilingual `PR_BODY.md` and `docs/sdk/v1-status.md` for
   `NativePrimitives.hmacSha256`, `no_sdk_hmac_sha256_v1`, RFC 2104 with the
   in-tree SHA-256, published vectors, 13/13, and no HMAC benchmark.
7. Use #75's `docs/sdk/hmac-sha256-review.md` for its documentation-only
   **PASS**, non-shipped boundary, 1/1 focused run and 13/13 full suite
   (both 0 failed/0 skipped), full-suite breakdown of 8 generated fixtures +
   2 `StringPoolTest` + 2 `ClassMethodListTest` + 1 SDK integration test,
   4/4 independent vector check, 11/11 C ABI probe, and the verifier's
   2/4/9/5/5/4 SHA-256/HMAC/`MessageDigest`/equality/string/concatenation
   counts. It makes no HMAC performance claim.
8. Use #73's `docs/architecture/ir-phase10-status.md` for all four field
   opcodes on exact `I`, exact `J`, and object/array descriptors, pending-NPE
   null-receiver exits, six primitive-sort fallbacks, 47 + 2 = 49 tests,
   50-method g++ smoke, and legacy default. It is based on preferred #70.
9. Use #76's `docs/architecture/ir-phase10-fable-review.md` only for its
   documentation-only **accept-with-nits** and 49/49 result. Use #77's
   `docs/architecture/ir-phase10-review.md` only for its documentation-only
   **PASS** and 49/49 result. Neither is a ship-readiness finding.
10. Keep #53's evaluator median `N/A`; do not back-fill it. Keep #37/#50 as
   evidence that requirement 7 is unmet.
11. Keep the complete written goal and all listed lanes. Option A remains only
   the v1 product recommendation.

中文核对项：

1. 以 #66 的 `docs/architecture/ir-phase9-status.md` 为准：保留四类具名操作、
   fallback/default 边界、42 + 2 = 44 个测试、39-method g++ 烟测，以及仍部分/
   未达上线就绪的状态。
2. 以 #70 的 `docs/architecture/ir-phase9-review.md` 为准：保留
   `jobject`/`jarray` 不匹配、cast 与回归修复、**accept** 结论、
   43 + 2 = 45 个测试、40-method 烟测及非上线就绪边界；将 #70 作为首选
   phase-9 tip。
3. #71 仅以 `docs/architecture/ir-phase9-fable-review.md` 为准：它是对
   #66 `32ac47d` 的纯文档 **accept-with-nits** 审阅，记录 44 个测试及其 nits，
   不包含 #70 修复。
4. 以 #68 的 `docs/architecture/ir-evaluator-backend.md` 与双语 `PR_BODY.md`
   为准：保留八个精确操作/opcode、`(J)J` eval 路径、`LDIV`/`LREM` fallback、
   31/31 结果，以及不新增 benchmark 数字的边界。
5. 以 #69 的 `docs/architecture/ir-evaluator-i64-review.md` 为准：保留
   **accept**、纯文档审阅范围、审阅分支无实现改动、31/31 结果，以及
   shared-frontend/direct-IR 集成说明。
6. 以 #72 的双语 `PR_BODY.md` 与 `docs/sdk/v1-status.md` 为准：保留
   `NativePrimitives.hmacSha256`、`no_sdk_hmac_sha256_v1`、RFC 2104 与仓内
   SHA-256、公开向量、13/13，以及不运行 HMAC benchmark 的边界。
7. 以 #75 的 `docs/sdk/hmac-sha256-review.md` 为准：保留纯文档 **PASS**、
   非已交付 SDK 边界、focused 1/1 与完整 suite 13/13（二者均 0 failed/
   0 skipped）、完整 suite 构成 8 generated fixtures + 2 `StringPoolTest` +
   2 `ClassMethodListTest` + 1 SDK integration test、独立向量 4/4、C ABI probe
   11/11，以及 verifier 的 SHA-256/HMAC/`MessageDigest`/equality/string/
   concatenation 计数 2/4/9/5/5/4；不作 HMAC 性能声明。
8. 以 #73 的 `docs/architecture/ir-phase10-status.md` 为准：保留 exact `I`、
   exact `J`、object/array descriptor 的四种字段 opcode、pending-NPE 的
   null-receiver 异常出口、六种 primitive sort fallback、47 + 2 = 49 个测试、
   50-method g++ 烟测及 legacy 默认值；它基于首选 #70。
9. #76 仅以 `docs/architecture/ir-phase10-fable-review.md` 为准，保留纯文档
   **accept-with-nits** 与 49/49；#77 仅以
   `docs/architecture/ir-phase10-review.md` 为准，保留纯文档 **PASS** 与
   49/49。两者均不是上线就绪结论。
10. 保持 #53 的 evaluator 中位数为 `N/A`，不得回填；保留 #37/#50 作为
   requirement 7 未满足的证据。
11. 保留完整书面目标与所有已列路线；A 仅为 v1 产品建议。

<!-- CURSOR_AGENT_PR_BODY_END -->
