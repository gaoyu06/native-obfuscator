# IR phase 11 status

Phase 11 extends the optional Java bytecode → typed CFG IR → C++/JNI compiler
with interface calls and non-constructor special calls. The CLI/API default
remains `legacy`; unsupported methods still fall back independently, and the
existing legacy snippets remain present.

Required base:
`cursor/ir-compiler-phase10-6d81` at
`b8cdb8efb09c135e7d119249f48feba22cf7e8f4`.
This base includes the phase-9 array-return `jarray` carrier fix and the phase-10
typed field regressions.

## What landed

### `INVOKEINTERFACE`

`INVOKEINTERFACE` is represented by a distinct `IrNodes.Invoke.Kind.INTERFACE`.
It accepts only these existing invoke carriers:

- exact `I`, represented by `IrType.I32` / `jint`;
- exact `J`, represented by `IrType.I64` / `jlong`;
- object and array descriptors, represented by `IrType.REFERENCE` / `jobject`;
  and
- `V` for the return descriptor.

The emitter resolves the interface owner through the existing class cache and
looks up the method with `GetMethodID`. Calls use the same JNI families as
virtual calls: `CallIntMethod`, `CallLongMethod`, `CallObjectMethod`, and
`CallVoidMethod`.

The frontend constructs one IR argument for each descriptor argument in
descriptor order. `IrNodes.Invoke` independently checks the argument count and
each argument/result type against the descriptor, preventing a generated
variadic JNI call from omitting or adding an argument.

Interface receivers retain the explicit null check and block exceptional exit.
`CfgBuilder` treats `INVOKEINTERFACE` as potentially throwing, so a pending
`NullPointerException` or a JNI call exception reaches the shared catch
dispatch when the bytecode call is protected.

### Non-constructor `INVOKESPECIAL`

An `INVOKESPECIAL` whose name is not `<init>` now accepts the same exact
`I` / `J` / reference / `V` carrier set. This covers supported super and
same-class private special calls. The emitter passes the receiver, declaring
class cache entry, method ID, and descriptor arguments to the matching
`CallNonvirtualIntMethod`, `CallNonvirtualLongMethod`,
`CallNonvirtualObjectMethod`, or `CallNonvirtualVoidMethod`.

Constructor calls remain on the existing void
`CallNonvirtualVoidMethod` path. Constructor method bodies remain excluded by
`MethodProcessor.shouldProcess`.

### Deliberate descriptor fallback

Invoke argument and return descriptors `Z`, `B`, `C`, `S`, `F`, and `D` remain
unsupported for the newly admitted opcodes. They are rejected during frontend
admission rather than being widened to `I` or mapped to an unrelated JNI call
family. `invokedynamic` also remains unsupported.

## Fallback before mutation

Opcode and descriptor admission, stack/local typing, lowering, and phi
connection still complete before the emitter allocates cache/string IDs and
before the JNI shell changes method flags, generated output, or native-method
metadata.

`rejectsUnsupportedAfterPhaseElevenInvokesBeforeMutation` places an admitted
interface call and an admitted non-constructor special call before an
unsupported opcode. It verifies that rejection leaves `ACC_NATIVE`, output,
native metadata, and all class/string/field/method caches unchanged.

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
IrCompilerTest: tests=53, skipped=0, failures=0, errors=0 (time=0.714 s)
CodegenModeTest: tests=2, skipped=0, failures=0, errors=0 (time=0.099 s)
Total: 55 tests, 0 skipped, 0 failures, 0 errors
```

The phase-11 cases cover interface and non-constructor special calls with exact
`I`, exact `J`, reference, and `V` returns; descriptor-ordered arguments;
interface owner `GetMethodID`; interface null-receiver catch routing;
unsupported primitive invoke descriptors; `invokedynamic`; and
fallback-before-mutation after both newly admitted invoke opcodes. The retained
phase-9 array-return and phase-10 typed-field regressions remain in the suite.

### Real g++ smoke evidence

Environment:

```text
g++ (Ubuntu 13.3.0-6ubuntu2~24.04.1) 13.3.0
openjdk version "21.0.10" 2026-01-20
JNI headers: present
```

`generatedCppPassesGppSyntaxCheckWhenToolchainAvailable` completed in 0.299 s
and has no `<skipped>` child in the JUnit XML. Its retained 59-method generated
translation unit includes all phase-11 call families and the null-receiver
case, as well as the phase-9 array-return and phase-10 field regressions.

The exact retained translation unit
`/tmp/ir-compile-smoke12639790254745751348/ir-smoke.cpp` was also independently
checked with:

```text
g++ -std=c++17 -fsyntax-only \
  -I/usr/lib/jvm/java-21-openjdk-amd64/include \
  -I/usr/lib/jvm/java-21-openjdk-amd64/include/linux \
  /tmp/ir-compile-smoke12639790254745751348/ir-smoke.cpp
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

Phase 11 remains a staged subset. Per-method fallback still covers:

- malformed exception regions and unsupported handler/control-flow shapes;
- `POP2`, `DUP2`, and other category-two stack manipulation forms;
- `MULTIANEWARRAY` and every primitive `NEWARRAY` kind except `int`;
- non-`int` primitive arrays and non-`int` array element loads/stores;
- field descriptors `Z`, `B`, `C`, `S`, `F`, and `D`;
- invoke arguments or returns outside exact `I`, exact `J`, references/arrays,
  and `V`;
- remaining long operations outside the documented subset;
- all float/double operations, conversions, method carriers, and field access;
- invokedynamic;
- constructor method bodies; and
- every other opcode or descriptor shape not listed in the phase 1–11 status
  documents.

The default was not changed from `legacy`, and existing snippet resources were
not removed.
