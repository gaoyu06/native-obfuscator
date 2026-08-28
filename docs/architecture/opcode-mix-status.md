# DemoKernel mix opcode lowering status

Status recorded on 2026-08-28 from branch
`cursor/opcode-mix-lowering-6d81`, based on
`cursor/opcode-dispatcher-review-d888` at `a84354e`.

## Compiler scope

The interpreter emitter and native runtime add exactly five integer opcodes:

| Value | Opcode | Java operation |
|---:|---|---|
| 20 | `IMUL` | wrapping `int` multiplication |
| 21 | `IXOR` | bitwise exclusive-or |
| 22 | `ISHL` | left shift with a distance masked by 31 |
| 23 | `IUSHR` | unsigned right shift with a distance masked by 31 |
| 24 | `IROTL` | `Integer.rotateLeft(int, int)` |

`IROTL` is emitted only for the exact static call
`java/lang/Integer.rotateLeft(II)I`. Other method calls remain unsupported by
this integer opcode slice. The default compiler backend remains `cpp`.

## Selection evidence

An interpreter-mode compiler integration fixture contains `DemoKernel.add`,
`DemoKernel.sumTo`, the evaluation kernel `DemoKernel.mix`, and an unsupported
integer division method. Its generated `DemoKernel_0.cpp` contains three opcode
arrays:

```text
__ngen_native_add1_interp_code[]
__ngen_native_sumTo2_interp_code[]
__ngen_native_mix3_interp_code[]
```

The `mix` array is 106 bytes and its descriptor is
`{ ISA_VERSION, 6, 4, ... }`. Its JNI function invokes:

```text
native_jvm::interp::execute_i(
    __ngen_native_mix3_interp_method, interp_frame, &interp_result)
```

The generated section from `// mix(II)I` to the next method has no direct
`jvalue cstack` body. The unsupported division method has no opcode array,
showing that selection remains per method rather than changing the fallback
policy.

## Java/native execution results

The production emitter generated the `mix` byte array used by a C++17 runtime
harness. The harness compiled `native_jvm_interp.cpp` with
`g++ -std=c++17 -Wall -Wextra -Werror` and compared each native result with the
Java reference implementation. A generated JNI project was also built with
GCC/G++ 13.3.0, and the transformed `DemoKernel` was invoked through
`libnative_library.so`. Both native paths produced the same real results as
Java:

| Seed | Rounds | Java | Native opcode path |
|---:|---:|---:|---:|
| `0` | `0` | `-1640531527` | `-1640531527` |
| `0` | `1` | `389078419` | `389078419` |
| `1` | `4` | `-1363050107` | `-1363050107` |
| `Integer.MIN_VALUE` | `3` | `-2129079926` | `-2129079926` |
| `0x12345678` | `16` | `567676480` | `567676480` |

The focused Gradle command covered backend parsing, emitter goldens, native
runtime execution, and compiler integration:

```text
./gradlew :obfuscator:test \
  --tests by.radioegor146.MainBackendOptionTest \
  --tests by.radioegor146.interpreter.InterpreterMethodEmitterTest \
  --tests by.radioegor146.interpreter.InterpreterRuntimeTest \
  --tests by.radioegor146.interpreter.InterpreterBackendIntegrationTest \
  --rerun-tasks
```

Result: `BUILD SUCCESSFUL`; all 10 discovered tests passed. The integration
test also compares no-flag output with explicit `CompilerBackend.CPP` output
byte for byte.
