# In-process interpreter on master: status

Status recorded on 2026-08-29 from `origin/master` at `e997d71`.

## Landed scope

- `--backend=cpp|interpreter` selects the compiler backend. `cpp` is the
  initialized CLI/API default, and `--codegen` still defaults to `legacy`.
- Explicit interpreter selection lowers eligible methods to an ISA-v1 opcode
  stream, a method side table, and a JNI trampoline. The shared C++17
  `switch` dispatcher is emitted in `native_jvm_interp.cpp` and linked into the
  generated shared library.
- Eligibility is deliberately narrow: static methods with an `int` return,
  only `int` arguments, no exception table, no synchronization, no special
  method name, and only `IPUSH`, `ILOAD`/`ISTORE`, `IADD`/`ISUB`, integer
  comparisons and branches, `GOTO`, and `IRETURN`. `IINC` expands to existing
  integer opcodes.
- Rejected methods fall back per method to the active codegen. This means
  `legacy` remains the fallback for the default codegen and structured C++
  remains the fallback for `--codegen=ir`.
- Constructor selection and fallback are unchanged. The interpreter rejects
  special method names before mutation, and the existing IR constructor
  restore path remains responsible for rejected constructors.
- The classfile version floor/preservation logic and the existing IR runtime
  repair paths were not changed.

## Default-off proof

The permanent fixture
`obfuscator/src/test/resources/interpreter/DefaultOffFixture.java` contains
`add`, `sumTo`, and the unsupported `multiply`. A detached worktree at
`origin/master` and this branch generated the fixture without `--backend`;
this branch also generated it with explicit `--backend=cpp`.

```text
git worktree add --detach /tmp/native-obfuscator-master-default-proof origin/master
cd /tmp/native-obfuscator-master-default-proof
CC=gcc CXX=g++ ./gradlew :obfuscator:shadowJar
cd /workspace
javac --release 8 -d /tmp \
  obfuscator/src/test/resources/interpreter/DefaultOffFixture.java
jar --create --file /tmp/interpreter-default-proof-fixture.jar \
  -C /tmp DefaultOffFixture.class
java -jar /tmp/native-obfuscator-master-default-proof/obfuscator/build/libs/obfuscator.jar \
  /tmp/interpreter-default-proof-fixture.jar \
  /tmp/interpreter-on-master-default-proof-2/master
java -jar obfuscator/build/libs/obfuscator.jar \
  /tmp/interpreter-default-proof-fixture.jar \
  /tmp/interpreter-on-master-default-proof-2/branch-no-flag
java -jar obfuscator/build/libs/obfuscator.jar \
  /tmp/interpreter-default-proof-fixture.jar \
  /tmp/interpreter-on-master-default-proof-2/branch-explicit-cpp \
  --backend=cpp
diff -r /tmp/interpreter-on-master-default-proof-2/master/cpp \
  /tmp/interpreter-on-master-default-proof-2/branch-no-flag/cpp
diff -r /tmp/interpreter-on-master-default-proof-2/branch-no-flag/cpp \
  /tmp/interpreter-on-master-default-proof-2/branch-explicit-cpp/cpp
```

Both `diff -r` commands exited **0** with no output. Thus the complete `cpp/`
tree with the flag omitted matched both current `origin/master` and explicit
`--backend=cpp`.

## Verification

Focused current-master suite:

```text
CC=gcc CXX=g++ ./gradlew :obfuscator:test \
  --tests by.radioegor146.ir.IrCompilerTest \
  --tests by.radioegor146.CodegenModeTest
```

Result: **BUILD SUCCESSFUL**; `IrCompilerTest` 91 and `CodegenModeTest` 5,
for 96 tests with 0 skipped, 0 failures, and 0 errors.

Interpreter unit and integration suite:

```text
CC=gcc CXX=g++ ./gradlew :obfuscator:test \
  --tests by.radioegor146.MainBackendOptionTest \
  --tests by.radioegor146.interpreter.InterpreterMethodEmitterTest \
  --tests by.radioegor146.interpreter.InterpreterRuntimeTest \
  --tests by.radioegor146.interpreter.InterpreterBackendIntegrationTest
```

Result: **BUILD SUCCESSFUL**; 9 tests with 0 skipped, 0 failures, and 0
errors. Coverage includes exact `add`/`sumTo` opcode-stream goldens, explicit
`multiply` fallback to both active codegen modes, generated-tree integration,
and a real `g++ -std=c++17 -Wall -Wextra -Werror` compile and execution of the
dispatcher runtime.

Generated shared-library compile:

```text
java -jar obfuscator/build/libs/obfuscator.jar \
  /tmp/interpreter-default-proof-fixture.jar \
  /tmp/interpreter-on-master-shared-library --backend=interpreter
CC=gcc CXX=g++ cmake \
  -S /tmp/interpreter-on-master-shared-library/cpp \
  -B /tmp/interpreter-on-master-shared-library/native-build \
  -DCMAKE_C_COMPILER=gcc -DCMAKE_CXX_COMPILER=g++
CC=gcc CXX=g++ cmake --build \
  /tmp/interpreter-on-master-shared-library/native-build --parallel 2
```

Result: configuration and compilation succeeded, including
`native_jvm_interp.cpp`; CMake reported
`[100%] Built target native_library`.

Environment: Linux 6.12.94+, OpenJDK 21.0.10, Gradle 9.3.1, GCC/G++ 13.3.0,
and CMake 3.28.3. The JDK value describes only the test environment; it is not
a compatibility claim.

## (a)(b)(c)(d) / （a）（b）（c）（d）

- **(a) Scope / 范围:** First explicit, default-off in-process interpreter
  lowering on current master, limited to the documented integer opcode stream
  and per-method fallback. /
  当前 master 上首个显式选择、默认关闭的进程内解释器 lowering；范围仅限本文
  记录的整数操作码流，并支持逐方法回退。
- **(b) Ship-ready? / 可直接发布？:** **No / 否。** This is a narrow first
  increment and does not complete the production goal. /
  这是首个窄范围增量，尚未完成生产目标。
- **(c) Default-off compatibility preserved? / 是否保持默认关闭兼容性？:**
  **Yes / 是。** The two complete-tree comparisons exited 0, and unsupported
  methods retained the active codegen path. /
  两次完整目录比较均以 0 退出，且不支持的方法仍使用当前 codegen 路径。
- **(d) Integration evidence / 集成证据:** The 96-test focused suite and the
  9-test interpreter suite passed with GCC/G++; the dispatcher compiled and
  executed under C++17; the generated shared library built successfully; and
  omitted `--backend` matched current master. Requirement 7 is not claimed, and
  no such evaluation was run. /
  使用 GCC/G++ 的 96 项聚焦测试与 9 项解释器测试均通过；调度器以 C++17
  编译并执行；生成的共享库构建成功；省略 `--backend` 的输出与当前 master
  一致。不声明满足要求 7，也未运行该类评估。
