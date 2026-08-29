# Intra-prefix constructor control flow status

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

All other guards are unchanged:

- Suffix jumps/switches into the prefix are rejected.
- try/catch regions crossing the split are rejected.
- Prefix `ASTORE` of a forwarded reference local (`this` or object/array
  arguments passed to the bridge) is rejected.
- Multiple this/super candidates are rejected.

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
- Negatives: prefix branch targeting a suffix label, suffix jump into the
  prefix, try/catch crossing the split, and multiple this/super candidates.
- Existing negatives (prefix `ASTORE` of forwarded reference locals,
  invokedynamic) still restore the original constructor.

This document records the split admission rule only. It does not assert any JDK
end-to-end support level.
