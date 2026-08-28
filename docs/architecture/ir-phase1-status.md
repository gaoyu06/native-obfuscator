# IR phase 1 status

This branch is Sol Extra High Fast implementing and cross-checking Fable IR
phase 1. It is an opt-in compiler slice, not a production-readiness claim.

## Landed

- `--codegen=legacy|ir`, with `legacy` as the CLI and API default.
- The existing `NativeObfuscator.process(...)` signature remains available. A
  trailing `CodegenMode` overload selects the new path for API callers.
- A typed normal-edge CFG with explicit blocks, typed SSA values, block-local
  local/stack phis, integer constants/add/subtract/multiply, JVM locals,
  integer conditional branches, `goto`, and integer/void returns.
- An ASM `MethodNode` frontend with explicit capability checks and
  method/instruction diagnostics.
- A small structured C++ AST and direct emitter. IR method bodies do not call
  `Snippets.getSnippet` and do not read `cppsnippets.properties`.
- A shared `MethodShellEmitter` used by the legacy and IR paths for the JNI
  signature, common prologue, registration mutation, default return, and special
  method postprocessing. Legacy-only `jvalue` argument loading and catch snippets
  remain isolated in the legacy mode.
- Fast JUnit tests construct ASM methods for `add` and `sumTo`, inspect the IR,
  and inspect emitted C++ without invoking CMake, a C++ compiler, or native code.

## Safe fallback

The IR frontend validates the whole method, including unreachable instructions,
and the structured body is built before the shared shell mutates the
`MethodNode`, native registration, or hidden-method state. A capability miss
therefore logs the class, method, descriptor, reason, and bytecode instruction,
then invokes the legacy generator for that method only. Unexpected compiler
errors are not mislabeled as capability misses and remain hard failures.

Phase 1 accepts methods with JVM int-carrier parameters and integer/void return,
integer constants, `ILOAD`/`ISTORE`/`IINC`, `IADD`/`ISUB`/`IMUL`, integer
branches, `GOTO`, and returns. Exception tables, references in descriptors,
object/JNI operations, switches, conversions, wide primitive values, and all
other opcodes fall back.

## Adjustments from the Fable design

- Fable's package map is an end-state inventory. This slice uses a compact node
  hierarchy and emits only the agreed `add`/`sumTo` vertical slice; empty pass,
  runtime-call, cache, backend-hook, and interpreter classes were not added.
- Local loads/stores are consumed by the SSA environment rather than retained as
  redundant IR nodes. Local and operand-stack state become typed block phis at
  every non-entry merge.
- Fable proposed catching any IR error for fallback. This implementation catches
  only `UnsupportedIrConstructException`, and only before method mutation.
  Falling back after an internal error could run legacy codegen on partially
  mutated state and hide a compiler defect.
- Arithmetic emission casts operands through `juint` before add/subtract/multiply
  and casts the result back to `jint`, preserving JVM 32-bit wraparound without
  C++ signed-overflow undefined behavior.
- The phase-one C++ is direct label/goto CFG output. A relooper and interpreter
  backend remain out of scope.

## Commands and evidence

Exact verification commands and outcomes are recorded here after the branch's
required pre-test commit and push.
