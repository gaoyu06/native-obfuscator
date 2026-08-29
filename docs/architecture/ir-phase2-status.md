# IR phase 2 status

This branch extends the opt-in typed-CFG compiler from
`cursor/ir-phase1-fable-review-6d81`. It remains selected only by
`--codegen=ir`; legacy codegen is still the CLI/API default, and an unsupported
method still falls back independently to the legacy compiler.

## Capability

Phase 1's integer constants, `ILOAD`/`ISTORE`/`IINC`,
`IADD`/`ISUB`/`IMUL`, integer branches, `GOTO`, and integer/void returns remain
supported. Phase 2 adds:

- Reference parameters and reference-typed CFG stack/local phis.
- `ALOAD` for reference parameters and `DUP` for category-1 integer/reference
  values.
- `GETFIELD` and `PUTFIELD` for instance fields with descriptor `I`.
- `INVOKESTATIC` and `INVOKEVIRTUAL` whose return descriptor is exactly `I` and
  whose arguments are `I`, object, or array references.
- Structured emission for class, field, and method cache lookup; receiver null
  checks; `GetIntField`/`SetIntField`; `CallStaticIntMethod`/`CallIntMethod`;
  and pending-JNI-exception returns.
- Source-line propagation to field/invoke null-check diagnostics.

The frontend computes typed operand-stack states at each normal CFG edge. A
merge with different stack carrier types is rejected rather than emitted as an
ill-typed phi.

## Safe fallback and the phase-1 review nit

Whole-method opcode, descriptor, local-carrier, stack-carrier, and definite-local
validation completes before `MethodShellEmitter.beginIr` mutates the method,
registration output, or hidden-method state. JNI cache allocation happens only
after that validation succeeds.

In particular, local 0 of an instance method is fixed as `REFERENCE`. An
`ISTORE 0` now throws `UnsupportedIrConstructException` in the frontend. The
regression test verifies that the method is not marked native, method output and
registration output remain empty, and class/field/method caches remain
unmodified. The caller can therefore run the existing per-method legacy
fallback on clean state.

## Compile-smoke evidence

`IrCompilerTest.generatedCppPassesGppSyntaxCheckWhenToolchainAvailable` emits
complete JNI functions through the shared shell for:

- `add(II)I`
- `sumTo(I)I`
- `subMul(II)I`
- an instance `value:I` increment using `GETFIELD` and `PUTFIELD`
- a simple `INVOKESTATIC`
- `String.length()` via `INVOKEVIRTUAL`

It assembles those functions into one translation unit with the actual cache
carrier declarations. If `g++`, `jni.h`, and the platform JNI include directory
are present, it runs the equivalent of:

```text
g++ -std=c++17 -fsyntax-only \
  -I$JAVA_HOME/include -I$JAVA_HOME/include/linux <generated-ir-smoke.cpp>
```

Real environment used on 2026-08-28:

```text
g++ (Ubuntu 13.3.0-6ubuntu2~24.04.1) 13.3.0
openjdk version "21.0.10" 2026-01-20
jni_headers=present
```

The first run of the new test was a real failure: the structured emitter
rendered a direct member-call condition as `if env->ExceptionCheck()`. The test
caught the C++ syntax error, the condition emission was fixed, and the final
compile-smoke invocation exited 0.

Final verification command:

```text
./gradlew :obfuscator:test \
  --tests by.radioegor146.CodegenModeTest \
  --tests by.radioegor146.ir.IrCompilerTest \
  :obfuscator:shadowJar
```

Result: `BUILD SUCCESSFUL`. The XML results record 8/8 `IrCompilerTest` tests
and 2/2 `CodegenModeTest` tests passing, with zero skipped tests, failures, or
errors. The compile-smoke test itself passed rather than being skipped.

## Deltas from the Fable design

- The Fable documents describe the eventual object model. This phase implements
  a deliberately useful narrow slice: int instance fields and int-returning
  static/virtual calls with simple arguments. It does not add placeholder nodes
  for unsupported operations.
- Cache materialization remains parity-oriented and is emitted at each field or
  invoke use. Fable's dominance-based hoisting/deduplication is not implemented.
- The Fable field example removes receiver null checks using nullability.
  This phase has carrier types but no reference refinement/nullability lattice,
  so it conservatively keeps the checks.
- Exception tables still fall back. JNI operations emit the no-handler behavior
  directly (`ExceptionCheck` then return); first-class exceptional CFG edges and
  catch dispatch are not implemented.
- Reference values are supported where needed as parameters, receivers, invoke
  arguments, and typed phis. Reference-returning operations and owned-local-ref
  lifetime analysis remain out of scope.
- The direct structured C++ path is the only IR lowering. No default-mode change
  is made.

## Remaining per-method fallbacks

The frontend still rejects exception tables; static fields; non-`I` fields;
reference-returning or void invokes; invokes with non-`I` primitive arguments;
`INVOKESPECIAL`, `INVOKEINTERFACE`, and dynamic invokes; reference stores and
returns; object/array creation and access; casts/type tests; monitors; throws;
switches; conversions; division/remainder; wide primitives; and all other
unlisted opcodes or descriptor shapes. These capability misses continue through
the existing per-method legacy fallback.
