# Goal status and options

## Executive status

This is a maintainer snapshot of `origin/master` at `e7ca4c8` and the pull
requests returned by `gh pr list --state all --limit 30` on 2026-08-28. All 30
listed PRs are open drafts. None of their code or documentation is therefore a
capability of `master`, and no PR had a reported status check or accepted GitHub
review at the time of this snapshot. Results below are evidence recorded on the
named branch, not invented merge or CI results.

PRs [#1](https://github.com/gaoyu06/native-obfuscator/pull/1) and
[#2](https://github.com/gaoyu06/native-obfuscator/pull/2) are Gemini research
inputs, not authorities. Only claims independently accepted or revised by the
Sol review in [#3](https://github.com/gaoyu06/native-obfuscator/pull/3)
(`docs/architecture/gemini-review-notes.md`) are used here.

| Area | Done on a draft branch | In flight | Not started or not evidenced |
|---|---|---|---|
| IR | Fable's typed-CFG/structured-C++ design is documented in [#5](https://github.com/gaoyu06/native-obfuscator/pull/5). Phase 1 in [#8](https://github.com/gaoyu06/native-obfuscator/pull/8) implements an opt-in integer/control-flow slice; [#13](https://github.com/gaoyu06/native-obfuscator/pull/13) fixes its non-compiling `juint` output and records an accept-with-nits Fable review. | [#16](https://github.com/gaoyu06/native-obfuscator/pull/16) is the phase-2 PR. It adds narrow integer field/static/virtual-call support and a real `g++ -fsyntax-only` smoke test (10/10 recorded tests), stacked as #8 → #13 → #16. | Full JVM semantics: arrays, broad descriptors and wide values, allocation, switches, exceptions/handlers, monitors, reference lifetime, class initialization, complete invokes, native-JAR differential E2E, parity, and any default switch. Legacy remains the default. |
| JDK compatibility | [#6](https://github.com/gaoyu06/native-obfuscator/pull/6) restores actual JUnit execution and adds JDK 17 behavioral fixtures. The stacked fix [#9](https://github.com/gaoyu06/native-obfuscator/pull/9) preserves modern class versions and accepts `TypeDescriptor` for record bootstrap rewriting; its Sol-verified run recorded 16 pass, 1 `krak2` skip, 0 fail. [#14](https://github.com/gaoyu06/native-obfuscator/pull/14) records all three new JDK 21 fixtures passing on the three harness modes, with 19 pass, 1 pre-existing skip, 0 fail. | The entire #6 → #9 → #14 stack is still draft. [#4](https://github.com/gaoyu06/native-obfuscator/pull/4) remains the audit baseline, not a pass result. | JDK 22–25 feature/class-file fixtures, JDK 25 compiler output, `ConstantDynamic`, modules, multi-release JARs, hidden classes, preview policy, virtual-thread behavior, and device-level Android evidence. The #14 machine had `javac 21.0.10`, so it could not create JDK 25 fixtures. |
| Benchmarks | [#10](https://github.com/gaoyu06/native-obfuscator/pull/10) adds a checksum-gated plain-HotSpot versus current transpiled-JNI harness with raw samples and environment data. [#11](https://github.com/gaoyu06/native-obfuscator/pull/11) removes repeated warm instance-member lookup work; its one-run deltas are explicitly mixed. | Both PRs are draft; #11 still needs the #6 launcher, concurrency/cache review, full E2E, and a same-machine rerun through #10. | JMH/forked baselines, confidence intervals, native-only isolation, IR/interpreter/SDK variants, controlled multi-machine repetitions, workload-derived release budgets, and continuous regression gates. |
| SDK | [#12](https://github.com/gaoyu06/native-obfuscator/pull/12) implements a Java 8/JNI/C-ABI v1 with ABI query, one-shot SHA-256, and equal-length constant-time byte comparison. The Linux CMake/G++ `-Xcheck:jni` integration run passed. [#15](https://github.com/gaoyu06/native-obfuscator/pull/15) independently re-ran it, checked the vendored source/license and JNI path, and concluded accept-with-nits. | #12 → #15 is still a draft stack. The product surface, unconditional embedding, provider/update policy, target matrix, and Zig path remain unresolved. | Broader approved v1 surface if required, fuzz/allocation/concurrency/sanitizer/ABI target coverage, SBOM/update process, optional JDK 22+ FFM adapter, and a release security sign-off. |
| Interpreter | [#7](https://github.com/gaoyu06/native-obfuscator/pull/7) documents the optional, default-off backend, ISA, and evaluation protocol. [#17](https://github.com/gaoyu06/native-obfuscator/pull/17) implements the initial integer slice; [#20](https://github.com/gaoyu06/native-obfuscator/pull/20) fixes dispatcher target validation; [#22](https://github.com/gaoyu06/native-obfuscator/pull/22) lowers the evaluation kernel's `mix` method; [#24](https://github.com/gaoyu06/native-obfuscator/pull/24) changes the generated method representation to compact hexadecimal byte blobs; and [#28](https://github.com/gaoyu06/native-obfuscator/pull/28) adds opt-in link-only publication of the transformed JAR and shared library without the generated C++ tree. | The implementation remains an open draft stack, default off, and integer-only. The three source-tree reader runs in [#21](https://github.com/gaoyu06/native-obfuscator/pull/21), [#23](https://github.com/gaoyu06/native-obfuscator/pull/23), and [#25](https://github.com/gaoyu06/native-obfuscator/pull/25) recovered both compared trees fully; the shared-library-only run in [#30](https://github.com/gaoyu06/native-obfuscator/pull/30) then recovered `add`, `sumTo`, and `mix` fully from the published `.so` without the C++ tree. | Stable shared-IR integration, broad opcode/runtime semantics, resource limits, wider differential tests, target/toolchain gates, and a human default/selection policy. |
| Automated-reader evaluation | [#21](https://github.com/gaoyu06/native-obfuscator/pull/21), [#23](https://github.com/gaoyu06/native-obfuscator/pull/23), and [#25](https://github.com/gaoyu06/native-obfuscator/pull/25) record three GPT-5.6 Sol reader runs on successive generated source-tree forms; both compared trees scored full in every run, and H0 was not rejected. [#30](https://github.com/gaoyu06/native-obfuscator/pull/30) records a fourth GPT-5.6 Sol run using the published `.so` alone: all three methods, including `mix`, scored full, so its shared-library-only H0 was rejected for the fixture. | All runs are `N=1` and tool-assisted. #21 was contaminated and `mix` fell back to direct C++; #23 had status-text contamination; #25 had limited procedural contamination and sequential carryover. #30 did not use generated C++ or the private compiler tree, but the reader knew method names and had recorded oracle input/output examples. | Independent readers, a frozen corpus, preregistered hypotheses, calibration, and uncontaminated repetitions would be required for a broader empirical claim. |

### Reader-eval evidence

The first three runs compared direct-C++ and interpreter-backend trees generated
from the same fixture revision, deferred the source/oracle comparison until
after both recoveries were written, and confirmed matching executable output.
The fourth run read the link-only published `.so` before opening the Java source
and used no generated C++ tree. The full/partial/fail scores below are the
recorded categorical outcomes, not a new derived metric.

| Run | What was controlled | What failed or limited the run | Measured outcome |
|---|---|---|---|
| [#21: first reader](https://github.com/gaoyu06/native-obfuscator/pull/21) | Same Java 8 class and compiler commit; interpreter tree read before direct C++; Java source reopened only after both recovery texts; Java, direct, and interpreter outputs matched. | `mix` fell back to method-specific direct C++ because the slice could not lower it. The reader had prior exposure to protocol text, fixture names, and opcode names, so the run was contaminated rather than unaided. | Interpreter and direct trees were both **full** for `add`, `sumTo`, and `mix`; H0 was not rejected. The `mix` result did not test opcode recovery. |
| [#23: blinded run after `mix` lowering](https://github.com/gaoyu06/native-obfuscator/pull/23) | Same preserved fixture for both trees; opcode recovery committed before direct recovery; fixture construction opened only afterward; `mix` was confirmed on the opcode path; both builds produced identical output. | A status-document query exposed method names and a fallback notice before recovery. No constants, instruction sequence, control flow, or source oracle was exposed, but the status-text contamination prevents an unaided claim. | Opcode and direct trees each scored **4 full / 0 partial / 0 fail**; H0 was not rejected. |
| [#25: compact-blob blinded run](https://github.com/gaoyu06/native-obfuscator/pull/25) | Same preserved fixture and source-last ordering; opcode and direct recoveries committed separately before opening fixture construction; original, direct, and opcode outputs matched exactly. | Class/build metadata and a fallback test name were exposed; the same reader handled the opcode condition before the direct condition. `divide` remained direct fallback. | Both trees scored **4 full / 0 partial / 0 fail**. The blobs plus `native_jvm_interp.cpp` were sufficient to recover all three lowered methods exactly; H0 was not rejected. |
| [#30: published-`.so`-only reader](https://github.com/gaoyu06/native-obfuscator/pull/30) | GPT-5.6 Sol received only the Linux x86-64 shared library published by #28. The directory contained the transformed JAR and `.so`, no `.cpp`; no generated C++ or private compiler tree was used. Recovery was written before Java source inspection, and published output matched the oracle. | The reader knew the names and signatures of `add`, `sumTo`, and `mix`; recorded oracle input/output examples were available as a consistency check. This is still one tool-assisted reader on one fixture and target. | `add`, `sumTo`, and `mix` all scored **full**. `mix` was recovered exactly, including constants, operation order, loop condition, shifts, multiply, xor, and rotate distance. The run rejected its “cannot recover critical logic from the published shared library alone” H0 for this fixture. |

These runs do not establish a population effect or equal reading effort. They
do establish the outcome for the tested fixture: **removing the C++ sources is
not sufficient while a decodable opcode stream and its opcode machine remain
in the shipped binary.** The current interpreter design does not meet the
GPT-5.6 Sol reader bar on this kernel, even when only the published `.so` is
provided.

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
| Keep reader outcomes scoped to the measured fixture and recorded limitations. | The four runs are `N=1`, tool-assisted case studies with different artifact boundaries and recorded limitations. Their full recoveries support the kernel-specific conclusion above, but not a population effect or broader claim. |

### Human decisions still required

#### Reader-eval maintainer options

- **A. Accept that this bar is out of scope for v1.** Ship only the
  compiler, compatibility, and SDK work that passes its own correctness and
  release gates.
- **B. Fund a different backend/product design.** **B1, link-only publication,**
  is already evidenced as insufficient by [#28](https://github.com/gaoyu06/native-obfuscator/pull/28)
  and [#30](https://github.com/gaoyu06/native-obfuscator/pull/30): removing the
  C++ tree still leaves a decodable opcode stream and its machine in the
  shipped library. The remaining B is a design that does not put a decodable
  opcode stream in the shipped library. If that design is not funded, drop the
  reader bar for v1 under A.
- **C. Keep iterating encodings.** This is likely wasted effort while the
  opcode machine and stream remain together in the generated tree or shipped
  library and the reader can recover method semantics from them.

**Recommendation:** choose A for v1 unless a new backend design that avoids a
decodable shipped opcode stream is explicitly funded. Link-only B1 is not that
design, and C is not recommended.

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
   [#14](https://github.com/gaoyu06/native-obfuscator/pull/14). This first
   establishes a real test oracle, then fixes the failures it exposes, then
   extends the corpus to JDK 21.
3. Merge [#10](https://github.com/gaoyu06/native-obfuscator/pull/10) before
   [#11](https://github.com/gaoyu06/native-obfuscator/pull/11), rebase #11 over
   #6/#10, and rerun both correctness and benchmark evidence. #11's mixed local
   result is not a speed gate.
4. Merge the IR stack exactly
   [#8](https://github.com/gaoyu06/native-obfuscator/pull/8) →
   [#13](https://github.com/gaoyu06/native-obfuscator/pull/13) →
   [#16](https://github.com/gaoyu06/native-obfuscator/pull/16). Rebase after #6
   so the duplicated JUnit-launcher change is resolved once. Do not squash away
   the #13 review fix or treat #16 as parity.
5. Merge [#12](https://github.com/gaoyu06/native-obfuscator/pull/12) →
   [#15](https://github.com/gaoyu06/native-obfuscator/pull/15) after resolving
   the same #6 launcher overlap. The Fable accept-with-nits review is not the
   human product/security approval listed above.
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

- There are no JDK 25 fixtures. The newest fixture branch used `javac 21.0.10`;
  parser support or a configured CI lane is not JDK 25 transpiler evidence.
- Four automated-reader evaluations have run, but each is an `N=1`,
  tool-assisted case study with recorded limitations. The first three produced
  full/full source-tree outcomes and did not reject H0; the fourth fully
  recovered all three methods from the published `.so` and rejected its
  shared-library-only H0. They support only the kernel-and-artifact conclusion
  in the reader-eval subsection.
- IR is opt-in and incomplete; legacy snippet codegen remains the default.
- #10's one local checksum-correct run shows the current transpiled-JNI path
  much slower than plain HotSpot for all three exact kernels: median ratios are
  about 18× for the integer loop, 23× for string concat/hash, and 199× for
  recursion. This is diagnostic evidence for those workloads, not a portable
  estimate, but it directly contradicts any present speedup claim.
- All referenced work is still in open draft PRs. `master` contains none of it.

## Before any production claim

A production claim requires evidence on the exact release commit and artifacts,
not the union of claims from draft branches:

1. Merge and review the applicable stacks, resolve overlaps, and rerun their
   full commands after rebasing. Required jobs must report actual test counts
   and artifacts, not merely a configured matrix or successful compilation.
2. Approve and publish the Java support dimensions and native target/toolchain
   tiers. Add feature-scoped JDK 25 fixtures built with a JDK 25 compiler, plus
   remaining required metadata, bootstrap, module/multi-release, refusal, and
   runtime cases.
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
   the #15 nits as appropriate, test all tier-1 targets and loaders (including
   Zig only if supported), and complete fuzzing, sanitizer, allocation,
   concurrency, license/provenance, SBOM, vulnerability/update, and security
   review gates.
6. If the interpreter ships, first submit and review its implementation against
   the shared IR, then prove differential parity, deterministic refusal,
   resource limits, format/version rejection, and target/toolchain behavior.
   Otherwise exclude it from the release claim.
7. Do not claim that the reader bar has been met: the four current runs report
   full recovery, and #30 recovered `mix` from the published `.so` without the
   C++ tree. Removing sources alone is therefore not sufficient for this
   fixture while the binary contains the opcode machine and stream. Any broader
   reader claim needs raw reproducible results whose scope and limitations
   support its exact wording, plus privacy and methodology approval. Such a
   claim is not a prerequisite if option A is selected.
8. Produce reproducible signed/provenanced artifacts, an SBOM and symbol
   allowlist, package/native-access documentation, crash/support and
   incident-response ownership, upgrade/rollback instructions, and final
   release approval for the residual compatibility, performance, and security
   risks.
