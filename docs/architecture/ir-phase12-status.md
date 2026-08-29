# IR phase 12 status

Phase 12 extends the optional Java bytecode → typed CFG IR → C++/JNI compiler
to supported constructor method bodies. The CLI and API default remains
`legacy`; unsupported constructors remain ordinary Java bytecode methods, all
other unsupported methods retain per-method legacy fallback, and the existing
legacy snippets remain present.

Required base:
`cursor/ir-compiler-phase11-6d81` at
`6fc64927a53c777a36c38e54aaed01b1bd696ed3` (draft PR #78).
The docs-only phase-11 review branches are not part of this branch.

## Why constructors need a bridge

The JVM class-file rules do not permit `ACC_NATIVE` on a method named
`<init>`. The verifier also does not permit passing uninitialized `this` to an
ordinary static or instance helper before the constructor-chain call.

Phase 12 therefore uses a verifier-safe split:

1. The complete constructor is admitted and lowered to typed IR first. This
   validates every opcode, descriptor, stack/local state, constructor call, and
   control-flow edge before any output, cache, method flag, or hidden bridge is
   changed.
2. The direct `this(...)` or `super(...)` call and its argument-producing
   bytecode prefix remain in the Java constructor. This is the operation that
   changes verifier state from uninitialized `this` to an initialized
   reference.
3. The initialized suffix is lowered to C++/JNI and exposed through a hidden
   static native bridge. The Java constructor invokes that bridge with `this`
   and its descriptor arguments, then returns.

The constructor itself remains non-native. Its C++ bridge returns JNI `void`.
Receiver local 0 is the normal IR `REFERENCE` parameter and maps to `jobject
obj`.

The complete constructor IR still represents a supported `INVOKESPECIAL
<init>` as the existing special, void invoke and lowers it through
`GetMethodID` plus `CallNonvirtualVoidMethod`. The executable bridge retains
the verifier-required constructor-chain call in bytecode to avoid invoking a
constructor twice.

## Admitted constructor bodies

`MethodProcessor.shouldProcess(method, codegenMode)` now admits `<init>` only
when `codegenMode == IR`. The existing one-argument overload retains legacy
behavior and still excludes constructors. `NativeObfuscator` applies the
mode-aware decision consistently during class selection, preprocessing, and
per-method code generation.

A constructor is admitted only when:

- it is an instance method with return descriptor `V`;
- its complete body is accepted by the phase 1–11 frontend subset;
- it has one direct constructor-chain call whose owner is the current class or
  direct superclass;
- the prefix through that call is linear;
- the prefix does not overwrite local 0 or a reference/array parameter local
  that the wrapper forwards to the bridge;
- suffix branches and switches do not target the prefix;
- exception regions are wholly inside the initialized suffix; and
- the suffix can be independently lowered with receiver and descriptor
  parameters as its entry locals.

This covers ordinary javac constructor bodies that call `super(...)` or
`this(...)` and then execute supported operations. It does not scan the JDK or
load `java.lang.Object.<init>` as an input method. Classes are considered only
when their classfiles are entries in the supplied input JAR.

## Fields, calls, and exceptions

The initialized suffix reuses the phase-10 field carriers without widening:

- exact `I` uses `SetIntField`;
- exact `J` uses `SetLongField`; and
- object and array descriptors use `SetObjectField`.

Constructor calls in the complete IR retain the phase-8/11 special-void
invariants and `CallNonvirtualVoidMethod` family. No `Z`, `B`, `C`, `S`, `F`,
or `D` field/invoke widening was added.

Potentially throwing JNI operations in the suffix use the existing exceptional
exit. An unprotected failure returns from the JNI `void` bridge while leaving
the exception pending. Protected suffix regions retain the shared typed-IR
catch dispatch.

## Fallback before mutation

`IrMethodCompiler` validates the complete constructor and creates the suffix IR
and C++ body before `MethodShellEmitter.beginIr` creates the hidden native
bridge or rewrites the constructor. A capability failure therefore leaves:

- the constructor without `ACC_NATIVE`;
- its instruction list unchanged;
- generated output and native metadata empty; and
- string, class, field, and method caches unchanged.

`NativeObfuscator` does not send a rejected constructor to the legacy method
processor, because that processor deliberately has no constructor shell.
Instead it leaves that constructor as Java bytecode and continues processing
other eligible methods in the class.

## Tests and recorded results

Command run on 2026-08-29:

```text
CC=gcc CXX=g++ ./gradlew :obfuscator:test \
  --tests by.radioegor146.ir.IrCompilerTest \
  --tests by.radioegor146.CodegenModeTest \
  --rerun-tasks
```

Result: `BUILD SUCCESSFUL`.

Counts read directly from Gradle's JUnit XML:

```text
IrCompilerTest: tests=58, skipped=0, failures=0, errors=0 (time=0.69 s)
CodegenModeTest: tests=2, skipped=0, failures=0, errors=0 (time=0.097 s)
Total: 60 tests, 0 skipped, 0 failures, 0 errors
```

Phase-12 coverage includes:

- an `I`-field constructor plus an independently IR-lowered getter;
- a subclass constructor that calls `super(I)V` and stores a `J` field;
- a constructor that delegates through `this(I)V`;
- object and array field stores through `SetObjectField`;
- an unsupported opcode after the constructor-chain call, proving rejection
  before constructor, output, bridge, or cache mutation;
- valid constructors that overwrite local 0 or a forwarded reference parameter
  before the constructor-chain call, proving verifier-safe rejection with the
  original class still loadable and invocable;
- serialization and JVM verification of the rewritten constructor plus hidden
  native bridge class; and
- the retained phase-9 array-return, phase-10 field, and phase-11 interface and
  non-constructor-special invoke regressions.

### Real g++ smoke evidence

Environment:

```text
g++ (Ubuntu 13.3.0-6ubuntu2~24.04.1) 13.3.0
openjdk version "21.0.10" 2026-01-20
JNI headers: present
```

`generatedCppPassesGppSyntaxCheckWhenToolchainAvailable` ran in 0.256 s and has
no `<skipped>` child in the JUnit XML. Its retained translation unit contains
61 `JNICALL` functions, including the phase-12 constructor bridges and the
phase-9 through phase-11 regressions.

The exact retained unit
`/tmp/ir-compile-smoke12551902791792438333/ir-smoke.cpp` was also checked
independently with:

```text
g++ -std=c++17 -fsyntax-only \
  -I/usr/lib/jvm/java-21-openjdk-amd64/include \
  -I/usr/lib/jvm/java-21-openjdk-amd64/include/linux \
  /tmp/ir-compile-smoke12551902791792438333/ir-smoke.cpp
```

g++ exited zero with empty diagnostics.

### Default and retained assets

`CodegenModeTest.cliDefaultsToLegacy` passed. `Main` still declares
`defaultValue = "legacy"`, and the public API overload without a `CodegenMode`
still delegates with `CodegenMode.LEGACY`. The phase-9 `jarray` return carrier,
phase-10 fields, phase-11 invokes, and `sources/cppsnippets.properties` remain
present.

## Constructors that still fall back

The following constructors remain Java bytecode methods:

- any body containing an opcode, descriptor, carrier, local state, stack state,
  handler shape, or control-flow shape outside the current IR subset;
- constructors without exactly one identifiable direct `this(...)` or
  `super(...)` call;
- constructors with branches or switches before that call, a suffix edge back
  into the prefix, or an exception region crossing the split;
- constructors that overwrite local 0 or a forwarded reference/array parameter
  local before the constructor-chain call;
- constructor suffixes that depend on non-parameter locals initialized only in
  the retained prefix;
- `java.lang.Object.<init>` itself, which has no superclass constructor-chain
  call and is not sought outside the input JAR; and
- float/double bodies, invokedynamic, `POP2`, `MULTIANEWARRAY`, non-`int`
  primitive-array operations, and `Z`/`B`/`C`/`S`/`F`/`D` field or invoke
  descriptors covered by the existing deliberate fallback policy.

The default was not changed from `legacy`, and no snippet resource was removed.
