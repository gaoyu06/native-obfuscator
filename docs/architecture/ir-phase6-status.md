# IR phase 6 status

This branch extends `cursor/ir-phase5-fable-review-6d81` with structured switch
terminators and object-array allocation in the opt-in typed CFG compiler. The
CLI/API default remains `legacy`; `--codegen=ir` still falls back independently
for methods outside the supported subset. The legacy snippet path remains
present.

Preferred PR merge base:
`cursor/ir-phase5-fable-review-6d81` at
`b72e3cf0d1cbf128a7f98508d98cbf1f63de1217`.

## What landed

### `TABLESWITCH` and `LOOKUPSWITCH`

`CfgBuilder` now makes every case target and the mandatory default target a
basic-block leader and a normal CFG successor. Malformed tables, mismatched
lookup key/label lists, duplicate lookup keys, and non-executable targets are
rejected during whole-method frontend validation.

Both bytecodes pop an `I32` selector and lower to a typed `IrNodes.Switch`
terminator containing ordered key/target pairs plus an explicit default target.
Normal stack-shape analysis, definite-local analysis, and phi connection all
visit the switch successors, including default. Duplicate destinations are one
CFG edge because every arm carries the same outgoing JVM state; the emitter
still emits an edge transfer for every logical case and for default.

`CppAst.Switch` renders a structured C++ `switch`. Every arm performs the
existing parallel phi copies and then `goto`s the selected IR block. The
generated C++ therefore has no implicit fallthrough and no jump across an
initialized SSA declaration.

Tests cover table and sparse lookup keys, mandatory default emission, stack-phi
inputs on every successor, and a deliberately mismatched carrier that is
reachable only through the table default. The latter is rejected at the CFG
merge, proving that default participates in carrier validation.

### General `ANEWARRAY`

`ANEWARRAY` with a non-empty ASM component type now lowers to a typed
`NewObjectArray(length, componentType) -> ref` instruction. This includes the
required `java/lang/String` and `java/lang/Object` cases and uses the existing
class cache for other reference component classes.

Array-typed components use the JVM array descriptor with `JNIEnv::FindClass`;
ordinary internal names continue through the classloader-based resolver. The
review branch corrected the original implementation, which incorrectly sent
array descriptors through the ordinary-name resolver.

The emitter:

- checks `< 0` first and raises `java/lang/NegativeArraySizeException`;
- resolves the component through the existing weak-global class cache;
- routes pending class-resolution failures through the block's exceptional
  exit;
- calls `env->NewObjectArray(length, componentClass, nullptr)`; and
- treats either a null result or `env->ExceptionCheck()` as failure.

Protected failures copy exceptional local-phi inputs and reach the shared
ordered `IR_CATCH_n` dispatch. Unprotected failures return the JNI default while
leaving the pending exception intact. `CfgBuilder` treats `ANEWARRAY` as a
potentially throwing block boundary.

## Fallback-before-mutation

Method shape, opcode and descriptor admission, switch shape and target
validation, local/stack typing, handler shape, reachability, lowering, and phi
connections complete in `AsmToIr.build(...)` before cache IDs or JNI
method-shell output are created.

The retained `rejectsIntStoreIntoInstanceReceiverLocal` and
`rejectsNonIntStaticFieldBeforeMutation` regressions still pass.
`rejectsUnsupportedInstructionAfterAnewarrayBeforeMutation` additionally puts a
supported `ANEWARRAY` before an unsupported `POP` and proves that whole-method
admission rejects the method with `ACC_NATIVE`, generated output,
native-method metadata, and all class/string/field/method caches untouched.

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
IrCompilerTest: tests=27, skipped=0, failures=0, errors=0 (time=0.472 s)
CodegenModeTest: tests=2, skipped=0, failures=0, errors=0 (time=0.132 s)
Total: 29 tests, 0 skipped, 0 failures, 0 errors
```

The phase-6 tests cover typed switch and object-array IR shape, structured C++
emission, case/default phi transfers, default-edge carrier validation, negative
length and allocation failure routing, String/Object component class caching,
array-component descriptor resolution, fallback atomicity, and all retained
phase-1 through phase-5 cases.

### Real g++ smoke evidence

Environment:

```text
g++ (Ubuntu 13.3.0-6ubuntu2~24.04.1) 13.3.0
openjdk version "21.0.10" 2026-01-20
JNI headers: present
```

`generatedCppPassesGppSyntaxCheckWhenToolchainAvailable` completed in 0.134 s
and has no `<skipped>` child in the JUnit XML. Its 23-method translation unit
includes `tableSelect`, `lookupSelect`, `allocateStrings`, and
`allocateObjects`; it also includes the array-component regression
`allocateStringRows`. The test runs:

```text
g++ -std=c++17 -fsyntax-only \
  -I${java.home}/include -I${java.home}/include/linux ir-smoke.cpp
```

The exact generated translation unit retained by that test run was also
compiled independently with the same C++17 syntax-only command; g++ exited
zero. Inspection of that file confirms each switch arm, including default,
contains phi copies followed by `goto`, and both object-array methods call
`NewObjectArray` with null/pending-exception checks. The nested-array case uses
`FindClass` with its array descriptor. No `juint` carrier appears.

## What still falls back per method

Phase 6 remains a staged subset. Per-method fallback still covers:

- malformed/empty exception regions, handler entries with reachable normal
  predecessors, and try/catch bodies containing any unsupported operation;
- `jsr`/`ret`, `POP`, reference/null branches, and other unlisted stack/control
  operations;
- object creation and constructor calls, `MULTIANEWARRAY`, and every primitive
  `NEWARRAY` kind except `int`;
- reference fields, non-`I` fields, and unsupported field descriptor shapes;
- `long`, `float`, and `double` carriers and their arithmetic/conversions;
- reference/void/wide-returning invokes, unsupported primitive arguments,
  `INVOKESPECIAL`, `INVOKEINTERFACE`, and `invokedynamic`;
- non-`int` array element loads/stores, reference stores/returns,
  `CHECKCAST`, `INSTANCEOF`, and ordinary bytecode type tests;
- library intrinsics beyond the existing `String.length()` slice; and
- every other opcode or descriptor shape not listed in the phase 1–6 status
  documents.

The default was not changed, constructor policy was not broadened, and existing
snippet resources were not removed.
