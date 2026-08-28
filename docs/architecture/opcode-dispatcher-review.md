# Opcode dispatcher review

Reviewed on 2026-08-28 against
`cursor/interpreter-backend-slice-e758` at `df83421`.

## Verdict

**Accept with nits**, including the correctness patch in this review branch.
There are no remaining correctness or CMake pickup blockers known within the
declared static-integer slice.

## Findings

### Fixed correctness issue

The runtime originally validated a conditional branch target only when the
condition was true. An otherwise executable stream with an out-of-range target
could therefore be accepted when the branch was not taken. The review patch
validates every unary and binary conditional target as soon as it is decoded.

The runtime harness now checks both outcomes of all unary and binary integer
conditions. It also rejects an unknown opcode, a truncated operand, an
out-of-range `GOTO`, out-of-range taken and untaken conditional targets, an
out-of-range local, stack underflow, and stack overflow.

### Integer and control-flow semantics

- `IPUSH`, `ILOAD`, `ISTORE`, `IADD`, `ISUB`, and `IRETURN` have the expected
  stack effects. Local and stack accesses are bounds checked.
- Addition and subtraction are performed as unsigned 32-bit operations and
  copied back to `int32_t`, preserving Java integer wraparound without signed
  C++ overflow.
- Unary and two-operand comparisons preserve signed Java `int` ordering.
  Two-operand comparisons pop right and then left. `GOTO` and conditional
  operands are absolute little-endian byte offsets.
- Operand reads reject truncated streams. The dispatch default rejects missing
  handlers, and falling off the end without `IRETURN` fails execution.
- The Java emitter accepts only static methods with `int` arguments and return
  type, no exception table or synchronization, and a fully supported
  instruction list. Unsupported methods remain on the direct C++ path.
  `IINC` expands to four existing operations, with two extra temporary stack
  slots included in the emitted maximum.

### JNI and source pickup

- The generated shell follows the existing static-method convention:
  `JNICALL`, `JNIEnv *`, `jclass`, JNI primitive arguments, `jint` return, and
  registration through the class `JNINativeMethod` table.
- Interpreter mode copies both runtime source files. Generated CMake input
  explicitly contains `native_jvm_interp.cpp`; an actual GCC/CMake build in
  this review compiled that translation unit and linked
  `libnative_library.so`.
- Zig source discovery recursively selects `.cpp` files, so its existing
  collection includes the runtime source. Zig compilation was inspected but
  not re-run in this review.

## Verification evidence

1. Default path comparison:

   A small JAR with `add`, `sumTo`, and an unsupported `multiply` method was
   generated with no `--backend` flag by a `master` executable and by the final
   review executable. `diff -r` over the complete generated `cpp/` trees
   exited 0 with no output.

2. Focused Gradle command:

   ```text
   ./gradlew :obfuscator:test \
     --tests by.radioegor146.MainBackendOptionTest \
     --tests by.radioegor146.interpreter.InterpreterMethodEmitterTest \
     --tests by.radioegor146.interpreter.InterpreterRuntimeTest \
     --tests by.radioegor146.interpreter.InterpreterBackendIntegrationTest \
     --rerun-tasks
   ```

   Result: `BUILD SUCCESSFUL`; all 8 discovered tests passed. The native
   harness was compiled with `g++ -std=c++17 -Wall -Wextra -Werror`.

3. Complete Gradle command:

   ```text
   CC=gcc CXX=g++ ./gradlew :obfuscator:test --rerun-tasks
   ```

   Result: `BUILD FAILED`; 20 tests completed, 19 passed, and
   `PullRequest72` failed before compiler execution because its ideal Java run
   could not load `TestStringConcatFactory`.

4. Generated CMake project:

   ```text
   CC=gcc CXX=g++ cmake -S <generated-cpp> -B <build> -DCMAKE_BUILD_TYPE=Release
   cmake --build <build> --config Release --parallel 2
   ```

   Result: success with GCC 13.3.0. Build output explicitly compiled
   `native_jvm_interp.cpp.o` and linked the shared library.

## Status-document check

- The focused 8-test result and default-path equality claim were reproduced.
- Generated CMake compilation and linking were reproduced. The earlier
  transformed-JAR output `12:45` was not re-run in this review.
- The complete-suite count in
  `interpreter-slice-status.md` was not reproduced: the current run discovered
  20 tests, not 19. Its stated failure cause for `PullRequest72` does match the
  current failure.

## Remaining nits and preconditions

- Update the historical complete-suite count if
  `interpreter-slice-status.md` is intended to describe the latest branch
  state.
- A maintainer should review the eager branch-target validation patch.
- Treat the full-suite failure as an explicit test-fixture disposition; it is
  not evidence of an interpreter failure, but the overall Gradle task is not
  green.
