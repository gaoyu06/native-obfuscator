# IR phase 7 status

This branch extends `cursor/ir-phase6-sol-review-6d81` with reference type
tests and an initial two-slot `I64` arithmetic slice in the opt-in typed CFG
compiler. The CLI/API default remains `legacy`; `--codegen=ir` still falls back
independently for methods outside the supported subset. The legacy snippet path
remains present.

Preferred PR merge base:
`cursor/ir-phase6-sol-review-6d81` at
`ac01e555aaa0109f61e98472dedd20f481643cf7`.

## What landed

### `CHECKCAST` and `INSTANCEOF`

Both bytecodes now lower to dedicated typed IR nodes. They consume a reference;
`CHECKCAST` produces a reference and `INSTANCEOF` produces `I32`.

The emitter resolves each target through the existing weak-global class cache.
Ordinary internal names use the classloader resolver. Array descriptors such as
`[Ljava/lang/String;` remain unchanged and use `JNIEnv::FindClass`, preserving
the array-component correction from phase 6.

`CHECKCAST` guards `IsInstanceOf` with an explicit non-null test, so null passes
through unchanged. A non-null mismatch raises
`java/lang/ClassCastException`. `INSTANCEOF` initializes its result to zero and
only calls `IsInstanceOf` for a non-null operand. Class-resolution failures and
cast failures use the block's existing exceptional exit: protected operations
reach the ordered shared `IR_CATCH_n` dispatcher, while unprotected operations
return the JNI default with the exception pending.

### Two-slot `I64`

`IrType.I64` uses the real JNI `jlong` carrier and records a width of two JVM
slots. Phase 7 supports:

- `LLOAD`, `LSTORE`, `LCONST_0`, and `LCONST_1`;
- wrapping `LADD`, `LSUB`, and `LMUL`;
- `LRETURN`; and
- `I2L` and `L2I`.

Long parameters and locals reserve their second physical local slot. Validation
rejects truncated or overlapping wide locals. Definite-local analysis tracks
both slots, while SSA creates one value/phi at the category-two value's first
slot. Operand-stack values stay typed logical values, but stack phi indices are
the corresponding physical JVM slot indices; an `I32` above an `I64` therefore
uses stack slot 2. Category-one `DUP` rejects an `I64` operand.

Generated long addition, subtraction, and multiplication cast operands to
standard `uint64_t` before the operation and cast the wrapped result to
`jlong`, avoiding signed-overflow undefined behavior. No fictitious JNI
unsigned-long type is introduced.

## Fallback-before-mutation

Method shape, complete opcode admission, local-width validation, stack typing,
handler validation, lowering, and phi connection all finish before cache IDs,
JNI shell output, method flags, or native-method metadata are changed.

`rejectsUnsupportedWideOperationBeforeMutation` places supported `LLOAD`,
`LCONST_1`, and `LMUL` before unsupported `LDIV`. It verifies that admission
fails on `LDIV` with `ACC_NATIVE`, generated output, native-method metadata, and
all class/string/field/method caches untouched.

## Tests and recorded results

Command run on 2026-08-28:

```text
./gradlew :obfuscator:test \
  --tests by.radioegor146.ir.IrCompilerTest \
  --tests by.radioegor146.CodegenModeTest
```

Result: `BUILD SUCCESSFUL`.

Recorded directly from Gradle's JUnit XML:

```text
IrCompilerTest: tests=33, skipped=0, failures=0, errors=0 (time=0.613 s)
CodegenModeTest: tests=2, skipped=0, failures=0, errors=0 (time=0.115 s)
Total: 35 tests, 0 skipped, 0 failures, 0 errors
```

The six phase-7 regressions cover ordinary and array type targets, null
semantics, protected `ClassCastException` dispatch, long locals/arithmetic and
conversions, physical wide stack-phi numbering, and unsupported-wide-op
fallback atomicity. All retained phase-1 through phase-6 cases also pass.

### Real g++ smoke evidence

Environment:

```text
g++ (Ubuntu 13.3.0-6ubuntu2~24.04.1) 13.3.0
openjdk version "21.0.10" 2026-01-20
JNI headers: present
```

`generatedCppPassesGppSyntaxCheckWhenToolchainAvailable` completed in 0.246 s
and has no `<skipped>` child in the JUnit XML. Its 29-method generated
translation unit includes `typeTest`, `arrayTypeTest`, `catchCast`,
`longArithmetic`, `longConversion`, and `widePhi`. The test invoked:

```text
g++ -std=c++17 -fsyntax-only \
  -I${java.home}/include -I${java.home}/include/linux ir-smoke.cpp
```

The exact retained translation unit was independently compiled with that
C++17 syntax-only command; g++ exited zero. Inspection confirms the array type
target uses `FindClass`, null cast/type-test guards are emitted, cast failure
reaches `IR_CATCH_0`, long signatures and SSA values use `jlong`, and wrapping
arithmetic uses `uint64_t`. Neither `juint` nor `julong` appears.

## What still falls back per method

Phase 7 remains a staged subset. Per-method fallback still covers:

- malformed/empty exception regions, handler entries with reachable normal
  predecessors, and protected bodies containing any unsupported operation;
- `jsr`/`ret`, category-two stack manipulation (`DUP2` and related forms),
  `POP`, reference/null branches, and other unlisted stack/control operations;
- object creation and constructor calls, `MULTIANEWARRAY`, and every primitive
  `NEWARRAY` kind except `int`;
- reference and wide fields, non-`I` field descriptors, and unsupported field
  shapes;
- remaining long operations, including long constants loaded by `LDC`,
  division/remainder, negation, shifts, bitwise operations, comparisons, array
  elements, fields, and invokes;
- all `float` and `double` carriers, arithmetic, and conversions;
- reference/void/wide-returning invokes, invokes with wide arguments,
  unsupported primitive arguments, `INVOKESPECIAL`, `INVOKEINTERFACE`, and
  `invokedynamic`;
- non-`int` array element loads/stores and reference method returns;
- library intrinsics beyond the existing `String.length()` slice; and
- every other opcode or descriptor shape not listed in the phase 1–7 status
  documents.

The default was not changed, constructor policy was not broadened, and existing
snippet resources were not removed.
