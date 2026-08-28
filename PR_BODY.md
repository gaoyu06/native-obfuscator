# Independent IR shared-evaluator reading / IR 共享 evaluator 独立读取

This branch independently reconstructs the four JNI integer methods in the
stripped `published.so`, then scores the reconstruction against the published
jar and run record.  It implements neither packing nor a new backend.

本分支先从全剥离的 `published.so` 独立还原四个 JNI 整数方法，再用已发布 jar 与运行记录
评分。不实现 packing，也不实现新后端。

## (a) Change scope / 改动范围

- Add `recovery.md` with the evaluator format, per-method formulas/control
  flow, confidence, and binary evidence.
- Add `scores.md` with N=1, per-method full/partial/none scores, and the live
  evaluator validity finding.
- Recover `add`, `sumTo`, `subMul`, and both branches of `mix`; all four score
  **full**.
- Change documentation only; do not alter compiler/runtime code or artifacts.

- 新增 `recovery.md`，记录 evaluator 格式、逐方法公式/控制流、置信度及二进制证据。
- 新增 `scores.md`，记录 N=1、逐方法 full/partial/none 评分及 live evaluator
  有效性结论。
- 完整还原 `add`、`sumTo`、`subMul` 及 `mix` 的两层分支；四项均为 **full**。
- 仅修改文档，不改编译器/运行时代码及产物。

## (b) Can this ship to production as-is? / 是否可直接上线

**No.** This is an evaluation report, not a production compiler change.

**否。** 这是评估报告，不是生产编译器改动。

## (c) Is review required? / 是否需要 review

**Yes. Recovery-first ordering is confirmed.**  `recovery.md` was written,
committed, and pushed as commit `8bab3fb` before `published.jar` or `run.md`
was opened.  No prohibited source/oracle material was viewed before that
commit.

**是，并确认 recovery-first 顺序。** `recovery.md` 已先以提交 `8bab3fb` 写入、
提交并推送，之后才打开 `published.jar` 或 `run.md`；该提交前未查看任何禁止的
source/oracle 材料。

## (d) Review preconditions and evidence / Review 前置条件与证据

1. **PASS:** only `published.so` was examined for recovery, with `nm`,
   `readelf`, `objdump`, and `strings`.
2. **PASS:** all four JNI entries are evaluator trampolines with distinct blobs;
   the shared evaluator contains the matching live opcode handlers.
3. **PASS:** scores are `add=full`, `sumTo=full`, `subMul=full`, and
   `mix=full`; the published cases match each formula/control-flow result.
4. **PASS:** the subject is live (trampoline + executed blob), not DCE.
5. Review the exact IR decoding and Java `int` overflow/signed-comparison
   statements in `recovery.md`; no GitHub PR is created by this task.

1. **通过：** recovery 阶段仅用 `nm`、`readelf`、`objdump` 与 `strings`
   检查 `published.so`。
2. **通过：** 四个 JNI 入口均为带独立 blob 的 evaluator trampoline；共享 evaluator
   保留对应活跃 opcode handler。
3. **通过：** 评分为 `add=full`、`sumTo=full`、`subMul=full`、`mix=full`；
   已发布样例均与还原公式/控制流一致。
4. **通过：** 样本是活跃的 trampoline + 执行中 blob，而非 DCE。
5. 请重点 review `recovery.md` 的 IR 解码以及 Java `int` 溢出/有符号比较说明；
   本任务不创建 GitHub PR。
