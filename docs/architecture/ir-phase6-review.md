# IR phase 6 compiler review

Review branch: `cursor/ir-phase6-sol-review-6d81`

Reviewed subject: `cursor/ir-compiler-phase6-6d81` /
[PR #47](https://github.com/gaoyu06/native-obfuscator/pull/47), compared with
`cursor/ir-phase5-fable-review-6d81` /
[PR #45](https://github.com/gaoyu06/native-obfuscator/pull/45).

## Verdict

**Accept with nits.**

The switch and object-array compiler paths are structurally sound after the
array-component class-resolution blocker described below was fixed. No
remaining correctness blocker was found in the reviewed phase-6 scope.

This is not a ship-readiness approval. The focused unit and syntax checks do not
replace supported-platform CI and native runtime-parity coverage, and the
stacked base still requires its normal human disposition.

## Scope reviewed

The complete current contents and phase-6 diffs were read for:

- `ir/IrMethod.java`
- `ir/IrNodes.java`
- `ir/emit/CppAst.java`
- `ir/emit/IrCppEmitter.java`
- `ir/frontend/AsmToIr.java`
- `ir/frontend/CfgBuilder.java`
- `ir/IrCompilerTest.java`

`CodegenModeTest`, `IrMethodCompiler`, `MethodShellEmitter`,
`NativeObfuscator`, `Main`, `MethodProcessor`, both special method processors,
the class-resolution runtime helper, and the retained snippet loader/resource
were also inspected for the requested integration and fallback checks.

## Findings

### Switch CFG and emission

- `TABLESWITCH` validates its range/label count and requires executable case and
  default targets.
- `LOOKUPSWITCH` validates key/label cardinality, duplicate keys, and executable
  case/default targets.
- Every logical case and the mandatory default is represented in
  `IrNodes.Switch`. Its CFG successor list deduplicates physical destinations
  without losing logical emitted arms.
- Stack-shape propagation, definite-local propagation, and phi connection all
  iterate the switch CFG successors, including default.
- The final generated `tableSelect` C++ has cases `-1`, `0`, `1`, and default.
  The final generated `lookupSelect` C++ has cases `-7`, `42`, and default.
  Every arm first snapshots all incoming phi values into edge temporaries, then
  assigns the destination phis, then executes exactly one `goto`.

No wrong default target, missing edge transfer, implicit fallthrough, or
parallel-copy clobber was found.

### Object-array allocation and JNI routing

- `ANEWARRAY` pops an `I32` length and produces a reference-typed
  `NewObjectArray`.
- Generated String and Object array paths check `length < 0` first and call
  `utils::throw_re` for `java/lang/NegativeArraySizeException`.
- Component resolution uses the shared weak-global class cache. Pending
  resolution failures take the block's exceptional exit.
- `NewObjectArray` receives the length, resolved component class, and
  `nullptr`. A null result or pending JNI exception takes the exceptional exit.
- In a protected block, exceptional local phis are copied before the shared
  `IR_CATCH_0` dispatch. In an unprotected block, the JNI default is returned
  while the pending exception remains.
- The emitted translation unit contains no `juint`; carriers use JNI types such
  as `jint`, `jobject`, `jclass`, and `jthrowable`.

### Blocker found and fixed

The original general `ANEWARRAY` implementation treated an array-typed
component such as `[Ljava/lang/String;` as an ordinary class name and passed its
dotted form to the classloader-based resolver. Array descriptors require
`JNIEnv::FindClass`, as already handled by the legacy path.

The review branch now selects `FindClass` for component names beginning with
`[`, preserves the descriptor unchanged, and keeps ordinary internal names on
the classloader-based path. The new
`resolvesArrayComponentAnewarrayWithFindClass` regression and the expanded g++
translation unit cover this correction.

### Fallback atomicity and retained path

`IrMethodCompiler.processMethod` calls, in order:

1. `AsmToIr.build(...)`
2. `IrCppEmitter.emitBody(...)`
3. `MethodShellEmitter.beginIr(...)`

All construction sites for `UnsupportedIrConstructException` in the IR
compiler are in `AsmToIr` or its `CfgBuilder` call. Therefore an unsupported
method is rejected during `build(...)`, before emitter cache IDs are allocated
and before `beginIr(...)` appends output/native metadata or invokes the special
processor that can set `ACC_NATIVE`.

The three mutation regressions pass and verify empty generated output/native
metadata, unchanged `ACC_NATIVE`, and untouched applicable caches. The CLI
annotation and API overload still default to `legacy`; IR remains opt-in.
`Snippets.java` and `sources/cppsnippets.properties` remain present and the
legacy method processor remains reachable.

## Verification evidence

Command:

```text
./gradlew :obfuscator:test \
  --tests by.radioegor146.ir.IrCompilerTest \
  --tests by.radioegor146.CodegenModeTest
```

Final JUnit XML:

```text
IrCompilerTest: 27 tests, 0 skipped, 0 failures, 0 errors
CodegenModeTest: 2 tests, 0 skipped, 0 failures, 0 errors
Total: 29 tests, 0 skipped, 0 failures, 0 errors
```

The XML testcase
`generatedCppPassesGppSyntaxCheckWhenToolchainAvailable` completed in 0.134 s
and has no `skipped` element. The host has g++ 13.3.0 and JDK 21 JNI headers, so
the smoke was real. Its retained 23-method translation unit was also compiled
independently:

```text
g++ -std=c++17 -fsyntax-only \
  -I/usr/lib/jvm/java-21-openjdk-amd64/include \
  -I/usr/lib/jvm/java-21-openjdk-amd64/include/linux \
  ir-smoke.cpp
```

The independent command exited 0 with empty diagnostics.

## Human preconditions

1. Review and land against `cursor/ir-phase5-fable-review-6d81`, not `master`,
   preserving the stack order.
2. Require the repository's supported-platform/JDK CI to pass for the final
   stacked commit.
3. Keep `legacy` as the default until broader native runtime-parity coverage
   accepts the IR path.
4. Reconfirm that no later conflict resolution drops the array-component fix,
   default switch edges, phi transfers, exception checks, or fallback
   atomicity tests.
