# IR phase 12 review

Review target: draft PR #84, `cursor/ir-compiler-phase12-6d81`, compared with
`cursor/ir-compiler-phase11-6d81` at
`6fc64927a53c777a36c38e54aaed01b1bd696ed3` (draft PR #78).

## Verdict

Pass after one correctness fix and a successful focused verification run on
the review branch.

The constructor split remains opt-in under `--codegen=ir`; the CLI and API
defaults remain `legacy`. The review found no change to the phase-9 array return
carrier, phase-10 field families, or phase-11 invoke families.

## Invariant review

- `<init>` is admitted only in IR mode and never receives `ACC_NATIVE`.
  `ConstructorSpecialMethodProcessor` marks only its hidden static bridge
  native.
- `IrMethodCompiler` builds the complete constructor IR, validates the split,
  builds the independent suffix IR, and emits the C++ body before
  `MethodShellEmitter.beginIr` creates a bridge or rewrites bytecode.
- The split requires one direct `INVOKESPECIAL <init>` whose owner is the
  current class or its direct superclass. It rejects branches and switches in
  the retained prefix, edges from the suffix into the prefix, and exception
  regions crossing the split.
- The retained wrapper ends immediately after the one direct constructor call,
  then loads initialized local 0 followed by constructor descriptor arguments,
  invokes the static bridge, and returns. The native IR body starts after the
  retained call, so that call is not executed by both paths.
- The bridge descriptor is `V` with an initial `java/lang/Object` receiver
  followed by the constructor arguments in descriptor order. The non-static
  constructor IR mapping makes local 0 a `REFERENCE` exposed as `jobject obj`.
- A constructor rejected by either complete-body admission or split/suffix
  admission throws `UnsupportedIrConstructException` before shell creation.
  `NativeObfuscator` leaves that constructor as Java instead of entering the
  constructor-excluding legacy shell.

## Correctness finding and fix

The original split allowed `ASTORE` to overwrite local 0 or a reference-typed
constructor parameter before the direct `this`/`super` call. Both locals are
loaded after that call and forwarded to the bridge:

- overwriting local 0 could make IR local 0 refer to another object rather than
  the initialized receiver; and
- overwriting a parameter with another reference carrier could make the
  wrapper fail verification when the bridge descriptor requires the original
  reference type. For example, a valid constructor can store its `Object`
  parameter into its `String` parameter local before `super()`, while the
  rewritten bridge invocation still requires `String`.

The review branch conservatively rejects prefix `ASTORE` instructions targeting
local 0 or any reference/array parameter local. Rejection happens in split
validation before C++ output, cache entries, hidden methods, access flags, or
instruction lists are changed. Regression coverage uses valid original
constructors for both receiver and parameter-local cases, checks all fallback
state, serializes the unchanged classes, and invokes the constructors.

## Retained earlier phases and defaults

- Phase 9: reference array returns retain the `jarray` cast.
- Phase 10: `I`, `J`, object, and array fields retain exact
  `Int`/`Long`/`Object` JNI accessors.
- Phase 11: static, virtual, interface, and non-constructor special invokes
  retain their existing descriptor and carrier handling.
- `Main` still declares `defaultValue = "legacy"` and the API overload without
  a `CodegenMode` still delegates with `CodegenMode.LEGACY`.
- `obfuscator/src/main/resources/sources/cppsnippets.properties` remains
  present.

## Re-run evidence

Command:

```text
CC=gcc CXX=g++ ./gradlew :obfuscator:test \
  --tests by.radioegor146.ir.IrCompilerTest \
  --tests by.radioegor146.CodegenModeTest \
  --rerun-tasks
```

Result: `BUILD SUCCESSFUL`.

Counts read from the review branch JUnit XML:

```text
IrCompilerTest: tests=58, skipped=0, failures=0, errors=0 (time=0.69 s)
CodegenModeTest: tests=2, skipped=0, failures=0, errors=0 (time=0.097 s)
Total: 60 tests, 0 skipped, 0 failures, 0 errors
```

The toolchain was present (`g++ 13.3.0`, OpenJDK 21.0.10, and JNI headers).
`generatedCppPassesGppSyntaxCheckWhenToolchainAvailable` ran unskipped in
0.256 s. The retained
`/tmp/ir-compile-smoke12551902791792438333/ir-smoke.cpp` unit contains 61
`JNICALL` functions and also passed:

```text
g++ -std=c++17 -fsyntax-only \
  -I/usr/lib/jvm/java-21-openjdk-amd64/include \
  -I/usr/lib/jvm/java-21-openjdk-amd64/include/linux \
  /tmp/ir-compile-smoke12551902791792438333/ir-smoke.cpp
```

The independent command exited zero with empty diagnostics.

## Verifier-safety assessment

The identified forwarded-reference-local issue is fixed by fallback before
mutation. No other verifier-safety defect was found in the reviewed constructor
path. The admitted subset remains deliberately conservative: nonlinear
prefixes, cross-split handlers, prefix-only suffix locals, forwarded reference
local writes, and unsupported IR operations stay as Java bytecode.
