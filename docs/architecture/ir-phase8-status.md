# IR phase 8 status

This branch extends `cursor/ir-phase7-sol-review-6d81-f29d` with object
allocation, constructor calls, and broader high-level invokes in the opt-in
typed CFG compiler. The CLI/API default remains `legacy`;
`--codegen=ir` still falls back independently for methods outside the supported
subset. The legacy snippet path remains present.

Preferred PR merge base:
`cursor/ir-phase7-sol-review-6d81-f29d` at
`2a36df34bb8a5a7a09e1c2c870037622c6c5ac80`.

## What landed

### `NEW` and constructor calls

An ordinary, non-array `NEW` type now lowers to a dedicated reference-producing
IR node. The emitter resolves the class through the existing weak-global class
cache and calls `JNIEnv::AllocObject`. A null result or pending JNI exception
uses the block's existing exceptional exit.

`INVOKESPECIAL` is admitted only for `<init>` descriptors returning `V`. It is
represented as a high-level invoke with a receiver and emitted through
`GetMethodID` plus `CallNonvirtualVoidMethod`. Constructor arguments use the
same supported carriers as other invokes. The normal verified pattern
`NEW`, `DUP`, `INVOKESPECIAL <init>` therefore leaves the duplicate initialized
reference available to later instructions.

This phase does not transpile constructor method bodies. The existing
`MethodProcessor.shouldProcess` policy still excludes methods named `<init>`;
the new support is allocation plus a constructor call inside another method.

Both `NEW` and `INVOKESPECIAL` are treated as potentially throwing CFG
operations. Class resolution, allocation, method lookup, and constructor
exceptions consequently reach the ordered shared catch dispatcher when
protected, or return the JNI default with the exception pending when
unprotected.

### Broader invokes

High-level `INVOKESTATIC` and `INVOKEVIRTUAL` now support:

- exact `I`, `J`, object, and array argument descriptors;
- `V`, exact `I`, `J`, object, and array return descriptors; and
- the matching JNI call families:
  `Call[Static]VoidMethod`, `Call[Static]IntMethod`,
  `Call[Static]LongMethod`, and `Call[Static]ObjectMethod`.

Reference returns use the existing `jobject` IR carrier. Long arguments and
returns use the JNI-defined `jlong` carrier. No synthetic JNI type was added.
The regression set includes a static `(J)J` call, a virtual
`()Ljava/lang/String;` call, a static String-returning call, and a static
`(J)V` call.

## Fallback before mutation

Complete opcode and descriptor admission, stack/local typing, lowering, and phi
connection still finish before the emitter allocates cache/string IDs and
before the JNI shell changes method flags, output, or native-method metadata.

`rejectsUnsupportedInstructionAfterNewBeforeMutation` places admitted `NEW`,
`DUP`, and `INVOKESPECIAL <init>` before unsupported `POP`. It verifies
rejection on `POP` with `ACC_NATIVE`, generated output, native metadata, and all
class/string/field/method caches unchanged.

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
IrCompilerTest: tests=36, skipped=0, failures=0, errors=0 (time=0.606 s)
CodegenModeTest: tests=2, skipped=0, failures=0, errors=0 (time=0.107 s)
Total: 38 tests, 0 skipped, 0 failures, 0 errors
```

The three phase-8 regressions cover allocation plus a constructor and ordinary
virtual call, expanded long/void/object invoke families, and fallback
atomicity after the newly admitted operations. All retained phase-1 through
phase-7 cases also pass.

### Real g++ smoke evidence

Environment:

```text
g++ (Ubuntu 13.3.0-6ubuntu2~24.04.1) 13.3.0
openjdk version "21.0.10" 2026-01-20
JNI headers: present
```

`generatedCppPassesGppSyntaxCheckWhenToolchainAvailable` completed in 0.259 s
and has no `<skipped>` child in the JUnit XML. Its 34-method generated
translation unit includes `constructObject`, `callLong`,
`virtualStringLength`, `staticStringLength`, and `consumeLong`, and contains
the expected `AllocObject`, `CallNonvirtualVoidMethod`,
`CallStaticLongMethod`, `CallObjectMethod`, `CallStaticObjectMethod`, and
`CallStaticVoidMethod` calls.

The test invoked:

```text
g++ -std=c++17 -fsyntax-only \
  -I${java.home}/include -I${java.home}/include/linux ir-smoke.cpp
```

The exact retained translation unit was independently compiled with that
C++17 syntax-only command; g++ exited zero with empty diagnostics.

## What still falls back per method

Phase 8 remains a staged subset. Per-method fallback still covers:

- malformed/empty exception regions, handler entries with reachable normal
  predecessors, and protected bodies containing any unsupported operation;
- `jsr`/`ret`, category-two stack manipulation (`DUP2` and related forms),
  `POP`, reference/null branches, null constants, and other unlisted
  stack/control operations;
- `MULTIANEWARRAY` and every primitive `NEWARRAY` kind except `int`;
- reference and wide fields, non-`I` field descriptors, and unsupported field
  shapes;
- remaining long operations, including long constants loaded by `LDC`,
  division/remainder, negation, shifts, bitwise operations, comparisons,
  arrays, and fields;
- all `float` and `double` carriers, arithmetic, conversions, and invoke
  descriptors;
- invoke argument or return descriptors outside exact `I`, `J`, references,
  arrays, and `V` returns; non-constructor `INVOKESPECIAL`,
  `INVOKEINTERFACE`, and `invokedynamic`;
- reference-returning method bodies (`ARETURN`), even though reference results
  from supported invokes may feed other supported operations or reference
  locals;
- non-`int` array element loads/stores; and
- every other opcode or descriptor shape not listed in the phase 1–8 status
  documents.

The default was not changed, constructor method bodies remain excluded, and
existing snippet resources were not removed.
