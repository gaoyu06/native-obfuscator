# IR shared-evaluator integer ISA review

## Verdict

**Accept.**

The integer ISA extension is internally consistent and preserves the evaluator's
existing opt-in and per-method fallback boundaries. I found no correctness bug
in the reviewed compiler or evaluator code. This technical verdict does not
change the evaluator's opt-in status or make it ship-ready without parent
review.

Reviewed range:
`52f5efb009a685842d143c6d2d3d6686ec498113..21f474d3983cbcb015bb787615112135740a048b`.

## What I re-ran

From the repository root:

```sh
CC=gcc CXX=g++ ./gradlew :obfuscator:test \
  --tests by.radioegor146.CodegenModeTest \
  --tests by.radioegor146.ir.IrCompilerTest \
  --tests by.radioegor146.ir.backend.InterpreterStreamStrategyTest \
  --rerun-tasks
```

Result: **28/28 tests passed**, with **0 skipped, 0 failures, and 0 errors**:

- `CodegenModeTest`: 4/4
- `IrCompilerTest`: 17/17
- `InterpreterStreamStrategyTest`: 7/7

The Gradle build completed successfully. Because the focused evaluator suite
reported zero skipped tests, both g++-gated checks ran: the evaluator
translation-unit syntax check and the linked native runtime harness.

## Opcode agreement findings

The Java serializer and C++ evaluator agree exactly:

| Operation | Java serializer | C++ evaluator |
| --- | ---: | ---: |
| `IAND` | `0x13` | `0x13` |
| `IOR` | `0x14` | `0x14` |
| `IXOR` | `0x15` | `0x15` |
| `ISHL` | `0x16` | `0x16` |
| `ISHR` | `0x17` | `0x17` |
| `IUSHR` | `0x18` | `0x18` |

All six instructions use the same serialized operand layout:
`dst:u16, lhs:u16, rhs:u16`. The serializer test checks each numeric opcode,
and the linked harness executes blobs produced by that serializer through the
C++ dispatch cases.

## Shift and `IUSHR` semantics findings

- The evaluator derives the distance as `right_value & 31U` before every shift,
  matching the JVM integer shift-distance rule.
- `ISHL` shifts a `std::uint32_t`, so discarded high bits and overflow retain
  the required 32-bit bit pattern without signed C++ overflow.
- `IUSHR` shifts the same unsigned carrier, producing a logical right shift.
- `ISHR` uses `arithmetic_shift_right`. It preserves the input when the masked
  distance is zero, shifts unsigned for other distances, and explicitly fills
  high bits when bit 31 is set. It therefore sign-extends without relying on
  implementation-defined signed right shift.
- Inputs and results cross the `jint` boundary through `memcpy` helpers guarded
  by a 32-bit `jint` assertion. Addition, subtraction, multiplication, bitwise
  operations, and shifts are computed as `std::uint32_t`, preserving 32-bit
  wrap/bit semantics.

The runtime harness covers all six new operations, a distance of 33 (therefore
masked to 1), and negative inputs for `ISHR` and `IUSHR`.

## Fallback-before-mutation findings

The invariant remains intact. `IrMethodCompiler.processMethod` builds the IR,
selects the strategy, and completes evaluator validation and serialization
before calling `MethodShellEmitter.beginEvaluator`, appending to method output,
or finishing the shell. The evaluator's `lower` method calls `validate` before
creating its serialized method data.

`rejectsUnsupportedEvaluatorNodeBeforeMethodMutation` exercised an unsupported
unary operation and passed while asserting all of the following remained
unchanged: the method's native access bit, generated output, native-method
registration output, and the string, class, method, and field caches.

At the enclosing compilation level, `NativeObfuscator` still catches
`UnsupportedIrConstructException` for an individual IR-selected method and
invokes the legacy `MethodProcessor` for that method. Unsupported evaluator IR
therefore retains per-method legacy fallback.

## Defaults unchanged?

**Yes.**

- Picocli still defaults `--codegen` to `legacy`.
- Picocli still defaults `--ir-lower` to `direct`.
- The `NativeObfuscator.process` compatibility overloads still delegate with
  `CodegenMode.LEGACY` and/or `IrLoweringMode.DIRECT`.
- Evaluator runtime sources are included only when both `--codegen=ir` and
  `--ir-lower=eval` are selected.
- All four CLI selection/default tests passed.

## Bugs fixed

None. No compiler code was changed on this review branch.
