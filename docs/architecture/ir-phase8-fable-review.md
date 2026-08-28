# IR phase 8 compiler review

Review branch: `cursor/ir-phase8-fable-review-6d81`

Reviewed subject: `cursor/ir-compiler-phase8-6d81` /
[PR #62](https://github.com/gaoyu06/native-obfuscator/pull/62), compared with
`cursor/ir-phase7-sol-review-6d81-f29d` /
[PR #56](https://github.com/gaoyu06/native-obfuscator/pull/56).

## Verdict

**Accept.**

No correctness blocker or review nit remains in the requested phase-8 scope.
This is not a ship-readiness approval: the focused unit and C++ syntax checks
do not replace supported-platform native runtime-parity coverage, and the
stacked base still requires its normal human disposition.

## Scope reviewed

The complete phase-8 diff (`b10efa1`) and the current contents of every changed
IR implementation file were read:

- `ir/IrMethod.java`
- `ir/IrNodes.java`
- `ir/emit/IrCppEmitter.java`
- `ir/frontend/AsmToIr.java`
- `ir/frontend/CfgBuilder.java`

The added test delta in `ir/IrCompilerTest.java` and the current
`CodegenModeTest.java` were also read. `IrMethodCompiler`, `MethodShellEmitter`,
`NativeObfuscator`, `CodegenMode`, and `PR_BODY.md` were inspected for
integration, default-mode, and fallback claims.

Constructor *method bodies* are out of scope by design: phase 8 lowers only the
`NEW`/`<init>` call site, not the compilation of `<init>` itself. Any `<init>`
method body is processed as its own method and is subject to the same admission
and per-method fallback as every other method.

## Findings

### `NEW` allocation and cached class lookup

- `AsmToIr` admits `NEW` only when the type operand is present, non-empty, and
  not an array descriptor (`!desc.startsWith("[")`); array creation stays with
  `ANEWARRAY`/`NEWARRAY`. The stack transfer pushes one `REFERENCE`, and the
  lowering emits a typed `IrNodes.NewObject` node carrying the class name.
- `CfgBuilder.mayThrow` now includes `NEW`, so an allocation inside a protected
  region ends its block with the ordered handler set attached.
- `IrCppEmitter.emitNewObject` resolves the class through the existing
  `emitClassCache` path (same cache shape used by `NEWARRAY`/invokes), guards a
  null class slot to the exceptional exit, then assigns
  `(jobject) env->AllocObject(cclasses[id])`. `AllocObject` allocates without
  running any constructor, which matches JVM `NEW` semantics (the constructor is
  a separate `INVOKESPECIAL`).

### Constructor `INVOKESPECIAL <init>` lowering

- `isSupportedInvoke` admits `INVOKESPECIAL` only when the name is `<init>` and
  the descriptor return is `void`; every other special call falls back.
- `IrNodes.Invoke` enforces the same rule structurally: a `SPECIAL` kind must
  have name `<init>` and a `null` result, or construction throws.
- `emitInvoke` emits `env->CallNonvirtualVoidMethod(receiver, cclasses[id],
  cmethods[id], args...)`. The JNI argument order (object, class, method id,
  args) is correct, and the method id is obtained through `GetMethodID` (the
  non-static path), which is correct for an instance `<init>`. The class passed
  is the invoke owner, which for `new Foo(...)` is the constructed class.

### Operand-stack / `DUP` behavior

- `javac` emits `NEW T; DUP; <args>; INVOKESPECIAL T.<init>`. In this IR, `NEW`
  pushes one SSA reference, `DUP` duplicates the same SSA value (value aliasing,
  category-one guard enforced), and the `void` `INVOKESPECIAL` pops one copy as
  the receiver and pushes nothing. The remaining stack entry is the same SSA
  value produced by `AllocObject`, so the constructed object is what stays live.
- The `constructObject` fixture (`NEW java/lang/Object; DUP; INVOKESPECIAL
  <init>; INVOKEVIRTUAL hashCode; IRETURN`) confirms the constructor consumes
  the duplicate and the surviving reference feeds `hashCode`.

### Failure routing

- Class lookup failure (`!cclasses[id]`) routes to the exceptional exit before
  `AllocObject` is attempted.
- Allocation failure routes to the exceptional exit when the result is null
  **or** `env->ExceptionCheck()` reports a pending exception, matching the
  `NEWARRAY`/`ANEWARRAY` failure pattern already in the tree.
- The constructor call is followed by the shared `exceptionCheck`, so a throwing
  constructor is propagated or dispatched to the ordered handler set.

### Static and virtual invoke widening

- Arguments now admit exact `I`, `J`, and reference sorts; returns admit `V`,
  exact `I`, `J`, and reference. `IrNodes.Invoke` validates arguments and result
  to `I32`/`I64`/`REFERENCE`.
- `invokeCallMethod` composes the JNI family from kind and carrier: `CallStatic`
  / `Call` / `CallNonvirtual` prefix crossed with `Void`/`Int`/`Long`/`Object`.
  The observed families are `CallStaticLongMethod`, `CallStaticObjectMethod`,
  `CallStaticVoidMethod`, `CallObjectMethod`, `CallLongMethod`, and
  `CallNonvirtualVoidMethod` — all valid JNI entry points.
- A void invoke emits a bare expression statement (no assignment); a
  value-returning invoke assigns the carrier result. `String.length()` remains
  routed to the dedicated `GetStringLength` intrinsic rather than a generic
  virtual call.

### Reject-before-mutation and default mode

`IrMethodCompiler.processMethod` performs, in order:

1. `AsmToIr.build(...)` — full opcode admission runs at the start of `build`,
   before IR construction and before the emitter allocates any cache/string id.
2. `IrCppEmitter.emitBody(...)`.
3. `MethodShellEmitter.beginIr(...)` and `context.output.append(...)`.

Because admission runs inside `build`, an unsupported construct is rejected
before the method, generated output, native-method metadata, or the
class/string/field/method caches are touched. The `unsupportedAfterNew` fixture
places `POP` after an admitted `NEW; DUP; INVOKESPECIAL <init>` and confirms the
rejection carries opcode `POP` while `ACC_NATIVE`, `context.output`,
`context.nativeMethods`, and all four caches remain empty. `NativeObfuscator`
then falls back to the legacy generator for that method only.

`CodegenMode` defaults to `LEGACY`: the API overload without an explicit mode
passes `CodegenMode.LEGACY`, and `cliDefaultsToLegacy` confirms the CLI default.
IR remains opt-in.

### Observation (non-blocking)

The constructor `INVOKESPECIAL` path emits a receiver null check before
`CallNonvirtualVoidMethod`. For the `new`/`dup`/`<init>` shape the receiver is a
just-allocated, null-checked object, so this branch is never taken. It is
harmless and consistent with the shared instance-invoke helper; no change is
required.

## Verification evidence

Command (run with `CC=gcc CXX=g++`):

```text
CC=gcc CXX=g++ ./gradlew :obfuscator:test \
  --tests by.radioegor146.ir.IrCompilerTest \
  --tests by.radioegor146.CodegenModeTest
```

The command completed with `BUILD SUCCESSFUL`. Final JUnit XML:

```text
IrCompilerTest:  36 tests, 0 skipped, 0 failures, 0 errors (0.59 s)
CodegenModeTest:  2 tests, 0 skipped, 0 failures, 0 errors (0.095 s)
Total:           38 tests, 0 skipped, 0 failures, 0 errors
```

The XML testcase `generatedCppPassesGppSyntaxCheckWhenToolchainAvailable`
completed in ~0.24 s and has no `skipped` element. It assembles a 34-method
translation unit that includes the phase-8 methods (`constructObject`,
`callLong`, `virtualStringLength`, `staticStringLength`, `consumeLong`), asserts
the phase-8 emissions (`env->AllocObject(cclasses[`,
`env->CallNonvirtualVoidMethod`, `env->CallStaticLongMethod`,
`env->CallObjectMethod`, `env->CallStaticObjectMethod`,
`env->CallStaticVoidMethod`), and syntax-checks the unit with
`g++ -std=c++17 -fsyntax-only` against real JNI headers. The host has g++ 13.3.0
and JDK 21 JNI headers
(`/usr/lib/jvm/java-21-openjdk-amd64/include{,/linux}`), so the smoke is real
rather than skipped.

## Blockers

None found; no correctness fix was required on the review branch.

## Human preconditions

1. Review and land against `cursor/ir-phase7-sol-review-6d81-f29d`, not
   `master`, preserving the stack order.
2. Require the repository's supported-platform/JDK CI and native
   runtime-parity checks for the final stacked commit.
3. Keep `legacy` as the default until broader runtime evidence supports a
   default-mode change.
4. During conflict resolution, retain cached-class allocation and its failure
   routing, `NEW`/`DUP`/constructor stack behavior, the
   `CallNonvirtualVoidMethod` argument order, the invoke carrier-to-JNI family
   mapping, constructor-method-body exclusion, and reject-before-mutation
   coverage.
