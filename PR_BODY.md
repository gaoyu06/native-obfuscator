## Summary / 摘要

This widens the default-off in-process interpreter from
[PR #124](https://github.com/gaoyu06/native-obfuscator/pull/124) with a real
integer arithmetic/logic increment. ISA v2 adds multiply, bitwise operations,
all int shifts, negate, divide, and remainder while preserving per-method
fallback and the existing defaults.

本变更扩展 PR #124 中默认关闭的进程内解释器，加入完整的整数算术与逻辑增量。
ISA v2 新增乘法、位运算、全部 int 移位、取负、除法和取余，同时保留逐方法回退
和现有默认选项。

## (a)(b)(c)(d) / （a）（b）（c）（d）

- **(a) Scope / 范围:** Add ISA-v2 `IMUL`, `IAND`, `IOR`, `IXOR`, `ISHL`,
  `ISHR`, `IUSHR`, `INEG`, `IDIV`, and `IREM` to the opcode stream and shared
  C++17 executor. Zero divisors become pending `ArithmeticException`s through
  the JNI trampoline; unsupported operations still fall back per method. /
  在操作码流和共享 C++17 执行器中加入 ISA-v2 `IMUL`、`IAND`、`IOR`、`IXOR`、
  `ISHL`、`ISHR`、`IUSHR`、`INEG`、`IDIV` 和 `IREM`。除数为零时通过 JNI
  跳板产生待处理的 `ArithmeticException`；不支持的操作仍逐方法回退。
- **(b) Ship-ready? / 可直接发布？:** **No / 否。** This widens only the
  integer slice and does not complete the production goal. /
  本变更仅扩展整数指令子集，尚未完成生产目标。
- **(c) Default-off compatibility preserved? / 是否保持默认关闭兼容性？:**
  **Yes / 是。** The complete generated `cpp/` tree with no backend flag
  matched current `origin/master` and explicit `--backend=cpp`. Constructor
  restore and classfile-version handling were not changed. /
  未提供后端选项时，完整生成的 `cpp/` 目录与当前 `origin/master` 及显式
  `--backend=cpp` 一致。构造器恢复和类文件版本处理均未修改。
- **(d) Integration evidence / 集成证据:** Both required Gradle commands
  passed; the 13-test interpreter suite covers exact multiply/bitwise/shift
  streams, arithmetic edges, ISA mismatch rejection, JNI exception emission,
  and `I2L` fallback. The generated shared library compiled, and both
  default-off diffs exited 0. Requirement 7 is not claimed. /
  两条必需的 Gradle 命令均通过；13 项解释器测试覆盖精确的乘法、位运算和移位
  操作码流、算术边界、ISA 版本不匹配拒绝、JNI 异常生成及 `I2L` 回退。生成的
  共享库编译成功，两次默认关闭比较均以 0 退出。不声明满足要求 7。

## Verification / 验证

```text
CC=gcc CXX=g++ ./gradlew :obfuscator:test \
  --tests by.radioegor146.ir.IrCompilerTest \
  --tests by.radioegor146.CodegenModeTest
# exit 0; BUILD SUCCESSFUL

CC=gcc CXX=g++ ./gradlew :obfuscator:test \
  --tests by.radioegor146.MainBackendOptionTest \
  --tests by.radioegor146.interpreter.InterpreterMethodEmitterTest \
  --tests by.radioegor146.interpreter.InterpreterRuntimeTest \
  --tests by.radioegor146.interpreter.InterpreterBackendIntegrationTest
# exit 0; BUILD SUCCESSFUL; 13 tests, 0 skipped/failures/errors

diff -r /tmp/interpreter-isa-widen-parity/master/cpp \
  /tmp/interpreter-isa-widen-parity/branch-default/cpp
# exit 0, no output

diff -r /tmp/interpreter-isa-widen-parity/branch-default/cpp \
  /tmp/interpreter-isa-widen-parity/branch-cpp/cpp
# exit 0, no output

CC=gcc CXX=g++ cmake \
  -S /tmp/interpreter-isa-widen-parity/interpreter/cpp \
  -B /tmp/interpreter-isa-widen-parity/native-build \
  -DCMAKE_C_COMPILER=gcc -DCMAKE_CXX_COMPILER=g++
CC=gcc CXX=g++ cmake --build \
  /tmp/interpreter-isa-widen-parity/native-build --parallel 2
# [100%] Built target native_library
```

Detailed status / 详细状态:
[`docs/architecture/interpreter-isa-widen-status.md`](docs/architecture/interpreter-isa-widen-status.md).
