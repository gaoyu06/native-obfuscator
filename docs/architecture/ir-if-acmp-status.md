# IR IF_ACMPEQ / IF_ACMPNE admission: status

Status recorded on 2026-08-29 from `origin/master` at `972bdfb`.

## Implemented increment

- The opt-in typed CFG IR path (`--codegen=ir`, `--ir-lower=direct`) now
  admits JVM `IF_ACMPEQ` and `IF_ACMPNE`, so methods that branch on reference
  identity no longer fall back to the legacy snippet generator.
- A dedicated `IrNodes.ReferenceCompareBranch` terminator carries two
  `REFERENCE` operands (`left`, `right`), an `EQ`/`NE` condition, and the
  true/false target blocks. It is kept separate from the null-only
  `IrNodes.ReferenceBranch` (which compares one reference against null) and
  from the i32 `IrNodes.Branch` (whose operands are `I32`, not references).
  The operand types are enforced in the constructor. Identity comparison does
  not throw, so no exceptional edge is added.
- `AsmToIr` wires the stack effect in both passes:
  - the supported-instruction check admits `IF_ACMPEQ` / `IF_ACMPNE` via a new
    `isReferenceCompareJump` predicate;
  - the type-check pass pops two `REFERENCE` operands;
  - `lowerJump` pops value2 first (right) then value1 (left), matching the JVM
    operand order, and builds the `ReferenceCompareBranch` with the branch-taken
    label as the true target.
- `IrCppEmitter` lowers `ReferenceCompareBranch` to a pointer-identity compare
  on the two carriers (`left == right` for `EQ`, `left != right` for `NE`),
  using the same `If` + edge-transfer pattern as `ReferenceBranch`. Because
  both null references carry as `nullptr`, `nullptr == nullptr` correctly takes
  the `EQ` target, matching JVM semantics.
- `IrMethod.toString` prints the terminator as
  `branch if_acmpeq <left>, <right> -> <trueBlock>, <falseBlock>` (and
  `if_acmpne` for `NE`).

## Retargeted reject-before-mutation sentinel

`rejectsUnsupportedWideOperationBeforeMutation` previously used `IF_ACMPEQ`,
which is now admitted. It was retargeted to `MONITORENTER`, which remains
outside the IR subset, so the reject-before-mutation guarantee stays proven.
The opcode assertion now expects `Opcodes.MONITORENTER`. Monitors are **not**
implemented in this increment.

## Out of scope

- The evaluator lowering (`--ir-lower=eval`, `InterpreterStreamStrategy`) does
  not lower `ReferenceCompareBranch`; it already rejects `ReferenceBranch`,
  `Switch`, and `Throw` terminators the same way, and methods containing
  `IF_ACMPEQ`/`IF_ACMPNE` under that mode fall back as before. This is expected
  and unchanged here.
- No interpreter, constructor-split, JEP 472, CLI-default, or reader work.
- No monitor (`MONITORENTER`/`MONITOREXIT`) or `invokedynamic` implementation;
  those remain rejected.
- Existing `IFNULL`/`IFNONNULL` lowering (`ReferenceBranch`) is unchanged.
- Defaults are unchanged: `--codegen` stays `legacy`, `--ir-lower` stays
  `direct`, `--backend` stays `cpp`.

## Verification

```text
CC=gcc CXX=g++ ./gradlew :obfuscator:test --rerun-tasks \
  --tests by.radioegor146.ir.IrCompilerTest \
  --tests by.radioegor146.CodegenModeTest
```

Result: `BUILD SUCCESSFUL`; 114 tests, 0 skipped, 0 failures, and 0 errors
(`IrCompilerTest` 107 and `CodegenModeTest` 7, from the JUnit XML reports).

New focused coverage:

- Frontend node shape: `ReferenceCompareBranch` with two `REFERENCE` operands,
  for both `EQ` and `NE`, printed as `branch if_acmpeq` / `branch if_acmpne`
  in the IR dump.
- Emission uses identity `==` / `!=` on the two references and does **not**
  emit a null-only `nullptr` comparison.
- A compiled-and-executed harness (`g++ -std=c++17`, run as a binary) checks
  that equal references (including both null) take the `EQ` target and unequal
  references take the `NE` target, for both `IF_ACMPEQ` and `IF_ACMPNE`.
- Both new methods participate in the existing generated-C++
  `g++ -std=c++17 -fsyntax-only` smoke test.
- Existing `IFNULL`/`IFNONNULL` tests still pass unchanged.
- The retargeted `MONITORENTER` sentinel still rejects before any mutation.

## Ship readiness

Not ship-ready. Review required (Fable-appropriate IR review). This increment
must not flip any defaults and does not complete any production goal.
