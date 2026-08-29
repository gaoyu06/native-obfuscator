# IR phase 9 compiler review

Review branch: `cursor/ir-phase9-sol-review-6d81`

Reviewed subject: `cursor/ir-compiler-phase9-6d81` /
[PR #66](https://github.com/gaoyu06/native-obfuscator/pull/66), compared with
`cursor/ir-compiler-phase8-6d81` at
`95eb5ffd2fc5a9515af65c1d15403e7c983c64a5`.

## Verdict

**Accept.**

The reference-return, null, reference-branch, and category-one discard paths
are correct after the array-return JNI carrier bug described below was fixed.
No remaining correctness blocker or review nit was found in the requested
phase-9 scope.

This is not a production-readiness approval. The focused unit and C++ syntax
checks do not replace supported-platform CI and native runtime-parity coverage,
and the stacked base still requires its normal human disposition.

## Scope reviewed

The complete phase-9 diff and current contents were inspected for:

- `ir/IrMethod.java`
- `ir/IrNodes.java`
- `ir/emit/IrCppEmitter.java`
- `ir/frontend/AsmToIr.java`
- `ir/IrCompilerTest.java`

`CodegenModeTest`, `IrMethodCompiler`, `MethodShellEmitter`,
`NativeObfuscator`, `Main`, `MethodProcessor`, the retained snippet resources,
`PR_BODY.md`, and `docs/architecture/ir-phase9-status.md` were also checked for
integration, fallback atomicity, constructor exclusion, and default-mode
claims.

## Findings

### `ARETURN` and `ACONST_NULL`

- Method-shape validation maps both object and array return descriptors to
  `IrType.REFERENCE`. `ARETURN` is admitted only when that carrier matches the
  method descriptor, pops exactly one reference, and creates a reference-valued
  `Return` terminator.
- `ACONST_NULL` creates a dedicated reference-typed `NullReference` instruction
  and emits `nullptr`.
- Object-return JNI functions use the existing `jobject` carrier. Array-return
  JNI functions use the shell's `jarray` return type and now cast the coarsened
  `jobject` SSA value to `jarray` at the return boundary.
- Unprotected JNI failure exits in reference-returning methods emit
  `return nullptr;`. These exits do not call `ExceptionClear`, so a pending
  exception remains pending.

### Correctness bug found and fixed

The implementation branch admitted array descriptors for `ARETURN`, but the IR
coarsens reference instruction results to `jobject` while the JNI shell emits
`jarray` for an array-returning method. Returning a `jobject` directly from
such a function is invalid C++: JNI's `_jobject*` does not implicitly convert
down to `_jarray*`. The original 39-method smoke covered only an object-return
method and did not exercise this boundary.

`IrCppEmitter` now emits an explicit `(jarray)` cast for a reference return when
the method return descriptor is an array. Object returns remain unchanged. The
new `returnsAllocatedArrayWithJniDescriptorCarrier` regression checks the
`jarray` function signature, unprotected `nullptr` default, and final carrier
cast. The generated array-return method is also part of the retained g++ smoke
translation unit.

### Reference null branches

- `IFNULL` and `IFNONNULL` are admitted as reference-only stack consumers.
- They lower to `IrNodes.ReferenceBranch` with `IS_NULL` and `IS_NON_NULL`,
  respectively; the existing integer `Branch` node is not reused.
- The emitter compares the reference expression directly with `nullptr` and
  preserves the existing true-target/fallthrough edge-transfer behavior.

### Category-one `POP`

- `POP` checks the top typed value's JVM width and accepts only one-slot
  category-one values.
- Applying `POP` to an `I64` is rejected. `POP2` is absent from instruction
  admission, so it and the other category-two stack forms still fall back per
  method.

### Fallback before mutation and retained defaults

`IrMethodCompiler.processMethod` performs frontend construction and complete
body emission before `MethodShellEmitter.beginIr` changes method flags or
appends output/native metadata. Complete opcode admission occurs before IR
lowering or emitter cache allocation. The mixed phase-9 regression places
`ACONST_NULL`, `POP`, `IFNONNULL`, and `ARETURN` before unsupported `POP2`; it
confirms unchanged `ACC_NATIVE`, empty output/native metadata, and untouched
class/string/field/method caches.

`MethodProcessor.shouldProcess` still excludes `<init>` method bodies. The CLI
option still has `defaultValue = "legacy"`, and the API overload without an
explicit mode still delegates with `CodegenMode.LEGACY`. The legacy snippet
resource remains present.

## Verification evidence

Command re-run on 2026-08-28:

```text
CC=gcc CXX=g++ ./gradlew :obfuscator:test \
  --tests by.radioegor146.ir.IrCompilerTest \
  --tests by.radioegor146.CodegenModeTest \
  --rerun-tasks
```

The command completed with `BUILD SUCCESSFUL`. Counts read directly from the
resulting JUnit XML:

```text
IrCompilerTest: tests=43, skipped=0, failures=0, errors=0 (time=0.686 s)
CodegenModeTest: tests=2, skipped=0, failures=0, errors=0 (time=0.149 s)
Total: 45 tests, 0 skipped, 0 failures, 0 errors
```

The implementation branch claimed 42 + 2 = 44. The reviewed total is one
higher because the correctness fix adds the array-return regression.

The XML testcase
`generatedCppPassesGppSyntaxCheckWhenToolchainAvailable` completed in 0.257 s
and has no `skipped` element. The environment had:

```text
g++ (Ubuntu 13.3.0-6ubuntu2~24.04.1) 13.3.0
openjdk version "21.0.10" 2026-01-20
JNI headers: present
```

The retained translation unit contained 40 generated methods, including
`returnAllocatedObjectArray(I)[Ljava/lang/Object;`. It was independently
checked with:

```text
g++ -std=c++17 -fsyntax-only \
  -I/usr/lib/jvm/java-21-openjdk-amd64/include \
  -I/usr/lib/jvm/java-21-openjdk-amd64/include/linux \
  /tmp/ir-compile-smoke3304799378171014695/ir-smoke.cpp
```

The independent command exited zero with empty diagnostics. Inspection of the
retained unit confirmed the array method has a `jarray` JNI signature, uses
`return nullptr;` on unprotected exceptional exits, and ends with
`return (jarray) v2;`.

## Human preconditions

1. Review and land against `cursor/ir-compiler-phase8-6d81`, not `master`,
   preserving the compiler stack order.
2. Require supported-platform/JDK CI and native runtime-parity checks for the
   final stacked commit.
3. Keep `legacy` as the default until broader runtime evidence supports a
   default-mode change.
4. During conflict resolution, retain the array-return carrier cast, dedicated
   reference conditions, category-one `POP` guard, pending-exception behavior,
   constructor exclusion, and fallback-before-mutation regression.
