# In-process interpreter ISA widening: status

Status recorded on 2026-08-29 from
`origin/cursor/interpreter-on-master-6d81-a8c4` at `e1996b2`.

## Implemented increment

- The interpreter stream is now ISA v2. Existing opcode numbers remain stable;
  `IMUL`, `IAND`, `IOR`, `IXOR`, `ISHL`, `ISHR`, `IUSHR`, `INEG`, `IDIV`, and
  `IREM` are appended as one-byte opcodes.
- Integer add, subtract, multiply, and negate use unsigned intermediates so
  32-bit JVM wraparound does not rely on signed C++ overflow. Shift counts are
  masked with `0x1f`; arithmetic right shift is implemented explicitly rather
  than relying on implementation-defined signed right shift, and unsigned
  right shift operates on `uint32_t`.
- `IDIV` and `IREM` handle `Integer.MIN_VALUE / -1` and
  `Integer.MIN_VALUE % -1` explicitly. A zero divisor returns a distinct
  `arithmetic_exception` executor status. The generated JNI trampoline maps
  that status to a pending `java/lang/ArithmeticException` and returns the JNI
  zero value.
- Generated method side tables embed ISA version 2. The shared-library runtime
  rejects any side table whose version does not equal its own `ISA_VERSION`.
  The runtime test includes an ISA-v1 negative case.
- Eligibility remains unchanged. Any unsupported instruction still causes
  per-method fallback before mutation; the permanent and synthetic fixtures
  use `I2L`/`L2I` as an explicit unsupported conversion case.
- `--backend` still defaults to `cpp`, and `--codegen` still defaults to
  `legacy`. Constructor restore and classfile-version handling were not
  changed.

## Verification

Required IR and codegen regression command:

```text
CC=gcc CXX=g++ ./gradlew :obfuscator:test \
  --tests by.radioegor146.ir.IrCompilerTest \
  --tests by.radioegor146.CodegenModeTest
```

Result: exit 0, `BUILD SUCCESSFUL`.

Required interpreter command:

```text
CC=gcc CXX=g++ ./gradlew :obfuscator:test \
  --tests by.radioegor146.MainBackendOptionTest \
  --tests by.radioegor146.interpreter.InterpreterMethodEmitterTest \
  --tests by.radioegor146.interpreter.InterpreterRuntimeTest \
  --tests by.radioegor146.interpreter.InterpreterBackendIntegrationTest
```

Result: exit 0, `BUILD SUCCESSFUL`; 13 tests, 0 skipped, 0 failures, and
0 errors. Coverage includes exact opcode-stream goldens for multiply, bitwise,
and shift methods; every newly added opcode in the C++17 executor; shift-mask
and overflow edges; division and remainder zero status; ISA-version mismatch
rejection; generated JNI exception mapping; and explicit fallback to both
active codegen modes.

The generated interpreter project was also configured and compiled:

```text
CC=gcc CXX=g++ cmake \
  -S /tmp/interpreter-isa-widen-parity/interpreter/cpp \
  -B /tmp/interpreter-isa-widen-parity/native-build \
  -DCMAKE_C_COMPILER=gcc -DCMAKE_CXX_COMPILER=g++
CC=gcc CXX=g++ cmake --build \
  /tmp/interpreter-isa-widen-parity/native-build --parallel 2
```

Result: exit 0, including compilation of `native_jvm_interp.cpp` and
`[100%] Built target native_library`.

## Default-off generated-tree proof

The permanent `DefaultOffFixture` now includes multiply, bitwise, and all three
integer shift forms, plus the unsupported conversion fallback. A detached
worktree at current `origin/master` (`af02503`) and this branch generated that
same fixture with no backend option; this branch also generated it with
explicit `--backend=cpp`.

```text
diff -r /tmp/interpreter-isa-widen-parity/master/cpp \
  /tmp/interpreter-isa-widen-parity/branch-default/cpp
# exit 0, no output

diff -r /tmp/interpreter-isa-widen-parity/branch-default/cpp \
  /tmp/interpreter-isa-widen-parity/branch-cpp/cpp
# exit 0, no output
```

Thus the complete default generated `cpp/` tree matched both current
`origin/master` and explicit `--backend=cpp`.

## (a)(b)(c)(d) / （a）（b）（c）（d）

- **(a) Scope / 范围:** Widen the default-off in-process interpreter's integer
  opcode stream with multiply, bitwise, shifts, negate, divide, and remainder,
  while retaining a method side table, JNI trampoline, shared-library runtime,
  and per-method fallback. /
  扩展默认关闭的进程内解释器整数操作码流，加入乘法、位运算、移位、取负、除法和
  取余；继续使用方法侧表、JNI 跳板和共享库运行时，并保留逐方法回退。
- **(b) Ship-ready? / 可直接发布？:** **No / 否。** This increment widens one
  integer slice only and does not complete the production goal. /
  本增量仅扩展一个整数指令子集，尚未完成生产目标。
- **(c) Default-off compatibility preserved? / 是否保持默认关闭兼容性？:**
  **Yes / 是。** Both complete-tree comparisons exited 0. Unsupported
  conversion still falls back to the active codegen, and defaults were not
  changed. /
  两次完整目录比较均以 0 退出。不支持的转换仍回退到当前 codegen，默认选项未
  改变。
- **(d) Integration evidence / 集成证据:** Both required Gradle commands
  passed with GCC/G++; the 13-test interpreter suite exercised the widened
  executor and rejection paths; the generated shared library compiled; and
  omitted `--backend` matched master and explicit C++. Requirement 7 is not
  claimed, and no such evaluation was run. /
  两条必需的 Gradle 命令均在 GCC/G++ 环境下通过；13 项解释器测试覆盖了扩展后
  的执行器与拒绝路径；生成的共享库编译成功；省略 `--backend` 的结果与 master
  及显式 C++ 后端一致。不声明满足要求 7，也未运行该类评估。
