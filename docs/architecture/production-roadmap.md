# Production roadmap

## Status and scope

This document is a production plan, not a claim that the current transpiler is
production-ready. Sections below that cite “evidence from `master` @ `e7ca4c8`”
were written before preferred tips landed. After
[#118](https://github.com/gaoyu06/native-obfuscator/pull/118) / `master` @
`e997d71`, the IR compiler through phase 18, the JDK 17 IR runtime repair, the
C++ SDK through AES-256-GCM, the E2E harness, and the bench harness are in the
tree. The CLI default is still `legacy`. Requirement 7 is still unmet. See
[project-status.md](project-status.md) for current measurements.

This file remains a plan. It does not by itself implement remaining work
(evaluator on the phase-18 tip, interpreter backend, broader JDK corpus,
support badges).

The product goal has two supported paths:

1. Selected platform-independent Java logic (primitive arithmetic, control
   flow, arrays, and a defined subset of string operations) becomes structured,
   compiled C++ rather than a sequence of JNI calls that imitates bytecode.
2. Hashing, encoding, and cryptographic-style operations are available through
   a small native SDK with a stable C ABI, C++ implementation, and Java bindings
   through JNI first and an optional FFM adapter on supported JDKs.

Correctness is the first gate. Native code is not presumed faster, safer, or
harder to analyze until the corresponding harness measures it.

## Evidence from the repository at `e7ca4c8` (historical)

The following observations were from `master` at `e7ca4c8`, before #118.
Several of them are now false on current `master` (classfile version 52
stamping; “IR not implemented”). Keep them as the pre-landing baseline:

- `MethodProcessor` walks ASM instructions linearly and maintains a numeric
  simulated stack pointer.
- `GenericInstructionHandler` selects an opcode name and substitutes strings
  from `cppsnippets.properties` through `Snippets`.
- Generated methods allocate one `jvalue` variable per stack/local slot and use
  JNI for Java object, field, array, and method operations.
- `NativeObfuscator` rewrites every processed class to class-file version 52.
  That is not a valid general lowering strategy for post-Java-8 class-file
  semantics.
- `IndyPreprocessor` rewrites `invokedynamic`; `InvokeDynamicHandler` deliberately
  throws if an indy reaches code generation. `LdcHandler` does not accept ASM
  `ConstantDynamic`.
- `ClassicTest` does perform a useful end-to-end comparison: compile Java,
  transpile, compile C++, run, and compare stdout. Its corpus and comparison
  oracle are too narrow for a production compatibility claim.
- `.github/workflows/main.yml` names JDK 8, 11, 17, 21, and 25 on three operating
  systems. At review time the repository exposed no GitHub Actions run history
  through the GitHub API, so this document does not call that matrix green.
- The README itself says only Java 8 is fully supported and warns that current
  output can be significantly slower.

These facts make an IR and a compatibility oracle prerequisites, not optional
cleanup.

## Target architecture

```text
input JAR
  -> archive/class-file validation and capability report
  -> ASM parse (preserve class-file version and attributes)
  -> bytecode verifier/type analysis
  -> JVM semantic IR
       -> deterministic structured C++ backend -> C++ compiler -> shared library
       -> optional in-process interpreter backend -> shared library
  -> RegisterNatives-based Java/JNI boundary and output JAR

explicit Java SDK API
  -> stable C ABI -> C++ SDK core
       -> JNI adapter (JDK 17 baseline)
       -> optional FFM adapter (standard API on JDK 22+)
```

The core compiler must not depend on `Snippets`, token substitution, or
`cppsnippets.properties`. Backends consume a typed IR and return structured
diagnostics. The old generator may exist behind an explicit migration flag only
until the parity gate; the production end state deletes it.

### JVM semantic IR

Start with a typed control-flow graph whose block parameters represent incoming
locals and operand-stack values. Do not start by forcing every value into a
global SSA graph. The IR must represent:

- Java value kinds: `i32`, `i64`, `f32`, `f64`, reference/null, and verifier
  states for uninitialized objects;
- basic blocks, normal successors, exception successors, and ordered catch
  regions;
- explicit field, array, allocation, monitor, class-initialization, invoke, and
  type-check operations;
- Java-specific integer overflow, shift masking, division corner cases,
  floating-point/NaN conversion, and exception ordering;
- ownership/lifetime facts for JNI local and global references;
- original class, method, descriptor, bytecode offset, and source line for every
  operation;
- declared effects (`pure`, `may_throw`, `may_allocate`, `jni_call`,
  `synchronizes`) so later optimizations cannot move observable behavior;
- deterministic serialization used by golden tests and both backends.

SSA can be introduced as an optimization form for verified regions after block
and exception semantics are stable. Unsupported `jsr`/`ret`, preview class
files, malformed frames, and unsupported constants must fail before output with
a class/method/bytecode-offset diagnostic.

### Structured C++ backend

The first backend emits direct typed C++ operations for primitive values and
structured branch/loop constructs where reducible control flow permits it.
Irreducible graphs may use a documented block dispatcher; arbitrary string
fragments are not an escape hatch.

JNI is a boundary/runtime service, not the representation of every opcode.
Java-dependent operations remain explicit runtime calls. Eligible pure regions
can use C++ scalars and bounded array/string views between one entry conversion
and one exit conversion. Generated code must use fixed-width types and helper
functions where C++ undefined or implementation-defined behavior differs from
the JVM.

Every output includes a machine-readable compilation manifest: tool version,
input digest, class-file versions, selected methods/backends, rejected methods
and reasons, compiler/toolchain identity, target triple, and SDK ABI version.

### Optional in-process interpreter backend

This is a compiler backend and runtime execution mode, not a packer.

- It consumes the same verified IR and emits compact, versioned instruction and
  metadata tables into the normal native library.
- A small typed-slot dispatch loop executes those tables in process and calls
  the same reviewed JNI runtime services as the C++ backend.
- Selection is explicit per build and optionally per method, for example
  `--backend=cpp`, `--backend=interpreter`, or a checked mapping file.
- The output manifest records the selection; debug builds can map interpreter
  PCs back to bytecode offsets.
- No encrypted payload, self-modification, executable-memory generation,
  anti-debugging behavior, process injection, or custom archive/packer is in
  scope.
- The intended uses are compatibility fallback, code-size experiments, and
  differential testing. No performance or analysis-resistance claim is made.

The interpreter is implemented only after the IR and direct C++ vertical slice.
Building it first would preserve the current one-opcode-at-a-time model and
would not prove the primary “real native code” path.

## Release gates

### Correctness and compatibility

- JDK 17 is the required production baseline.
- JDK 21 and 25 run the same required semantic corpus; they remain evaluation
  lanes until their feature rows are covered and passing.
- JDK 8 and 11 are explicit legacy profiles, never inferred from JDK 17 results.
- Each selected method is run in reference Java, generated C++, and (when
  enabled) interpreter modes with equal inputs and an oracle that compares
  return values, thrown type/message where contractual, state changes, stdout,
  and process exit.
- Dedicated fixtures cover class-file versions and attributes, nestmates,
  records, sealed classes, string-concat indy, lambdas, custom indy,
  `ConstantDynamic`, multi-release JARs, modules, exceptions, monitors, arrays,
  class initialization, reflection-visible metadata, and Unicode.
- Negative tests prove deterministic refusal and prove that no partial output
  JAR is presented as successful.
- Sanitizer builds, JNI `-Xcheck:jni`, native static analysis, and compiler
  warnings-as-errors are clean on supported native targets.

### Performance

Performance is measured only after semantic equivalence:

- JMH compares reference Java, current generator during migration, IR-to-C++,
  interpreter, and explicit SDK calls.
- Workloads separate JNI transition cost, pure scalar/control-flow kernels,
  bounded array/string kernels, and SDK operations at multiple input sizes.
- A native microbenchmark layer measures the C++ kernel without Java so JNI and
  algorithm costs can be distinguished.
- An end-to-end job records transpile time, native compile time, package size,
  cold startup/load, warm steady-state throughput, allocations/GC, and peak RSS.
- Results are raw JMH JSON plus environment metadata, not hand-written tables.
  Forks, warmup, measurement duration, JVM flags, CPU governor, toolchains, and
  confidence intervals are recorded.
- CI first detects correctness and large regressions. Performance release
  thresholds are set by a human only after repeatable baselines exist.

The existing `Calc` timer is retained only as a compatibility workload. It is
not accepted as benchmark evidence.

### Native SDK and security

- The API and ABI are versioned independently.
- Crypto implementation selection, license, provenance, SBOM, known-answer
  vectors, fuzzing, side-channel review, and update policy pass security review.
- The compiler does not recognize and silently replace arbitrary user crypto
  bytecode. Native SDK use is explicit by API or reviewed annotation.
- Critical JNI array/string access is allowed only for short, bounded regions
  that do not make blocking or JNI calls; copy or direct-buffer paths are the
  safe default until measurements justify another policy.
- No raw address allocation/free API is exposed in v1.

## Recommended PR sequence

“Ship-ready?” means whether that PR's own scoped feature can be merged and
released after its listed gates. It does not mean the complete product roadmap
is finished. Every code PR requires human review even where implementation can
proceed autonomously.

| PR | Depends on | Scope | Ship-ready? | Review required? | Review preconditions |
|---|---|---|---|---|---|
| 1. Compatibility oracle and corpus | this design PR | Add fixture builder, reference-vs-output oracle, capability/failure taxonomy, JDK 17 required lane, and 8/11/21/25 feature lanes. No compatibility claim yet. | Yes, as test infrastructure | Yes | Human approves supported-profile definitions and oracle fields; CI evidence includes each matrix cell rather than one aggregate job. |
| 2. Reproducible benchmark harness | PR 1 fixture conventions | Add JMH variants, native microbench entry points, end-to-end transpile/compile/run driver, JSON artifacts, and environment capture. Publish no speed claim. | Yes, as measurement infrastructure | Yes | Benchmark reviewer approves workloads, isolation, forks, statistics, and correctness checks; at least two repeat runs are comparable. |
| 3. Typed CFG IR frontend | PR 1 | Add bytecode capability scan, verifier-aware typed block IR, exception edges, deterministic IR dumps, and golden/property tests. Existing backend remains default. | No product behavior; safe behind an internal flag | Yes | Compiler reviewer approves IR invariants, unsupported-input diagnostics, verifier states, and exception model; corpus produces stable IR. |
| 4. Direct C++ backend vertical slice | PRs 2–3 | Emit primitive arithmetic, conversions, locals, branches, switches, returns, and pure calls from IR. Run differential tests and benchmarks; opt-in only. | Experimental only | Yes | IR invariants are frozen for the slice; semantic differential suite passes on JDK 17; generated C++ passes sanitizers and warnings. |
| 5. Java-object semantics and legacy codegen retirement | PRs 3–4 | Add arrays, strings, fields, invokes, allocation, class init, exceptions, monitors, JNI reference lifetime, and remaining supported ops; migrate all supported methods; delete `Snippets`, `GenericInstructionHandler`, and `cppsnippets.properties`. | Candidate only after all gates | Yes | No supported opcode uses legacy output; negative cases refuse safely; required compatibility matrix and sanitizer/JNI checks pass. |
| 6. SDK ABI and first primitives | PR 2; API/security decisions | Add versioned C ABI/C++ headers, Java API, `RegisterNatives` JNI adapter, capabilities, hex/Base64, SHA-256, and constant-time byte equality using an approved implementation. | Yes only as opt-in SDK | Yes—API and security | Human freezes v1 semantics and dependency; known-answer, malformed-input, fuzz, allocation-failure, concurrency, ABI, and packaging tests pass. |
| 7. Optional FFM adapter | PR 6 | Add a separate JDK 22+ adapter artifact over the same C ABI; test 22, 25, native-access configuration, and parity with JNI. Do not use JDK 21 preview APIs in a release artifact. | Optional/experimental first | Yes | Human confirms supported JDK floor and distribution shape; JNI/FFM parity and startup diagnostics pass. |
| 8. In-process interpreter backend | PRs 3 and 5 | Implement the designed typed instruction format, dispatcher, runtime calls, per-method selection, source maps, limits, and differential tests. No packing features. | Experimental until parity/limits pass | Yes—compiler and runtime | IR semantics are stable; format/versioning and resource limits are approved; C++/interpreter/reference differential suite passes. |
| 9. Automated-analysis quality harness | PR 4 for first artifacts; PR 8 for interpreter comparison | Add frozen tasks, artifact variants, blinded analyzer runners, scoring, provenance, leakage checks, raw results, and statistical report generation. | Yes, as evaluation tooling; no resistance claim | Yes—methodology/privacy | Dataset license and prompt/tool policy approved; task keys are separated; repeated runs and human spot-check calibration succeed. |
| 10. Production hardening and release candidate | PRs 1–9 as applicable | Reproducible builds, signed/provenanced artifacts, SBOM, crash diagnostics, support policy, upgrade/rollback docs, fuzzing budget, platform CI, and release gates. | Yes if every required gate passes | Yes—release/security/operations | Named owners accept compatibility, performance thresholds, SDK security review, native targets, incident response, and residual risks. |

### Dependency and concurrency notes

- PR 1 is the first code PR. A compiler rewrite without a trustworthy oracle
  only changes which bugs are invisible.
- PR 2 can proceed in parallel with PR 3 once fixture identity and output
  conventions from PR 1 are fixed.
- SDK ABI design can proceed while the compiler IR is built, but PR 6 is blocked
  on the human API/dependency decisions and the benchmark harness.
- Evaluation dataset design can proceed without generated artifacts; scoring a
  backend waits for stable artifacts.
- PR 8 must not block the direct C++ path and must not become the default until
  separately gated.
- PR 10 is the only production release decision. Passing one JDK or one native
  target cannot satisfy it.

## Work that can proceed without a human decision

An agent can prepare fixture generators, deterministic manifests/IR snapshots,
diagnostic schemas, JMH plumbing, raw-result storage, CMake/Zig test wiring,
negative tests, and prototypes behind disabled flags. It can also collect
evidence for each decision in `human-decision-matrix.md`.

Human approval is required before freezing public API/ABI, choosing and shipping
a cryptographic dependency, dropping JDK/runtime or native-platform support,
setting benchmark thresholds, changing default backend behavior, declaring a
compatibility tier production-ready, or making claims based on automated
analysis evaluation.

## Definition of production-ready

Production-ready means all applicable PR 10 preconditions are evidenced on the
exact release commit and artifacts. It does not mean “compiles on the CI
matrix,” “passes the historical stdout tests,” or “generated native code.” A
release must publish its supported JDK/class-file/native-target matrix,
unsupported-feature diagnostics, benchmark methodology and raw results,
security/SBOM material, deterministic build inputs, and rollback procedure.
