# Documentation map / 文档索引

This tree is a mix of **current** status, **design** notes, and **historical**
draft measurements. Prefer the current-status page when something disagrees
with an older brief.

当旧简报与现状页冲突时，以现状页为准。

## Start here / 从这里读

| Document | Language | What it is |
| --- | --- | --- |
| [architecture/current-goal.md](architecture/current-goal.md) | EN + 中文 | Active goal: IR-complete codegen, then delete legacy. |
| [architecture/project-status.md](architecture/project-status.md) | EN + 中文 | What is on `master` after #118–#173. Claims that are **not** allowed. |
| [architecture/integration-master-tips.md](architecture/integration-master-tips.md) | EN | How the preferred draft tips were folded onto `master`. |
| [architecture/human-decision-matrix.md](architecture/human-decision-matrix.md) | EN | Product decisions that still need a human (D1–D24). |
| [architecture/production-roadmap.md](architecture/production-roadmap.md) | EN | Longer-term production plan. The “evidence from master @ e7ca4c8” section is historical; see the preface. |

## Compiler / 编译器

| Document | Notes |
| --- | --- |
| [architecture/ir-compiler.md](architecture/ir-compiler.md) | Typed CFG IR (implemented on master as `--codegen=ir`; original “docs only” header is historical) |
| [architecture/ir-examples.md](architecture/ir-examples.md) | Teaching examples, not a dump of current emitter output |
| [architecture/ir-migration-plan.md](architecture/ir-migration-plan.md) | Historical migration rationale; IR path now exists |
| [architecture/ir-flex-ctor-status.md](architecture/ir-flex-ctor-status.md) | Constructor split through immediate multi-super returns (#146/#160/#163–#166/#170–#173) |
| [architecture/ir-class-ldc-status.md](architecture/ir-class-ldc-status.md) | Primitive `Class` `LDC` via wrapper `TYPE` (#167) |
| [architecture/ir-condy-status.md](architecture/ir-condy-status.md) | Proven `ConstantDynamic` + raw MH/MT `LDC` (#161); interface companions (#168) |
| [architecture/ir-indy-status.md](architecture/ir-indy-status.md) | IR `invokedynamic` via preprocessor (#159) |
| [architecture/ir-monitors-status.md](architecture/ir-monitors-status.md) | IR monitors / synchronized (#158) |
| [architecture/ir-if-acmp-status.md](architecture/ir-if-acmp-status.md) | IR `IF_ACMPEQ` / `IF_ACMPNE` (#157) |
| [architecture/ir-lcmp-status.md](architecture/ir-lcmp-status.md) | IR `LCMP` / `LongCompare` (#153) |
| [reviews/ir-lcmp-fable.md](reviews/ir-lcmp-fable.md) | Fable accept of #153 |
| [architecture/ir-phase20-status.md](architecture/ir-phase20-status.md) | Phase 20 IR increment (`LDIV`/`LREM`/`LNEG`) |
| [reviews/ir-phase20-sol.md](reviews/ir-phase20-sol.md) / [reviews/ir-phase20-fable.md](reviews/ir-phase20-fable.md) | Independent accepts of phase 20 |
| [architecture/ir-phase19-status.md](architecture/ir-phase19-status.md) | Long bitwise + shifts |
| [architecture/ir-p19-jdk21-integration.md](architecture/ir-p19-jdk21-integration.md) | How #128 and #126 were combined |
| [architecture/ir-phase18-status.md](architecture/ir-phase18-status.md) | Primitive arrays + `MULTIANEWARRAY` |
| [architecture/ir-jdk17-runtime-fix.md](architecture/ir-jdk17-runtime-fix.md) | Classfile version, indy packaging, `invokeExact` trampolines |
| [reviews/ir-phase19-fable.md](reviews/ir-phase19-fable.md) | Fable accept-with-nits of long bitwise/shifts |
| [reviews/opcode-backend-v2-sol.md](reviews/opcode-backend-v2-sol.md) | Sol accept-with-nits of interpreter ISA v2 |
| [reviews/ir-phase18-sol.md](reviews/ir-phase18-sol.md) / [reviews/ir-phase18-fable.md](reviews/ir-phase18-fable.md) | Independent accepts of phase 18 (docs-only) |
| [reviews/ir-jdk17-runtime-fix-sol.md](reviews/ir-jdk17-runtime-fix-sol.md) / [reviews/ir-jdk17-runtime-fix-fable.md](reviews/ir-jdk17-runtime-fix-fable.md) | Sol reject+fix, then Fable accept of the runtime-fix tip |

Per-phase `ir-phaseN-status.md` / `*-review.md` files under `architecture/` are
the incremental history. They were written against draft tips, not as a claim
that every phase is a separate product.

## SDK

| Document | Notes |
| --- | --- |
| [sdk/v1-status.md](sdk/v1-status.md) | Java API + C ABI |
| [sdk/v1-fable-review.md](sdk/v1-fable-review.md) | Review of primitives v1 |
| [sdk/hmac-sha256-review.md](sdk/hmac-sha256-review.md) | HMAC review |
| [sdk/aes-256-gcm-review.md](sdk/aes-256-gcm-review.md) | AES-256-GCM review (prefer the 32-bit length fix) |
| [architecture/sdk-cpp.md](architecture/sdk-cpp.md) | Earlier design note |
| [research/cpp-sdk-options.md](research/cpp-sdk-options.md) / [research/sdk-api-sketch.md](research/sdk-api-sketch.md) | Research, not a shipped contract |

## Compatibility and tests / 兼容与测试

| Document | Notes |
| --- | --- |
| [benchmarks/ir-jdk25-e2e-corpus.md](benchmarks/ir-jdk25-e2e-corpus.md) | 4-fixture IR behavioral E2E on one VM (20/21 IR, one hybrid ctor, JEP 472 warning); not a JDK 25 badge. Review: [reviews/ir-jdk25-e2e-fable.md](reviews/ir-jdk25-e2e-fable.md) |
| [benchmarks/ir-jdk21-e2e-corpus.md](benchmarks/ir-jdk21-e2e-corpus.md) | 6-fixture IR behavioral E2E on one VM; not a JDK 21 badge |
| [benchmarks/ir-jdk17-e2e-corpus.md](benchmarks/ir-jdk17-e2e-corpus.md) | 11-fixture IR behavioral E2E on one VM; not a support badge |
| [audit/jdk17-e2e-status.md](audit/jdk17-e2e-status.md) | Legacy-path JDK 17 harness |
| [audit/jdk21-25-e2e-status.md](audit/jdk21-25-e2e-status.md) / [audit/jdk25-e2e-status.md](audit/jdk25-e2e-status.md) | Extra fixtures; not “JDK 25 supported” |
| [architecture/jep472-native-access.md](architecture/jep472-native-access.md) | Output JAR `Enable-Native-Access: ALL-UNNAMED` (#145). Review: [reviews/jep472-native-access-fable.md](reviews/jep472-native-access-fable.md). Not a JDK 25 badge |
| [benchmarks/ir-jdk17-e2e-phase17.md](benchmarks/ir-jdk17-e2e-phase17.md) | IR-mode 0/5 crashes **before** the runtime repair |
| [architecture/ir-jdk17-runtime-fix.md](architecture/ir-jdk17-runtime-fix.md) | Same five fixtures after the repair |
| [audit/opcode-coverage.md](audit/opcode-coverage.md) | Legacy opcode coverage notes |
| [audit/jdk-compatibility-gaps.md](audit/jdk-compatibility-gaps.md) | Pre-#118 audit; `version = 52` path is historical |
| [audit/codegen-pipeline.md](audit/codegen-pipeline.md) | Pre-#118 snippet-pipeline audit |

## Measurements / 测量

Do not invent numbers. Do not back-fill [#53](https://github.com/gaoyu06/native-obfuscator/pull/53) eval medians (`N/A`).

| Document | Notes |
| --- | --- |
| [benchmarks/README.md](benchmarks/README.md) | How to run `benchmarks/run.py` (JVM + legacy + IR) |
| [benchmarks/results-ir-vs-legacy-phase19.md](benchmarks/results-ir-vs-legacy-phase19.md) | Latest three-mode bench (all three kernels on IR; one VM). Fable accept-with-nits: [reviews/bench-ir-phase19-fable.md](reviews/bench-ir-phase19-fable.md) |
| [benchmarks/results-ir-vs-legacy-master.md](benchmarks/results-ir-vs-legacy-master.md) | Pre-phase-19 three-mode bench; only `string-concat-hash` stayed fully IR |
| [benchmarks/ir-leftover-inventory.md](benchmarks/ir-leftover-inventory.md) | Post-#168 admission inventory (ClassicTest 108/108, JDK 17/21/25 82/82, 47/47, 21/21). Not coverage-complete |
| [benchmarks/ir-admission-phase18-corpus.md](benchmarks/ir-admission-phase18-corpus.md) | Latest admission tables (ClassicTest 108/108 IR; JDK 17 36/36 admit; JDK 21 extra 36/38) |
| [benchmarks/results-local.md](benchmarks/results-local.md) | Early local JVM vs JNI numbers |
| [benchmarks/results-ir-vs-legacy.md](benchmarks/results-ir-vs-legacy.md) | IR vs legacy vs JVM (local) |
| [benchmarks/results-ir-eval-lower.md](benchmarks/results-ir-eval-lower.md) | Eval fell back; median `N/A` |
| [benchmarks/results-ir-eval-ushr.md](benchmarks/results-ir-eval-ushr.md) | Later eval stay-on-eval measure (local only) |

## Default-off backends on master / 已在 master、默认关闭的后端

| Document | Stack |
| --- | --- |
| [architecture/interpreter-isa-exceptions-status.md](architecture/interpreter-isa-exceptions-status.md) | Interpreter `ATHROW` + exception table (#150) |
| [reviews/interpreter-isa-exceptions-sol.md](reviews/interpreter-isa-exceptions-sol.md) | Sol accept of #150 |
| [architecture/interpreter-isa-objects-status.md](architecture/interpreter-isa-objects-status.md) | Interpreter ISA v4 reference slice (#148) |
| [reviews/interpreter-isa-objects-sol.md](reviews/interpreter-isa-objects-sol.md) | Sol accept of #148 |
| [architecture/interpreter-isa-i64-status.md](architecture/interpreter-isa-i64-status.md) | Interpreter ISA v3 i64 slice (#140) |
| [reviews/interpreter-isa-i64-sol.md](reviews/interpreter-isa-i64-sol.md) | Sol accept of #140 |
| [architecture/interpreter-on-master-status.md](architecture/interpreter-on-master-status.md) | What `--backend=interpreter` actually does on master |
| [reviews/interpreter-on-master-fable.md](reviews/interpreter-on-master-fable.md) | Fable accept-with-nits of #124 |
| [architecture/ir-evaluator-backend.md](architecture/ir-evaluator-backend.md) | `--ir-lower=eval` on master (default `direct`; #137) |
| [reviews/ir-eval-port-sol.md](reviews/ir-eval-port-sol.md) | Sol accept-with-nits of #137 |
| [reviews/ir-eval-ldiv-sol.md](reviews/ir-eval-ldiv-sol.md) | Sol accept of #139 `LDIV`/`LREM` wire-up |
| [eval/](eval/) | Reader / recovery notes (requirement 7 unmet) |

Older #17–#28 sibling flags and old evaluator PRs #42–#87 are not the current CLI.

## Research / 调研

[research/java-to-cpp-paths.md](research/java-to-cpp-paths.md) and
[research/benchmark-methodology.md](research/benchmark-methodology.md) are
surveys. Gemini drafts in that era are not authorities unless a later
independent review accepted the claim
([architecture/gemini-review-notes.md](architecture/gemini-review-notes.md)).

## Historical maintainer brief / 历史维护者简报

[architecture/goal-status-and-options.md](architecture/goal-status-and-options.md)
is the historical maintainer brief through PR #117, written when `master` was
still `e7ca4c8`. Keep it as history. For the active engineering goal, use
[architecture/current-goal.md](architecture/current-goal.md). For “what is
true on master today”, use
[architecture/project-status.md](architecture/project-status.md).
