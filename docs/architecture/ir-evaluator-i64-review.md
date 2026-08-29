# IR evaluator i64 review

Review target: PR #68 (`cursor/ir-eval-i64-6d81`), based on
`cursor/ir-evaluator-ushr-6d81`.

## Verdict

**Accept.** Static review and the focused re-run found no correctness defect in
the i64 evaluator slice. No implementation code was changed by this review.

## Findings

### Java/C++ ISA agreement

The serializer and native evaluator use the same contiguous assignments:

| JVM operation | Evaluator opcode |
| --- | ---: |
| `LLOAD` | `0x23` |
| `LSTORE` | `0x24` |
| `LADD` | `0x25` |
| `LSUB` | `0x26` |
| `LMUL` | `0x27` |
| `LRETURN` | `0x28` |
| `I2L` | `0x29` |
| `L2I` | `0x2a` |

`InterpreterStreamStrategyTest.serializesEveryI64OpcodeNumber` checks emitted
bytes for every assignment, and
`cppEvaluatorUsesTheSameI64OpcodeNumbers` checks the native constants.

### Wide locals and i64 semantics

- `IrType.I64` occupies two JVM slots. Parameter-local indexing advances by the
  ASM type size, wide local indices are bounds-checked, the second slot is
  tracked as a continuation, and stack phi slot numbers advance by two for an
  i64 value.
- JVM local loads/stores become SSA values in `AsmToIr`. In the evaluator ISA,
  `LLOAD` materializes a `jlong` argument and `LSTORE` copies the i64 return
  value into a staging register before `LRETURN`.
- The native register file stores `std::uint64_t` bit carriers.
  `LADD`/`LSUB`/`LMUL` therefore have defined modulo-2^64 behavior. Conversion
  between those bits and `jlong` uses `memcpy`, avoiding signed-overflow and
  out-of-range conversion assumptions.
- `I2L` first reconstructs a signed `jint`, then converts it to `jlong`, which
  sign-extends negative values. `L2I` keeps the low 32 bits; `evaluate_i32`
  reconstructs the corresponding `jint`.

### Selection and fallback

- Generated `roundTrip(J)J` output is checked within that method's source
  region for evaluator data and `evaluate_i64`. The same check rejects the
  direct-IR marker and legacy stack/body markers.
- `LDIV` and `LREM` are absent from the frontend long-operation allowlist, so
  they throw `UnsupportedIrConstructException` before the shell marks the
  method native or mutates output/registration/cache state.
- Evaluator validation and serialization run before `MethodShellEmitter`.
  Existing tests verify that an evaluator capability miss leaves the method and
  all output/cache state unchanged.
- CLI defaults remain `--codegen=legacy` and `--ir-lower=direct`.

### Direct-IR sibling files

Compiler code is changed by the reviewed stack: **yes**. This is not an
isolation defect. The evaluator consumes the shared typed IR, so `IrType`,
`IrNodes`, `IrMethod`, and `AsmToIr` must represent i64 values, conversions, and
two-slot locals. `AsmToIr` runs before lowering-strategy selection; consequently
the direct emitter and its C++ AST must also understand the newly admitted
nodes so the still-default `--ir-lower=direct` path does not fail after a
successful shared-frontend build. The direct compiler test added by the stack
guards that compatibility.

The diff contains the i64 shared-IR/direct-emitter support corresponding to this
slice, not unrelated later direct-IR phase work. No sibling files were reverted.

## Bugs fixed

None. Static review found no correctness bug.

## Focused re-run

Command (with `CC=gcc CXX=g++`):

```text
./gradlew :obfuscator:test \
  --tests by.radioegor146.CodegenModeTest \
  --tests by.radioegor146.ir.IrCompilerTest \
  --tests by.radioegor146.ir.backend.InterpreterStreamStrategyTest \
  --rerun-tasks --console=plain
```

Result: **31/31 passed**, with 0 skipped, 0 failures, and 0 errors:

- `CodegenModeTest`: 4/4
- `IrCompilerTest`: 18/18
- `InterpreterStreamStrategyTest`: 9/9

The evaluator translation-unit g++ check and the linked native evaluator
harness both ran rather than being skipped. The harness covered i64 add,
subtract, and multiply wraparound, negative `I2L`, low-32-bit `L2I`, and
`(J)J` value transport.
