# IR phase 9 status

Phase 9 extends the optional Java bytecode → typed CFG IR → C++/JNI lowering
path with reference method returns, typed null values and branches, and
category-one discard. The CLI/API default remains `legacy`; unsupported methods
still fall back independently, and the existing legacy snippets remain present.

Preferred PR merge base:
`cursor/ir-compiler-phase8-6d81` at
`95eb5ffd2fc5a9515af65c1d15403e7c983c64a5`.

## What landed

### Reference returns and null values

`ARETURN` now terminates methods whose object or array descriptor maps to the
existing `IrType.REFERENCE` / `jobject` carrier. `ACONST_NULL` produces a
dedicated typed `NullReference` value and emits `nullptr`.

An ordinary `NEW`, `DUP`, `INVOKESPECIAL <init>`, `ARETURN` sequence can
therefore allocate, initialize, and return an object. Allocation and constructor
failures continue to use the block's exceptional exit. When no handler protects
the operation, the generated JNI function returns `nullptr` while the exception
remains pending.

Constructor method bodies are still excluded by
`MethodProcessor.shouldProcess`; this support only covers constructor calls
inside otherwise-supported methods.

### Reference null branches

`IFNULL` and `IFNONNULL` lower to a dedicated structured
`IrNodes.ReferenceBranch`. Its condition is explicitly `IS_NULL` or
`IS_NON_NULL`, its operand must be `IrType.REFERENCE`, and C++ compares the
`jobject` carrier with `nullptr`. These opcodes are not represented as integer
comparisons.

### Category-one `POP`

`POP` removes exactly one category-one operand from the typed JVM stack.
`I32` and reference invoke results and other one-slot values are accepted.
Applying `POP` to `I64` is rejected. `POP2`, `DUP2`, and the other category-two
stack manipulation forms remain outside this phase and fall back per method.

## Fallback before mutation

Opcode and descriptor admission, stack/local typing, lowering, and phi
connection still complete before the emitter allocates cache/string IDs and
before the JNI shell changes method flags, generated output, or native-method
metadata.

`rejectsUnsupportedAfterPhaseNineOpsBeforeMutation` places admitted
`ACONST_NULL`, `POP`, and `IFNONNULL` before unsupported `POP2`. It verifies
that rejection leaves `ACC_NATIVE`, output, native metadata, and all
class/string/field/method caches unchanged.

## Tests and recorded results

Pre-verification implementation checkpoint: the required focused Gradle command
and independent retained-translation-unit g++ command have not yet been run on
this branch. This section will be replaced with actual command output and JUnit
XML counts after that verification.

## What still falls back per method

Phase 9 remains a staged subset. Per-method fallback still covers:

- malformed exception regions and unsupported handler/control-flow shapes;
- `POP2`, `DUP2`, and other category-two stack manipulation forms; `POP` also
  rejects category-two operands;
- `MULTIANEWARRAY` and every primitive `NEWARRAY` kind except `int`;
- non-`int` primitive arrays and non-`int` array element loads/stores;
- reference and wide fields, non-`I` field descriptors, and unsupported field
  shapes;
- remaining long operations outside the documented subset;
- all `float` and `double` carriers, arithmetic, conversions, and descriptors;
- non-constructor `INVOKESPECIAL`, `INVOKEINTERFACE`, and invokedynamic;
- constructor method bodies; and
- every other opcode or descriptor shape not listed in the phase 1–9 status
  documents.

The default was not changed from `legacy`, and existing snippet resources were
not removed.
