# IR phase 11 compiler review

Scope: review of the optional Java bytecode → typed CFG IR → C++/JNI lowering
for interface calls and non-constructor special calls added on
`cursor/ir-compiler-phase11-6d81` (draft PR #78), reviewed on branch
`cursor/ir-phase11-fable-review-6d81`.

This review reads Java bytecode admission, typed IR construction, and C++ JNI
emission. It uses neutral compiler terminology only.

## Verdict

**accept.**

The interface and non-constructor special invoke paths match the JNI lowering
contract, the descriptor typing is exact, the null-receiver and reject-before-
mutation invariants hold, and the legacy default and constructor-body exclusion
are unchanged. No correctness defect was found, so no compiler code was
modified by this review.

## Reviewed base

Branch `cursor/ir-compiler-phase11-6d81` tip `6fc6492` (phase-11 documentation),
whose code commit is `a8f3b1f` ("Add interface and nonvirtual IR invokes"),
stacked on `cursor/ir-compiler-phase10-6d81`
(`b8cdb8efb09c135e7d119249f48feba22cf7e8f4`).

Files carrying the phase-11 behavior:

- `obfuscator/src/main/java/by/radioegor146/ir/IrNodes.java`
- `obfuscator/src/main/java/by/radioegor146/ir/frontend/AsmToIr.java`
- `obfuscator/src/main/java/by/radioegor146/ir/frontend/CfgBuilder.java`
- `obfuscator/src/main/java/by/radioegor146/ir/emit/IrCppEmitter.java`
  (interface/special lowering shared with the existing invoke emitter)

## Findings against the review checklist

### 1. `INVOKEINTERFACE` → `Call*Method` with `GetMethodID` on the interface

Confirmed. `AsmToIr.invokeKind` maps `INVOKEINTERFACE` to the new
`IrNodes.Invoke.Kind.INTERFACE`. In `IrCppEmitter.emitInvoke`, an interface
invoke is neither `staticInvoke` nor `specialInvoke`, so the member cache is
built with `GetMethodID` (not `GetStaticMethodID`) against
`invoke.getOwner()` — for `INVOKEINTERFACE` the owner is the interface class
recorded in the constant pool. `invokeCallMethod` selects the `Call` prefix
(the else branch) and the carrier by result type, producing `CallIntMethod`,
`CallLongMethod`, `CallObjectMethod`, or `CallVoidMethod`. This is the same
virtual JNI family used by `INVOKEVIRTUAL`, which is correct: JNI resolves an
interface method ID obtained from `GetMethodID` on the interface through the
`Call*Method` families.

### 2. Non-constructor `INVOKESPECIAL` → `CallNonvirtual*Method(receiver, class, mid, args)`

Confirmed. `specialInvoke` is true for `Kind.SPECIAL`. The argument list is
built as receiver first (`expression(invoke.getReceiver())`), then the
declaring class slot (`array("cclasses", slots.classId)` for the special case),
then the method ID (`array("cmethods", slots.memberId)`), then the descriptor
arguments in order. `invokeCallMethod` selects the `CallNonvirtual` prefix,
yielding `CallNonvirtualIntMethod`, `CallNonvirtualLongMethod`,
`CallNonvirtualObjectMethod`, or `CallNonvirtualVoidMethod`. The
receiver/class/method-ID/args ordering matches the JNI signature.

### 3. `<init>` still `CallNonvirtualVoidMethod`

Confirmed. A constructor call remains `Kind.SPECIAL` with a void return, so it
takes the `CallNonvirtual` prefix with the `Void` carrier. `IrNodes.Invoke`
enforces that any `<init>` name must be a void special call, and
`AsmToIr.isSupportedInvoke` rejects `<init>` unless the opcode is
`INVOKESPECIAL` and the return sort is `VOID`. Constructor method *bodies*
remain excluded from processing by `MethodProcessor.shouldProcess`, which
returns false for `<init>`.

### 4. Null receiver exceptional exit

Confirmed. `emitInvoke` adds a `nullCheck` for every non-static invoke, so both
interface and special receivers are guarded. `CfgBuilder.canThrow` now lists
`INVOKEINTERFACE` (alongside the previously listed `INVOKESPECIAL`), so a
pending `NullPointerException` or a JNI call exception reaches the shared catch
dispatch when the call sits inside a protected region. The `exceptionCheck`
after the call routes pending JNI exceptions to the same exceptional exit.

### 5. Reject invokedynamic and unsupported primitive invoke sorts

Confirmed. `invokedynamic` produces an `InvokeDynamicInsnNode`, not a
`MethodInsnNode`; the admission switch in `AsmToIr` falls to the `default`
branch and marks it unsupported, throwing before any mutation.
`isSupportedInvoke` also restricts opcodes to the four method invoke opcodes and
restricts argument and return descriptor sorts to `INT`, `LONG`, references, and
`VOID` (return only). Primitive sorts `Z`, `B`, `C`, `S`, `F`, and `D` are
rejected rather than widened. `IrNodes.Invoke` independently re-derives the
descriptor's argument and return types via `invokeType`, re-checks the argument
count against the descriptor, and re-checks each argument and the result type,
which prevents a generated variadic JNI call from dropping or adding an argument
even if a caller bypassed frontend admission.

### 6. Reject-before-mutation; legacy default; constructor bodies excluded

Confirmed. Opcode and descriptor admission runs in the frontend validation pass
before the emitter allocates any cache or string IDs or changes method flags,
output, or native metadata. `assertUnchangedAfterRejectedIr` verifies that a
rejected method keeps `ACC_NATIVE` clear and leaves output, native metadata, and
the class/string/field/method caches empty; it backs
`rejectsUnsupportedAfterPhaseElevenInvokesBeforeMutation`,
`rejectsInvokedynamicBeforeMutation`, and
`rejectsInvokeDescriptorsOutsideExactCarrierSet`. The CLI option retains
`defaultValue = "legacy"` in `Main.java`, and the public API overload without a
`CodegenMode` argument delegates with `CodegenMode.LEGACY` in
`NativeObfuscator.java`. No snippet resources were removed.

## Observations (non-blocking)

- The supported invoke carrier set is encoded in two places: frontend admission
  (`AsmToIr.isSupportedInvoke` / `isSupportedInvokeReturn`) and the node
  constructor (`IrNodes.invokeType`). This is deliberate defense-in-depth — the
  node validates independently of the frontend — but the two lists must be kept
  in sync if the carrier set is later widened. Not a defect.
- The interface and special method IDs are cached in the shared `cmethods` array
  keyed by `CachedMethodInfo(owner, name, descriptor, static)`. Because the
  interface owner differs from a virtual owner, and special calls use the
  declaring class, no cache-key collision arises across kinds.

## Tests

Command run on 2026-08-29 with `CC=gcc CXX=g++`:

```text
CC=gcc CXX=g++ ./gradlew :obfuscator:test \
  --tests by.radioegor146.ir.IrCompilerTest \
  --tests by.radioegor146.CodegenModeTest \
  --rerun-tasks
```

Result: `BUILD SUCCESSFUL`.

Counts read directly from Gradle's JUnit XML in this run:

```text
IrCompilerTest:  tests=53, skipped=0, failures=0, errors=0 (time=0.667 s)
CodegenModeTest: tests=2,  skipped=0, failures=0, errors=0 (time=0.153 s)
Total: 55 tests, 0 skipped, 0 failures, 0 errors
```

Toolchain in this environment:

```text
g++ (Ubuntu 13.3.0-6ubuntu2~24.04.1) 13.3.0
openjdk version "21.0.10" 2026-01-20
```

`generatedCppPassesGppSyntaxCheckWhenToolchainAvailable` ran (time=0.22 s) with
no `<skipped>` element in the XML, so the generated translation unit was
actually compiled by g++ rather than the assumption being skipped.
`CodegenModeTest.cliDefaultsToLegacy` passed, and source inspection confirms the
`legacy` CLI default and the `CodegenMode.LEGACY` API delegation.

## Compiler code changed by this review

No. This review is documentation-only; no correctness defect required a fix.
