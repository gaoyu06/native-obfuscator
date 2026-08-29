# IR evaluator backend

Status: ported to the current compiler line as an opt-in, default-off lowering
for a narrow IR i32/i64 slice. This is compiler/codegen infrastructure, not a
packer, protector, obfuscation product, or anti-analysis feature. It is **not
ship-ready**. The benchmark issue #53 evaluator median remains `N/A`.

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
  JNI trampoline that passes Java integer/long arguments through `jlong`
  carriers, plus the current `JNIEnv*`, to
  `native_jvm::ir_eval::evaluate_i32` or `evaluate_i64`.

The evaluator source is copied to the generated `cpp/` tree and added to the
existing CMake shared-library target only for `--codegen=ir --ir-lower=eval`.

## Method-data format

All multi-byte integers are little-endian. Registers are unsigned 16-bit
indices into a per-call 64-bit bit-carrier array. I32 operations consume the
low 32 bits; i64 operations consume all 64 bits. IR parameters occupy registers
`0..argument_count-1`; other SSA values keep their IR value IDs. Additional
registers at the end of the array stage parallel phi copies and an i64 return.

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
| `0x23` | `dst:u16, argument:u16` | `LLOAD`: load a `jlong` argument into an i64 register |
| `0x24` | `dst:u16, src:u16` | `LSTORE`: copy an i64 register into an i64 staging register |
| `0x25` | `dst:u16, lhs:u16, rhs:u16` | JVM-wrapping `LADD` |
| `0x26` | `dst:u16, lhs:u16, rhs:u16` | JVM-wrapping `LSUB` |
| `0x27` | `dst:u16, lhs:u16, rhs:u16` | JVM-wrapping `LMUL` |
| `0x28` | `src:u16` | `LRETURN`: return an i64 register as `jlong` |
| `0x29` | `dst:u16, src:u16` | `I2L`: sign-extend an i32 register to i64 |
| `0x2a` | `dst:u16, src:u16` | `L2I`: keep the low 32 bits of an i64 register |
| `0x2b` | — | Reserved for future `LDIV`; not implemented |
| `0x2c` | — | Reserved for future `LREM`; not implemented |
| `0x2d` | `dst:u16, lhs:u16, rhs:u16` | Bitwise `LAND` |
| `0x2e` | `dst:u16, lhs:u16, rhs:u16` | Bitwise `LOR` |
| `0x2f` | `dst:u16, lhs:u16, rhs:u16` | Bitwise `LXOR` |
| `0x30` | `dst:u16, value:u16, count:u16` | `LSHL`; shift distance is masked with `& 63` |
| `0x31` | `dst:u16, value:u16, count:u16` | `LSHR`; shift distance is masked with `& 63` |
| `0x32` | `dst:u16, value:u16, count:u16` | `LUSHR`; shift distance is masked with `& 63` |

`0x2b` and `0x2c` remain reserved so this port does not claim the direct IR
agent's future `LDIV`/`LREM` work. `0x20`–`0x22` retain their existing
control-flow and i32-return assignments.

Bitwise operations and left shifts run on the 32-bit unsigned carrier and copy
the result bits back to `jint`, preserving JVM wraparound without signed C++
overflow. `IUSHR` shifts that unsigned carrier. `ISHR` explicitly fills the
high bits when the sign bit is set, so its JVM arithmetic-shift behavior does
not depend on C++17's implementation-defined signed right shift.

`LADD`, `LSUB`, and `LMUL` operate on an unsigned 64-bit bit carrier and copy
the result bits to/from JNI `jlong`, avoiding signed C++ overflow while
preserving JVM two's-complement wraparound. `I2L` sign-extends `jint`; `L2I`
truncates to the low 32 bits. JVM local `LLOAD`/`LSTORE` instructions disappear
into SSA in the frontend; the evaluator `LLOAD` materializes i64 parameters and
`LSTORE` stages the i64 return without changing those values.

`LAND`, `LOR`, and `LXOR` operate directly on the 64-bit carrier. Long shifts
mask the i32 count with `& 63`. `LSHL` and `LUSHR` use unsigned shifts; `LSHR`
fills the high bits explicitly and does not rely on implementation-defined
signed C++ right shift.

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

- static methods with JVM integer-carrier/long arguments and an i32 or i64
  return;
- i32 constants;
- `IADD`, `ISUB`, `IMUL`, `IAND`, `IOR`, `IXOR`, `ISHL`, `ISHR`, and `IUSHR`;
- `LLOAD`, `LSTORE`, `LADD`, `LSUB`, `LMUL`, `LAND`, `LOR`, `LXOR`, `LSHL`,
  `LSHR`, `LUSHR`, `I2L`, `L2I`, and `LRETURN` through the shared typed SSA
  representation;
- `GOTO`, all unary and binary integer comparisons represented by the IR, and
  `IRETURN`;
- local-variable and operand-stack merges already represented as IR phi values.

Serialization and capability validation finish before `MethodShellEmitter`
marks the method native or writes registration/output state. A capability miss
throws `UnsupportedIrConstructException` from `IrMethodCompiler`, so the
existing per-method legacy fallback remains safe.

The current evaluator path falls back for instance methods, void/reference
signatures, exception edges, JNI-dependent nodes (fields, invokes, arrays, and
throws), integer operations outside the operations above, unary integer
operations, and any other IR node not listed here. Long constants remain
fallbacks. Float/double and object values are outside this evaluator slice.
The current frontend does not admit `LDIV`, `LREM`, or `LNEG`; this port does
not add frontend nodes or evaluator opcodes for them. If those operations become
available as IR nodes, evaluator lowering must continue to reject them until a
separate capability extension is reviewed.

## Port verification

The default-off generation checks use one fixture with a static `(II)I`
add/subtract/multiply method and an unsupported unary-int method. They compare
complete generated output trees, inspect evaluator C++ output, and verify that
the unsupported method reaches the existing per-method legacy fallback. Exact
commands and exit codes are recorded here after running the focused suite on
the committed port.
