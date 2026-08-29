# Current engineering goal / 当前工程目标

Recorded 2026-08-29 after an explicit maintainer direction:
the previous eight-requirement production write-up is **no longer** the
active goal. Historical measurements stay in
[project-status.md](project-status.md) and
[goal-status-and-options.md](goal-status-and-options.md).
They do not define what to work on next.

2026-08-29 维护者明确调整方向：原先八条生产目标**不再**是现行目标。
历史测量仍保留，但不决定下一步做什么。

## Active goal / 现行目标

1. **Move all method-body native codegen onto the typed CFG IR.**
   Bytecode → IR → C/C++ → compile. Stop using the legacy snippet /
   string-concatenation generator (`cppsnippets.properties`, `Snippets`,
   `GenericInstructionHandler`, and the `instructions/*` string emitters)
   for new work.
2. **Keep going until the legacy path can be deleted.**
   Every method that today’s tool would have sent through the snippet
   generator must be admitted and emitted through IR with **no**
   per-method legacy fallback. Then delete the legacy path so
   `--codegen=legacy` is no longer required.

1. **把所有方法体原生代码生成迁到 typed CFG IR。**
   字节码 → IR → C/C++ → 编译。新工作不要再走字符串拼接的 legacy 生成器。
2. **一直做到可以完整废弃 legacy。**
   今天会走 snippet 路径的方法都必须能被 IR 接纳并生成，不再逐方法
   fallback。然后删除 legacy，使 `--codegen=legacy` 不再必要。

This goal is **not** complete. `--codegen` still defaults to `legacy`.
Unsupported IR constructs still fall back per method.

本目标**尚未完成**。CLI 默认仍是 `legacy`。IR 不支持的构造仍会逐方法回退。

## Done means / 完成标准

The goal is complete only when all of the following are true:

- `--codegen=ir` can compile the methods the product intends to support
  without calling the snippet generator.
- The CLI/API default is `ir` (or the only remaining path is IR).
- `Snippets`, `GenericInstructionHandler`, `cppsnippets.properties`, and
  the leftover string-concat instruction handlers are gone from the
  production tree.
- A still-unsupported construct fails closed with a precise diagnostic
  instead of silently emitting snippet C++.

在此之前不要把本目标标成完成，也不要把接纳率或小语料 E2E 写成
“已经废弃 legacy”。

## Sequencing / 顺序

1. **Fill IR admission gaps** (current work). Known leftovers on
   `master` after #192 (proven extras through distinct path-id
   suffixes) include at least: remaining constructor-split rejects
   (non-identity prefix `ASTORE 0` / receiver-alias forwarding,
   unproven prefix→suffix jumps/switches, other mixed prefix/suffix
   try/catch placements beyond #171/#184/#187/#188, remaining
   multi-super shapes such as nested/`IDIV` computed inputs, branched
   suffixes, hybrid identical-plus-distinct suffix sets, or more than
   eight distinct paths, extras still unassigned on a bridge-taking
   path),
   remaining unsafe/unproven condy shapes (non-static, varargs,
   malformed, cyclic; stay reject-before-mutation), and `jsr` / `ret`
   (obsolete; reject is fine). In-tree ClassicTest / JDK fixture
   admission (#191 measurement on post-#190 `47e35fc`) observed no
   leftover methods; that is not a complete JVM inventory. #181 remains
   the earlier post-#180 snapshot.
2. **Do not flip `--codegen` off `legacy`** until those supported methods
   no longer need fallback. The default flip is reversible and comes
   *after* coverage, not before.
3. **Delete the legacy path** only after the default flip has soaked
   and no supported method still needs snippet emission.
   That deletion is now the approved destination (human decision D7),
   not an optional afterthought.

解释器（`--backend=interpreter`）和 evaluator（`--ir-lower=eval`）仍是
默认关闭的旁路。它们**不能**代替把方法体迁到 IR。不要为了扩解释器 ISA
而停下 IR 接纳缺口。

## How we work / 工作方式（2026-08-29）

- **Large increments with fast models.** Prefer Claude Opus 5 Fast
  (`claude-opus-5-thinking-high-fast`) for ordinary IR admission work.
  If that model is blocked on this repository, use another fast model
  (Sol-class fast is acceptable). Do not slice every leftover into a
  review-gated micro-PR.
- **Accept with real tests, not stacked code-only reviews.** The gate
  is `IrCompilerTest` + `CodegenModeTest` plus compile-and-run harnesses
  (g++ / JNI) that exercise the new bytecode. Do not open Fable/Sol
  review PRs whose only job is to re-read the diff.
- **Fable 5 is reserved.** Use it only for important product decisions
  and genuinely hard code: `invokedynamic` / ConstantDynamic / MethodHandle
  `LDC`, leftover constructor-split rejects, or a default-flip / legacy
  deletion decision. Do not use Fable 5 for routine opcode admission.

- **大增量、快模型。** 普通 IR 接纳优先 Opus 5 Fast；该模型不可用时换
  其他快模型。不要每个缺口都再叠一层纯代码审查。
- **用真实测试验收。** 门禁是聚焦测试加上会编译并跑起来的 harness，
  不是再开一个只读 diff 的审查 PR。
- **Fable 5 只用在难事上。** 重要决策和困难代码（indy / condy /
  构造器切分剩余拒绝、默认值翻转 / 删除 legacy）。例行接纳不要派 Fable。

## Policies that remain / 仍然有效的政策

- Do not invent benchmark numbers. Keep #53’s eval median as `N/A`.
- Do not publish “supports JDK 17/21/25” from admission counts or small
  fixture sets. Java 8 remains the only version this project has ever
  called fully supported.
- Do not claim a general native speedup versus HotSpot.
- Do not launch another encoding-tweak reader. The old requirement-7
  recovery bar is historical and unmet; it is not this goal.

## (a)(b)(c)(d)

- **(a) Scope / 范围:** IR-complete codegen, then retire legacy; process
  is fast-model increments gated by executed tests. /
  IR 覆盖完整后废弃 legacy；用快模型做大增量，用跑起来的测试验收。
- **(b) Ship-ready? / 可直接上线？** **No.** / **否。**
- **(c) Review / 是否需要审查？** No stacked code-only review.
  Confirm this page does not flip the CLI default. /
  不再叠纯代码审查。确认没有改掉默认值。
- **(d) Preconditions / 前置条件:** Cite only known leftover constructs;
  do not invent a full JVM coverage matrix. /
  只列出已知缺口，不要编造完整 JVM 覆盖表。
