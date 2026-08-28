# IR evaluator backend

Status: implemented as an opt-in lowering for the current IR integer slice.

## Selection

The command-line defaults remain:

```text
--codegen=legacy
--ir-lower=direct
```

`--ir-lower` accepts `direct` and `eval`. It is consulted only when
`--codegen=ir` successfully builds an `IrMethod`.

- `direct` uses `DirectCppStrategy` and the existing structured, straight-line
  C++ emitter.
- `eval` uses `InterpreterStreamStrategy`. It emits one method-data array and a
  JNI trampoline that passes the Java integer arguments to
  `native_jvm::ir_eval::evaluate_i32`.

The evaluator source is copied to the generated `cpp/` tree and added to the
existing CMake shared-library target only for `--codegen=ir --ir-lower=eval`.

## Method-data format

All multi-byte integers are little-endian. Registers are unsigned 16-bit
indices into a per-call `jint` register array. IR parameters occupy registers
`0..argument_count-1`; other SSA values keep their IR value IDs. Additional
registers at the end of the array stage parallel phi copies.

The eight-byte header is:

| Offset | Width | Meaning |
| --- | ---: | --- |
| 0 | 4 | `N`, `J`, `E`, format version `1` |
| 4 | 2 | register count |
| 6 | 2 | integer argument count |

Instructions follow immediately:

| Opcode | Operands | Semantics |
| --- | --- | --- |
| `0x01` | `dst:u16, immediate:i32` | Load an integer constant |
| `0x02` | `dst:u16, src:u16` | Copy a register |
| `0x10` | `dst:u16, lhs:u16, rhs:u16` | JVM-wrapping `IADD` |
| `0x11` | `dst:u16, lhs:u16, rhs:u16` | JVM-wrapping `ISUB` |
| `0x12` | `dst:u16, lhs:u16, rhs:u16` | JVM-wrapping `IMUL` |
| `0x13` | `dst:u16, lhs:u16, rhs:u16` | Bitwise `IAND` |
| `0x14` | `dst:u16, lhs:u16, rhs:u16` | Bitwise `IOR` |
| `0x15` | `dst:u16, lhs:u16, rhs:u16` | Bitwise `IXOR` |
| `0x16` | `dst:u16, lhs:u16, rhs:u16` | JVM-wrapping `ISHL`; shift distance is masked with `& 31` |
| `0x17` | `dst:u16, lhs:u16, rhs:u16` | Arithmetic `ISHR`; shift distance is masked with `& 31` |
| `0x18` | `dst:u16, lhs:u16, rhs:u16` | Logical `IUSHR`; shift distance is masked with `& 31` |
| `0x20` | `target:u32` | Jump to a byte offset in the method data |
| `0x21` | `condition:u8, lhs:u16, rhs:u16, true:u32, false:u32` | Signed integer branch |
| `0x22` | `src:u16` | Return an integer register |

Bitwise operations and left shifts run on the 32-bit unsigned carrier and copy
the result bits back to `jint`, preserving JVM wraparound without signed C++
overflow. `IUSHR` shifts that unsigned carrier. `ISHR` explicitly fills the
high bits when the sign bit is set, so its JVM arithmetic-shift behavior does
not depend on C++17's implementation-defined signed right shift.

Branch condition values are `0 EQ`, `1 NE`, `2 LT`, `3 GE`, `4 GT`, and
`5 LE`. An `rhs` value of `0xffff` means the literal integer zero, matching the
single-operand JVM branch instructions.

Control-flow targets are absolute byte offsets from the beginning of the data.
Conditional edges target small edge sequences. Those sequences stage every
incoming value in temporary registers, copy the staged values into the target
block's phi registers, and then jump to the target block. This preserves
parallel phi semantics, including loop backedges.

## Supported lowering and fallback

The evaluator lowering currently accepts:

- static methods with only JVM integer-carrier arguments and an integer return;
- integer constants;
- `IADD`, `ISUB`, `IMUL`, `IAND`, `IOR`, `IXOR`, `ISHL`, `ISHR`, and `IUSHR`;
- `GOTO`, all unary and binary integer comparisons represented by the IR, and
  `IRETURN`;
- local-variable and operand-stack merges already represented as IR phi values.

Serialization and capability validation finish before `MethodShellEmitter`
marks the method native or writes registration/output state. A capability miss
throws `UnsupportedIrConstructException` from `IrMethodCompiler`, so the
existing per-method legacy fallback remains safe.

The current evaluator path falls back for instance methods, void/reference
signatures, exception edges, JNI-dependent nodes (fields, invokes, arrays, and
throws), integer operations outside the binary operations above, unary integer
operations, and any other IR node not listed here. The direct IR strategy
retains its existing broader support.
