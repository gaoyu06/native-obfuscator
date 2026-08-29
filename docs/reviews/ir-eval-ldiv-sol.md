# Evaluator LDIV/LREM wire-up — Sol independent review

Reviewed draft [PR #139](https://github.com/gaoyu06/native-obfuscator/pull/139)
at `ad27a769cd51e2f508ed0bb1df42b7e74c7aec17`, against current
`origin/master` at `a7e54539461f12fa0eddd21973c716bc5f99708e`.

## Verdict

**Accept.**

No compiler-correctness defect was found. The evaluator serializer and C++17
runtime agree on opcodes and operand order, implement the JVM signed
divide/remainder edge cases without evaluating an invalid C++ expression, and
preserve the existing capability and fallback boundaries. No compiler fix was
made on this review branch.

## Requirement audit

1. **Scope:** Apart from the requested `PR_BODY.md`, the draft changes only
   `InterpreterStreamStrategy.java`, `native_jvm_eval.cpp`, the evaluator
   strategy test, and the evaluator backend status document. Diffs over
   `AsmToIr`, `IrNodes`, `IrCppEmitter`, the interpreter package, and `Main`
   are empty.
2. **Opcode and operands:** Java and C++ both assign `LDIV=0x2b` and
   `LREM=0x2c`. The serializer writes `dst, lhs, rhs`; the runtime reads the
   same order. An independent extraction found all 30 Java and C++ evaluator
   opcode declarations equal.
3. **Zero divisor:** The runtime reads the divisor, checks it before `/` or
   `%`, requests `java/lang/ArithmeticException` through the trampoline's
   `JNIEnv*`, and returns immediately. No signed C++ divide or remainder by
   zero is reached.
4. **Signed arithmetic:** Ordinary `int64_t` division and remainder use C++17
   toward-zero semantics. The explicit minimum/-one branch returns
   `Long.MIN_VALUE` for division and zero for remainder before the otherwise
   overflowing expression can be evaluated.
5. **Mutation boundary:** `InterpreterStreamStrategy.lower` validates and
   serializes the complete method before returning. `IrMethodCompiler` calls
   `MethodShellEmitter.beginIr` only after lowering succeeds. The rejection
   tests confirm that unsupported input leaves method and output state
   unchanged.
6. **Selection and fallback:** A generated static `divide(JJ)J` method contains
   evaluator opcode decimal `43`, calls `evaluate_i64`, and has no direct-IR
   structured body. A protected `LDIV` has an exception edge, is rejected by
   evaluator capability validation, and therefore retains the existing
   per-method fallback behavior before shell mutation.
7. **Defaults and status:** The diff does not change CLI or compatibility
   defaults. They remain `legacy`, `direct`, and `cpp`. The #53 evaluator
   median remains `N/A`.

## Independent verification

The requested command completed successfully with GCC/G++ 13.3.0:

```text
CC=gcc CXX=g++ ./gradlew :obfuscator:test \
  --tests by.radioegor146.ir.backend.InterpreterStreamStrategyTest \
  --tests by.radioegor146.CodegenModeTest
```

JUnit XML records:

| Suite | Tests | Skipped | Failures | Errors |
| --- | ---: | ---: | ---: | ---: |
| `InterpreterStreamStrategyTest` | 10 | 0 | 0 | 0 |
| `CodegenModeTest` | 7 | 0 | 0 | 0 |
| **Total** | **17** | **0** | **0** | **0** |

The C++17 divide/remainder harness ran rather than skipping. It exercised
negative toward-zero results, both minimum/-one outcomes, and both zero-divisor
exception paths.

A fresh `divide(JJ)J` fixture was also generated with omitted `--ir-lower` and
explicit `--ir-lower=direct`. The raw top-level `diff -r` returned 1 because
the two output JAR containers differed in 14 ZIP timestamp bytes. The complete
`cpp/` trees matched, and the extracted JAR contents matched (`diff -r` exit 0
for both comparisons). This is archive metadata nondeterminism, not a lowering
or default-selection difference.

Finally, the generated evaluator CMake tree configured with GCC/G++ and built
`native_jvm_eval.cpp` into `libnative_library.so` successfully.

## Readiness

Ship-ready: **No.** This is a narrow, opt-in evaluator compiler increment; the
review does not establish broad runtime or JDK support.
