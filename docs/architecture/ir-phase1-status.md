# IR phase 1 status

This branch is Sol Extra High Fast implementing and cross-checking Fable IR
phase 1. It is an opt-in compiler slice, not a production-readiness claim.

## Landed

- `--codegen=legacy|ir`, with `legacy` as the CLI and API default.
- The existing `NativeObfuscator.process(...)` signature remains available. A
  trailing `CodegenMode` overload selects the new path for API callers.
- A typed normal-edge CFG with explicit blocks, typed SSA values, block-local
  local/stack phis, integer constants/add/subtract/multiply, JVM locals,
  integer conditional branches, `goto`, and integer/void returns.
- An ASM `MethodNode` frontend with explicit capability checks and
  method/instruction diagnostics.
- A small structured C++ AST and direct emitter. IR method bodies do not call
  `Snippets.getSnippet` and do not read `cppsnippets.properties`.
- A shared `MethodShellEmitter` used by the legacy and IR paths for the JNI
  signature, common prologue, registration mutation, default return, and special
  method postprocessing. Legacy-only `jvalue` argument loading and catch snippets
  remain isolated in the legacy mode.
- Fast JUnit tests construct ASM methods for `add` and `sumTo`, inspect the IR,
  and inspect emitted C++ without invoking CMake, a C++ compiler, or native code.

## Safe fallback

The IR frontend validates the whole method, including unreachable instructions,
and the structured body is built before the shared shell mutates the
`MethodNode`, native registration, or hidden-method state. A capability miss
therefore logs the class, method, descriptor, reason, and bytecode instruction,
then invokes the legacy generator for that method only. Unexpected compiler
errors are not mislabeled as capability misses and remain hard failures.

Phase 1 accepts methods with JVM int-carrier parameters and integer/void return,
integer constants, `ILOAD`/`ISTORE`/`IINC`, `IADD`/`ISUB`/`IMUL`, integer
branches, `GOTO`, and returns. Exception tables, references in descriptors,
object/JNI operations, switches, conversions, wide primitive values, and all
other opcodes fall back.

## Adjustments from the Fable design

- Fable's package map is an end-state inventory. This slice uses a compact node
  hierarchy and emits only the agreed `add`/`sumTo` vertical slice; empty pass,
  runtime-call, cache, backend-hook, and interpreter classes were not added.
- Local loads/stores are consumed by the SSA environment rather than retained as
  redundant IR nodes. Local and operand-stack state become typed block phis at
  every non-entry merge.
- Fable proposed catching any IR error for fallback. This implementation catches
  only `UnsupportedIrConstructException`, and only before method mutation.
  Falling back after an internal error could run legacy codegen on partially
  mutated state and hide a compiler defect.
- Arithmetic emission casts operands through `juint` before add/subtract/multiply
  and casts the result back to `jint`, preserving JVM 32-bit wraparound without
  C++ signed-overflow undefined behavior.
- The phase-one C++ is direct label/goto CFG output. A relooper and interpreter
  backend remain out of scope.

## Commands and evidence

The implementation was committed and pushed before these verification commands,
as required for this branch.

### Gradle

```text
./gradlew :obfuscator:test --tests by.radioegor146.CodegenModeTest --tests by.radioegor146.ir.IrCompilerTest
```

Result on the final test sources: **failed at test-process startup** after
`compileJava`, `compileTestJava`, and `testClasses` were up to date. Exact Gradle
diagnostic:

```text
Could not start Gradle Test Executor 1: Failed to load JUnit Platform.
Please ensure that all JUnit Platform dependencies are available on the test's
runtime classpath, including the JUnit Platform launcher.
```

Executor 2 reported the same error. This is the known missing-launcher problem.
JUnit dependencies and `obfuscator/build.gradle` test configuration were not
changed.

An earlier invocation of the same command exposed an invalid use of a newer
Picocli test API (`ParseResult.valueFor`) and failed `compileTestJava`; the test
was corrected to work with the repository's Picocli 4.6.3. The current
compilation evidence is:

```text
./gradlew :obfuscator:compileTestJava :obfuscator:shadowJar
BUILD SUCCESSFUL
```

Because the launcher could not start, a temporary Java source launcher called
the six new public test methods directly with the compiled test classes, the
shadow jar, JUnit API, platform-commons, opentest4j, and apiguardian on the
classpath:

```text
java --class-path <compiled-tests:shadow-jar:resolved-junit-api-jars> /tmp/RunIrChecks.java
MANUAL_ASSERTIONS_PASSED
```

The temporary runner was deleted. An earlier JShell attempt is not counted as
evidence because JShell continued after individual snippet exceptions.

### CLI and generated-source smoke checks

```text
java -jar obfuscator/build/libs/obfuscator.jar --help
```

Exited 0 and displayed
`--codegen=<codegenMode> Method code generator: LEGACY, IR (default: legacy)`.

A temporary Java 8 jar containing `add`, `sumTo`, and an object-using
`unsupported` method was transpiled once with no codegen flag and once with
`--codegen=ir`:

```text
javac --release 8 .../Example.java &&
jar --create --file .../example.jar ... &&
java -jar obfuscator/build/libs/obfuscator.jar .../example.jar .../default &&
java -jar obfuscator/build/libs/obfuscator.jar .../example.jar .../ir --codegen=ir
```

Both invocations exited 0. The default output used legacy `cstack` code for
`add` and `sumTo`. The IR output contained direct IR bodies for both methods and
legacy `cstack` code for only the unsupported method. The logged fallback was:

```text
IR codegen unsupported for Example#unsupported(Ljava/lang/Object;)I:
Only JVM int-carrier arguments are supported; falling back to legacy for this method
```

The IR output also contained the existing JNI signature shape:

```text
jint JNICALL __ngen_native_add1(JNIEnv *env, jclass clazz, jint arg0, jint arg1)
```

For the shell extraction check, the same fixture was transpiled by a detached
`master` worktree and by this branch in default mode. `cmp` reported exact
equality for `cpp/output/Example_0.cpp` and `cpp/string_pool.cpp`.

```text
git diff master -- obfuscator/build.gradle obfuscator/test_data
```

Produced no output. No CMake build, C++ compiler, transformed-jar execution, or
full native E2E was run, so this branch is not direct-production evidence.
