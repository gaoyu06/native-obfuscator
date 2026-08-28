# IR phase 3 status

This branch extends the opt-in typed-CFG compiler from
`cursor/ir-phase2-fable-review-6d81` (phase 2 plus its accept-with-nits Fable
review). It remains selected only by `--codegen=ir`; legacy codegen is still the
CLI/API default, and an unsupported method still falls back independently to the
legacy compiler. No opcode machine was implemented and the default was not
flipped.

## New capability in phase 3

Phase 1/2 kept integer constants, `ILOAD`/`ISTORE`/`ALOAD`/`IINC`/`DUP`,
`IADD`/`ISUB`/`IMUL`, integer branches/`GOTO`, integer/void returns, `I`
instance fields (`GETFIELD`/`PUTFIELD`), and `I`-returning
`INVOKESTATIC`/`INVOKEVIRTUAL`. Phase 3 adds:

1. **More integer arithmetic (structured, carrier-correct).**
   - Bitwise `IAND`/`IOR`/`IXOR` — a new `IrNodes.Binary` operation each,
     emitted as `(jint)((uint32_t) l OP (uint32_t) r)`.
   - Shifts `ISHL`/`ISHR`/`IUSHR` — the JVM shift-amount mask (`& 31`) is applied
     explicitly, and signedness is carried by the operand cast: `ISHL`/`IUSHR`
     shift a `uint32_t`, `ISHR` shifts an `int32_t` for an arithmetic right
     shift.
   - Unary `INEG` and the narrowing conversions `I2B`/`I2S`/`I2C` — a new
     `IrNodes.Unary` node. `INEG` is `(jint)(-(uint32_t) v)` so overflow wraps;
     the conversions are nested casts (`(jint)(jbyte) v`, `(jint)(jshort) v`,
     `(jint)(jchar) v`).

2. **`int[]` array access as dedicated IR nodes with real bounds checking.**
   - `ARRAYLENGTH` → `IrNodes.ArrayLength`, emitting `env->GetArrayLength(...)`
     after a receiver null check.
   - `IALOAD` → `IrNodes.ArrayLoad`, emitting `env->GetIntArrayRegion(arr, i, 1,
     &tmp)` into a scoped temporary. `GetIntArrayRegion` itself raises
     `ArrayIndexOutOfBoundsException` for an out-of-range index, so the emitted
     `ExceptionCheck` early-return is a real bounds edge, not dead code.
   - `IASTORE` → `IrNodes.ArrayStore`, emitting `env->SetIntArrayRegion(arr, i,
     1, &tmp)` with the same real bounds behavior.
   - The region temporaries are declared inside their own C++ block scope so the
     function-level `goto` edges never jump into an initialized automatic
     variable (the same discipline phase 1/2 used for SSA carriers and edge
     copies).

3. **`String.length()` as a dedicated intrinsic (real C++ / limited JNI).**
   `INVOKEVIRTUAL java/lang/String.length()I` now lowers to
   `IrNodes.StringLength`, emitting one `env->GetStringLength((jstring) recv)`
   after a receiver null check — not a `GetMethodID` cache lookup plus
   `CallIntMethod`. The regression test confirms the emitted C++ contains
   `GetStringLength` and contains neither `GetMethodID` nor `CallIntMethod`.
   Other `int`-returning virtual calls (e.g. `Object.hashCode()`) still take the
   generic invoke path.

The frontend's forward operand-stack **type** analysis was extended to type all
of the above (bitwise/shift/unary keep the `i32` carrier; `ARRAYLENGTH`/`IALOAD`
consume a reference and produce `i32`; `IASTORE` consumes reference + index +
value). `connectPhis` still re-checks carrier identity across every edge.

## Safe fallback and per-method fallback-before-mutation (preserved)

The phase-1/2 property is unchanged and load-bearing for the new opcodes.
Whole-method opcode, descriptor, local-carrier, stack-carrier, and
definite-local validation completes inside `AsmToIr.build(...)` — which throws
`UnsupportedIrConstructException` — before `IrCppEmitter.emitBody` allocates any
JNI cache id and before `MethodShellEmitter.beginIr` mutates `output`,
`nativeMethods`, or `ACC_NATIVE`. A capability miss therefore leaves the shared
caches and the reused `MethodContext` pristine, and the existing per-method
legacy fallback in `NativeObfuscator` runs on clean state. The
`rejectsIntStoreIntoInstanceReceiverLocal` regression test still asserts that a
rejected method stays non-native with empty output and untouched caches.

## Try/catch (item 4): skipped, with reason

Phase 2 rejects every method with a non-empty `tryCatchBlocks`, and
`CfgBuilder` deliberately builds a **normal-edge-only** CFG (`finishIr` even
throws if `context.catches` is non-empty). A correct try/catch lowering would
require modelling exceptional CFG edges from every protected instruction to
handler blocks, seeding each handler's operand stack with the caught exception,
and emitting `ExceptionOccurred`/`ExceptionClear` plus `IsInstanceOf`-based
catch-type dispatch with the phi machinery. That is a structural change to the
frontend CFG and the phi/stack-typing analyses, disproportionate to the rest of
phase 3, so it is **skipped** this phase. Methods with exception tables continue
through the per-method legacy fallback exactly as in phase 2.

## Compile-smoke evidence (item 5)

`IrCompilerTest.generatedCppPassesGppSyntaxCheckWhenToolchainAvailable` emits
complete JNI functions through the shared shell and assembles them into one
translation unit with the real cache-carrier declarations, then (when `g++`,
`jni.h`, and the platform JNI include directory are present) runs
`g++ -std=c++17 -fsyntax-only`. The phase-3 method set adds:

- `mix(II)I` — `IAND`/`IOR`/`IXOR`/`ISHL`/`ISHR`/`IUSHR`
- `narrow(I)I` — `INEG`/`I2B`/`I2S`/`I2C`
- `bump([II)I` — `ARRAYLENGTH` + `IALOAD` + `IASTORE`
- `stringLength(Ljava/lang/String;)I` — the `GetStringLength` intrinsic

alongside the existing `add`/`sumTo`/`subMul`, the `value:I` field increment, the
`INVOKESTATIC`, and the (now `Object.hashCode()`) generic `INVOKEVIRTUAL`.

Real environment used on 2026-08-28:

```text
g++ (Ubuntu 13.3.0-6ubuntu2~24.04.1) 13.3.0
openjdk version "21.0.10" 2026-01-20
cmake version 3.28.3
java.home=/usr/lib/jvm/java-21-openjdk-amd64
jni_headers=present
```

Direct reproduction of the syntax check on the generated translation unit
(exactly what the in-test smoke runs), exit code recorded:

```text
$ g++ -std=c++17 -fsyntax-only \
    -I$JAVA_HOME/include -I$JAVA_HOME/include/linux <generated ir-smoke.cpp>
g++ exit=0
```

The generated `bump([II)I` body, verbatim from that compiled unit, showing the
null check, the scoped region temporary, and the real bounds `ExceptionCheck`:

```cpp
    if (arg0 == nullptr) {
        utils::throw_re(env, ((char *)(string_pool + 56LL)), ((char *)(string_pool + 234LL)), -1);
        return 0;
    }
    {
        jint iaload0 = 0;
        env->GetIntArrayRegion((jintArray) arg0, arg1, 1, (&iaload0));
        if (env->ExceptionCheck() != 0) {
            return 0;
        }
        v3 = iaload0;
    }
```

The `stringLength` body, verbatim:

```cpp
    if (arg0 == nullptr) {
        utils::throw_re(env, ((char *)(string_pool + 56LL)), ((char *)(string_pool + 269LL)), -1);
        return 0;
    }
    v1 = env->GetStringLength((jstring) arg0);
    return v1;
```

## Test result (item 5/6)

Command:

```text
./gradlew :obfuscator:test \
  --tests by.radioegor146.ir.IrCompilerTest \
  --tests by.radioegor146.CodegenModeTest
```

Result: `BUILD SUCCESSFUL`. The JUnit XML records `IrCompilerTest` `tests="12"
skipped="0" failures="0" errors="0"` and `CodegenModeTest` `tests="2" skipped="0"
failures="0" errors="0"`. The compile-smoke test executed (0.123 s), it was not
skipped, and its embedded `g++ -std=c++17 -fsyntax-only` exited 0.

The full `:obfuscator:test` run additionally executes the `TestsGenerator`
end-to-end suite (8 cases), which fails in this VM with `CMake prepare has
failed`. That failure is **pre-existing and unrelated**: it reproduces
identically (8/8 failed) on the base branch `cursor/ir-phase2-fable-review-6d81`
with these changes stashed, and those e2e cases build with the default legacy
codegen (they never pass `--codegen=ir`), so the opt-in IR path cannot affect
them. It is an environment/toolchain gap, not a regression from this branch.

## What still falls back per method

Everything not listed above. The frontend still rejects: exception tables
(try/catch, see item 4 above); static fields and non-`I` fields; `long`/`float`/
`double`/reference-returning or void invokes; invokes with non-`I` primitive
arguments; `INVOKESPECIAL`/`INVOKEINTERFACE`/`invokedynamic`; object and array
**creation** (`new`, `<init>`, `newarray`/`anewarray`/`multianewarray`) — the
phase-2 "skip constructors" policy is unchanged and no constructors were
started; non-`int` array element access (`AALOAD`/`BALOAD`/`CALOAD`/… and their
stores); reference stores/returns; `String.charAt`/`String.hashCode` and every
other `String`/library method beyond the `String.length()` intrinsic; casts and
type tests; monitors; `athrow`; switches; `long`/`float`/`double` conversions and
arithmetic; `IDIV`/`IREM` (division/remainder are not added this phase); wide
primitives; and any other unlisted opcode or descriptor shape. These capability
misses continue through the existing per-method legacy fallback with the shared
state left clean.

## Deltas from the Fable design (carried from phase 2)

- Cache materialization for the generic invoke path is still emitted at each use;
  dominance-based hoisting/deduplication is not implemented.
- The phase-2 nit "dead `ExceptionCheck` after `GetIntField`/`SetIntField`" is
  left as-is to keep the phase-3 diff focused on new capability; it remains a
  known non-blocking follow-up. The array-region `ExceptionCheck`s added this
  phase are *not* dead — those JNI calls really can raise.
- `IrType.REFERENCE` is still coarsened to `jobject`; the array/string nodes
  recover the precise JNI carrier at the call site with an explicit
  `(jintArray)`/`(jarray)`/`(jstring)` cast rather than threading a refined
  reference type through the IR.
- The direct structured C++ path remains the only IR lowering; no default-mode
  change is made.
