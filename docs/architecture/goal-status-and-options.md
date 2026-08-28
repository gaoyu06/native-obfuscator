# Goal status and human options / 目标状态与人工选项

## Executive status

This is a maintainer snapshot of `origin/master` at `e7ca4c8` and the pull
requests returned by `gh pr list --state all --limit 100` on 2026-08-28. PRs
#1–#65 are all open drafts. `master` is unchanged from the preceding brief and
contains none of their code or documentation. Results below are evidence
recorded on the named branch, not invented merge, review, or CI results.

PRs [#1](https://github.com/gaoyu06/native-obfuscator/pull/1) and
[#2](https://github.com/gaoyu06/native-obfuscator/pull/2) are Gemini research
inputs, not authorities. Only claims independently accepted or revised by the
Sol review in [#3](https://github.com/gaoyu06/native-obfuscator/pull/3)
(`docs/architecture/gemini-review-notes.md`) are used here.

## 双语维护者简报 / Bilingual maintainer brief

### (a) 本次改动范围 / Change scope

本次仅更新文档至新草稿：纳入 [#44](https://github.com/gaoyu06/native-obfuscator/pull/44)
对 evaluator #42 的 “accept with nits” 审阅、
[#45](https://github.com/gaoyu06/native-obfuscator/pull/45) 对 phase 5 #40 的
Fable “accept with nits” 审阅、[#46](https://github.com/gaoyu06/native-obfuscator/pull/46)
干净叠在 SDK v1 #12 上的 `NativeStrings`、[#47](https://github.com/gaoyu06/native-obfuscator/pull/47)
仍为 opt-in 的 switch 与 `ANEWARRAY` phase 6，以及
[#51](https://github.com/gaoyu06/native-obfuscator/pull/51) 在 Fable policy-block
后进行的 Sol phase-6 审阅。#51 的记录结论为 **accept with nits**，并修复了
数组组件 `ANEWARRAY`，使其以数组 descriptor 调用 `FindClass`。此外纳入
[#48](https://github.com/gaoyu06/native-obfuscator/pull/48) 的 live
`--ir-lower=eval` stripped `.so` artifact，以及
[#50](https://github.com/gaoyu06/native-obfuscator/pull/50) 对该 artifact
先恢复、后评分的 blinded reader：`add`、`sumTo`、`subMul`、`mix` 均为
**full**。保留 [#37](https://github.com/gaoyu06/native-obfuscator/pull/37)
对有效 live direct-IR stripped `.so` 的相同四方法完整恢复结论；本文不合并或
实现任何草稿。此外，[#53](https://github.com/gaoyu06/native-obfuscator/pull/53)
在 `IrFriendlyIntKernel.run(I)I` 上记录 JVM、legacy、direct IR 与 eval-lower
选择；direct IR 保持在 IR 路径，eval 因 `USHR` 回退到 legacy，故不声称 eval
timing。[#54](https://github.com/gaoyu06/native-obfuscator/pull/54) 为仍然 opt-in
的 IR phase 7 增加 `CHECKCAST`、`INSTANCEOF` 与部分两槽 `I64` 算术；其状态
文档声称 33 个 `IrCompilerTest` 加 2 个 `CodegenModeTest`。
[#56](https://github.com/gaoyu06/native-obfuscator/pull/56) 是 Sol 对 #54 的
纯文档审阅，记录结论 **accept**，并记录重新运行 35/35 个聚焦测试。独立的
[#57](https://github.com/gaoyu06/native-obfuscator/pull/57) 从 #44 扩展
evaluator ISA，加入 `IAND`、`IOR`、`IXOR`、`ISHL`、`ISHR` 与 `IUSHR`，使
`IrFriendlyIntKernel` 的等价整数操作流可保持在 eval 路径；其双语记录声称
28/28 个聚焦测试通过，并明确不新增 benchmark timing。在其上叠加的
[#59](https://github.com/gaoyu06/native-obfuscator/pull/59) 是独立的后续本地
诊断测量：每个进程 5 次 warmup、10 次记录样本，全部 JVM/native 运行的
checksum 均为 2,038,221,507；记录的 JVM、legacy、direct IR、evaluator IR
中位数依次为 10,017,146.0 ns、167,870,311.5 ns、10,021,957.0 ns 与
411,875,537.5 ns。目标方法存在 evaluator-data marker，且没有目标方法或
`IUSHR` fallback。该测量不是可移植加速结论，也不回填 #53：#53 的 eval
中位数仍为 `N/A`。[#61](https://github.com/gaoyu06/native-obfuscator/pull/61)
是 Sol 对 #57 evaluator IUSHR ISA 的纯文档审阅，记录结论 **accept**、未改
编译器及 28/28 个聚焦测试通过；该结论不是上线就绪声明。
[#62](https://github.com/gaoyu06/native-obfuscator/pull/62) 叠加在 #56 上，
将仍为 opt-in 的 IR phase 8 扩展至通过 `AllocObject` 支持 `NEW`、通过
`CallNonvirtualVoidMethod` 支持仅限构造器的 `INVOKESPECIAL`，并扩展
`I`/`J`/引用 invoke 形状。构造器方法体仍排除，默认仍为 legacy；其状态记录
36 + 2 = 38 个聚焦测试及包含 34 个方法的 g++ 烟测。该阶段仍不完整且未达
上线就绪。[#63](https://github.com/gaoyu06/native-obfuscator/pull/63) 是 Fable
对 #62 的纯文档审阅，记录结论 **accept**、无编译器改动及 38/38 个聚焦测试；
其唯一非阻塞观察是构造器 receiver 的 null check 在已分配且检查过的对象路径上
永不触发。[#64](https://github.com/gaoyu06/native-obfuscator/pull/64) 是 Sol 对
#62 的纯文档审阅，记录结论 **accept**、无编译器改动、38/38 个聚焦测试及
34-method g++ 语法检查。两项审阅均不构成上线就绪声明。

This documentation-only update carries the brief through #44's
accept-with-nits review of evaluator #42, #45's Fable accept-with-nits review
of phase 5 #40, #46's clean `NativeStrings` stack on SDK v1 #12, #47's still
opt-in switch and `ANEWARRAY` phase 6, and #51's Sol phase-6 review after the
Fable policy block. #51 records an **accept with nits** verdict and fixes
array-component `ANEWARRAY` to call `FindClass` with the array descriptor. It
also carries #48's live
`--ir-lower=eval` stripped-`.so` artifact. #50 is the recovery-first blinded
reader on that artifact; `add`, `sumTo`, `subMul`, and `mix` all scored
**full**. It retains #37's full recovery of the same four methods from a valid
live direct-IR stripped `.so`. #53 records JVM, legacy, direct IR, and
eval-lower selection on `IrFriendlyIntKernel.run(I)I`: direct IR stayed on IR,
while eval fell back to legacy on `USHR`, so no eval timing is claimed. #54
adds `CHECKCAST`, `INSTANCEOF`, and a two-slot `I64` arithmetic slice to the
still-opt-in IR phase 7; its status document claims 33 `IrCompilerTest` plus
2 `CodegenModeTest`. #56 is Sol's documentation-only review of #54; it records
an **accept** verdict and a 35/35 focused-test rerun. Separately, #57 extends
the evaluator ISA from #44 with `IAND`, `IOR`, `IXOR`, `ISHL`, `ISHR`, and
`IUSHR`, allowing an `IrFriendlyIntKernel`-equivalent integer stream to stay
on eval. Its bilingual record claims 28/28 focused tests and explicitly adds
no benchmark timings. Stacked on #57, [#59](https://github.com/gaoyu06/native-obfuscator/pull/59)
is a separate follow-up local diagnostic: every process used 5 warmups and 10
recorded samples, every JVM/native run returned checksum 2,038,221,507, and
the recorded JVM, legacy, direct-IR, and evaluator-IR medians were respectively
10,017,146.0 ns, 167,870,311.5 ns, 10,021,957.0 ns, and 411,875,537.5 ns. The
target method had an evaluator-data marker and no target-method or `IUSHR`
fallback. This is not a portable speedup claim and does not back-fill #53:
#53's eval median remains `N/A`. [#61](https://github.com/gaoyu06/native-obfuscator/pull/61)
is Sol's documentation-only review of #57's evaluator IUSHR ISA; it records an
**accept** verdict, no compiler change, and 28/28 focused tests. It is not a
ship-readiness finding. [#62](https://github.com/gaoyu06/native-obfuscator/pull/62),
stacked on #56, extends still-opt-in IR phase 8 with `NEW` via `AllocObject`,
constructor-only `INVOKESPECIAL` via `CallNonvirtualVoidMethod`, and broader
`I`/`J`/reference invoke shapes. Constructor bodies remain excluded and legacy
remains the default. Its status records 36 + 2 = 38 focused tests and a
34-method g++ smoke translation unit. Phase 8 remains partial and not
ship-ready. [#63](https://github.com/gaoyu06/native-obfuscator/pull/63) is
Fable's documentation-only review of #62; it records an **accept** verdict,
no compiler change, and 38/38 focused tests. Its sole non-blocking observation
is a constructor-receiver null check that is never taken for the already
allocated and checked object path. [#64](https://github.com/gaoyu06/native-obfuscator/pull/64)
is Sol's documentation-only review of #62; it records an **accept** verdict,
no compiler change, 38/38 focused tests, and a 34-method g++ syntax check.
Neither review is a ship-readiness finding. This brief neither merges nor
implements any draft.

### (b) 是否可直接上线 / Can this ship to production as-is?

**No / 否。** PRs #1–#65 均为草稿，`master` 未包含这些能力；默认 codegen
仍是 legacy。#56 的 accept 审阅不把 #54 的不完整 opt-in phase 7 变成上线
批准；#61 对 #57 的 accept 审阅同样不是上线批准；#57 仍是窄范围、opt-in
且逐方法 fallback 的 evaluator lowering。#62 仍是部分、opt-in 且逐方法
fallback 的 phase 8，构造器方法体仍排除；#63 与 #64 对 #62 的 accept
审阅也都明确不是上线就绪声明。#46
、#53 与 #59 的本地测量均不是可移植性能结论；#53 的 eval timing 仍为
`N/A`，#57 本身不含新 benchmark 数字，#59 只是叠加在 #57 上的独立诊断。
#37 与 #50 分别从有效 live direct-IR 与 shared-evaluator stripped `.so`
完整恢复了四个方法，因此 requirement 7 并未满足。

**No.** PRs #1–#65 remain drafts and `master` has none of these capabilities;
the default codegen remains legacy. #56's accept review does not turn #54's
incomplete opt-in phase 7 into ship approval, and #61's accept review of #57
is likewise not ship approval. #57 remains a narrow, opt-in evaluator lowering
with per-method fallback. #62 remains a partial, opt-in phase 8 with per-method
fallback and constructor bodies still excluded; #63's and #64's accept reviews
of #62 are also explicitly not ship-readiness findings. The local measurements in
#46, #53, and #59 are not portable performance claims; #53's eval timing
remains `N/A`, #57 itself contains no new benchmark numbers, and #59 is a
separate diagnostic stacked on #57. #37 and #50 respectively recovered all
four methods from valid live direct-IR and shared-evaluator stripped `.so`
subjects, so requirement 7 is not met.

### (c) 上线前是否需要 review / Is review required?

**Yes / 是。** 每个实现 PR 均需独立代码审查、rebase 后复测及适用的产品/发布
审批。本文中的 benchmark 与 reader 结论也必须按其记录的 kernel、artifact
和方法边界审查，不能外推。

**Yes.** Each implementation PR still needs independent code review, post-rebase
verification, and applicable product/release approval. Benchmark and reader
claims must also be reviewed within their recorded kernel, artifact, and method
boundaries rather than generalized.

### (d) review 的前置条件 / Review preconditions

1. 继续以 #34–#42 的对应记录为其事实来源；新增结论仅引用 #44 的
   `docs/architecture/ir-evaluator-review.md`、#45 的
   `docs/architecture/ir-phase5-fable-review.md`、#46 的
   `docs/sdk/v1-status.md`、#47 的
   `docs/architecture/ir-phase6-status.md`、#48 的
   `docs/eval/ir-eval-lower/run.md` 与 `liveness.md`，以及 #50 的
   `docs/eval/ir-eval-lower/recovery.md` 与 `scores.md`，再加 #51 的
   `docs/architecture/ir-phase6-review.md` 与更新后的
   `docs/architecture/ir-phase6-status.md`，以及 #53 的
   `docs/benchmarks/results-ir-eval-lower.md`、#54 的
   `docs/architecture/ir-phase7-status.md`、#56 的
   `docs/architecture/ir-phase7-review.md`，以及 #57 的
   `docs/architecture/ir-evaluator-backend.md` 与双语 `PR_BODY.md`，再加
   #59 的 `docs/benchmarks/results-ir-eval-ushr.md` 与双语 `PR_BODY.md`、
   #61 的 `docs/architecture/ir-evaluator-ushr-review.md`、#62 的
   `docs/architecture/ir-phase8-status.md` 与双语 `PR_BODY.md`、#63 的
   `docs/architecture/ir-phase8-fable-review.md`，以及 #64 的
   `docs/architecture/ir-phase8-review.md`。
   Continue to use the #34–#42 records for their claims, and use only those
   named branch documents for the new #44–#64 claims.
2. #53 仅对 `IrFriendlyIntKernel.run(I)I` 记录本地中位数：JVM
   12,207,144.5 ns、legacy 202,090,247.0 ns、direct IR 11,311,481.5 ns。
   direct IR 保持在 IR 路径；eval 因 `USHR` 回退到 legacy，eval 中位数为
   `N/A`，不得引用 fallback observation 作为 eval timing，也不得把任何本地
   数值视为可移植性能结论。 #53 records local medians only for that method:
   12,207,144.5 ns for JVM, 202,090,247.0 ns for legacy, and 11,311,481.5 ns
   for direct IR. Direct IR stayed on IR; eval fell back to legacy on `USHR`,
   its eval median is `N/A`, and no fallback observation may be cited as an
   eval timing or any local value treated as portable.
3. #59 是叠加在 #57 上、与 #53 分开的后续本地诊断。每个进程记录 5 次
   warmup 与 10 次测量，全部运行的 checksum 为 2,038,221,507；JVM、legacy、
   direct IR、evaluator IR 的中位数依次为 10,017,146.0 ns、
   167,870,311.5 ns、10,021,957.0 ns 与 411,875,537.5 ns。目标方法的
   evaluator-data marker 存在，且没有目标方法或 `IUSHR` fallback。不得把该
   单次本地诊断变成可移植或加速声明，也不得用其回填 #53。 #59 is a
   follow-up local diagnostic stacked on #57 and separate from #53. Each
   process recorded 5 warmups and 10 measurements, all runs returned checksum
   2,038,221,507, and the JVM, legacy, direct-IR, and evaluator-IR medians were
   respectively 10,017,146.0 ns, 167,870,311.5 ns, 10,021,957.0 ns, and
   411,875,537.5 ns. The target method had its evaluator-data marker and no
   target-method or `IUSHR` fallback. Do not generalize this one local
   diagnostic into a portable result or speedup, and do not back-fill #53.
4. #31 的 `mix` 被 DCE，仍不能计入 reader bar；#37 与 #50 分别读取有效存活的
   direct-IR 与 shared-evaluator artifact，均先提交恢复、再对 oracle 评分，并均
   报告四个方法为 full，因此 requirement 7 未满足。 #31 remains invalid;
   #37 and #50 respectively read valid live direct-IR and shared-evaluator
   artifacts, committed recovery before oracle scoring, and report all four
   methods as full. Requirement 7 is not met.
5. #61 仅记录对 #57 的 **accept** 技术审阅、纯文档范围、无编译器改动及
   28/28 个聚焦测试；它不构成上线就绪结论。#62 叠加在 #56 上，仅记录
   opt-in phase 8 的 `NEW`/`AllocObject`、仅限构造器的 `INVOKESPECIAL`/
   `CallNonvirtualVoidMethod` 与更广的 `I`/`J`/引用 invoke 形状；构造器方法体
   仍排除，默认仍为 legacy。其聚焦测试记录为 36 + 2 = 38，g++ 烟测翻译单元
   包含 34 个方法；该阶段仍部分且未达上线就绪。 #61 records only an
   **accept** technical review of #57, a documentation-only scope, no compiler
   change, and 28/28 focused tests; it is not a ship-readiness finding. #62 is
   stacked on #56 and records only still-opt-in phase-8 `NEW`/`AllocObject`,
   constructor-only `INVOKESPECIAL`/`CallNonvirtualVoidMethod`, and broader
   `I`/`J`/reference invoke shapes. Constructor bodies remain excluded and
   legacy remains the default. Its focused result is 36 + 2 = 38 tests and its
   g++ smoke translation unit contains 34 methods; the phase remains partial
   and not ship-ready. #63 is Fable's documentation-only review of #62 and
   records **accept**, no compiler change, and 38/38 focused tests; its
   non-blocking observation is a never-taken receiver null check on the
   constructor path. #64 is Sol's documentation-only review of #62 and records
   **accept**, no compiler change, 38/38 focused tests, and a 34-method g++
   syntax check. Neither review is a ship-readiness finding. #63 是 Fable 对
   #62 的纯文档审阅，记录 **accept**、无编译器改动及 38/38 个聚焦测试；其
   非阻塞观察为构造器路径上永不触发的 receiver null check。#64 是 Sol 对
   #62 的纯文档审阅，记录 **accept**、无编译器改动、38/38 个聚焦测试及
   34-method g++ 语法检查。两者均不是上线就绪结论。
6. 选项 A 仍是先前简报对 v1 **产品范围**的建议，不是缩小书面工程目标的建议；
   下一工程方向不是继续调整 encoding，而是设计不会把源算法直线、可读地降为
   native code 或可解码 evaluator blob 的 lowering；#45 → #47 → #51 →
   #54 → #56 → #62 → #63/#64（#62 的审阅）的 direct-IR coverage/review、
   #34 → #53 的 benchmark 与叠加
   在 #57 上的独立 #59 后续测量、#42 → #44 → #48 → #50 的独立 evaluator
   实验、其 #57 ISA sibling 及对 #57 的 #61 审阅、SDK #12 → #15 → #46、
   compatibility #6 → #9 → #14 → #41，以及 options brief … → #60 → #65 →
   本 PR 分别继续。
   Option A remains the prior v1 **product** recommendation, not a recommendation
   to shrink the written goal; the next lowering must avoid straight-line
   readable native output of the source algorithm and decodable evaluator
   blobs. The #45 → #47 → #51 → #54 → #56 → #62 → #63/#64 direct-IR
   coverage/review lane, with #63/#64 reviewing #62,
   the #34 → #53 benchmark lane plus separate #59 follow-up stacked on #57,
   the #42 → #44 → #48 → #50 evaluator experiment with #57 as an ISA sibling
   and #61 reviewing #57,
   SDK #12 → #15 → #46, compatibility #6 → #9 → #14 → #41, and options briefs
   … → #60 → #65 → this PR continue as separate lanes.

| Area | Done on a draft branch | In flight | Not started or not evidenced |
|---|---|---|---|
| IR | Fable's typed-CFG/structured-C++ design is documented in [#5](https://github.com/gaoyu06/native-obfuscator/pull/5). The opt-in direct-IR implementation runs through phase 5 in [#40](https://github.com/gaoyu06/native-obfuscator/pull/40); [#45](https://github.com/gaoyu06/native-obfuscator/pull/45) is Fable's docs-only **accept with nits** review of that phase and changes no compiler code. [#44](https://github.com/gaoyu06/native-obfuscator/pull/44) separately records an **accept with nits** review of evaluator [#42](https://github.com/gaoyu06/native-obfuscator/pull/42), with no compiler change. | [#47](https://github.com/gaoyu06/native-obfuscator/pull/47), [#51](https://github.com/gaoyu06/native-obfuscator/pull/51), [#54](https://github.com/gaoyu06/native-obfuscator/pull/54), and [#56](https://github.com/gaoyu06/native-obfuscator/pull/56) form the still-opt-in phase-6/7 coverage and review path. [#62](https://github.com/gaoyu06/native-obfuscator/pull/62), stacked on #56, adds phase-8 `NEW`/`AllocObject`, constructor-only `INVOKESPECIAL`/`CallNonvirtualVoidMethod`, and broader `I`/`J`/reference invoke shapes; constructor bodies remain excluded, default legacy remains, and its record claims 38 focused tests plus a 34-method g++ smoke. [#63](https://github.com/gaoyu06/native-obfuscator/pull/63) and [#64](https://github.com/gaoyu06/native-obfuscator/pull/64) are documentation-only **accept** reviews of #62 with no compiler changes and 38/38 focused tests; #63 records one non-blocking never-taken constructor-receiver null-check observation, while #64 records a 34-method g++ syntax check. The separate evaluator experiment #42 → #44 → [#48](https://github.com/gaoyu06/native-obfuscator/pull/48) → [#50](https://github.com/gaoyu06/native-obfuscator/pull/50) publishes a valid live stripped `--ir-lower=eval` `.so`, then records all four methods as full. [#57](https://github.com/gaoyu06/native-obfuscator/pull/57) is an ISA sibling from #44 with six named bitwise/shift operations and no benchmark timings; [#61](https://github.com/gaoyu06/native-obfuscator/pull/61) is its docs-only **accept** review with no compiler change and a 28/28 rerun. None of #61–#64 establishes ship-readiness. | Full JVM semantics and parity remain incomplete, including broad descriptors/wide values, monitors, broader object construction, most primitive and multidimensional array allocation, complete invokes and exceptions, reference lifetime, class initialization, native-JAR differential E2E, and any reviewed default switch. #50 shows that the shared-evaluator lowering does not meet requirement 7 on this subject. |
| JDK compatibility | [#6](https://github.com/gaoyu06/native-obfuscator/pull/6) restores actual JUnit execution and adds JDK 17 behavioral fixtures. The stacked fix [#9](https://github.com/gaoyu06/native-obfuscator/pull/9) preserves modern class versions and accepts `TypeDescriptor` for record bootstrap rewriting; its Sol-verified run recorded 16 pass, 1 `krak2` skip, 0 fail. [#14](https://github.com/gaoyu06/native-obfuscator/pull/14) records all three new JDK 21 fixtures passing on the three harness modes, with 19 pass, 1 pre-existing skip, 0 fail. | [#41](https://github.com/gaoyu06/native-obfuscator/pull/41), stacked on #14, adds four ClassicTest fixtures compiled independently with `javac --release 25` (class-file major 69). Its status document records 24 total: 23 passed, 1 pre-existing `krak2` skip, 0 failed; each new fixture reached `OK` on `HOTSPOT`, `STD_JAVA`, and `ANDROID`. The full #6 → #9 → #14 → #41 stack remains draft. | #41 is not a blanket full-JDK-25 claim: it does not cover every language feature, library API, runtime mode, generated class shape, preview feature, or separate JDK 22–24 class file. `ConstantDynamic`, multi-release JARs, hidden classes, preview policy, virtual-thread behavior, and device-level Android evidence remain gaps. |
| Benchmarks | [#10](https://github.com/gaoyu06/native-obfuscator/pull/10) adds a checksum-gated plain-HotSpot versus current transpiled-JNI harness with raw samples and environment data. [#11](https://github.com/gaoyu06/native-obfuscator/pull/11) removes repeated warm instance-member lookup work; its one-run deltas are explicitly mixed. [#34](https://github.com/gaoyu06/native-obfuscator/pull/34) runs JVM, legacy, and IR tasks through the same harness. | [#53](https://github.com/gaoyu06/native-obfuscator/pull/53), stacked on #34, records `IrFriendlyIntKernel.run(I)I` local medians of 12,207,144.5 ns for JVM, 202,090,247.0 ns for legacy, and 11,311,481.5 ns for direct IR. Direct IR stayed on IR. Eval rejected `USHR` and used legacy fallback, so the evaluator median is `N/A` and no eval timing is claimed. Separately, [#59](https://github.com/gaoyu06/native-obfuscator/pull/59), stacked on #57, records 5/10 warmup/iterations, checksum 2,038,221,507, and JVM/legacy/direct-IR/evaluator-IR medians of 10,017,146.0 / 167,870,311.5 / 10,021,957.0 / 411,875,537.5 ns. Its target evaluator-data marker was present with no target-method or `IUSHR` fallback. Both are one-VM diagnostics, not portable results; #59 does not revise or back-fill #53. | JMH/forked baselines, confidence intervals, native-only isolation, controlled multi-machine repetitions, workload-derived release budgets, and continuous regression gates. |
| SDK | [#12](https://github.com/gaoyu06/native-obfuscator/pull/12) implements a Java 8/JNI/C-ABI v1 with ABI query, one-shot SHA-256, and equal-length constant-time byte comparison. The Linux CMake/G++ `-Xcheck:jni` integration run passed. [#15](https://github.com/gaoyu06/native-obfuscator/pull/15) independently re-ran it, checked the vendored source/license and JNI path, and concluded accept-with-nits. | [#46](https://github.com/gaoyu06/native-obfuscator/pull/46) cleanly stacks `NativeStrings` length/hash/concat on #12 without copying the general benchmark harness. Its local diagnostic remeasurement was slower than Java; the status document explicitly says this is not portable and not a speedup claim. The #12 → #15 → #46 lane remains draft. | The product surface, embedding and provider/update policy, target matrix, Zig execution, broader approved v1 surface if required, fuzz/allocation/concurrency/sanitizer/ABI target coverage, SBOM/update process, optional JDK 22+ FFM adapter, and release security sign-off remain unresolved. |
| Interpreter | [#7](https://github.com/gaoyu06/native-obfuscator/pull/7) documents the optional, default-off backend, ISA, and evaluation protocol. [#17](https://github.com/gaoyu06/native-obfuscator/pull/17) implements the initial integer slice; [#20](https://github.com/gaoyu06/native-obfuscator/pull/20) fixes dispatcher target validation; [#22](https://github.com/gaoyu06/native-obfuscator/pull/22) lowers the evaluation kernel's `mix` method; [#24](https://github.com/gaoyu06/native-obfuscator/pull/24) changes the generated method representation to compact hexadecimal byte blobs; and [#28](https://github.com/gaoyu06/native-obfuscator/pull/28) adds opt-in link-only publication of the transformed JAR and shared library without the generated C++ tree. | The implementation remains an open draft stack, default off, and integer-only. The three source-tree reader runs in [#21](https://github.com/gaoyu06/native-obfuscator/pull/21), [#23](https://github.com/gaoyu06/native-obfuscator/pull/23), and [#25](https://github.com/gaoyu06/native-obfuscator/pull/25) recovered both compared trees fully; the shared-library-only run in [#30](https://github.com/gaoyu06/native-obfuscator/pull/30) then recovered `add`, `sumTo`, and `mix` fully from the published `.so` without the C++ tree. | Stable shared-IR integration, broad opcode/runtime semantics, resource limits, wider differential tests, target/toolchain gates, and a human default/selection policy. |
| Automated-reader evaluation | [#21](https://github.com/gaoyu06/native-obfuscator/pull/21), [#23](https://github.com/gaoyu06/native-obfuscator/pull/23), and [#25](https://github.com/gaoyu06/native-obfuscator/pull/25) record three GPT-5.6 Sol reader runs on successive generated source-tree forms; both compared trees scored full in every run, and H0 was not rejected. [#30](https://github.com/gaoyu06/native-obfuscator/pull/30) records a fourth run using the published interpreter `.so` alone. [#37](https://github.com/gaoyu06/native-obfuscator/pull/37), stacked on the live direct-IR artifact [#35](https://github.com/gaoyu06/native-obfuscator/pull/35), records a recovery-first blinded read in which `add`, `sumTo`, `subMul`, and `mix` all scored full. [#50](https://github.com/gaoyu06/native-obfuscator/pull/50), stacked on evaluator artifact [#48](https://github.com/gaoyu06/native-obfuscator/pull/48), records the same four full scores after recovery was committed before source/oracle scoring. | Every usable run is an `N=1` tool-assisted case study with the limitations below. [#31](https://github.com/gaoyu06/native-obfuscator/pull/31) remains invalid reader-bar evidence because optimization reduced `mix` to constant-zero behavior. #37 and #50 use valid live direct-IR and shared-evaluator subjects; both full recoveries mean requirement 7 is not met. | A materially different lowering is needed: not another encoding tweak, not straight-line readable native output of the source algorithm, and not a decodable evaluator blob shipped with its evaluator. Independent readers, a frozen corpus, preregistered hypotheses, calibration, and uncontaminated repetitions remain necessary for a broader empirical claim. |

### Reader-eval evidence

The first three usable runs compared direct-C++ and interpreter-backend trees
generated from the same fixture revision, deferred the source/oracle comparison
until after both recoveries were written, and confirmed matching executable output.
The fourth run read the link-only published `.so` before opening the Java source
and used no generated C++ tree. The full/partial/fail scores below are the
recorded categorical outcomes, not a new derived metric.

#31 did run a reader, but its interesting `mix` kernel had been optimized to
constant-zero behavior, so the algorithm was not present as live code and the
result remains excluded. #35 repaired that artifact precondition: its builder
recorded six distinct `mix` outputs and live multiply, shift, bitwise, and add
instructions in the stripped direct-IR `.so`.

PR [#37](https://github.com/gaoyu06/native-obfuscator/pull/37) is the blinded reader evaluation stacked on #35: the recovery was committed first, then `add`, `sumTo`, `subMul`, and `mix` all scored **full**.
PR [#50](https://github.com/gaoyu06/native-obfuscator/pull/50) is the blinded reader evaluation stacked on #48: recovery was committed before source/oracle scoring, then `add`, `sumTo`, `subMul`, and `mix` all scored **full** on the valid live shared-evaluator stripped-`.so` subject.
Requirement 7 is **not met**: unaided readers fully recovered both the valid
live IR/direct subject in #37 and the valid live shared-evaluator subject in
#50, rather than DCE'd kernels.

| Run | What was controlled | What failed or limited the run | Measured outcome |
|---|---|---|---|
| [#21: first reader](https://github.com/gaoyu06/native-obfuscator/pull/21) | Same Java 8 class and compiler commit; interpreter tree read before direct C++; Java source reopened only after both recovery texts; Java, direct, and interpreter outputs matched. | `mix` fell back to method-specific direct C++ because the slice could not lower it. The reader had prior exposure to protocol text, fixture names, and opcode names, so the run was contaminated rather than unaided. | Interpreter and direct trees were both **full** for `add`, `sumTo`, and `mix`; H0 was not rejected. The `mix` result did not test opcode recovery. |
| [#23: blinded run after `mix` lowering](https://github.com/gaoyu06/native-obfuscator/pull/23) | Same preserved fixture for both trees; opcode recovery committed before direct recovery; fixture construction opened only afterward; `mix` was confirmed on the opcode path; both builds produced identical output. | A status-document query exposed method names and a fallback notice before recovery. No constants, instruction sequence, control flow, or source oracle was exposed, but the status-text contamination prevents an unaided claim. | Opcode and direct trees each scored **4 full / 0 partial / 0 fail**; H0 was not rejected. |
| [#25: compact-blob blinded run](https://github.com/gaoyu06/native-obfuscator/pull/25) | Same preserved fixture and source-last ordering; opcode and direct recoveries committed separately before opening fixture construction; original, direct, and opcode outputs matched exactly. | Class/build metadata and a fallback test name were exposed; the same reader handled the opcode condition before the direct condition. `divide` remained direct fallback. | Both trees scored **4 full / 0 partial / 0 fail**. The blobs plus `native_jvm_interp.cpp` were sufficient to recover all three lowered methods exactly; H0 was not rejected. |
| [#30: published-`.so`-only reader](https://github.com/gaoyu06/native-obfuscator/pull/30) | GPT-5.6 Sol received only the Linux x86-64 shared library published by #28. The directory contained the transformed JAR and `.so`, no `.cpp`; no generated C++ or private compiler tree was used. Recovery was written before Java source inspection, and published output matched the oracle. | The reader knew the names and signatures of `add`, `sumTo`, and `mix`; recorded oracle input/output examples were available as a consistency check. This is still one tool-assisted reader on one fixture and target. | `add`, `sumTo`, and `mix` all scored **full**. `mix` was recovered exactly, including constants, operation order, loop condition, shifts, multiply, xor, and rotate distance. The run rejected its “cannot recover critical logic from the published shared library alone” H0 for this fixture. |
| [#31: stripped direct-IR attempt](https://github.com/gaoyu06/native-obfuscator/pull/31) | A stripped IR/direct-C++ `.so` was read without an opcode machine. | Optimization left only constant-zero behavior for `mix`; the live algorithm was absent. | **Excluded from the reader bar.** “`mix` not recovered” cannot count as success when DCE removed the kernel. |
| [#35 artifact](https://github.com/gaoyu06/native-obfuscator/pull/35) → [#37 reader](https://github.com/gaoyu06/native-obfuscator/pull/37) | #35's builder evidence records diverse `mix` outputs and live integer operations in the stripped direct-IR `.so`. #37 committed reconstruction before opening the jar, run record, or source. | One unaided reader on one x86-64 artifact (`N=1`). | `add`, `sumTo`, `subMul`, and `mix` all scored **full**. The subject was valid and input-dependent, not a constant-return stub; requirement 7 was not met. |
| [#48 artifact](https://github.com/gaoyu06/native-obfuscator/pull/48) → [#50 reader](https://github.com/gaoyu06/native-obfuscator/pull/50) | #48's liveness gate confirms a stripped `--ir-lower=eval` `.so`, matching oracle/native output, evaluator trampolines, and live operations in the evaluator/blobs. #50 committed recovery before opening source/oracle material for scoring. | One unaided reader on one x86-64 artifact (`N=1`). | `add`, `sumTo`, `subMul`, and `mix` all scored **full**. The subject was valid live evaluator code, not DCE; requirement 7 was not met. |

These runs do not establish a population effect or equal reading effort. The
interpreter runs establish that **removing the C++ sources is not sufficient
while a decodable opcode stream and its opcode machine remain in the shipped
binary.** #37 separately establishes that this direct-IR lowering also misses
the reader bar: its live source algorithm remained readable in straight-line
native code in the stripped `.so`. #50 establishes that the shared-evaluator
lowering also misses the bar on its valid live stripped `.so`: the reader
recovered all four method formulas/control flow from the trampolines, blobs,
and evaluator semantics.

## Decisions

“Does not need a human” below means no new product-policy choice is needed:
the engineering stance is already justified by repository evidence or the
Sol/Fable cross-check. It does **not** waive normal code review for draft PRs.

### Already taken; no new human decision

| Decision already taken | Rationale and boundary |
|---|---|
| Treat #1/#2 as untrusted research except where #3 accepts or revises a claim. | The drafts contain unmeasured rankings and performance claims. #3 re-derived accepted ideas against the repository and explicitly rejects fabricated numbers and unsupported production labels. |
| Restore executable JUnit and use behavioral reference-versus-transformed oracles. | #4 found that the prior Gradle invocation executed no JUnit tests; #6 fixes that and requires exact observable output. Test infrastructure can be merged without defining a product support tier. |
| Preserve input class-file versions and reject unsupported semantics rather than blindly stamping version 52. | #6 reproduced broken nest/record/sealed metadata; #9 fixed the cause and Sol independently verified major 61 retention and the 16/1/0 run. This is a correctness repair, not a market-position choice. |
| Build a project-owned typed CFG over ASM and emit structured C++ before a second backend. | Independent Sol #3 and Fable #5 designs converge on this migration shape. It addresses the audited string-template limitations while keeping backend semantics shared. Whether and when IR becomes the public default remains a human decision. |
| Validate an entire IR method before mutating output, and keep legacy as the migration default. | #13/#16 verify clean per-method fallback and compileability for their narrow slices. This contains experimental risk; it does not establish parity or authorize an eventual default flip. |
| Require checksums, raw samples, environment metadata, and scoped wording for performance evidence. | #10 demonstrates both modes actually ran and agreed. #11's mixed result shows why a single local run cannot become a global speed or non-regression claim. |
| Keep reader outcomes scoped to the measured fixture and recorded limitations. | The usable runs are `N=1`, tool-assisted case studies with different artifact boundaries and recorded limitations. Their full recoveries support the kernel-specific conclusions above, but not a population effect or broader claim. |

### Human decisions still required

#### Reader-eval maintainer options

- **A. Accept that this bar is out of scope for the v1 product.** Ship only the
  compiler, compatibility, and SDK work that passes its own correctness and
  release gates. This is a product-scope option, not a proposal to shrink the
  written engineering goal.
- **B. Fund a different backend/product design.** **B1, link-only publication,**
  is already evidenced as insufficient by [#28](https://github.com/gaoyu06/native-obfuscator/pull/28)
  and [#30](https://github.com/gaoyu06/native-obfuscator/pull/30): removing the
  C++ tree still leaves a decodable opcode stream and its machine in the
  shipped library. #37 now also shows that direct IR lowering to straight-line
  readable native code exposes the live source algorithm. #50 shows that
  shared-evaluator trampolines plus live decodable blobs also expose all four
  methods on this subject. The remaining B is a lowering that avoids all three
  evidenced forms. If that design is not funded, drop the
  reader bar from the v1 product gate under A.
- **C. Keep iterating encodings.** This is likely wasted effort while the
  opcode machine and stream remain together in the generated tree or shipped
  library and the reader can recover method semantics from them.

**Product recommendation retained from the earlier briefs:** choose A for v1
unless a materially different lowering is explicitly funded. This does **not**
recommend rewriting or shrinking the written goal to A. #37 supersedes the
wait for a live-kernel reader. The next reader-bar design must not leave the
source algorithm as straight-line readable native code or a decodable
evaluator blob shipped with its evaluator; encoding tweaks alone are not that
design. Wider opt-in direct-IR coverage/review in #45 → #47 → #51 → #54 →
#56 → #62 → #63/#64 (reviews of #62), the #34 → #53 benchmark lane plus #59
stacked on the #57 ISA sibling,
the separate #42 → #44 → #48 → #50 evaluator experiment with #61 reviewing
#57, the SDK #12 → #15 → #46 and compatibility #6 → #9 → #14 → #41 stacks,
and options briefs … → #60 → #65 → this PR continue as separate engineering
lanes.

#### Other product decisions

| Decision | Concrete options | Recommendation | Main risk |
|---|---|---|---|
| Production Java promise | Baseline 8, 11, 17, or 21; use one “supports JDK N” badge or publish host/input/output/runtime dimensions separately. | Make JDK 17 the first required baseline; publish each dimension and keep 8/11 as separately tested legacy profiles, with 21/25 promoted only after feature-corpus evidence. | A high floor excludes users; a broad badge without feature evidence creates a false compatibility promise. |
| IR rollout and unsupported methods | Keep legacy indefinitely; flip to IR at partial coverage; flip only after parity; fail closed or allow explicit Java/legacy fallback with a manifest. | Keep legacy default now. Flip only after supported-op parity and full native differential gates; use precise refusal by default and an explicitly selected, recorded fallback profile. | An early flip breaks workloads; indefinite fallback doubles semantics and can hide unsupported methods. |
| SDK v1 product and supply-chain contract | Ship current SHA-256/equality surface; add encoding or BLAKE3 first; always embed or opt in; keep the pinned vendored provider or mandate a system/FIPS provider. | Prefer the smallest opt-in v1 justified by a real workload. Freeze the API only with security/license/update approval; avoid adding BLAKE3 without a concrete use case. | Public ABI mistakes persist; unconditional embedding increases footprint and update duty; provider policy can create compliance or side-channel liability. |
| JNI data-access/native-access policy | Copy arrays; direct buffers; bounded critical access; size-based hybrid. For modern JDKs: document enablement, warning-allowed operation, or deny-by-policy. | Keep checked copies as the default, add caller-selected direct buffers only when useful, and allow critical access only after bounded collector-aware evidence. Explicitly test/document modern native-access flags. | Copies can be slow; pinning can harm GC or violate JNI constraints; missing deployment policy can turn warnings or denial into production failures. |
| Interpreter product policy | Off; explicit per-method/build opt-in; automatic fallback; default backend. Keep format internal or promise public compatibility. | Keep it off by default and explicitly selected with manifest/resource limits; version the format for rejection but keep it internal. | Automatic/default interpretation can conceal compiler gaps, add runtime attack surface, and impose dispatch cost; a public format freezes evolution. |
| Native target/toolchain tiers | Linux only; x86-64 Linux/Windows/macOS; add arm64; CMake host compilers, Zig, or both. | Start with an evidence-backed x86-64 Linux/Windows/macOS CMake tier; validate arm64 and Zig separately before promotion. | A broad matrix multiplies ABI, loader, sanitizer, signing, and support work; a narrow one excludes customers. |
| Performance release gates | No threshold; absolute limits; relative-to-HotSpot limits; per-workload regression budgets. | Establish repeatable baselines first, then approve correctness-first, workload-specific budgets; never use one global speedup target. | No gate permits regressions; premature/noisy thresholds can be gamed and optimize the wrong workloads. |

## Suggested merge order

Every item below still needs its own review and branch evidence re-run after
rebasing. For a stacked PR, merge the base first, retarget the next PR to
`master`, verify that only the intended delta remains, then merge it.

1. Merge the authority/design set in dependency order:
   [#3](https://github.com/gaoyu06/native-obfuscator/pull/3) →
   [#4](https://github.com/gaoyu06/native-obfuscator/pull/4) →
   [#5](https://github.com/gaoyu06/native-obfuscator/pull/5) →
   [#7](https://github.com/gaoyu06/native-obfuscator/pull/7). #1/#2 are optional
   research archives only after their untrusted status and #3 corrections are
   preserved; they are not implementation prerequisites.
2. Merge the compatibility stack exactly
   [#6](https://github.com/gaoyu06/native-obfuscator/pull/6) →
   [#9](https://github.com/gaoyu06/native-obfuscator/pull/9) →
   [#14](https://github.com/gaoyu06/native-obfuscator/pull/14) →
   [#41](https://github.com/gaoyu06/native-obfuscator/pull/41). This first
   establishes a real test oracle, fixes the failures it exposes, extends the
   corpus to JDK 21, then adds the narrowly scoped `javac --release 25`
   fixtures; #41 is not a full-JDK-25 support claim.
3. Merge [#10](https://github.com/gaoyu06/native-obfuscator/pull/10) before
   [#11](https://github.com/gaoyu06/native-obfuscator/pull/11), rebase #11 over
   #6/#10, and rerun both correctness and benchmark evidence. #11's mixed local
   result is not a speed gate.
4. Merge the IR stack exactly
   [#8](https://github.com/gaoyu06/native-obfuscator/pull/8) →
   [#13](https://github.com/gaoyu06/native-obfuscator/pull/13) →
   [#16](https://github.com/gaoyu06/native-obfuscator/pull/16) →
   [#19](https://github.com/gaoyu06/native-obfuscator/pull/19) →
   [#29](https://github.com/gaoyu06/native-obfuscator/pull/29) →
   [#33](https://github.com/gaoyu06/native-obfuscator/pull/33) →
   [#36](https://github.com/gaoyu06/native-obfuscator/pull/36) →
   [#39](https://github.com/gaoyu06/native-obfuscator/pull/39) →
   [#40](https://github.com/gaoyu06/native-obfuscator/pull/40) →
   [#45](https://github.com/gaoyu06/native-obfuscator/pull/45) →
   [#47](https://github.com/gaoyu06/native-obfuscator/pull/47) →
   [#51](https://github.com/gaoyu06/native-obfuscator/pull/51) →
   [#54](https://github.com/gaoyu06/native-obfuscator/pull/54) →
   [#56](https://github.com/gaoyu06/native-obfuscator/pull/56) →
   [#62](https://github.com/gaoyu06/native-obfuscator/pull/62) →
   [#63](https://github.com/gaoyu06/native-obfuscator/pull/63) /
   [#64](https://github.com/gaoyu06/native-obfuscator/pull/64). Rebase after
   #6 so the duplicated JUnit-launcher change is resolved once. Do not squash
   away review fixes or treat #47/#51/#54/#56/#62 as parity or ship-ready. #39 and
   #45 are the docs-only Fable reviews of phases 4 and 5; #51 is Sol's phase-6
   accept-with-nits review after Fable was policy-blocked and includes the
   array-component `FindClass` fix. #54 adds the still-opt-in phase-7
   `CHECKCAST`/`INSTANCEOF` and initial `I64` slice. #56 is Sol's docs-only
   **accept** review and records the 35/35 focused-test rerun.
   #62 is stacked on #56 and adds the still-opt-in phase-8 allocation,
   constructor-call, and broader invoke slice; constructor bodies remain
   excluded, legacy remains the default, and the recorded 38 focused tests
   plus 34-method g++ smoke do not make it ship-ready.
   #63 and #64 are parallel documentation-only reviews of #62 rather than
   compiler successors. Both record **accept**, no compiler change, and 38/38
   focused tests; #63 records one non-blocking never-taken constructor-receiver
   null-check observation, while #64 records a 34-method g++ syntax check.
   Neither review establishes ship-readiness.
   [#42](https://github.com/gaoyu06/native-obfuscator/pull/42) is a separate
   sibling lane from #39, not the next item in the direct-IR stack. Review it
   through [#44](https://github.com/gaoyu06/native-obfuscator/pull/44), then
   retain [#48](https://github.com/gaoyu06/native-obfuscator/pull/48) as its
   artifact-only successor and [#50](https://github.com/gaoyu06/native-obfuscator/pull/50)
   as the recovery-first reader record: #42 → #44 → #48 → #50. Retain
   `direct` as the default lowering. #50 is evaluation evidence, not an
   implementation merge prerequisite. [#57](https://github.com/gaoyu06/native-obfuscator/pull/57)
   is a separate ISA sibling from #44, not a successor to #50: it adds the six
   recorded bitwise/shift operations so the equivalent integer kernel can stay
   on eval, claims 28/28 focused tests, and records no new benchmark timing.
   [#61](https://github.com/gaoyu06/native-obfuscator/pull/61) is Sol's
   documentation-only review of #57; it records **accept**, no compiler change,
   and a 28/28 focused-test rerun, but no ship-readiness finding.
   [#59](https://github.com/gaoyu06/native-obfuscator/pull/59) is a benchmark
   follow-up stacked on #57. It records evaluator-path timing only for its own
   no-fallback run and must not be used to back-fill #53.
   Keep the original benchmark evidence in order:
   [#34](https://github.com/gaoyu06/native-obfuscator/pull/34) →
   [#53](https://github.com/gaoyu06/native-obfuscator/pull/53). #53 integrates
   evaluator selection but records no eval timing because `USHR` caused legacy
   fallback; it is evidence, not an implementation merge prerequisite. Keep
   #59 as the distinct sibling/follow-up benchmark stacked on #57 rather than
   collapsing the two runs.
   Separately,
   [#35](https://github.com/gaoyu06/native-obfuscator/pull/35) is an eval-only
   live-artifact sibling on #33; keep the #35 →
   [#37](https://github.com/gaoyu06/native-obfuscator/pull/37) evaluation lane
   separate from both #40 and #42. Preserve #31 as an invalid reader-bar record
   and #37 as #35's recovery-first reader record. Neither evaluation draft is
   an implementation merge prerequisite.
5. Merge [#12](https://github.com/gaoyu06/native-obfuscator/pull/12) →
   [#15](https://github.com/gaoyu06/native-obfuscator/pull/15) →
   [#46](https://github.com/gaoyu06/native-obfuscator/pull/46) after resolving
   the same #6 launcher overlap and retargeting the clean #46 delta over the
   reviewed SDK base. The Fable accept-with-nits review is not the human
   product/security approval listed above; #46's local slower-than-Java result
   is not a portable release gate.
6. Land [#7](https://github.com/gaoyu06/native-obfuscator/pull/7) first, then
   review the interpreter implementation stack in order:
   [#17](https://github.com/gaoyu06/native-obfuscator/pull/17) →
   [#20](https://github.com/gaoyu06/native-obfuscator/pull/20) →
   [#22](https://github.com/gaoyu06/native-obfuscator/pull/22) →
   [#24](https://github.com/gaoyu06/native-obfuscator/pull/24) →
   [#28](https://github.com/gaoyu06/native-obfuscator/pull/28). Keep it default
   off and review it against the stable shared IR before placing it after the
   direct-C++ slice. Preserve the corresponding reader records:
   [#21](https://github.com/gaoyu06/native-obfuscator/pull/21) for #17,
   [#23](https://github.com/gaoyu06/native-obfuscator/pull/23) for #22, and
   [#25](https://github.com/gaoyu06/native-obfuscator/pull/25) for #24, followed
   by [#30](https://github.com/gaoyu06/native-obfuscator/pull/30) for #28; these
   document measured outcomes and are not implementation prerequisites.

The independent compatibility, benchmark, IR, and SDK lanes may be reviewed in
parallel, but their order within each arrowed stack must be preserved.

## Honest gaps

- #41 adds four ClassicTest fixtures compiled independently with
  `javac --release 25`; its status document records 23 passed, 1 pre-existing
  skip, and 0 failed. This is evidence only for class-file major 69 and the
  four listed surfaces on that VM, not full JDK 25 support, preview coverage,
  or separate JDK 22–24 class-file coverage.
- The usable automated-reader evaluations are `N=1`, tool-assisted case
  studies with recorded limitations. The first three produced
  full/full source-tree outcomes and did not reject H0; the fourth fully
  recovered all three methods from the published `.so` and rejected its
  shared-library-only H0. They support only the kernel-and-artifact conclusion
  in the reader-eval subsection. The fifth, #37, fully recovered all four
  methods from #35's valid live direct-IR stripped `.so`; #50 fully recovered
  the same four methods from #48's valid live shared-evaluator stripped `.so`.
  #31 remains invalid for the reader bar because its `mix` kernel was DCE'd.
- IR is opt-in and incomplete. #47 adds the recorded switch and object
  `ANEWARRAY` phase-6 slice. #51's Sol review records **accept with nits** after
  fixing array-component resolution to use descriptor-based `FindClass`.
  #54 adds phase-7 `CHECKCAST`/`INSTANCEOF` and an initial two-slot `I64` slice;
  its status document claims 33 `IrCompilerTest` plus 2 `CodegenModeTest`, all
  with 0 skipped/failures/errors, and keeps legacy as the codegen default. #56
  is Sol's docs-only **accept** review of #54 and records 35/35 focused tests
  rerun. The #42 evaluator supports a narrower integer subset, is selected only
  with `--ir-lower=eval`, and keeps `direct` as the lowering default. #44
  accepts it with nits; #48 publishes a live stripped artifact, and #50's
  recovery-first reader scores all four methods full. As a sibling from #44,
  #57 adds `IAND`/`IOR`/`IXOR`/`ISHL`/`ISHR`/`IUSHR`, records the equivalent
  integer kernel staying on eval, claims 28/28 focused tests, and adds no
  benchmark timings. #61 is Sol's docs-only **accept** review of #57, records
  no compiler change and 28/28 focused tests, and is not a ship-readiness
  finding. #62 is stacked on #56 and adds opt-in `NEW` via `AllocObject`,
  constructor-only `INVOKESPECIAL` via `CallNonvirtualVoidMethod`, and broader
  `I`/`J`/reference invoke shapes. Constructor bodies remain excluded, legacy
  remains the default, and its recorded 38 focused tests plus 34-method g++
  smoke do not make this partial phase ship-ready. #63 and #64 are
  documentation-only **accept** reviews of #62 with no compiler changes and
  38/38 focused tests. #63's sole non-blocking observation is a never-taken
  constructor-receiver null check; #64 additionally records a 34-method g++
  syntax check. Neither is a ship-readiness finding.
- #46 cleanly stacks `NativeStrings` on SDK v1 without duplicating the general
  benchmark harness. Its status document records the local diagnostic as
  slower than Java and explicitly rejects a portable or speedup claim.
- #10's one local checksum-correct run shows the current transpiled-JNI path
  much slower than plain HotSpot for all three exact kernels: median ratios are
  about 18× for the integer loop, 23× for string concat/hash, and 199× for
  recursion. This is diagnostic evidence for those workloads, not a portable
  estimate, but it directly contradicts any present speedup claim.
- #53 advances the #34 benchmark lane on `IrFriendlyIntKernel.run(I)I`. Its
  recorded local medians are 12,207,144.5 ns for JVM, 202,090,247.0 ns for
  legacy, and 11,311,481.5 ns for direct IR. Direct IR stayed on IR. Eval
  rejected `USHR` and fell back to legacy, so its median is `N/A` and no eval
  timing is claimed. None of these local values is portable.
- #59 is a separate remeasurement stacked on #57, not a correction to #53.
  It records 5 warmups / 10 measured iterations, checksum 2,038,221,507, and
  medians of 10,017,146.0 ns for JVM, 167,870,311.5 ns for legacy,
  10,021,957.0 ns for direct IR, and 411,875,537.5 ns for evaluator IR. The
  target evaluator-data marker was present and no target-method or `IUSHR`
  fallback occurred. This is one local diagnostic, not a portable result or
  speedup claim; #53's eval median remains `N/A`.
- PRs #1–#65 are still open drafts. `master` contains none of their work.

## Before any production claim

A production claim requires evidence on the exact release commit and artifacts,
not the union of claims from draft branches:

1. Merge and review the applicable stacks, resolve overlaps, and rerun their
   full commands after rebasing. Required jobs must report actual test counts
   and artifacts, not merely a configured matrix or successful compilation.
2. Approve and publish the Java support dimensions and native target/toolchain
   tiers. Treat #41's four JDK 25 fixtures as narrow evidence, then add the
   remaining required metadata, bootstrap, module/multi-release, refusal,
   preview-policy, runtime, and broader feature cases before any full-JDK-25
   claim.
3. For any production IR claim, complete the declared semantic surface, prove
   reference-Java versus generated-native behavior across the supported matrix,
   compile generated C++ with warnings-as-errors/sanitizers, run `-Xcheck:jni`,
   and make a reviewed default/fallback/legacy-retirement decision.
4. Replace the one-machine diagnostic benchmark with controlled repeated raw
   results, forked/JMH and native-only isolation where applicable, end-to-end
   cost data, and human-approved workload budgets. Either meet those budgets or
   explicitly accept the current JNI cost; do not market a speedup from present
   evidence.
5. If the SDK ships, freeze its API/ABI and embedding/provider choices, close
   the #15 nits and review #46's JNI lifetime, UTF-16, concat-overflow, and
   loader behavior as appropriate, test all tier-1 targets and loaders
   (including Zig only if supported), and complete fuzzing, sanitizer,
   allocation, concurrency, license/provenance, SBOM, vulnerability/update,
   and security review gates. Do not turn #46's local measurement into a
   portable performance claim.
6. If the interpreter ships, first submit and review its implementation against
   the shared IR, then prove differential parity, deterministic refusal,
   resource limits, format/version rejection, and target/toolchain behavior.
   Otherwise exclude it from the release claim.
7. Do not claim that the reader bar has been met. #30 recovered `mix` from the
   interpreter `.so` without the C++ tree, and #37 recovered `add`, `sumTo`,
   `subMul`, and `mix` from #35's valid live IR/direct stripped `.so`. #31
   remains invalid because its `mix` was DCE'd; it does not offset #37. #50
   also recovered all four methods from #48's valid live shared-evaluator
   stripped `.so`. The written goal therefore needs a lowering that is neither
   a straight-line readable native form of the source algorithm nor a
   decodable evaluator blob shipped with its evaluator, not another encoding
   tweak. Any broader reader claim needs raw reproducible results whose scope
   and limitations support its exact wording, plus privacy and methodology
   approval. The reader claim is not a v1 product prerequisite if option A is
   selected, but selecting A does not shrink the written engineering goal.
8. Produce reproducible signed/provenanced artifacts, an SBOM and symbol
   allowlist, package/native-access documentation, crash/support and
   incident-response ownership, upgrade/rollback instructions, and final
   release approval for the residual compatibility, performance, and security
   risks.
