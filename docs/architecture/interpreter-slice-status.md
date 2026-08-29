# Interpreter backend vertical slice status

Status recorded on 2026-08-28.

## Implemented scope

- `--backend=cpp|interpreter` selects the compiler backend. `cpp` is the
  declared and initialized default.
- Interpreter mode lowers eligible methods to versioned byte arrays and JNI
  trampolines. Eligibility in this slice is deliberately limited to static
  methods with an `int` return, only `int` arguments, no exception table, no
  synchronization, and only the supported instructions.
- A method that is not eligible continues through the existing direct C++
  `MethodProcessor`; selection is per method.
- `native_jvm_interp.cpp` uses portable C++17 `switch` dispatch. It is copied
  into interpreter-mode output and listed in generated CMake input. Zig's
  existing recursive `.cpp` collection discovers the same file.
- No annotation is required or added.

## Opcode subset

ISA version 1 has these byte opcodes:

| Group | Opcodes |
|---|---|
| Constants | `IPUSH i32` |
| Locals | `ILOAD u16`, `ISTORE u16` |
| Arithmetic | `IADD`, `ISUB` |
| Unary branches | `IFEQ`, `IFNE`, `IFLT`, `IFGE`, `IFGT`, `IFLE` |
| Integer comparisons | `IF_ICMPEQ`, `IF_ICMPNE`, `IF_ICMPLT`, `IF_ICMPGE`, `IF_ICMPGT`, `IF_ICMPLE` |
| Control/return | `GOTO`, `IRETURN` |

ASM `ICONST_*`, `BIPUSH`, `SIPUSH`, and integer `LDC` lower to `IPUSH`.
`IINC` lowers to `ILOAD`, `IPUSH`, `IADD`, `ISTORE`; it does not add another
runtime opcode. Branch operands are absolute little-endian byte offsets.

## Verification results

All results below are from the repository and toolchains on the date above.

1. Focused Gradle suite: **8 tests passed**.

   ```text
   ./gradlew :obfuscator:test \
     --tests by.radioegor146.MainBackendOptionTest \
     --tests by.radioegor146.interpreter.InterpreterMethodEmitterTest \
     --tests by.radioegor146.interpreter.InterpreterRuntimeTest \
     --tests by.radioegor146.interpreter.InterpreterBackendIntegrationTest
   BUILD SUCCESSFUL
   ```

   This includes exact Java-side opcode goldens for `add`, subtraction, and
   `sumTo`; explicit C++ fallback for an unsupported multiply method; and a
   runtime test compiled with
   `g++ -std=c++17 -Wall -Wextra -Werror`. The compiled test executed add,
   subtraction, wrapping integer arithmetic, and the loop.

2. Default-off comparison: a smoke JAR containing `add` and `sumTo` was
   generated once from `master` before the edits and again from this branch
   with no backend flag. `diff -r` over both complete `cpp/` trees exited 0
   with no output. The default tree contains neither
   `native_jvm_interp.cpp` nor a CMake reference to it. The integration test
   independently compares no-flag output against explicit
   `CompilerBackend.CPP` byte for byte.

3. Generated-library run: interpreter-mode output was configured and built by
   CMake with GCC 13.3.0. CMake compiled `native_jvm_interp.cpp` and linked
   `libnative_library.so`. Running the transformed smoke JAR against that
   library printed:

   ```text
   12:45
   ```

   The values are `add(7, 5)` and `sumTo(10)`.

4. Complete Gradle regression run with `CC=gcc CXX=g++`: **18 of 19 tests
   passed**. `PullRequest72` failed in its reference-Java setup before compiler
   execution because `TestStringConcatFactory` was absent when `krak2` was not
   available. The focused interpreter suite and the other existing tests
   passed.

Environment note: this image's default `/usr/bin/c++` is Clang and could not
link `libstdc++`; explicitly selecting the installed GCC toolchain made the
generated CMake build succeed.
