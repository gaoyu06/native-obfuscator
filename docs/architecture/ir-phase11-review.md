# IR phase 11 compiler review

Review branch: `cursor/ir-phase11-sol-review-6d81`

Reviewed subject: `cursor/ir-compiler-phase11-6d81` at
`6fc64927a53c777a36c38e54aaed01b1bd696ed3` /
[PR #78](https://github.com/gaoyu06/native-obfuscator/pull/78), compared with
`cursor/ir-compiler-phase10-6d81` at
`b8cdb8efb09c135e7d119249f48feba22cf7e8f4` /
[PR #73](https://github.com/gaoyu06/native-obfuscator/pull/73).

## Verdict

**Accept.**

No correctness bug was found in the requested phase-11 scope, so this review
does not change compiler code. The interface and non-constructor special call
paths use the required JNI lookup and call families, preserve descriptor
argument order, and retain the established fallback and integration
invariants.

This verdict is not a production-readiness approval. Phase 11 is still an
optional compiler subset, and the focused unit and C++ syntax checks do not
replace supported-platform native runtime-parity gates.

## Findings

### `INVOKEINTERFACE`

- `AsmToIr` admits `INVOKEINTERFACE` only for the existing exact `I`, exact
  `J`, object/array reference, and `V` carriers and records it as the distinct
  `IrNodes.Invoke.Kind.INTERFACE`.
- The frontend pops JVM operands from the stack in reverse order but stores
  them in descriptor order. `IrNodes.Invoke` independently checks that the
  supplied argument count equals the descriptor argument count and checks
  every argument and result carrier.
- `IrCppEmitter` resolves the interface owner through its class cache and calls
  `GetMethodID` on that cached interface class. It emits
  `CallIntMethod`, `CallLongMethod`, `CallObjectMethod`, or `CallVoidMethod`
  with receiver, method ID, and descriptor arguments.
- The retained generated translation unit contains the exact one-argument
  integer, one-argument long, one-argument reference-return, and two-argument
  void interface call shapes.

### Non-constructor `INVOKESPECIAL`

- Non-constructor special calls use the same supported carrier set and lower
  to `CallNonvirtualIntMethod`, `CallNonvirtualLongMethod`,
  `CallNonvirtualObjectMethod`, or `CallNonvirtualVoidMethod`.
- Generated calls pass receiver, declaring-class cache entry, method ID, then
  descriptor arguments. The method ID is obtained with `GetMethodID` from the
  same declaring-class entry.
- `<init>` remains restricted to void `INVOKESPECIAL`, has no IR result, and
  therefore stays on the existing `CallNonvirtualVoidMethod` path.
  `MethodProcessor.shouldProcess` still excludes constructor method bodies.

### Exceptional exits and fallback

- `CfgBuilder` classifies both `INVOKEINTERFACE` and `INVOKESPECIAL` as
  potentially throwing. Non-static invokes receive the existing explicit null
  check. An unprotected null receiver returns through the exceptional exit
  without clearing the pending `NullPointerException`; a protected call enters
  the shared catch dispatch.
- The generated protected interface case checks the receiver before the JNI
  call, raises `NullPointerException`, and jumps to `IR_CATCH_0`. The shared
  dispatcher performs the normal catch matching and rethrows an unmatched
  exception.
- `invokedynamic` remains outside instruction admission. Its regression reports
  `INVOKEDYNAMIC` and verifies rejection before method or cache mutation.
- The phase-11 mixed fallback regression places supported interface and
  non-constructor special calls before unsupported `FCONST_0`. Frontend
  rejection leaves `ACC_NATIVE`, generated output, native metadata, and the
  class/string/field/method caches unchanged.

### Retained stacked behavior

- The phase-9 array-return boundary still casts the coarsened reference value
  to `jarray`; the generated smoke source contains the cast.
- Phase-10 instance/static `I`, `J`, object, and array field regressions remain
  in the test and generated-C++ smoke coverage.
- The CLI option still defaults to `legacy`, and the API overload without an
  explicit mode still delegates with `CodegenMode.LEGACY`.
- The phase-11 diff does not remove or modify legacy snippet resources.

## Bugs fixed

None. No compiler-code change was needed for this review.

## Verification evidence

Command re-run on 2026-08-29:

```text
CC=gcc CXX=g++ ./gradlew :obfuscator:test \
  --tests by.radioegor146.ir.IrCompilerTest \
  --tests by.radioegor146.CodegenModeTest \
  --rerun-tasks
```

The command completed with `BUILD SUCCESSFUL`. Counts read directly from the
new JUnit XML:

```text
IrCompilerTest: tests=53, skipped=0, failures=0, errors=0 (time=0.725 s)
CodegenModeTest: tests=2, skipped=0, failures=0, errors=0 (time=0.098 s)
Total: 55 tests, 0 skipped, 0 failures, 0 errors
```

The implementation claim was 53 + 2 = 55; the review re-run matches it.

`generatedCppPassesGppSyntaxCheckWhenToolchainAvailable` completed in 0.280 s
and has no `<skipped>` child in the XML. The environment had:

```text
g++ (Ubuntu 13.3.0-6ubuntu2~24.04.1) 13.3.0
openjdk version "21.0.10" 2026-01-20
JNI headers: present
```

The retained file
`/tmp/ir-compile-smoke13769997576742768220/ir-smoke.cpp` contains 59
`IR codegen:` method markers. It includes every phase-11 JNI call family, the
interface null-receiver case, the phase-9 `jarray` return cast, and the phase-10
field accessors. It was independently checked with:

```text
g++ -std=c++17 -fsyntax-only \
  -I/usr/lib/jvm/java-21-openjdk-amd64/include \
  -I/usr/lib/jvm/java-21-openjdk-amd64/include/linux \
  /tmp/ir-compile-smoke13769997576742768220/ir-smoke.cpp
```

The independent command exited zero with empty diagnostics.

## Human preconditions

1. Review and land against `cursor/ir-compiler-phase10-6d81`, not `master`,
   preserving the compiler stack order.
2. Require supported-platform/JDK CI and native runtime-parity checks before
   treating the optional slice as production-ready.
3. Keep `legacy` as the default.
4. During conflict resolution, retain descriptor-exact invoke validation,
   nonvirtual argument ordering, null-receiver exceptional exits,
   fallback-before-mutation, the phase-9 `jarray` cast, phase-10 fields, and
   constructor-body exclusion.
