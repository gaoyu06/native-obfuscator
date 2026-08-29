# Documentation map / 文档索引

This tree is a mix of **current** status, **design** notes, and **historical**
draft measurements. Prefer the current-status page when something disagrees
with an older brief.

当旧简报与现状页冲突时，以现状页为准。

## Start here / 从这里读

| Document | Language | What it is |
| --- | --- | --- |
| [architecture/project-status.md](architecture/project-status.md) | EN + 中文 | What is on `master` after #118/#119. Claims that are **not** allowed. |
| [architecture/integration-master-tips.md](architecture/integration-master-tips.md) | EN | How the preferred draft tips were folded onto `master`. |
| [architecture/human-decision-matrix.md](architecture/human-decision-matrix.md) | EN | Product decisions that still need a human (D1–D24). |
| [architecture/production-roadmap.md](architecture/production-roadmap.md) | EN | Longer-term production plan. The “evidence from master @ e7ca4c8” section is historical; see the preface. |

## Compiler / 编译器

| Document | Notes |
| --- | --- |
| [architecture/ir-compiler.md](architecture/ir-compiler.md) | Typed CFG IR (implemented on master as `--codegen=ir`; original “docs only” header is historical) |
| [architecture/ir-examples.md](architecture/ir-examples.md) | Teaching examples, not a dump of current emitter output |
| [architecture/ir-migration-plan.md](architecture/ir-migration-plan.md) | Historical migration rationale; IR path now exists |
| [architecture/ir-phase18-status.md](architecture/ir-phase18-status.md) | Latest IR increment on master (primitive arrays + `MULTIANEWARRAY`) |
| [architecture/ir-jdk17-runtime-fix.md](architecture/ir-jdk17-runtime-fix.md) | Classfile version, indy packaging, `invokeExact` trampolines |
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
| [audit/jdk17-e2e-status.md](audit/jdk17-e2e-status.md) | Legacy-path JDK 17 harness |
| [audit/jdk21-25-e2e-status.md](audit/jdk21-25-e2e-status.md) / [audit/jdk25-e2e-status.md](audit/jdk25-e2e-status.md) | Extra fixtures; not “JDK 25 supported” |
| [benchmarks/ir-jdk17-e2e-phase17.md](benchmarks/ir-jdk17-e2e-phase17.md) | IR-mode 0/5 crashes **before** the runtime repair |
| [architecture/ir-jdk17-runtime-fix.md](architecture/ir-jdk17-runtime-fix.md) | Same five fixtures after the repair |
| [audit/opcode-coverage.md](audit/opcode-coverage.md) | Legacy opcode coverage notes |
| [audit/jdk-compatibility-gaps.md](audit/jdk-compatibility-gaps.md) | Pre-#118 audit; `version = 52` path is historical |
| [audit/codegen-pipeline.md](audit/codegen-pipeline.md) | Pre-#118 snippet-pipeline audit |

## Measurements / 测量

Do not invent numbers. Do not back-fill [#53](https://github.com/gaoyu06/native-obfuscator/pull/53) eval medians (`N/A`).

| Document | Notes |
| --- | --- |
| [benchmarks/README.md](benchmarks/README.md) | How to run `benchmarks/run.py` |
| [benchmarks/ir-admission-phase18-corpus.md](benchmarks/ir-admission-phase18-corpus.md) | Latest admission tables (ClassicTest 108/108 IR; JDK 17 36/36 admit; JDK 21 extra 36/38) |
| [benchmarks/results-local.md](benchmarks/results-local.md) | Early local JVM vs JNI numbers |
| [benchmarks/results-ir-vs-legacy.md](benchmarks/results-ir-vs-legacy.md) | IR vs legacy vs JVM (local) |
| [benchmarks/results-ir-eval-lower.md](benchmarks/results-ir-eval-lower.md) | Eval fell back; median `N/A` |
| [benchmarks/results-ir-eval-ushr.md](benchmarks/results-ir-eval-ushr.md) | Later eval stay-on-eval measure (local only) |

## Sibling stacks not in the master compiler / 未合入主线编译器的兄弟栈

These directories record work that **did not** land as compiler code on the
phase-18 line. Do not treat them as enabled CLI features on current `master`.

| Document | Stack |
| --- | --- |
| [architecture/ir-evaluator-backend.md](architecture/ir-evaluator-backend.md) | `--ir-lower=eval` sibling |
| [architecture/interpreter-backend.md](architecture/interpreter-backend.md) / [architecture/interpreter-isa.md](architecture/interpreter-isa.md) | Opcode interpreter design |
| [eval/](eval/) | Reader / recovery notes (requirement 7 unmet) |

## Research / 调研

[research/java-to-cpp-paths.md](research/java-to-cpp-paths.md) and
[research/benchmark-methodology.md](research/benchmark-methodology.md) are
surveys. Gemini drafts in that era are not authorities unless a later
independent review accepted the claim
([architecture/gemini-review-notes.md](architecture/gemini-review-notes.md)).

## Historical maintainer brief / 历史维护者简报

[architecture/goal-status-and-options.md](architecture/goal-status-and-options.md)
was written when `master` was still `e7ca4c8` and PRs #1–#107 were unmerged
drafts. Keep it as history. For “what is true on master today”, use
[architecture/project-status.md](architecture/project-status.md).
