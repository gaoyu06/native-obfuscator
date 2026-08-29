# IR phase 5 status

This branch extends `cursor/ir-phase4-fable-review-6d81` with three additional
structured lowering families in the opt-in typed CFG compiler. The CLI/API
default remains `legacy`; `--codegen=ir` still falls back independently for
methods outside the supported subset. The legacy snippet path remains present.

Preferred PR merge base:
`cursor/ir-phase4-fable-review-6d81` at
`564589c687af50f72982190af96c1461b2f43a48`.

## What landed

### `IDIV` and `IREM`

`IDIV` and `IREM` lower to typed `IntDivRem` instructions. `CfgBuilder` treats
both bytecodes as potentially throwing block boundaries, so a zero divisor in a
protected block reaches the same ordered `IR_CATCH_n` dispatch introduced in
phase 4.

The C++ emitter:

- checks the divisor before evaluating `/` or `%`;
- raises `java/lang/ArithmeticException` through `utils::throw_re`;
- copies exceptional local-phi inputs and jumps to shared catch dispatch when
  the block has handlers;
- otherwise returns the JNI default while leaving the exception pending; and
- handles `Integer.MIN_VALUE / -1` as `Integer.MIN_VALUE` and
  `Integer.MIN_VALUE % -1` as zero before evaluating signed C++ arithmetic.

The last guard is required because both overflowing signed operations are
undefined in C++, although their JVM results are defined. Ordinary division and
remainder use `int32_t` operands and `jint` results. No non-JNI unsigned carrier
type was introduced.

### `NEWARRAY T_INT`

The frontend now lowers only the `T_INT` form of `NEWARRAY` to a typed
`NewArray(length) -> ref` instruction. Every other primitive-array kind is
rejected during whole-method validation.

Emission checks negative lengths and raises
`java/lang/NegativeArraySizeException`, calls `env->NewIntArray`, and checks
both the returned reference and `env->ExceptionCheck()`. Every failure path
uses the block's phase-4 exceptional exit: protected allocations reach shared
catch dispatch, while unprotected allocations return the JNI default with the
JNI exception pending.

### Static `I` fields

`GETSTATIC` and `PUTSTATIC` with descriptor `I` lower to typed
`GetStaticField` / `PutStaticField` instructions. They reuse:

- `CachedFieldInfo(..., true)`;
- the existing class and field cache arrays;
- `GetStaticFieldID`;
- `GetStaticIntField`; and
- `SetStaticIntField`.

Cache lookup and field-access exception checks use normal IR exceptional exits.
Any static field whose descriptor is not exactly `I` is rejected by opcode
admission before cache allocation or method mutation.

## Fallback-before-mutation

Method shape, opcode and descriptor admission, local/stack typing, handler
shape, reachability, lowering, and phi connections still complete in
`AsmToIr.build(...)` before cache IDs or JNI method-shell output are created.

`rejectsIntStoreIntoInstanceReceiverLocal` remains unchanged. The additional
`rejectsNonIntStaticFieldBeforeMutation` case proves that a non-`I`
`GETSTATIC` leaves `ACC_NATIVE`, generated output, native-method metadata, and
the class/field/method caches untouched.

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
IrCompilerTest: tests=22, skipped=0, failures=0, errors=0
CodegenModeTest: tests=2, skipped=0, failures=0, errors=0
Total: 24 tests, 0 skipped, 0 failures, 0 errors
```

The phase-5 tests cover typed IR shape, C++ zero/overflow guards, protected
divide-by-zero dispatch, allocation failure dispatch, static-int cache/JNI
calls, non-`I` fallback atomicity, and the retained phase-1 through phase-4
cases.

### Real g++ smoke evidence

Environment:

```text
g++ (Ubuntu 13.3.0-6ubuntu2~24.04.1) 13.3.0
openjdk version "21.0.10" 2026-01-20
JNI headers: present
```

`generatedCppPassesGppSyntaxCheckWhenToolchainAvailable` completed in 0.194 s
and has no `<skipped>` child in the JUnit XML. Its 18-method translation unit
includes `divRem`, `catchDivide`, `allocate`, and `setAndGetCounter`, and runs:

```text
g++ -std=c++17 -fsyntax-only \
  -I${java.home}/include -I${java.home}/include/linux ir-smoke.cpp
```

The exact generated translation unit retained by that test run was also
compiled independently with the same C++17 syntax-only command; g++ exited
zero in 0.184 s.

## What still falls back per method

Phase 5 remains a staged subset. Per-method fallback still covers:

- malformed/empty exception regions, handler entries with reachable normal
  predecessors, and try/catch bodies containing any unsupported operation;
- `TABLESWITCH`, `LOOKUPSWITCH`, `jsr`/`ret`, `POP`, and other unlisted
  stack/control operations;
- object creation and constructor calls, `ANEWARRAY`, `MULTIANEWARRAY`, and
  every primitive `NEWARRAY` kind except `int`;
- reference fields, non-`I` fields, and all unsupported field descriptor
  shapes;
- `long`, `float`, and `double` carriers and their arithmetic/conversions;
- reference/void/wide-returning invokes, unsupported primitive arguments,
  `INVOKESPECIAL`, `INVOKEINTERFACE`, and `invokedynamic`;
- non-`int` array element loads/stores, reference stores/returns, casts, and
  ordinary bytecode type tests;
- library intrinsics beyond the existing `String.length()` slice; and
- every other opcode or descriptor shape not listed in the phase 1–5 status
  documents.

The default was not changed, constructor policy was not broadened, and existing
snippet resources were not removed.
