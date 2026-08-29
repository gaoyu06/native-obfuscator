# In-process interpreter ISA v4 exception dispatch: status

Status recorded on 2026-08-29 from `origin/master` at `7eda1ec`.

## Implemented increment

- The explicit, default-off `--backend=interpreter` stream remains ISA v4.
  Opcodes 1–51 keep their existing values; `ATHROW` is appended as opcode 52.
- Eligible static methods may now contain ordered try/catch entries when every
  instruction in the method and its handlers is already representable.
  Constructors, class initializers, instance methods, synchronized methods,
  interface methods, and methods with unsupported instructions still use the
  selected per-method fallback without interpreter mutation.
- Each emitted method descriptor can reference an exception table containing
  start PC, exclusive end PC, handler PC, and an internal catch class name or
  `nullptr` for catch-all. PCs use the same byte offsets as branch targets.
- `ATHROW`, `IDIV`, `IREM`, `LDIV`, and `LREM` walk the table in classfile
  order. A match clears the operand-stack height, puts the exception reference
  in slot zero, and transfers control to the handler. A null `ATHROW` creates
  `java/lang/NullPointerException`.
- Integer and long division/remainder by zero create
  `java/lang/ArithmeticException`, so covered paths are catchable. Existing
  nonzero arithmetic behavior is unchanged, including `MIN_VALUE / -1` and
  the corresponding zero remainder.
- An unmatched exception returns `execution_result::pending_exception`. The
  JNI trampoline preserves an already-pending JNI exception or throws the
  exception reference returned in the interpreter frame, then returns the JNI
  zero value.
- `NEW`, invoke instructions (including `INVOKESPECIAL`), and field access are
  still outside this interpreter ISA slice.
- `--backend` remains `cpp` by default and `--codegen` remains `legacy`.

Ship-ready: **No**. Review required: **Yes**; a Sol-only interpreter review is
acceptable.

## Verification

The required combined command completed with `BUILD SUCCESSFUL`:

```text
CC=gcc CXX=g++ ./gradlew :obfuscator:test --rerun-tasks \
  --tests by.radioegor146.MainBackendOptionTest \
  --tests by.radioegor146.interpreter.InterpreterMethodEmitterTest \
  --tests by.radioegor146.interpreter.InterpreterRuntimeTest \
  --tests by.radioegor146.interpreter.InterpreterBackendIntegrationTest \
  --tests by.radioegor146.ir.IrCompilerTest \
  --tests by.radioegor146.CodegenModeTest
```

JUnit XML counts, recorded separately:

```text
Option/interpreter suite:
MainBackendOptionTest:                    2
InterpreterMethodEmitterTest:           17
InterpreterRuntimeTest:                  1
InterpreterBackendIntegrationTest:       2
Subtotal:                                22

IR/codegen regression:
IrCompilerTest:                         102
CodegenModeTest:                          7
Subtotal:                               109

Total:                                  131
Skipped:                                  0
Failures:                                 0
Errors:                                   0
```

The runtime test compiled the dispatcher using
`g++ -std=c++17 -Wall -Wextra -Werror` and executed 61 numbered checks. New
checks cover unhandled `ATHROW`, catch-all `IDIV` and `LDIV` zero divisors,
ordered typed matches and misses, and null `ATHROW`; all prior i32, i64, and
reference checks remain present.

The generated integration project, including an emitted typed exception
table, also configured and built successfully:

```text
CC=gcc CXX=g++ cmake \
  -S /tmp/native-jvm-interpreter-integration-4618654598449167340/interpreter/cpp \
  -B /tmp/native-jvm-interpreter-integration-4618654598449167340/interpreter/native-build \
  -DCMAKE_C_COMPILER=gcc -DCMAKE_CXX_COMPILER=g++
CC=gcc CXX=g++ cmake --build \
  /tmp/native-jvm-interpreter-integration-4618654598449167340/interpreter/native-build \
  --parallel 2
```

Result: `[100%] Built target native_library` with GCC/G++ 13.3.0.

## Default-off generated-tree proof

The proof built detached `origin/master`, built this branch, compiled the
existing `DefaultOffFixture`, and generated three output trees:

```text
git worktree add --detach \
  /tmp/interpreter-exceptions-master-a565 origin/master
CC=gcc CXX=g++ \
  /tmp/interpreter-exceptions-master-a565/gradlew \
  -p /tmp/interpreter-exceptions-master-a565 :obfuscator:shadowJar
CC=gcc CXX=g++ ./gradlew :obfuscator:shadowJar

mkdir -p /tmp/interpreter-exceptions-proof-a565/fixture-classes
javac --release 8 \
  -d /tmp/interpreter-exceptions-proof-a565/fixture-classes \
  obfuscator/src/test/resources/interpreter/DefaultOffFixture.java
jar --create \
  --file /tmp/interpreter-exceptions-proof-a565/fixture.jar \
  -C /tmp/interpreter-exceptions-proof-a565/fixture-classes \
  DefaultOffFixture.class

mkdir -p /tmp/interpreter-exceptions-proof-final-a565
java -jar \
  /tmp/interpreter-exceptions-master-a565/obfuscator/build/libs/obfuscator.jar \
  /tmp/interpreter-exceptions-proof-a565/fixture.jar \
  /tmp/interpreter-exceptions-proof-final-a565/master
java -jar obfuscator/build/libs/obfuscator.jar \
  /tmp/interpreter-exceptions-proof-a565/fixture.jar \
  /tmp/interpreter-exceptions-proof-final-a565/branch-default
java -jar obfuscator/build/libs/obfuscator.jar \
  /tmp/interpreter-exceptions-proof-a565/fixture.jar \
  /tmp/interpreter-exceptions-proof-final-a565/branch-cpp \
  --backend=cpp

diff -r \
  /tmp/interpreter-exceptions-proof-final-a565/master/cpp \
  /tmp/interpreter-exceptions-proof-final-a565/branch-default/cpp
# exit 0, no output

diff -r \
  /tmp/interpreter-exceptions-proof-final-a565/branch-default/cpp \
  /tmp/interpreter-exceptions-proof-final-a565/branch-cpp/cpp
# exit 0, no output
```

Both complete-tree comparisons exited 0. Output with no backend option
therefore matches both detached master and explicit `--backend=cpp`.
