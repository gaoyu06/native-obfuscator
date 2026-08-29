# IR phase 10 status

Phase 10 extends the optional Java bytecode → typed CFG IR → C++/JNI compiler
with typed instance and static field access. The CLI/API default remains
`legacy`; unsupported methods still fall back independently, and the existing
legacy snippets remain present.

Preferred PR merge base:
`cursor/ir-phase9-sol-review-6d81` at
`0e323da959d34f29b3c3cede206e48aa96a4559e`.
This base includes the phase-9 array-return `jarray` carrier fix.

## What landed

### Exact field descriptor carriers

`GETFIELD`, `PUTFIELD`, `GETSTATIC`, and `PUTSTATIC` now accept these field
descriptor groups:

- exact `I`, represented by `IrType.I32` / `jint`;
- exact `J`, represented by `IrType.I64` / `jlong`; and
- object and array descriptors, represented by `IrType.REFERENCE` / `jobject`.

The frontend uses the field descriptor when it simulates the JVM operand stack,
creates the result SSA value, and pops a put value. The field IR nodes check the
same descriptor-to-value invariant. This avoids treating `J` or references as
the existing integer carrier.

The emitter chooses the matching JNI field family:

| Descriptor | Instance get/put | Static get/put |
| --- | --- | --- |
| `I` | `GetIntField` / `SetIntField` | `GetStaticIntField` / `SetStaticIntField` |
| `J` | `GetLongField` / `SetLongField` | `GetStaticLongField` / `SetStaticLongField` |
| object or array | `GetObjectField` / `SetObjectField` | `GetStaticObjectField` / `SetStaticObjectField` |

All four forms retain the existing `CachedFieldInfo`, `cfields`,
`GetFieldID`, and `GetStaticFieldID` paths. Arrays deliberately use the JNI
object-field accessors because array references are JNI object references. The
phase-9 `jarray` cast at an array-returning function boundary remains in place.

### Instance null receivers and exceptional exits

Instance gets and puts retain the explicit receiver check before the JNI field
accessor. A null receiver creates a pending `NullPointerException` through the
shared helper and immediately takes the current block's exceptional exit. An
unprotected access returns the JNI default while leaving the exception pending;
a protected access transfers to the block's shared catch dispatch.

### Deliberate descriptor fallback

Field descriptors `Z`, `B`, `C`, `S`, `F`, and `D` remain unsupported in this
phase. They are rejected during frontend admission instead of being widened to
`I`. Supporting them requires their JVM-accurate JNI accessor families and
matching tests.

## Fallback before mutation

Opcode and descriptor admission, stack/local typing, lowering, and phi
connection still complete before the emitter allocates cache/string IDs and
before the JNI shell changes method flags, generated output, or native-method
metadata.

`rejectsUnsupportedAfterPhaseTenFieldsBeforeMutation` places admitted instance
and static `J`, object, and array field operations before an unsupported `F`
field. It verifies that rejection leaves `ACC_NATIVE`, output, native metadata,
and all class/string/field/method caches unchanged.

## Tests and recorded results

Command run on 2026-08-29:

```text
CC=gcc CXX=g++ ./gradlew :obfuscator:test \
  --tests by.radioegor146.ir.IrCompilerTest \
  --tests by.radioegor146.CodegenModeTest \
  --rerun-tasks
```

Result: `BUILD SUCCESSFUL`.

Recorded directly from Gradle's JUnit XML:

```text
IrCompilerTest: tests=47, skipped=0, failures=0, errors=0 (time=0.553 s)
CodegenModeTest: tests=2, skipped=0, failures=0, errors=0 (time=0.107 s)
Total: 49 tests, 0 skipped, 0 failures, 0 errors
```

The phase-10 cases cover instance and static get/put round-trips for exact `I`,
exact `J`, object references, and `[I` array references; both get and put null
receiver exits; rejection of all six deliberately unsupported primitive field
sorts; and fallback-before-mutation after newly admitted field operations. The
phase-9 allocated-array return regression remains in the suite.

### Real g++ smoke evidence

Environment:

```text
g++ (Ubuntu 13.3.0-6ubuntu2~24.04.1) 13.3.0
openjdk version "21.0.10" 2026-01-20
JNI headers: present
```

`generatedCppPassesGppSyntaxCheckWhenToolchainAvailable` completed in 0.239 s
and has no `<skipped>` child in the JUnit XML. Its retained 50-method generated
translation unit includes all phase-10 round-trip and null-receiver methods as
well as the phase-9 array-return regression.

The exact retained translation unit
`/tmp/ir-compile-smoke16942186550084681249/ir-smoke.cpp` was also independently
checked with:

```text
g++ -std=c++17 -fsyntax-only \
  -I/usr/lib/jvm/java-21-openjdk-amd64/include \
  -I/usr/lib/jvm/java-21-openjdk-amd64/include/linux \
  /tmp/ir-compile-smoke16942186550084681249/ir-smoke.cpp
```

g++ exited zero with empty diagnostics.

### Default and scope evidence

`CodegenModeTest.cliDefaultsToLegacy` passed in the recorded XML. Source
inspection also confirms the CLI option retains `defaultValue = "legacy"` and
the public API overload without a `CodegenMode` argument still delegates with
`CodegenMode.LEGACY`.

Constructor method bodies remain excluded by `MethodProcessor.shouldProcess`.
No snippet resources were removed.

## What still falls back per method

Phase 10 remains a staged subset. Per-method fallback still covers:

- malformed exception regions and unsupported handler/control-flow shapes;
- `POP2`, `DUP2`, and other category-two stack manipulation forms;
- `MULTIANEWARRAY` and every primitive `NEWARRAY` kind except `int`;
- non-`int` primitive arrays and non-`int` array element loads/stores;
- field descriptors `Z`, `B`, `C`, `S`, `F`, and `D`;
- remaining long operations outside the documented subset;
- all float/double operations, conversions, method carriers, and field access;
- non-constructor `INVOKESPECIAL`, `INVOKEINTERFACE`, and invokedynamic;
- constructor method bodies; and
- every other opcode or descriptor shape not listed in the phase 1–10 status
  documents.

The default was not changed from `legacy`, and existing snippet resources were
not removed.
