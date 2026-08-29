# Fable review: IR phase 19 — long bitwise and shifts (PR #128)

- **Reviewed tip:** `origin/cursor/ir-compiler-phase19-6d81` at
  `720205d3645f49d4fabcf6d98bb13db3cfbdf733`
- **Base:** `master` at `e997d71c7525a4c607e29b6eb1ae9140a72dfd22`
- **Verdict: accept with nits.** No blocking correctness bug found. The nits
  below are documentation/test-shape observations; no code change is required.

## Scope of the change

PR #128 admits the JVM long bitwise and shift family into the opt-in direct
IR compiler (`--codegen=ir`):

- `LAND`, `LOR`, `LXOR` extend the existing `IrNodes.LongBinary` node.
- `LSHL`, `LSHR`, `LUSHR` get a new `IrNodes.LongShift` node.
- `IrCppEmitter` emits the six operations; `AsmToIr` gains the admission,
  stack-typing, and lowering paths; `IrMethod` gains pretty-printing for the
  new node.
- Two new focused tests plus assertions in the integrated smoke test.
- `PR_BODY.md` and `docs/architecture/ir-phase19-status.md` document the
  increment.

No CLI, evaluator, reader, `--ir-lower`, classfile-version, or
constructor-restoration files are touched.

## Checklist findings

### 1. `LAND`/`LOR`/`LXOR` take two I64 operands — verified

`LongBinary`'s constructor enforces `requireI64` on result, left, and right
(`IrNodes.java`). `AsmToIr` pops two `I64` values in both the stack-typing
pre-pass and the lowering pass, and pushes `I64`. Emission is
`(jlong) ((uint64_t) left OP (uint64_t) right)` with `&`, `|`, `^` — bitwise
operations are bit-pattern identical for signed and unsigned carriers, so the
unsigned carrier is semantically exact.

### 2. `LSHL`/`LSHR`/`LUSHR` take I64 value + I32 count — verified

`LongShift`'s constructor enforces `requireI64(value)` and
`requireI32(count)`. `AsmToIr` pops in the correct JVM order: the `I32` count
(top of stack) first, then the `I64` value, and pushes `I64`. A focused test
(`lowersLongBitwiseAndShiftFamilyWithTypedCountsAndMasking`) asserts the
operand types on all three shift nodes.

### 3. JVM shift-count mask `count & 0x3f` — verified

`IrCppEmitter.longShiftAmount` emits `((uint32_t) count & 63)` for all three
shifts, matching JVMS `lshl`/`lshr`/`lushr` ("only the six lowest-order bits
of the count are used"). The `uint32_t` cast also makes negative counts
well-defined before masking.

### 4. Logical vs. arithmetic right shift — verified

- `LSHR` emits `(jlong) ((int64_t) value >> masked)` — arithmetic
  (sign-extending) shift on a signed carrier.
- `LUSHR` emits `(jlong) ((uint64_t) value >> masked)` — logical shift on an
  unsigned carrier; zero bits are shifted in and no sign extension occurs.
- `LSHL` uses the `uint64_t` carrier, so left-shift overflow wraps instead of
  being undefined behavior.

I compiled and ran a differential check of the exact emitted patterns with
`g++ -std=c++17 -O2` on this machine; all nine cases matched Java semantics:

| Expression | Java value | Emitted-pattern C++ value |
| --- | --- | --- |
| `Long.MAX_VALUE << 65` | `-2` | `-2` |
| `-8L >> 1` | `-4` | `-4` |
| `-8L >>> 1` | `0x7ffffffffffffffc` | `0x7ffffffffffffffc` |
| `-1L >>> 63` | `1` | `1` |
| `-1L >> 63` | `-1` | `-1` |
| `1L << 64` | `1` | `1` |
| `0x8000000000000000L >> 4` | `0xf800000000000000` | `0xf800000000000000` |
| `0x8000000000000000L >>> 4` | `0x0800000000000000` | `0x0800000000000000` |
| `-1L << -1` | `0x8000000000000000` | `0x8000000000000000` |

### 5. Default `--codegen=legacy` unchanged — verified

`Main.java` is untouched by this diff and still declares
`@CommandLine.Option(names = {"--codegen"}, defaultValue = "legacy", ...)`.
`CodegenModeTest` (5 tests) passes, and per-method legacy fallback code paths
are not modified.

### 6. All IR consumers handle the new node — verified

On this branch the only consumers of `IrNodes` are `AsmToIr`, `IrCppEmitter`,
and `IrMethod` (pretty-printer); all three were updated. There is no
evaluator or reader in this tree that could receive an unhandled `LongShift`.

## Test results (run by this reviewer at the reviewed tip)

```text
CC=gcc CXX=g++ ./gradlew :obfuscator:test \
  --tests by.radioegor146.ir.IrCompilerTest \
  --tests by.radioegor146.CodegenModeTest
```

Result on 2026-08-29 (OpenJDK 21.0.10, gcc/g++ 13.3.0, Linux):
`BUILD SUCCESSFUL`. Gradle JUnit XML:

```text
IrCompilerTest:  tests=93, skipped=0, failures=0, errors=0
CodegenModeTest: tests=5,  skipped=0, failures=0, errors=0
Total: 98 tests, 0 skipped, 0 failures, 0 errors
```

This matches the counts claimed in `docs/architecture/ir-phase19-status.md`.

## Benchmark-kernel admission claim — independently reproduced

I rebuilt `obfuscator.jar` and `transpiler-benchmarks.jar` at the reviewed tip
and ran the CLI with `--codegen=ir -w benchmarks/whitelist.txt`. Observed:

- transpile exit code `0`;
- the log contains no `unsupported` and no `falling back` entry;
- generated sources carry `IR codegen:` markers for
  `benchmarks/kernels/IntegerLoopKernel.run(I)J` and
  `benchmarks/kernels/RecursionKernel.recurse(IJ)J`;
- the integer-loop source emits its `>>> 17` as
  `(uint64_t) v5 >> ((uint32_t) v16 & 63)` and the recursion source emits its
  mix through `uint64_t` `^`, as the status doc describes.

No timing benchmark was run by the PR or by this review; no performance
numbers are claimed.

## Nits (non-blocking)

1. **Signed right shift is implementation-defined pre-C++20.**
   `(int64_t) value >> n` on negative values is implementation-defined in
   C++17 (C++20 defines it as arithmetic). Mainstream toolchains implement it
   as an arithmetic shift, my differential check confirms that on this
   toolchain, and the emission mirrors the already-accepted int `ISHR`
   pattern (`(int32_t) left >> ...`), so this is a consistency observation,
   not a defect. If the project ever wants strict-C++17 portability, both int
   and long right shifts could be rewritten via unsigned carriers plus
   explicit sign fill in one follow-up.
2. **`(jlong)` narrowing from `uint64_t` above `INT64_MAX`** is likewise
   implementation-defined pre-C++20 and well-defined modular conversion in
   C++20. This is the same carrier pattern used by the existing
   `LongBinary`/`Binary` wrapping arithmetic, so phase 19 introduces nothing
   new here.
3. **One tautological test assertion.** In
   `emitsWrappingLongLeftShiftThroughUnsignedCarrier`,
   `assertEquals(-2L, Long.MAX_VALUE << 65)` evaluates the shift in Java, not
   in emitted code, so it can never fail meaningfully. The string assertions
   on the emitted C++ carry the real coverage; the extra assertion is
   harmless but could be dropped.

## Boundaries respected

- This review does not claim JDK 17 or JDK 21 input support, and does not
  claim requirement 7 (or any requirements checklist item) is met.
- The `--codegen` default stays `legacy`; nothing in this review flips it.
- No merge action is taken or recommended here for PR #126.
