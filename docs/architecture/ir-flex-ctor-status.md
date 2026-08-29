# Flexible constructor split status

## Scope

`ConstructorSpecialMethodProcessor.split()` decides whether a constructor can be
split into a retained bytecode part (the uninitialized-this prefix plus the
verifier-required this/super `INVOKESPECIAL <init>` call) and an initialized-this
suffix that is compiled by the IR frontend and reached through a hidden static
native bridge.

The constructor split now covers these related prefix shapes:

- branches and switches that remain entirely in the retained prefix;
- try/catch entries whose start, end, and handler labels all remain in the
  retained prefix;
- suffix-protected try/catch entries targeting an isolated prefix
  `POP; RETURN` handler, or an isolated `POP; GOTO` handler whose sole target
  is an isolated prefix `RETURN`, when neither block has an extra incoming edge;
- `ASTORE 0` writes whose stack input is proven to be the original constructor
  receiver, with every selected this/super call proven to consume that same
  receiver;
- `ASTORE` updates to reference/array constructor-parameter slots, with only
  those bridge parameters widened to `java/lang/Object`;
- extra reference or primitive locals written in the prefix and read in the
  suffix, when their incoming state at the hidden bridge is provable;
- multiple direct this/super calls in a strict diamond that converges at one
  shared suffix label;
- exactly two direct this/super calls where the first call is followed by one
  or two declared int-family argument `ILOAD`s, a unary int-zero or binary
  `IF_ICMPxx` jump to the shared suffix, and immediate fallthrough `RETURN`,
  while the second call falls through to that suffix; and
- exactly two direct this/super calls where the first call is followed by one
  declared int-family argument `ILOAD` and `TABLESWITCH` or `LOOKUPSWITCH`,
  every target is the shared suffix, an immediate `RETURN`, or a direct `GOTO`
  to that suffix, and the second call falls through to the suffix; and
- exactly two direct this/super calls where the first call returns immediately
  and the second falls through to the suffix, only when a forwarded extra local
  is unassigned at the exiting call but has one provable type on every path
  that reaches the hidden bridge; and
- exactly two direct this/super calls whose separate straight-line suffix
  copies, including immediate `RETURN` copies, are proven
  instruction-for-instruction identical; and
- three or more direct this/super calls with the same identical straight-line
  suffix-copy proof, where every call additionally consumes the original
  receiver plus locally proven arguments (matching direct declared-argument
  loads, int-family constants, or one `INEG` over a direct declared int-family
  argument load, plus one `IADD`, `ISUB`, `IMUL`, `IAND`, `IOR`, `IXOR`,
  `ISHL`, `ISHR`, or `IUSHR` whose two operands are each one of those
  already-proven int-family inputs).

## Current rule

`split()` classifies prefix branches by target:

- A prefix jump or switch whose every target label is also in the prefix
  is **admitted**. Both edges remain in the retained bytecode, so a this/super
  call is still reachable.
- A prefix jump or switch whose target lands in the suffix is **rejected**
  (`Constructor prefix branches across the this/super call`) unless it matches
  one of the exact shared-suffix exceptions below.
- For a multi-call diamond, the one exception is the `GOTO` immediately after
  each non-final chain call. It must target the exact shared join label. The
  final chain call must fall through to that same label.
- One non-`GOTO` exception is admitted only for exactly two chain calls with no
  exception table. The first call must be followed by either one `ILOAD` of a
  declared int-family constructor argument and `IFEQ`, `IFNE`, `IFLT`, `IFGE`,
  `IFGT`, or `IFLE`, or two such direct `ILOAD`s and `IF_ICMPEQ`,
  `IF_ICMPNE`, `IF_ICMPLT`, `IF_ICMPGE`, `IF_ICMPGT`, or `IF_ICMPLE`. The jump
  must target the exact shared join and its fallthrough must be an immediate
  `RETURN`; the second call must fall through to the join. Receiver-stack
  analysis requires both calls to consume the original constructor receiver
  with no older stack values. The count-state CFG proof still requires exactly
  one call at the join and at the early return.
- One switch exception is admitted under the same two-call, empty-exception-
  table, empty-chain-entry-stack, and original-receiver restrictions. The first
  call must be followed by exactly one direct `ILOAD` of a declared int-family
  constructor argument and then `TABLESWITCH` or `LOOKUPSWITCH`. Every case and
  default must target the exact shared suffix label, an immediate prefix
  `RETURN`, or a prefix label whose first executable instruction is a direct
  `GOTO` to that suffix. The second call must fall through to the suffix. The
  count-state proof rejects zero-call suffix/return paths and any switch target
  that executes another chain call.
- One prefix-exit exception is admitted only for exactly two chain calls with
  no exception table and empty chain-entry stacks. The first call must be
  followed immediately by `RETURN`; the final call must fall through directly
  to the suffix. The suffix boundary is the instruction after that final call,
  so this rule does not depend on an otherwise unreferenced label surviving a
  class-file round trip.
- That prefix-exit form is conditional-extra-only: the normal extra-local
  analysis must prove one compatible type at every actual hidden-bridge entry,
  and at least one forwarded extra must be unassigned at the earlier,
  immediately returning chain call. The retained return never loads that local
  or invokes the bridge. No synthetic `null` or zero value is introduced.

Multiple direct this/super candidates are admitted only under a stricter
fail-closed rule:

- There must be at least two direct `<init>` calls targeting the constructor
  owner or its direct superclass.
- A count-state CFG analysis starts with zero chain calls, increments at every
  candidate, and rejects a path that reaches another candidate after one call,
  reaches the shared suffix with zero or multiple calls, or reaches a return
  without exactly one call. Every candidate must be reachable.
- Outside the immediate-prefix-return exception, the final candidate in
  bytecode order must be followed by a join label before the first suffix
  instruction. In the strict-diamond form, every earlier candidate's next
  executable instruction must be `GOTO` to that exact label. This proves that
  all suffix-taking prefix paths enter one identical range.
- The retained wrapper keeps the complete diamond, both chain calls, their
  join label, and one hidden-bridge invocation. `createNativeBody` starts at
  the shared join and emits that suffix once.

One additional family is reduced to that same shared-join form:

- With two or more candidates, each must be immediately followed by a
  straight-line suffix copy ending in `RETURN`. The copies may be empty, in
  which case each call is immediately followed by `RETURN`.
- The copies may not contain labels, branches, switches, throws, nested
  constructor calls, unsafe constants, or exception-table coverage. Every
  executable instruction and operand in every copy must match the final
  bytecode-order copy.
- With three or more candidates, each call's complete input sequence must
  additionally start with a direct `ALOAD 0`. In invocation order, each
  argument must then be either a direct load of a declared constructor
  argument with a matching JVM carrier; `ICONST_M1` through `ICONST_5`,
  `BIPUSH`, `SIPUSH`, or `LDC` of `Integer` for an int-family call argument;
  or exactly one `INEG` over a direct `ILOAD` of a declared int-family
  constructor argument. An int-family argument may also be exactly one `IADD`,
  `ISUB`, `IMUL`, `IAND`, `IOR`, `IXOR`, `ISHL`, `ISHR`, or `IUSHR` whose
  left and right operands are independently one of those direct loads,
  constants, or single-load `INEG` forms. The operand proof is leaf-only, so
  neither operand may itself be a binary expression. Shift-count masking is
  not reproduced by this proof: the admitted shift remains in the retained
  bytecode prefix and therefore keeps JVM shift semantics.
- A receiver-state CFG analysis proves that each call consumes the original
  constructor receiver with no older operand-stack values. The suffix may
  enter with only the receiver and declared constructor arguments; prefix
  `ASTORE 0` and extra-local suffix inputs are not admitted for this shape.
- After all checks pass, every noncanonical suffix copy is replaced with
  `GOTO` and the final copy receives the shared join label. The existing
  strict-diamond split then retains every call and emits the canonical suffix
  once behind one hidden bridge.

Three-or-more calls with extra-local or aliased chain inputs, nested binary
expressions (including nested bitwise and shift forms), `IDIV`, `IREM`, other
binary arithmetic, `IINC`, non-int-family constants, non-`Integer` `LDC`,
fields, method calls, stack duplication, computed or rewritten receivers, or
any other unlisted input remain rejected. Distinct joins, other conditional or
switch forms, condition/switch-key loads that are not direct declared
int-family arguments, labels or control flow in a copied suffix, nonempty
exception tables for the post-chain forms, zero-call paths, unreachable
candidates, extra-local suffix reads, and non-identical per-call suffixes also
remain rejected.
This is intentionally a narrow set of proven one-join forms, not a general
multi-exit constructor rewriter.

It also classifies writes to reference/array constructor-argument locals:

- A prefix `ASTORE` into a reference/array parameter slot is **admitted**. The
  retained prefix computes the replacement value and the wrapper loads that
  slot after the this/super call. Only the affected argument is widened to
  `java/lang/Object` in the hidden bridge and independent suffix descriptor, so
  a verifier-valid prefix may replace (for example) a declared `String`
  parameter with an `Object`.
- Unmodified argument descriptors remain exact; array descriptor information is
  not erased globally.
- A prefix `ASTORE 0` is **admitted only when a must-style stack/local CFG
  analysis proves its input is the original constructor receiver**. Copies
  through `ALOAD`/`ASTORE` locals and JVM stack shuffles preserve this identity;
  merges retain it only when every incoming path agrees. The analysis also
  requires each selected chain call to consume that receiver.
- The wrapper can then continue to load local 0 after the chain call for the
  hidden bridge. If any `ASTORE 0` input or chain-call receiver is unreachable,
  ambiguous, or not the original receiver, `split()` rejects before bridge or
  C++ mutation.
- General alias forwarding remains unsupported. In particular, replacing local
  0 with another object while a prefix-only alias carries `uninitializedThis`
  through the chain call is rejected.

Prefix stores into non-parameter locals are classified separately:

- `ALOAD`, `ILOAD`, `LLOAD`, `FLOAD`, `DLOAD`, and `IINC` in the suffix identify
  extra locals whose prefix values need to cross the split.
- A must-style prefix CFG analysis requires one compatible stored type on every
  path that reaches the hidden bridge. Normally that is also every path through
  the this/super call. The exact two-call immediate-prefix-return exception
  above may leave the local unassigned on its exiting call because that path
  cannot reach the suffix load or bridge. A missing bridge-path assignment or
  incompatible store family is rejected by `split()` before bridge or C++
  mutation, with the local index in the diagnostic.
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
- All other try/catch label placements crossing the split are rejected.
- Prefix `ASTORE 0` that is not proven identity-preserving is rejected for both
  single- and multi-call shapes.
- `jsr`/`ret` remains unsupported.

A prefix branch into the suffix can bypass the mandatory chain call. A
cross-split exception region cannot preserve a bytecode handler edge from
exceptions raised by the native suffix. Those cases therefore remain unsafe
for this split shape.

Try/catch entries are classified independently and fail closed:

- If `start`, `end`, and `handler` all have instruction indexes below the
  suffix start, the entry is admitted. `postProcess` clones the complete entry
  with the retained prefix and does not expose it to the JNI shell or copy it
  into `createNativeBody`.
- Two exact mixed forms are admitted when `start` and `end` are suffix labels
  and `handler` is a prefix label. The existing form requires the handler's
  executable sequence to be exactly `POP; RETURN` (stack-map frames may
  separate those nodes). The new form requires exactly `POP; GOTO ret`, where
  `ret` is a prefix label whose first executable instruction is `RETURN`.
  In both forms the preceding executable instruction before `handler` must be
  `GOTO`, no jump or switch may target `handler`, and `handler` may not delimit
  a protected range.
- For `POP; GOTO ret; RETURN`, the handler's `GOTO` must be the only jump or
  switch target edge to `ret`; `ret` may not delimit a protected range, serve
  as an exception handler, or have a fallthrough predecessor. No synthetic
  local or stack value is introduced. `createNativeBody` appends the isolated
  handler and return block to the suffix clone and preserves the original
  exception-table edge. `postProcess` omits both dead prefix blocks from the
  bytecode wrapper.
- If all three labels are in the suffix, the entry remains admitted and is
  cloned into `createNativeBody` for IR lowering.
- Every other mixed placement is rejected, including a prefix protected range
  with a suffix handler and any suffix protected range with a prefix handler
  that does not match one of the two isolated return forms.
  The hidden bridge is outside every admitted prefix protected range, so native
  suffix exceptions cannot enter a bytecode handler before the bridge.

`createNativeBody` still emits the suffix only. `postProcess` keeps the prefix
plus every admitted this/super call in the source constructor and appends one
bridge invocation at the split. Prefix + this/super stay in bytecode; no
uninitialized-this prefix code is IR-lowered. Label cloning in
`cloneRange`/`createNativeBody` maps every label of the method, so cloned prefix
branches resolve correctly.

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
- An identity-preserving `ALOAD 0; DUP; ASTORE 0` prefix is retained with the
  this/super call while the field-writing suffix moves behind the hidden bridge.
  A serialized rewritten class verifies and reaches the unresolved bridge, and
  the full CMake/g++ transform has stdout parity under
  `java -Xverify:all -Xcheck:jni`.
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
- `admitsConditionallyAssignedExtraOnBridgePathOnly` proves the exact two-call
  prefix-exit form retains the unassigned immediate return while forwarding
  the bridge-only reference extra.
- `rewrittenConditionalBridgeExtraPassesJvmVerification` executes the prefix
  exit successfully and verifies that the assigned path reaches the unresolved
  native bridge.
- `conditionalBridgeExtraCompilesAndRunsWithJavaParity` exercises both paths
  through plain Java and the complete CMake/g++ JNI transform under
  `-Xverify:all -Xcheck:jni`; both runs print `BRIDGE-ASSIGNED` and
  `PREFIX-EXIT`.
- `admitsMultipleSuperDiamondWithSharedSuffix` proves two retained direct
  superclass calls converge on one hidden bridge and that the independent
  native body contains only the shared suffix.
- `rewrittenMultipleSuperDiamondPassesJvmVerification` serializes and loads
  the rewritten subclass and its superclass; constructor invocation reaches
  the unresolved native bridge only after JVM verification succeeds.
- `multipleSuperDiamondCompilesAndRunsWithJavaParity` exercises positive and
  negative constructor arguments through the plain Java class and the complete
  CMake/g++ JNI transform under `-Xverify:all -Xcheck:jni`; both runs print the
  same superclass and shared-suffix field values.
- `admitsPostChainConditionalBranchToSharedSuffix` proves the exact
  `ILOAD; IFNE sharedSuffix; RETURN` post-call shape emits only the shared
  suffix as the independent native body, preserves the conditional branch and
  early return in bytecode, and adds one hidden bridge without a synthetic
  join `GOTO`.
- `rewrittenPostChainConditionalBranchPassesJvmVerification` executes the
  early-return path and verifies both suffix-taking paths up to the unresolved
  native bridge after JVM verification.
- `postChainConditionalBranchCompilesAndRunsWithJavaParity` exercises the
  taken prefix-to-suffix edge, its fallthrough early return, and the alternate
  second-call path through plain Java and the complete CMake/g++ JNI transform
  under `-Xverify:all -Xcheck:jni`, requiring identical stdout.
- `admitsPostChainIntCompareFamiliesToSharedSuffix` applies the same split proof
  to the five additional unary int-zero compares and all six binary
  `IF_ICMPxx` compares, each using only direct declared int-family argument
  loads.
- `rewrittenPostChainIntCompareFamiliesPassJvmVerification` verifies the
  suffix-taken, immediate-return, and alternate second-call paths for every
  newly admitted compare.
- `postChainIntCompareFamiliesCompileAndRunWithJavaParity` exercises those
  three paths for every newly admitted compare through plain Java and the
  complete CMake/g++ JNI transform under `-Xverify:all -Xcheck:jni`, requiring
  identical stdout.
- `admitsPostChainSwitchesToSharedSuffix` checks both switch opcodes with a
  direct shared-suffix target, a prefix `GOTO` target, and an immediate-return
  default while preserving both chain calls and one hidden bridge.
- `rewrittenPostChainSwitchesPassJvmVerification` verifies the direct and
  trampoline suffix cases, immediate-return default, and alternate second-call
  path for both switch opcodes.
- `postChainSwitchesCompileAndRunWithJavaParity` runs those cases through plain
  Java and the complete CMake/g++ JNI transform under
  `-Xverify:all -Xcheck:jni`, requiring identical stdout.
- `admitsMultipleSuperWithIdenticalLinearSuffixCopies` proves that two
  separate, identical field-writing suffix copies produce one native body and
  normalize to a retained two-call wrapper with one hidden bridge.
- `rewrittenIdenticalMultiSuperSuffixCopiesPassJvmVerification` executes both
  retained chain-call paths through JVM verification up to the unresolved
  native bridge.
- `identicalMultiSuperSuffixCopiesCompileAndRunWithJavaParity` exercises both
  paths through plain Java and the complete CMake/g++ JNI transform under
  `-Xverify:all -Xcheck:jni`, requiring identical stdout.
- `admitsMultipleSuperWithImmediateSeparateReturns` proves that an immediate
  `RETURN` after each of exactly two chain calls creates a `RETURN`-only native
  body and normalizes to one retained wrapper join and one hidden bridge.
- `rewrittenImmediateMultiSuperReturnsPassJvmVerification` executes both
  retained chain-call paths through JVM verification up to the unresolved
  native bridge.
- `immediateMultiSuperReturnsCompileAndRunWithJavaParity` executes both paths
  through plain Java and the complete CMake/g++ JNI transform under
  `-Xverify:all -Xcheck:jni`, requiring identical stdout.
- `admitsThreeSuperCallsWithImmediateSeparateReturns` proves that three calls
  using only the original receiver and direct declared-argument loads normalize
  to two retained `GOTO`s, one canonical `RETURN`, and one hidden bridge.
- `admitsThreeImmediateReturnsWithIaddOfProvenChainInputs` applies the same
  normalization to `ILOAD 1; ICONST_1; IADD`, one `INEG` over the declared
  load, and `ICONST_0`; it retains all three calls and one hidden bridge while
  the native body contains only the shared `RETURN`.
- `rewrittenThreeImmediateIaddSuperReturnsPassJvmVerification` selects each
  `IADD`, `INEG`, or constant call path and reaches the unresolved bridge only
  after the rewritten owner and hidden class pass JVM verification.
- `threeImmediateIaddSuperReturnsCompileAndRunWithJavaParity` selects all three
  paths through plain Java and the complete CMake/g++ JNI transform under
  `-Xverify:all -Xcheck:jni`, requiring identical stdout.
- `admitsThreeImmediateReturnsWithIsubAndImulOfProvenChainInputs` applies the
  same leaf-only proof to one `ISUB` path, one `IMUL` path, and one direct-load
  path; it retains both arithmetic opcodes, all three calls, two shared-join
  `GOTO`s, and one hidden bridge.
- `rewrittenThreeImmediateIsubImulSuperReturnsPassJvmVerification` serializes
  and loads the rewritten owner and hidden class, then selects all three paths
  up to the unresolved bridge after JVM verification.
- `threeImmediateIsubImulSuperReturnsCompileAndRunWithJavaParity` selects the
  subtraction, multiplication, and direct-load paths through plain Java and the
  complete CMake/g++ JNI transform under `-Xverify:all -Xcheck:jni`, requiring
  identical stdout.
- `admitsThreeImmediateReturnsWithBitwiseProvenChainInputs` applies the same
  leaf-only proof to `IAND`, `IOR`, and `IXOR` paths; it retains all three
  bitwise opcodes and chain calls, two shared-join `GOTO`s, and one hidden
  bridge.
- `rewrittenThreeImmediateBitwiseSuperReturnsPassJvmVerification` serializes
  and loads the rewritten owner and hidden class, then selects all three
  bitwise paths up to the unresolved bridge after JVM verification.
- `threeImmediateBitwiseSuperReturnsCompileAndRunWithJavaParity` selects the
  `IAND`, `IOR`, and `IXOR` paths through plain Java and the complete CMake/g++
  JNI transform under `-Xverify:all -Xcheck:jni`, requiring identical stdout.
- `admitsThreeImmediateReturnsWithShiftProvenChainInputs` applies the same
  leaf-only proof to `ISHL`, `ISHR`, and `IUSHR` paths; it retains all three
  shift opcodes and chain calls, two shared-join `GOTO`s, and one hidden
  bridge.
- `rewrittenThreeImmediateShiftSuperReturnsPassJvmVerification` serializes and
  loads the rewritten owner and hidden class, then selects all three shift
  paths up to the unresolved bridge after JVM verification.
- `threeImmediateShiftSuperReturnsCompileAndRunWithJavaParity` selects the
  `ISHL`, `ISHR`, and `IUSHR` paths through plain Java and the complete
  CMake/g++ JNI transform under `-Xverify:all -Xcheck:jni`, requiring identical
  stdout while the retained shift bytecode keeps JVM count masking.
- `admitsThreeSuperCallsWithIdenticalNonemptyLinearSuffixCopies` proves that
  three instruction-identical `ICONST_4; POP; RETURN` copies normalize to two
  retained `GOTO`s, one canonical suffix, and one hidden bridge while retaining
  the same `IADD`/`INEG`/constant chain-input proof.
- `rewrittenThreeIdenticalNonemptySuffixCopiesPassJvmVerification` serializes
  the rewritten owner, superclass, and hidden class, then selects all three
  paths up to the unresolved native bridge after JVM verification.
- `threeIdenticalNonemptySuffixCopiesCompileAndRunWithJavaParity` selects all
  three paths through plain Java and the complete CMake/g++ JNI transform under
  `-Xverify:all -Xcheck:jni`, requiring identical stdout.
- `admitsPrefixOnlyTryCatchAndRetainsItInRewrittenConstructor` proves that the
  independent suffix has no prefix handler while the rewritten constructor
  retains the complete prefix exception-table entry and forwards its assigned
  local to the suffix bridge.
- `admitsSuffixOnlyTryCatchInNativeBody` proves that an all-suffix entry is
  still cloned and lowered.
- `rewrittenPrefixOnlyTryCatchConstructorPassesJvmVerification` loads the
  rewritten owner, superclass, and hidden bridge class, then executes the catch
  path through JVM verification.
- `prefixOnlyTryCatchConstructorCompilesAndRunsWithJavaParity` compares normal
  and caught inputs through plain Java and the complete CMake/g++ JNI transform
  under `-Xverify:all -Xcheck:jni`.
- `admitsSuffixTryCatchWithIsolatedPrefixReturnHandler` checks that the
  previously admitted mixed handler is present in the independent suffix CFG
  and absent from the bytecode wrapper.
- `rewrittenRelocatedPrefixReturnHandlerPassesJvmVerification` loads the
  rewritten owner and reaches the unresolved bridge after JVM verification.
- `relocatedPrefixReturnHandlerCompilesAndRunsWithJavaParity` compares normal
  division and caught divide-by-zero paths through plain Java and the complete
  CMake/g++ JNI transform under `-Xverify:all -Xcheck:jni`.
- `admitsSuffixTryCatchWithIsolatedPrefixGotoReturnHandler` checks that
  `POP; GOTO ret; RETURN` and its exception edge are present in the independent
  suffix CFG while both isolated prefix blocks are absent from the wrapper.
- `rejectsUnsafePrefixGotoReturnHandlersBeforeMutation` covers extra handler
  work, a non-`RETURN` target, and an extra incoming edge to `ret`.
- `rewrittenRelocatedPrefixGotoReturnHandlerPassesJvmVerification` serializes
  and loads the rewritten owner plus hidden class and reaches the unresolved
  bridge on the caught-path input after JVM verification.
- `relocatedPrefixGotoReturnHandlerCompilesAndRunsWithJavaParity` compares
  normal division and caught divide-by-zero paths through plain Java and the
  complete CMake/g++ JNI transform under
  `java -Xverify:all -Xcheck:jni`.
- `rejectsEveryMixedTryCatchLabelPlacement` checks all six mixed prefix/suffix
  assignments at non-boundary labels and verifies rejection before mutation.
- Three-call negatives reject direct extra-local inputs, every admitted
  arithmetic, bitwise, or shift binary opcode using an extra local, nested
  forms of each admitted binary opcode, trapping `IDIV`, a rewritten `ASTORE 0`
  receiver, non-identical post-call work, a branched suffix, a zero-call
  return, skip-super paths, and nonempty exception tables before mutation.
  Other multi-call negatives still cover try/catch spanning a chain call and
  suffix code, a path that executes two chain calls, and distinct non-empty
  per-call suffixes. Existing prefix-to-suffix, suffix-to-prefix, and
  conditionally assigned extra-local negatives remain.
- Existing unsupported-opcode fallback still restores the original constructor.

The focused gate was executed with:

```text
CC=gcc CXX=g++ ./gradlew :obfuscator:test --rerun-tasks \
  --tests by.radioegor146.ir.IrCompilerTest \
  --tests by.radioegor146.CodegenModeTest
```

JUnit XML records for this increment:

- `IrCompilerTest`: 193 tests, 0 failures, 0 errors, 0 skipped.
- `CodegenModeTest`: 7 tests, 0 failures, 0 errors, 0 skipped.
- Total: 200 tests, 0 failures, 0 errors, 0 skipped.

This focused suite includes the existing constructor branch/parameter-store,
constant-dynamic, invokedynamic, and monitor harnesses.

This document records the split admission rule only. It does not assert any JDK
end-to-end support level.
