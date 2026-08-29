# IR LCMP admission: status

Status recorded on 2026-08-29 from `origin/master` at `e4fb63b`.

## Implemented increment

- The opt-in typed CFG IR path (`--codegen=ir`, `--ir-lower=direct`) now
  admits JVM `LCMP`, so methods that three-way compare two longs no longer
  fall back to the legacy snippet generator.
- A dedicated `IrNodes.LongCompare` node carries an i64 left operand, an i64
  right operand, and an i32 result, with the types enforced in the
  constructor like the other i64 nodes. Longs have no NaN, so the node is
  deliberately separate from the NaN-aware `IrNodes.FloatingCompare`, and it
  never throws (`CfgBuilder.mayThrow` does not treat `LCMP` as throwing —
  that was already the case and required no change).
- `AsmToIr` wires the stack effect in both the type-check pass and the
  lowering pass (pop I64, pop I64, push I32), following the
  `isFloatingCompareOp` pattern. JVM operand order is preserved: value2 is
  popped first (right), then value1 (left).
- `IrCppEmitter.emitLongCompare` lowers to a direct signed three-way compare
  on the `int64_t` carrier:
  `((int64_t) left > (int64_t) right) ? 1 : (((int64_t) left < (int64_t) right) ? -1 : 0)`.
  This is the ordered half of `emitFloatingCompare` without the
  `std::isnan` guard. It is not a subtract-based lowering, so overflow
  (for example `Long.MIN_VALUE` vs `Long.MAX_VALUE`) cannot misorder the
  result.
- `rejectsUnsupportedWideOperationBeforeMutation` was retargeted from `LCMP`
  (now admitted) to `IF_ACMPEQ`, a reference compare branch that is still
  outside the IR subset, so the reject-before-mutation guarantee remains
  proven. `IF_ACMPEQ`/`IF_ACMPNE` are not implemented in this increment.

## Out of scope

- The evaluator lowering (`--ir-lower=eval`, `InterpreterStreamStrategy`)
  does not lower `LongCompare`; methods containing `LCMP` under that mode
  fall back as before. This is expected and unchanged here.
- No interpreter, constructor-split, JEP 472, CLI-default, or reader work.
- Defaults are unchanged: `--codegen` stays `legacy`, `--ir-lower` stays
  `direct`, `--backend` stays `cpp`.

## Verification

```text
CC=gcc CXX=g++ ./gradlew :obfuscator:test --rerun-tasks \
  --tests by.radioegor146.ir.IrCompilerTest \
  --tests by.radioegor146.CodegenModeTest
```

Result: `BUILD SUCCESSFUL`; 112 tests, 0 skipped, 0 failures, and 0 errors
(`IrCompilerTest` 105 and `CodegenModeTest` 7, from the JUnit XML reports).

New focused coverage:

- Frontend node shape: `LongCompare` with I64/I64 operands and an I32 result,
  printed as `lcmp` in the IR dump.
- Emission is the signed ternary, with assertions that no `std::isnan` and
  no operand subtraction appear.
- A compiled-and-executed harness (`g++ -std=c++17`, run as a binary) checks
  negative/zero/positive results, `Long.MIN_VALUE` vs `-1`, equal longs
  (including `MIN_VALUE` vs `MIN_VALUE`), and both orderings of
  `Long.MIN_VALUE` vs `Long.MAX_VALUE`, which a subtract-based lowering
  would misorder.
- `LCMP` feeding an `IFGE` branch still builds and emits a `>= 0` condition.
- Both new methods participate in the existing generated-C++
  `g++ -std=c++17 -fsyntax-only` smoke test.
- The retargeted `IF_ACMPEQ` sentinel still rejects before any mutation.

## Ship readiness

Not ship-ready. Review required (Fable-appropriate IR review). This
increment must not flip any defaults and does not complete any production
goal.
