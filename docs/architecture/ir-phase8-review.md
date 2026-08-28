# IR phase 8 compiler review

Review branch: `cursor/ir-phase8-sol-review-6d81`

Reviewed subject: `cursor/ir-compiler-phase8-6d81` /
[PR #62](https://github.com/gaoyu06/native-obfuscator/pull/62), compared with
`cursor/ir-phase7-sol-review-6d81-f29d` /
[PR #56](https://github.com/gaoyu06/native-obfuscator/pull/56).

## Verdict

**Accept.**

No correctness blocker or review nit remains in the requested phase-8 scope.
This is not a ship-readiness approval: phase 8 remains an incomplete opt-in
compiler slice, and focused unit plus C++ syntax checks do not replace
supported-platform native runtime-parity coverage.

## Scope reviewed

The complete phase-8 diff and current contents were inspected for:

- `ir/IrMethod.java`
- `ir/IrNodes.java`
- `ir/emit/IrCppEmitter.java`
- `ir/frontend/AsmToIr.java`
- `ir/frontend/CfgBuilder.java`
- `ir/IrMethodCompiler.java`
- `ir/emit/MethodShellEmitter.java`
- `ir/IrCompilerTest.java`
- `CodegenModeTest.java`
- `Main.java`, `NativeObfuscator.java`, and `MethodProcessor.java`

The status document, PR body, changed-file list, and resource delta were also
checked for the default-mode, constructor-body, and retained-snippet claims.

## Allocation and exception-routing findings

- `NEW` is represented by `NewObject`, whose result is required to be the
  `REFERENCE` IR type. The frontend admits a nonempty ordinary class name and
  rejects an array descriptor for this opcode.
- The emitter resolves the class through the existing class cache, checks that
  the cache slot is usable, and passes that `jclass` to `JNIEnv::AllocObject`.
  A null allocation result or a pending JNI exception takes the block's
  exceptional exit.
- `CfgBuilder.mayThrow` now includes both `NEW` and `INVOKESPECIAL`. A protected
  class-resolution, allocation, constructor method lookup, null check, or
  constructor call therefore transfers to the ordered shared catch dispatcher;
  an unprotected failure returns the JNI default while preserving the pending
  exception.
- The verified `NEW`, `DUP`, `INVOKESPECIAL <init>` sequence has the expected
  stack behavior. `NEW` pushes one reference SSA value, `DUP` places the same
  value in both logical slots, and the constructor pops only its receiver. The
  duplicate remains for the next instruction; no second allocation or copy is
  introduced.
- Constructor method bodies remain excluded by
  `MethodProcessor.shouldProcess`, which rejects methods named `<init>`. This
  phase only supports constructor calls from otherwise supported methods.

## Invoke and JNI-family mapping findings

Admission is deliberately exact. Static and virtual invokes accept descriptor
arguments `I`, `J`, object, and array; they accept `V`, exact `I`, `J`, object,
and array returns. Other primitive descriptor sorts are not silently treated
as `I`. `INVOKESPECIAL` is admitted only when the name is `<init>` and the
return is `V`.

| Bytecode kind | `V` | `I` | `J` | object/array |
| --- | --- | --- | --- | --- |
| `INVOKESTATIC` | `CallStaticVoidMethod` | `CallStaticIntMethod` | `CallStaticLongMethod` | `CallStaticObjectMethod` |
| `INVOKEVIRTUAL` | `CallVoidMethod` | `CallIntMethod` | `CallLongMethod` | `CallObjectMethod` |
| constructor `INVOKESPECIAL` | `CallNonvirtualVoidMethod` | not admitted | not admitted | not admitted |

The frontend pops declared arguments from the JVM stack in reverse order but
stores each value at its descriptor index. It then pops the receiver. The
emitter consequently supplies:

- static calls as `class, method ID, arg0, arg1, ...`;
- virtual calls as `receiver, method ID, arg0, arg1, ...`; and
- constructor calls as
  `receiver, declaring class, method ID, arg0, arg1, ...`.

`I` uses the `jint`/`I32` carrier, `J` uses `jlong`/`I64`, and references use
`jobject`/`REFERENCE`. Return assignments select the matching JNI family from
the typed result, and every emitted call is followed by the block-aware
exception check.

## Fallback-before-mutation findings

`IrMethodCompiler.processMethod` still orders work as:

1. complete `AsmToIr.build`;
2. complete `IrCppEmitter.emitBody`;
3. call `MethodShellEmitter.beginIr` and mutate the method shell.

`AsmToIr.build` performs whole-method opcode and invoke-descriptor admission
before stack/local analysis and lowering. Thus the phase-8 regression that
places supported `NEW`, `DUP`, and constructor `INVOKESPECIAL` before an
unsupported `POP` fails before emitter cache allocation and before shell
mutation. It checks `ACC_NATIVE`, output, native metadata, and every cache.

## Defaults and retained behavior

Defaults are unchanged. The CLI option still declares
`defaultValue = "legacy"`, and the public API overload without a codegen
argument still passes `CodegenMode.LEGACY`. `--codegen=ir` remains opt-in and
unsupported methods fall back independently.

The phase-8 changed-file list contains no snippet resource. Existing snippets
remain present and the legacy path remains selected by default.

## Verification evidence

The branch-local rerun is intentionally recorded after the pre-test review
commit. The final review commit will replace this paragraph with the exact
commands, JUnit XML counts, g++ smoke skip state, and independent emitted-unit
syntax result from this environment.

## Bugs fixed

None. The reviewed compiler implementation is acceptable as submitted, so this
review branch does not change compiler code.
