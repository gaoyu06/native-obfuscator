# docs: Fable review of #132 phase-19 bench

## English

### (a) Scope

Independent Fable review of PR #132 (`cursor/bench-ir-phase19-6d81` at
`71b76a3`, base `master` `76ebedd`), the docs-only re-measurement of the
three-mode benchmark (plain JVM, `--codegen=legacy`, `--codegen=ir`) after IR
phase 19 landed. This branch is docs-only: it adds
`docs/reviews/bench-ir-phase19-fable.md` and this `PR_BODY.md`. No compiler,
harness, CLI, interpreter, or SDK source is touched, and no benchmark was
re-run; the review examines the written evidence.

**Verdict: accept with nits.** Evidence recorded in the review document:

- The #132 diff touches only `docs/benchmarks/results-ir-vs-legacy-phase19.md`,
  the `docs/benchmarks/README.md` pointer, and the child branch's
  `PR_BODY.md`.
- All 18 statistics (median and mean for three kernels × three modes) were
  independently recomputed from the listed raw samples and match exactly. No
  arithmetic fix was needed.
- Checksums match across all three modes in the written tables.
- The IR-mode numbers are fresh, not copies of the pre-phase-19 #122-era
  record: every sample list and median differs, and the two
  previously-falling-back methods (`IntegerLoopKernel.run`, opcode 125
  `LUSHR`; `RecursionKernel.recurse`, opcode 131 `LXOR`) now admit —
  consistent with the phase-19 admission code present in `AsmToIr.java` on
  `76ebedd`.
- All four measured methods carry `// IR codegen:` marker evidence; no
  fallback or mixed row is labeled as an IR timing.
- The `#53` eval median remains `N/A` in `results-ir-eval-lower.md`
  (untouched); `--codegen` default remains `legacy`; no "native beats
  HotSpot" claim or JDK support badge was introduced; the production goal is
  not marked complete.

Nits (non-blocking, no new bench run required): the superseded
`results-ir-vs-legacy-master.md` still carries its old "Current-master" title
and could be renamed to "Pre-phase-19" in a follow-up; the cited audit
artifacts (`transpile-ir.log`, generated `.cpp` trees) are VM-local and
reproducible only via the recorded commands, not retrievable from the
repository.

### (b) Ship-ready?

**No.** Both #132 and this review are documentation of a single-VM diagnostic
run. Nothing here is a release gate, a portable speedup claim, or a
production-readiness statement.

### (c) Review required?

This branch *is* the independent review of #132. Its own merge needs only an
ordinary docs review (confirm the recomputation table and the diff scope).

### (d) Preconditions

- The production goal remains incomplete; requirement 7 (resisting unaided
  Sol-class recovery) remains unmet.
- Keep `--codegen` default at `legacy`.
- Cite post-phase-19 numbers only from
  `docs/benchmarks/results-ir-vs-legacy-phase19.md`.
- Do not back-fill the `#53` eval median (`N/A`).

---

## 中文

### (a) 范围

对 PR #132（`cursor/bench-ir-phase19-6d81`，提交 `71b76a3`，基线 `master`
`76ebedd`）的独立 Fable 评审。#132 是 IR 第 19 阶段合入后对三模式基准
（纯 JVM、`--codegen=legacy`、`--codegen=ir`）的纯文档重测记录。本分支同样
仅改文档：新增 `docs/reviews/bench-ir-phase19-fable.md` 与本 `PR_BODY.md`。
未触碰编译器、基准脚本、CLI、解释器或 SDK 源码，也未重新运行基准；评审
基于书面证据。

**结论：接受，附非阻塞性 nit。** 证据要点：

- #132 的 diff 仅涉及第 19 阶段结果文件、基准 README 指针和子分支的
  `PR_BODY.md`；
- 全部 18 个统计量（三内核 × 三模式的中位数与均值）已从原始样本独立重算，
  全部精确一致，无需修正任何算术；
- 三种模式的校验和在表格中全部一致；
- IR 模式数字为本次实测，并非复制第 19 阶段之前的记录：所有样本序列与
  中位数均不同，且此前回退的两个方法（`IntegerLoopKernel.run` 的操作码
  125 `LUSHR`、`RecursionKernel.recurse` 的操作码 131 `LXOR`）现已准入，
  与 `76ebedd` 上 `AsmToIr.java` 的第 19 阶段准入代码一致；
- 四个被测方法均有 `// IR codegen:` 标记证据，没有把回退或混合行标注为
  IR 计时；
- #53 的 eval 中位数在未被触碰的 `results-ir-eval-lower.md` 中保持
  `N/A`；`--codegen` 默认值仍为 `legacy`；未引入"原生普遍快于 HotSpot"
  或 JDK 支持徽章；未宣布生产目标完成。

Nit（非阻塞，无需重跑基准）：被取代的 `results-ir-vs-legacy-master.md`
仍沿用旧标题"Current-master"，可在后续文档提交中改为"Pre-phase-19"；审计
引用的产物（`transpile-ir.log`、生成的 `.cpp` 树）仅存在于测量虚拟机上，
只能按记录的命令复现，无法从仓库直接取回。

### (b) 可以直接上线吗？

**否。** #132 与本评审都只是单虚拟机诊断运行的文档记录，不构成发布门槛、
可移植加速结论或生产就绪声明。

### (c) 需要评审吗？

本分支即是对 #132 的独立评审；其自身合入仅需常规文档评审（核对重算表与
diff 范围）。

### (d) 前置条件

- 生产目标仍未完成；需求 7（抵御无辅助的 Sol 级恢复）仍未满足。
- `--codegen` 默认值保持 `legacy`。
- 第 19 阶段之后的数字只引用
  `docs/benchmarks/results-ir-vs-legacy-phase19.md`。
- 不得回填 #53 的 eval 中位数（保持 `N/A`）。
