# IR evaluator LDIV/LREM — compiler review

Reviewer: GPT-5.6 Sol.

Subject: [`cursor/ir-eval-ldiv-6d81` (draft PR #85)](https://github.com/gaoyu06/native-obfuscator/pull/85),
based on [`cursor/ir-eval-i64-6d81` (PR #68)](https://github.com/gaoyu06/native-obfuscator/pull/68).

This is a compiler-correctness review of the Java serializer, shared C++
evaluator, typed frontend, direct C++ lowering, fallback ordering, generated
method selection, and focused tests for JVM `LDIV`/`LREM`.

## Verdict

**Accept.**

The implementation matches the required JVM behavior. The Java serializer and
C++ evaluator assign `LDIV=0x2b` and `LREM=0x2c`. A zero divisor requests
`java/lang/ArithmeticException` through the trampoline's `JNIEnv*` and returns
from evaluation immediately. The `Long.MIN_VALUE / -1` and
`Long.MIN_VALUE % -1` cases are intercepted before a signed C++ division or
remainder: they produce `Long.MIN_VALUE` and zero respectively.

Generated `(JJ)J` divide and remainder methods use `evaluate_i64`. The shared
frontend also admits both operations for direct IR, whose structured emitter
applies the same zero-divisor and overflow rules and uses the existing
exception-edge dispatch when the bytecode has a matching handler.

No correctness defect was found, so this review changes no compiler code.

## Requirement audit

| Requirement | Authoritative evidence | Result |
| --- | --- | --- |
| Java/C++ opcodes | `InterpreterStreamStrategy.OP_LDIV/OP_LREM`, serialized bytes, and `native_jvm_eval.cpp` constants | `0x2b` / `0x2c` agree |
| Zero divisor | `native_jvm_eval.cpp` checks `right_value == 0`, calls `throw_arithmetic_exception(env)`, then returns before another instruction can execute | Pending `ArithmeticException` and immediate exit |
| `Long.MIN_VALUE / -1` | Explicit minimum/-one branch copies the dividend bits for `LDIV` | JVM minimum value; no signed C++ overflow |
| `Long.MIN_VALUE % -1` | The same branch writes zero for `LREM` | JVM zero; no signed C++ overflow |
| `(JJ)J` evaluator selection | Generated-source assertions isolate `divide(JJ)J` and `remainder(JJ)J` and require `evaluate_i64` plus two `jlong` arguments | Evaluator i64 path retained |
| Direct-IR admission | `AsmToIr.isLongBinaryOp`, `longBinaryOperation`, `IrNodes.LongBinary`, and `IrCppEmitter.emitLongDivision` | Both operations admitted with the same JVM edges |
| Throwing CFG boundary | `CfgBuilder.mayThrow` includes `LDIV` and `LREM`, forcing the instruction to end its basic block | Exceptional locals/handlers observe the throw point |
| Fallback before mutation | `IrMethodCompiler` finishes frontend construction and selected lowering before `MethodShellEmitter.begin*`; the evaluator rejection regression checks method, output, registration, and caches | Invariant preserved |
| Defaults | `Main`, `NativeObfuscator`, and `CodegenModeTest` | Legacy codegen and direct IR lowering remain defaults |

## Focused rerun

The required independent `CC=gcc CXX=g++` rerun is intentionally not claimed
in this pre-test review commit. The final review revision will record counts
from the generated JUnit XML for:

- `by.radioegor146.CodegenModeTest`;
- `by.radioegor146.ir.IrCompilerTest`;
- `by.radioegor146.ir.backend.InterpreterStreamStrategyTest`.

It will also verify that the toolchain-gated g++ syntax and linked evaluator
harness tests were executed rather than skipped.

## Findings

There are no correctness blockers and no compiler fix on this review branch.
One non-blocking coverage note remains: the evaluator has a linked native
runtime harness for divide/remainder, zero-divisor pending exceptions,
immediate exit, and the minimum/-one cases, while direct IR verifies generated
source and g++ syntax rather than executing a Java-to-native exception-catching
fixture. The direct emitter reuses the reviewed exception-dispatch mechanism,
and inspection shows the zero path leaves the arithmetic block before the C++
division expression, so this is a test-depth note rather than contradictory
correctness evidence.

No benchmark result is added, and the review does not rewrite #53 or #59.
