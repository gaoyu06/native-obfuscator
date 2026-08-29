# In-process interpreter ISA i64 increment: status

Status recorded on 2026-08-29 from `origin/master` at `a7e5453`.

## Implemented increment

- The explicit, default-off `--backend=interpreter` stream is now ISA v3.
  Existing opcode numbers remain stable. The appended opcodes are `LPUSH` (30),
  `LLOAD` (31), `LSTORE` (32), `LADD` (33), `LSUB` (34), `LMUL` (35),
  `LAND` (36), `LOR` (37), `LXOR` (38), `LSHL` (39), `LSHR` (40),
  `LUSHR` (41), `LNEG` (42), `LRETURN` (43), `LDIV` (44), and `LREM` (45).
- Long values occupy two consecutive 32-bit JVM slots in interpreter locals
  and on the interpreter stack. JNI long arguments are written at descriptor
  slot offsets, and long-returning methods use the typed `execute_j` entry.
- Add, subtract, multiply, bitwise operations, left shift, and negate operate
  on unsigned 64-bit carriers. The result is reinterpreted as signed only at
  the `jlong` boundary, so JVM wraparound does not depend on signed C++
  overflow. Shift counts come from an i32 slot and are masked with `0x3f`.
  Arithmetic and logical right shifts preserve JVM `LSHR` and `LUSHR`
  behavior.
- `LDIV` and `LREM` return the existing arithmetic-exception executor status
  for a zero divisor. The JNI trampoline throws
  `java/lang/ArithmeticException` and exits with the JNI zero value.
  `Long.MIN_VALUE / -1` returns `Long.MIN_VALUE`, and the corresponding
  remainder is zero, without evaluating undefined signed C++ operations.
- Eligibility widens only from `int` to `int`/`long` method descriptors and
  the listed opcodes. Unsupported instructions still reject the method before
  mutation and fall through to the selected IR or legacy codegen.
  `--backend` remains `cpp` by default and `--codegen` remains `legacy`.

## Verification

Focused interpreter suite:

```text
CC=gcc CXX=g++ ./gradlew :obfuscator:test --rerun-tasks \
  --tests by.radioegor146.MainBackendOptionTest \
  --tests by.radioegor146.interpreter.InterpreterMethodEmitterTest \
  --tests by.radioegor146.interpreter.InterpreterRuntimeTest \
  --tests by.radioegor146.interpreter.InterpreterBackendIntegrationTest
```

Result: `BUILD SUCCESSFUL`; 16 tests, 0 skipped, 0 failures, and 0 errors
(2 option tests, 11 emitter tests, 1 C++17 runtime test, and 2 integration
tests). The C++ runtime harness compiles with
`g++ -std=c++17 -Wall -Wextra -Werror` and executes wraparound, two-slot,
constant, store, signed/unsigned shift, negate, divide, remainder, divide by
zero, and `MIN_VALUE / -1` cases.

IR and codegen regression suite:

```text
CC=gcc CXX=g++ ./gradlew :obfuscator:test --rerun-tasks \
  --tests by.radioegor146.ir.IrCompilerTest \
  --tests by.radioegor146.CodegenModeTest
```

Result: `BUILD SUCCESSFUL`; 104 tests, 0 skipped, 0 failures, and 0 errors
(`IrCompilerTest` 97 and `CodegenModeTest` 7).

The interpreter integration fixture generated six i64 interpreter methods.
Its complete generated CMake project configured with GCC/G++ 13.3.0 and built
successfully, including `InterpreterFixture_0.cpp`,
`native_jvm_interp.cpp`, and `[100%] Built target native_library`.

## Default-off generated-tree proof

The same `DefaultOffFixture` JAR was processed by detached `origin/master`,
this branch with no backend option, and this branch with
`--backend=cpp`. Both complete-tree comparisons exited 0 with no output:

```text
diff -r /tmp/interpreter-isa-i64-proof/master/cpp \
  /tmp/interpreter-isa-i64-proof/branch-default/cpp
# exit 0

diff -r /tmp/interpreter-isa-i64-proof/branch-default/cpp \
  /tmp/interpreter-isa-i64-proof/branch-cpp/cpp
# exit 0
```

Thus omitting `--backend` produces the same generated `cpp/` tree as
`origin/master` and explicit `--backend=cpp`; interpreter sources and behavior
remain absent when the flag is off.

## (a)(b)(c)(d) / （a）（b）（c）（d）

- **(a) Scope / 范围:** Add the first i64 opcode increment to the explicit
  in-process interpreter, including constants, two-slot locals/stack values,
  arithmetic, bitwise operations, shifts, negate, return, divide, and
  remainder. /
  为显式启用的进程内解释器加入首个 i64 操作码增量，包括常量、双槽局部变量与
  操作数栈、算术、位运算、移位、取负、返回、除法和取余。
- **(b) Ship-ready? / 可直接发布？:** **No / 否。** This remains a narrow,
  default-off compiler-backend slice and does not complete the production
  interpreter goal. /
  这仍是一个范围有限且默认关闭的编译器后端增量，尚未完成生产级解释器目标。
- **(c) Default-off compatibility preserved? / 是否保持默认关闭兼容性？:**
  **Yes / 是。** Both complete `diff -r` comparisons exited 0, and unsupported
  methods retain per-method fallback to the active codegen. /
  两次完整的 `diff -r` 比较均以 0 退出，不支持的方法仍逐方法回退到当前
  codegen。
- **(d) Integration evidence / 集成证据:** The 16-test interpreter suite and
  104-test IR/codegen regression suite passed; the C++17 dispatcher compiled
  and executed; the generated i64 interpreter shared library built; and the
  no-flag output matched master and explicit C++. /
  16 项解释器测试和 104 项 IR/codegen 回归测试均通过；C++17 调度器已编译并
  执行；生成的 i64 解释器共享库构建成功；未指定后端的输出与 master 及显式
  C++ 后端一致。
