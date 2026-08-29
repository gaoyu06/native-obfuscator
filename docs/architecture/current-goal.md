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
   `master` after phase 20 / #146 include at least:
   `LCMP` (in flight as an IR increment), `IF_ACMPEQ` / `IF_ACMPNE`,
   `invokedynamic` and `LDC` of MethodHandle / MethodType / ConstantDynamic,
   `monitorenter` / `monitorexit` / synchronized methods,
   remaining constructor-split rejects (prefix branch into suffix,
   multiple this/super, try/catch across the split, prefix `ASTORE` of
   forwarded refs), and `jsr` / `ret`.
   This list is not a complete JVM inventory.
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

## Policies that remain / 仍然有效的政策

- Do not invent benchmark numbers. Keep #53’s eval median as `N/A`.
- Do not publish “supports JDK 17/21/25” from admission counts or small
  fixture sets. Java 8 remains the only version this project has ever
  called fully supported.
- Do not claim a general native speedup versus HotSpot.
- Do not launch another encoding-tweak reader. The old requirement-7
  recovery bar is historical and unmet; it is not this goal.

## (a)(b)(c)(d)

- **(a) Scope / 范围:** Replace the active engineering goal with
  IR-complete codegen and legacy retirement. / 把现行目标改成 IR 覆盖完整并废弃 legacy。
- **(b) Ship-ready? / 可直接上线？** **No.** / **否。**
- **(c) Review / 是否需要审查？** Yes — confirm this page does not
  flip the CLI default or claim legacy is already gone. /
  是，确认没有把默认值改掉，也没有写成 legacy 已经删除。
- **(d) Preconditions / 前置条件:** Cite only known leftover constructs;
  do not invent a full JVM coverage matrix. /
  只列出已知缺口，不要编造完整 JVM 覆盖表。
