# Flexible constructor split status

## Scope

`ConstructorSpecialMethodProcessor.split()` decides whether a constructor can be
split into a retained bytecode part (the uninitialized-this prefix plus the
verifier-required this/super `INVOKESPECIAL <init>` call) and an initialized-this
suffix that is compiled by the IR frontend and reached through a hidden static
native bridge.

The constructor split now covers three related prefix shapes:

- branches and switches that remain entirely in the retained prefix;
- `ASTORE` updates to reference/array constructor-parameter slots, with only
  those bridge parameters widened to `java/lang/Object`; and
- extra reference or primitive locals written in the prefix and read in the
  suffix, when their incoming state at the chain call is provable.

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

Prefix stores into non-parameter locals are classified separately:

- `ALOAD`, `ILOAD`, `LLOAD`, `FLOAD`, `DLOAD`, and `IINC` in the suffix identify
  extra locals whose prefix values need to cross the split.
- A must-style prefix CFG analysis requires one compatible stored type on every
  path that reaches the this/super call. A missing assignment or incompatible
  store family is rejected by `split()` before bridge or C++ mutation, with the
  local index in the diagnostic.
- The independent suffix descriptor and hidden static bridge append the extra
  parameters in increasing local-index order. The wrapper loads them from their
  original local indexes after loading the receiver and declared constructor
  arguments.
- Only prefix-stored, suffix-read extras are appended. An unassigned local
  between two original indexes is not forwarded, so it does not need a
  descriptor entry or a value at the chain call.
- The independent suffix clone remaps forwarded loads, stores, and `IINC` from
  each original index to its packed trailing parameter slot. Unrelated
  suffix-only locals are moved after those parameters to keep local identities
  distinct. The retained prefix and wrapper are not remapped.
- Reference extras use `java/lang/Object`. Primitive extras retain the exact
  verifier carrier selected by their store/load family: `I`, `J`, `F`, or `D`.
  A packed `long` or `double` consumes two local slots. Category-2 overlap with
  another suffix read remains rejected.

Other guards remain unchanged:

- Suffix jumps/switches into the prefix are rejected.
- try/catch regions crossing the split are rejected.
- Multiple this/super candidates are rejected.
- `jsr`/`ret` remains unsupported.

A prefix branch into the suffix can bypass the mandatory chain call. Multiple
this/super candidates require path-sensitive split exits rather than the current
single `callIndex`. A cross-split exception region cannot preserve a bytecode
handler edge from exceptions raised by the native suffix. Those cases therefore
remain unsafe for this split shape.

`createNativeBody` still emits the suffix only. `postProcess` keeps the prefix
plus the this/super call in the source constructor and appends the bridge
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
- `forwardsPrefixExtraReferenceAndIntLocalsIntoSuffixDescriptors` checks linear
  reference and `int` extras in the independent suffix and hidden bridge
  descriptors, including identity local mappings for contiguous extras.
- `packsAndRemapsGappedPrefixExtrasInIndependentSuffix` checks that an unused
  hole is omitted, a higher reference extra is packed into the trailing
  parameter slot, suffix `ALOAD` and `IINC` indexes are remapped, and the
  wrapper still loads the original prefix index.
- `forwardsPrimitivePrefixExtrasWithExactTypesAndWideSlots` checks `I`, `J`, `F`,
  and `D` forwarding together, including the category-2 local positions.
- `rewrittenPrefixExtraLocalConstructorPassesJvmVerification` serializes and
  loads the rewritten owner and hidden class; invocation reaches the unresolved
  native bridge only after JVM verification succeeds.
- `rewrittenGappedPrefixExtraConstructorPassesJvmVerification` applies the same
  JVM verification check to the packed gapped-extra shape.
- `prefixExtraReferenceLocalCompilesAndRunsWithJavaParity` executes a synthetic
  class first as plain Java, then through the complete IR transform, generated
  CMake C++ library, hidden bridge registration, and
  `java -Xverify:all -Xcheck:jni`; both runs print
  `PREFIX-EXTRA-FORWARDED`.
- `gappedPrefixExtraReferenceLocalCompilesAndRunsWithJavaParity` repeats that
  compile-and-run parity path with the extra stored at local 3 and local 2
  unused.
- Negatives: prefix branch targeting a suffix label, suffix jump into the
  prefix, try/catch crossing the split, multiple this/super candidates, and
  prefix `ASTORE 0`. `rejectsConditionallyAssignedPrefixExtraBeforeMutation`
  additionally proves that a one-branch-only extra assignment is rejected
  before mutation.
- Existing unsupported-opcode fallback still restores the original constructor.

The focused gate was executed with:

```text
CC=gcc CXX=g++ ./gradlew :obfuscator:test --rerun-tasks \
  --tests by.radioegor146.ir.IrCompilerTest \
  --tests by.radioegor146.CodegenModeTest
```

JUnit XML records:

- `IrCompilerTest`: 129 tests, 0 failures, 0 errors, 0 skipped.
- `CodegenModeTest`: 7 tests, 0 failures, 0 errors, 0 skipped.
- Total: 136 tests, 0 failures, 0 errors, 0 skipped.

This focused suite includes the existing constructor branch/parameter-store,
constant-dynamic, invokedynamic, and monitor harnesses.

This document records the split admission rule only. It does not assert any JDK
end-to-end support level.
