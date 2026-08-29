# IR Migration Plan

> **Historical plan.** The IR path it describes is on `master` as
> `--codegen=ir` (default still `legacy`). Later phase notes and
> [project-status.md](project-status.md) supersede the original PR-slicing
> schedule. This file is kept as the migration rationale, not as current
> “not implemented” status.

Companion to [`ir-compiler.md`](./ir-compiler.md) (the design) and
[`ir-examples.md`](./ir-examples.md) (worked examples). This document is the
file-by-file change list, the PR slicing, the risk register, and the rollback
story for moving `native-obfuscator` from string-template codegen to the IR
compiler.

The guiding constraint from the design: **the legacy snippet path and the new IR
path coexist behind a flag, and both stay green in CI for the entire migration.**
Nothing here removes the legacy path until the IR path has reached full parity and
soaked in CI.

---

## 0. Migration principles

1. **Flag-gated from day one.** A `--codegen=legacy|ir` option (default `legacy`)
   selects the path per run. No behavior changes for existing users until they opt
   in.
2. **Per-method fallback.** If the IR path throws `UnsupportedIrConstructException`
   (or any error) for a method, the tool logs it and falls back to the legacy path
   *for that method only*. This lets IR ship with partial opcode coverage without
   breaking any jar. The fallback is itself flag-controlled
   (`--ir-fallback=on|off`; `off` in CI parity jobs to surface gaps).
3. **Shared shell, divergent body.** The JNI signature, prologue, cache arrays,
   registration, and catch epilogue are extracted into a shared emitter used by
   both paths, so the two paths differ only in the method body. This keeps the JNI
   ABI identical and shrinks the regression surface.
4. **Runtime ABI frozen.** `native_jvm.{cpp,hpp}`, `string_pool.*`, the loader
   classes, and `__ngen_register_methods` do not change in phases 1–6. The IR emits
   calls into the *existing* `utils::*` contract.
5. **Every PR is independently green.** Each PR either adds inert code (not yet
   wired) or extends the flag-gated path; the default path is untouched until the
   final flip.

---

## 1. File-by-file change list

Legend: **ADD** = new file, **MOD** = modified file, **UNCHANGED (reused)** =
depended on but not edited. Paths are relative to repo root.

### 1.1 New IR sources (all ADD, package `by.radioegor146.ir`)

Under `obfuscator/src/main/java/by/radioegor146/ir/`:

- **ADD** `IrMethod.java`, `IrBlock.java`, `IrValue.java`, `IrInstruction.java`,
  `IrTerminator.java`, `IrPhi.java`, `BlockId.java` — core IR data structures
  (§3 of the design).
- **ADD** `type/IrType.java`, `type/RefType.java`, `type/Nullability.java` — the
  type system (§4).
- **ADD** `node/*.java` — one class (or a small sealed hierarchy) per node listed
  in §11 of the design: `Const`, `BinOp`, `UnOp`, `Convert`, `Compare`,
  `LocalLoad`, `LocalStore`, `StackCopy`, `ArrayLoad`, `ArrayStore`, `ArrayLength`,
  `NewArray`, `MultiNewArray`, `GetField`, `PutField`, `GetStatic`, `PutStatic`,
  `Invoke`, `New`, `CheckCast`, `InstanceOf`, `MonitorEnter`, `MonitorExit`,
  `Throw`, `Return`, `Branch`, `Switch`, `Goto`, `CaughtException`, `EnvIntrinsic`,
  `StringConst`, `ClassRef`, `MethodRef`, `FieldRef`.
- **ADD** `frontend/CfgBuilder.java`, `frontend/StackToSsa.java`,
  `frontend/AsmToIr.java` — build the IR from a preprocessed `MethodNode`
  (§7 passes 1–2). `StackToSsa` absorbs the stack-effect tables currently spread
  across `instructions/*Handler.getNewStackPointer`.
- **ADD** `pass/Pass.java`, `pass/PassManager.java`, and one class per §7 pass:
  `CfgConstructionPass`, `SsaBuildPass`, `TypeAssignPass`, `IrVerifier`,
  `InvokeLoweringPass`, `CacheMaterializationPass`, `ExceptionEdgePass`,
  `ConstFoldDcePass`, `NullCheckEliminationPass`, `LocalRefLivenessPass`,
  `SlotAllocationPass`.
- **ADD** `emit/CType.java`, `emit/CExpr.java`, `emit/CStmt.java`,
  `emit/CEmitter.java`, `emit/CPrinter.java`, `emit/RuntimeCalls.java`,
  `emit/MethodShellEmitter.java` — the structured emitter (§8).
- **ADD** `backend/MethodLoweringStrategy.java`, `backend/DirectCppStrategy.java`,
  `backend/LoweredMethod.java`, `backend/LoweringContext.java` — the backend hook
  (§9.3). Only `DirectCppStrategy` is implemented; the interpreter-stream strategy
  is left as the documented interface.
- **ADD** `IrMethodCompiler.java` — orchestrator; the IR counterpart of
  `MethodProcessor.processMethod`, producing identical `MethodContext` outputs.
- **ADD** `IrCodegenMode.java` (or reuse an enum in `Main`) — `LEGACY` / `IR`.

### 1.2 Wiring changes to existing Java (MOD)

- **MOD** `Main.java` — add `--codegen` (default `legacy`), `--ir-fallback`
  (default `on`), and optionally `--dump-ir=<dir>` for golden-test capture. Thread
  the mode into `NativeObfuscator.process(...)`. Only new CLI options; existing
  flags untouched. (`obfuscator/src/main/java/by/radioegor146/Main.java`.)
- **MOD** `NativeObfuscator.java` — accept the codegen mode; construct either
  `MethodProcessor` (legacy) or `IrMethodCompiler` (IR); in the per-method loop
  call the selected compiler and apply per-method fallback. The class/loader/jar
  assembly around it is unchanged.
  (`by/radioegor146/NativeObfuscator.java`, the loop around lines 253–277.)
- **MOD** `MethodProcessor.java` — extract the prologue/epilogue (signature,
  `clazz`/`classloader`/`lookup` setup, try-catch class caching, `jvalue` slot
  decls, `refs` decl, argument load, catch dispatch) into
  `emit/MethodShellEmitter` and call it from the legacy path. Legacy body walk is
  otherwise unchanged; this is a refactor to enable sharing, done in its own PR and
  verified by the existing suite before any IR body exists.
- **MOD** `MethodContext.java` — add an optional handle to the in-progress
  `IrMethod` and the codegen mode. Minimal; the field is null on the legacy path.

### 1.3 Reused unchanged (UNCHANGED — depended on, not edited)

These are load-bearing for the IR path but require no edits:

- `bytecode/PreprocessorRunner`, `IndyPreprocessor`, `LdcPreprocessor`,
  `MethodHandleUtils`, `PreprocessorUtils`, `Preprocessor` — the IR consumes their
  output (ordinary bytecode + magic-marker intrinsics). See §4 for why they stay at
  the bytecode level initially.
- `special/*` (`ClInitSpecialMethodProcessor`, `DefaultSpecialMethodProcessor`,
  `SpecialMethodProcessor`) — operate on `MethodNode` rewriting and proxy setup,
  independent of body codegen; called identically from both paths.
- `source/ClassSourceBuilder`, `CMakeFilesBuilder`, `MainSourceBuilder`,
  `StringPool` — class assembly and cache-array layout are unchanged.
- `NodeCache`, `CachedMethodInfo`, `CachedFieldInfo`, `CatchesBlock`, `LabelPool`,
  `HiddenMethodsPool`, `HiddenCppMethod` — reused by the IR emitter (the IR wraps
  `NodeCache` behind the `StringConst`/`ClassRef`/`MethodRef`/`FieldRef` nodes).
- `Platform`, `Util`, `ru.gravit.launchserver.asm.*` — unchanged.
- `zig/*`, `compiletime/*` (loaders) — unchanged.
- `resources/sources/native_jvm.{cpp,hpp}`, `native_jvm_output.{cpp,hpp}`,
  `string_pool.{cpp,hpp}`, `CMakeLists.txt` — runtime ABI frozen in phases 1–6.

### 1.4 Eventually deprecated (only after full parity)

- `instructions/*` (`GenericInstructionHandler` and the per-opcode handlers) and
  `Snippets` + `resources/sources/cppsnippets.properties` remain the legacy path
  and are **not** deleted during migration. They are removed only in the final
  cleanup PR, after the IR path has been the default and soaked in CI, and only if
  the team decides to drop legacy entirely (a human decision — see §6).

### 1.5 Build / CI / tests

- **MOD** `obfuscator/build.gradle` — no new runtime dependencies (ASM 9.8 already
  present covers the frontend). Add a test source set convenience for IR golden
  files if desired; optionally a `-Pcodegen=ir` project property that the parity
  test reads. (`obfuscator/build.gradle`.)
- **MOD** `.github/workflows/main.yml` — add the IR path to CI. Two-stage:
  1. While IR is partial: a **non-blocking** extra invocation/job that runs the
     suite with `--codegen=ir --ir-fallback=on` (informational).
  2. At parity: a **blocking** matrix axis `codegen: [legacy, ir]` so both paths
     gate merges across JDK 8/11/17/21/25 × Ubuntu/macOS/Windows.
- **ADD** tests under `obfuscator/src/test/java/by/radioegor146/ir/`:
  IR snapshot/golden tests, per-pass unit tests, emitter compile tests
  (§10 of the design).
- **ADD** an IR variant of the end-to-end run. Two options; the plan recommends
  (a): (a) parameterize `ClassicTest`/`TestsGenerator` to run each fixture under
  both codegen modes; or (b) add a parallel `IrClassicTest`. Option (a) reuses the
  entire existing harness (compile → transpile → CMake/Zig build → diff stdout).

---

## 2. PR slicing

Each PR is small, independently reviewable, and leaves `master` green with the
default (legacy) behavior unchanged until PR-8.

- **PR-1 — Shell extraction (refactor, no IR).**
  Extract prologue/epilogue from `MethodProcessor` into `MethodShellEmitter`; have
  the legacy path use it. Pure refactor; gate is the existing suite passing
  unchanged. De-risks every later PR because both paths will share this code.

- **PR-2 — IR core + frontend, not wired.**
  `IrMethod`/`IrBlock`/`IrValue`/nodes, `type/*`, `CfgBuilder`, `StackToSsa`,
  `AsmToIr`, `IrVerifier`, plus a textual IR dumper. No emitter, not reachable from
  `Main`. Gate: IR golden tests + verifier over a fixture corpus. Zero risk to
  production (inert code).

- **PR-3 — Emitter + flag, minimal opcode subset.**
  `emit/*`, `IrMethodCompiler`, `--codegen`/`--ir-fallback` in `Main` and
  `NativeObfuscator`. Support only the low-level tier + control flow
  (const/arith/compare/convert/local/branch/goto/return). Everything else falls
  back to legacy per-method. Gate: differential parity on fixtures that only use
  the supported subset; full suite still green because fallback covers the rest.

- **PR-4 — Object model: fields, invokes, new/array.**
  `GetField/PutField/GetStatic/PutStatic`, `Invoke*`, `New`, `NewArray`/`ANEWARRAY`,
  `ArrayLoad/Store/Length`, `CheckCast`/`InstanceOf`, `CacheMaterialization`
  (parity mode), `InvokeLoweringPass`. Brings most non-exception methods to parity.
  Gate: differential parity expands to cover these fixtures.

- **PR-5 — Exceptions + remaining opcodes.**
  `ExceptionEdgePass`, `CaughtException`, handler dispatch, `Throw`, `Monitor*`,
  `TableSwitch`/`LookupSwitch`, `MultiNewArray`, and the method-handle/indy
  intrinsics (`link_call_site`, `invoke_reverse`) surfaced from the preprocessors.
  Gate: differential parity on the full `test_data` corpus with `--ir-fallback=off`
  in a non-blocking CI job.

- **PR-6 — Optimization passes (perf, output still correct).**
  `ConstFoldDcePass`, `NullCheckEliminationPass`, `LocalRefLivenessPass`
  (JNI-transition minimization), cache hoisting/dedup in `CacheMaterializationPass`,
  optional typed-scalar `SlotAllocationPass`. Gate: parity unchanged; add
  microbenchmarks / code-size deltas to the PR description. Each pass individually
  toggleable so a regression can be bisected to one pass.

- **PR-7 — CI promotion.**
  Flip the IR CI job from non-blocking to a blocking `codegen: [legacy, ir]` axis
  once PR-5/PR-6 show sustained parity. No product default change yet.

- **PR-8 — Default flip (reversible).**
  Change the `--codegen` default to `ir` (legacy still selectable). Announce in
  README. This is the only PR that changes default behavior; it is a one-line
  default change and trivially revertible.

- **PR-9 (optional, later, human-gated) — Legacy removal.**
  Delete `instructions/*`, `Snippets`, `cppsnippets.properties`, legacy branches.
  Only if the team decides to stop supporting the snippet path. Not required for
  the IR to be the default.

Dependency order: PR-1 → PR-2 → PR-3 → PR-4 → PR-5 → PR-6 → PR-7 → PR-8 → (PR-9).
PR-2 can be developed in parallel with PR-1 since it is inert.

---

## 3. Risk register

| # | Risk | Likelihood | Impact | Mitigation |
| --- | --- | --- | --- | --- |
| R1 | IR miscompiles a method (wrong stdout) | Med | High | Differential parity suite is the gate; per-method fallback; `IrVerifier`; ship IR off-by-default until PR-8. |
| R2 | Stack-to-SSA mishandles long/double 2-slot values or `DUP*`/`SWAP` | Med | High | Reuse the proven `getNewStackPointer` tables as the stack-effect model; dedicated golden tests for `DUP2_X2`, `LSTORE`, `SWAP`; seed from ASM frames. |
| R3 | Exception-edge model diverges from the `$trycatchhandler` semantics (handler order, rethrow, `finally`) | Med | High | Model handler lists exactly as `CatchesBlock` does today; parity tests with nested/`finally` fixtures; keep dispatch dedup identical. |
| R4 | Local-ref liveness frees a reference too early → use-after-free / JVM crash | Med | High | Default to bulk `refs`/`clear_refs` (today's behavior) in phase 1; enable precise deletion only in PR-6 behind a sub-flag, with escape analysis excluding returned/stored/passed refs; stress fixtures under `-Xcheck:jni`. |
| R5 | Cache hoisting changes lazy-init timing / thread-safety | Low | High | Preserve the mutex+weak-global-ref init verbatim; only change *where* it is emitted, guarded by dominance; keep per-access init in parity mode. |
| R6 | `invokedynamic`/condy parity across platforms (HotSpot vs std_java vs android) | Med | Med | Keep `IndyPreprocessor`/`LdcPreprocessor` at bytecode level so both paths share identical indy handling; parity tests run over all `Platform` values (as `ClassicTest` already does). |
| R7 | JDK 8–25 behavioral differences surface in IR path | Low | Med | CI already spans JDK 8/11/17/21/25; IR axis runs the same matrix; no source/target level change (`build.gradle` stays Java 8). |
| R8 | Prologue extraction (PR-1) subtly changes emitted text | Low | High | PR-1 is a pure refactor gated by byte-identical output on the existing suite; land it before any IR body. |
| R9 | Scope creep / half-landed compiler | Med | Med | Strict PR slicing; each PR independently green; fallback keeps every intermediate state shippable. |
| R10 | Two code paths increase maintenance cost during migration | High | Low | Accepted, time-boxed by the plan; shared shell limits duplication; PR-9 removes legacy once IR is trusted. |
| R11 | Emitter produces C++ that fails to compile on one toolchain (MSVC/clang/gcc/zig) | Low | Med | Emitter reuses the exact call shapes already compiling today; emitter compile tests + full CMake/Zig build in CI on all three OSes. |

---

## 4. Why `invokedynamic`/condy stay at the bytecode level (initially)

`IndyPreprocessor` and `LdcPreprocessor` already lower `invokedynamic`, method
handles, and `condy` into ordinary bytecode plus a handful of magic-marker static
calls (`PreprocessorUtils.LOOKUP_LOCAL`, `CLASSLOADER_LOCAL`, `CLASS_LOCAL`,
`LINK_CALL_SITE_METHOD`, `INVOKE_REVERSE`) that `MethodHandler` recognizes. This
logic is platform-specific (HotSpot vs std_java/android), battle-tested, and
orthogonal to codegen. Keeping it as a **frontend normalization** stage means:

- the IR frontend only ever sees ordinary invokes + the intrinsics, which it models
  as `EnvIntrinsic`/`Invoke` nodes (recognized structurally, not by string owner);
- both codegen paths get identical indy behavior for free, eliminating a whole
  class of parity risk (R6);
- moving indy lowering *into* the IR later is a clean, optional follow-up, not a
  migration blocker.

---

## 5. Rollback

Rollback is cheap because the legacy path is never removed during migration.

- **Per-run:** users pass `--codegen=legacy` (the default before PR-8) to get
  exactly today's behavior. After PR-8, `--codegen=legacy` is still available.
- **Per-method:** `--ir-fallback=on` (default) silently falls back to legacy for
  any method the IR cannot yet handle, so a jar always transpiles.
- **Per-pass:** each *(opt)* pass (PR-6) has an individual toggle, so a suspected
  regression can be disabled without reverting the pass.
- **Revert the default flip:** PR-8 is a one-line default change; reverting it (or
  setting the default back to `legacy`) instantly restores prior behavior with no
  data migration.
- **Revert a PR:** because each PR is additive and flag-gated (except PR-1's
  refactor, which is byte-identical-gated), reverting any single PR leaves the
  default path working.

The only irreversible step is PR-9 (legacy deletion), which is explicitly optional,
human-gated, and sequenced last.

---

## 6. Decisions that need a human

These are called out here and summarized in the PR body. The design already makes a
recommendation for each; a human must ratify before implementation.

1. **IR flavor** — the design recommends *typed CFG + block-local SSA with phi
   (hybrid)*. Alternatives (formalized stack-machine, or full global SSA) are
   viable; this choice shapes every later PR. **Needs human sign-off.**
2. **Compatibility vs performance posture** — how aggressive the phase-6 passes
   should be by default (e.g. precise local-ref deletion, check elimination). Safer
   defaults = closer to legacy behavior and lower crash risk; aggressive defaults =
   more of the README's performance gap closed. **Needs human sign-off.**
3. **When (and whether) to delete the legacy snippet path (PR-9)** — keeping it is
   a permanent maintenance cost; removing it drops a fallback. **Needs human
   decision.**
4. **CI cost** — doubling the matrix (`codegen: [legacy, ir]`) roughly doubles CI
   time for the affected jobs. Acceptable, or should the IR axis run on a reduced
   JDK/OS subset? **Needs human decision.**
5. **Package/type names** under `by.radioegor146.ir` (design §11) — proposed, open
   to rename before code lands.

Decisions already made in the design (with rationale), not requiring a human unless
they object:

- Reuse `IndyPreprocessor`/`LdcPreprocessor` unchanged as frontend normalization
  (§4) — lowest-risk path to indy parity.
- Freeze the runtime `utils::*` ABI and cache-array layout for phases 1–6 — keeps
  the JNI contract stable and the blast radius small.
- Keep `goto`+labels emission (no relooper) — matches the existing `LabelPool`
  model and C++ target.
- Default `--codegen=legacy` until parity + soak — no user-visible change during
  migration.
