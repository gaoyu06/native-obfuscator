# Opcode compact encoding status

Status recorded on 2026-08-28 from
`cursor/opcode-compact-encoding-6d81`, based on
`cursor/opcode-mix-lowering-6d81` at `09ec787`.

## Encoding

Interpreter ISA version 2 assigns a distinct, non-sequential byte value to each
supported integer operation. Generated opcode methods emit those bytes and
their operands as a hexadecimal `std::uint8_t` blob. Two-byte local indexes and
four-byte integer and branch operands remain little-endian.

The generated method symbols use class and method indexes. The opcode method
blocks contain neither Java method comments nor Java method names. The semantic
opcode enum and the byte-to-opcode decode table are in the anonymous namespace
of `native_jvm_interp.cpp`; `native_jvm_interp.hpp` exposes only the ISA
version, method/frame structures, and execution entry point.

The generated `DemoKernel.mix` block now consists of a 106-byte blob, a method
descriptor, and a generic JNI entry point that calls `execute_i`. Its two
32-bit constants appear only as little-endian byte quartets. There are no named
opcode enumerators or complete 32-bit constant literals in that block.

Interpreter remains an explicit backend selection. With no backend option, the
compiler still selects `cpp`. The integration test recursively compares every
file and every byte generated with no option against `CompilerBackend.CPP`; the
trees were identical. Neither output contains the interpreter runtime.

## Real GCC/G++ test result

The following command was run at 2026-08-28 21:02 UTC:

```text
CC=gcc CXX=g++ ./gradlew :obfuscator:test \
  --tests by.radioegor146.MainBackendOptionTest \
  --tests by.radioegor146.interpreter.InterpreterMethodEmitterTest \
  --tests by.radioegor146.interpreter.InterpreterRuntimeTest \
  --tests by.radioegor146.interpreter.InterpreterBackendIntegrationTest \
  --rerun-tasks --console=plain
```

Result: `BUILD SUCCESSFUL` with 10 tests, 0 skipped, 0 failures, and 0
errors. The compiler executables were GCC/G++ 13.3.0. The result files reported:

| Test class | Tests | Failures |
|---|---:|---:|
| `MainBackendOptionTest` | 2 | 0 |
| `InterpreterMethodEmitterTest` | 5 | 0 |
| `InterpreterRuntimeTest` | 2 | 0 |
| `InterpreterBackendIntegrationTest` | 1 | 0 |

The runtime tests compiled `native_jvm_interp.cpp` with
`g++ -std=c++17 -Wall -Wextra -Werror`, executed the resulting binaries, and
checked add, subtraction, `sumTo`, every conditional branch, invalid streams,
and emitted `mix` code. The checked integer results included:

| Operation/input | Native opcode result |
|---|---:|
| `add(7, -3)` | `4` |
| `add(Integer.MAX_VALUE, 1)` | `Integer.MIN_VALUE` |
| `sumTo(-3)` | `0` |
| `sumTo(0)` | `0` |
| `sumTo(10)` | `45` |
| `mix(0, 0)` | `-1640531527` |
| `mix(0, 1)` | `389078419` |
| `mix(1, 4)` | `-1363050107` |
| `mix(Integer.MIN_VALUE, 3)` | `-2129079926` |
| `mix(0x12345678, 16)` | `567676480` |

For every `mix` row, the native result was compared directly with the Java
reference result in the passing runtime test.
