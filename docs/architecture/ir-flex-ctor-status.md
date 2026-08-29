# Flexible constructor split status

## Scope

`ConstructorSpecialMethodProcessor.split()` decides whether a constructor can be
split into a retained bytecode part (the uninitialized-this prefix plus the
verifier-required this/super `INVOKESPECIAL <init>` call) and an initialized-this
suffix that is compiled by the IR frontend and reached through a hidden static
native bridge.

Before this change `split()` rejected **any** `JumpInsnNode`,
`TableSwitchInsnNode`, or `LookupSwitchInsnNode` located at or before the
this/super call. That blocked constructors whose prologue contains a branch that
stays entirely inside the prefix, such as the JEP 513-style constructor in
`obfuscator/test_data/tests/jdk25/FlexibleConstructorBodiesE2E/Main.java`:

```java
Validated(int value) {
    int normalized = Math.abs(value);
    if (normalized == 0) {
        throw new IllegalArgumentException("zero");
    }
    super(normalized);
}
```

The `if` compiles to an `IFNE` (opcode 154) whose target label sits before the
`super(...)` call, so the whole branch is prefix-local.

## Current rule

`split()` now classifies prefix branches by target:

- A prefix jump or switch whose every target label is also in the prefix
  (instruction index `<= callIndex`) is **admitted**. Both edges remain in the
  retained bytecode, so the this/super call is still reachable.
- A prefix jump or switch whose target lands in the suffix is **rejected**
  (`Constructor prefix branches across the this/super call`); it would skip the
  mandatory chain call.

It also classifies writes to reference/array constructor-argument locals:

- A prefix `ASTORE` into a reference/array parameter slot is **admitted**. The
  retained prefix computes the replacement value and the wrapper loads that
  slot after the this/super call. Only the affected argument is widened to
  `java/lang/Object` in the hidden bridge and independent suffix descriptor, so
  a verifier-valid prefix may replace (for example) a declared `String`
  parameter with an `Object`.
- Unmodified argument descriptors remain exact; array descriptor information is
  not erased globally.
- A prefix `ASTORE 0` is still **rejected**. Local 0 can stop naming the
  initialized receiver while another prefix-only alias carries
  `uninitializedThis` through the chain call. The bridge has no general way to
  forward that alias or reconstruct receiver identity for the suffix.

Other guards remain unchanged:

- Suffix jumps/switches into the prefix are rejected.
- try/catch regions crossing the split are rejected.
- Multiple this/super candidates are rejected.

A prefix branch into the suffix can bypass the mandatory chain call. Multiple
this/super candidates require path-sensitive split exits rather than the current
single `callIndex`. A cross-split exception region cannot preserve a bytecode
handler edge from exceptions raised by the native suffix. Those cases therefore
remain unsafe for this split shape.

`createNativeBody` still emits the suffix only. `postProcess` still keeps the
prefix plus the this/super call in the source constructor and appends the bridge
invocation. Prefix + this/super stay in bytecode; no uninitialized-this prefix
code is IR-lowered. Label cloning in `cloneRange`/`createNativeBody` maps every
label of the method, so cloned prefix branches resolve correctly.

## Verification

Synthetic bytecode unit tests in
`obfuscator/src/test/java/by/radioegor146/ir/IrCompilerTest.java`:

- A positive `Validated`-equivalent constructor (`Math.abs`, `IFNE`, throw, then
  `super`): `createNativeBody` succeeds and `IrMethodCompiler.processMethod`
  produces the native bridge while keeping the prefix branch and this/super call
  in the constructor.
- A prefix `Object`-to-`String` parameter-slot `ASTORE` is admitted with an
  `Object` bridge/suffix entry descriptor. Serializing and loading the rewritten
  class reaches the unresolved native bridge, proving JVM verification.
- A compile-and-run test executes the same synthetic class first as plain Java,
  then through the complete IR transform, generated CMake C++ library, hidden
  native bridge registration, and `java -Xverify:all -Xcheck:jni`; both runs
  print `forwarded-result`.
- Negatives: prefix branch targeting a suffix label, suffix jump into the
  prefix, try/catch crossing the split, multiple this/super candidates, and
  prefix `ASTORE 0`.
- Existing unsupported-opcode fallback still restores the original constructor.

This document records the split admission rule only. It does not assert any JDK
end-to-end support level.
