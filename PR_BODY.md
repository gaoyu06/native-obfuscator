## Summary / 摘要

This ports the first default-off in-process interpreter backend onto current
`origin/master` (`e997d71`) without merging the sibling implementation stack.
The existing `legacy` default and opt-in `--codegen=ir` path remain unchanged
when `--backend` is omitted.

本变更在当前 `origin/master`（`e997d71`）上重新实现首个默认关闭的进程内解释器
后端，未合并同级实现分支。省略 `--backend` 时，现有的 `legacy` 默认值和显式
`--codegen=ir` 路径保持不变。

## (a)(b)(c)(d) / （a）（b）（c）（d）

- **(a) Scope / 范围:** Add `--backend=cpp|interpreter` with `cpp` as the
  initialized default. Eligible static integer methods lower to an ISA-v1
  opcode stream, method side table, JNI trampoline, and the shared C++17
  `switch` dispatcher. Unsupported methods fall back per method to the active
  `legacy` or IR codegen. /
  新增 `--backend=cpp|interpreter`，并将 `cpp` 明确初始化为默认值。符合条件的
  静态整数方法 lowering 为 ISA-v1 操作码流、方法侧表、JNI 跳板以及共享的
  C++17 `switch` 调度器；不支持的方法逐方法回退到当前 `legacy` 或 IR codegen。
- **(b) Ship-ready? / 可直接发布？:** **No / 否。** This is a narrow first
  increment and does not complete the production goal. /
  这是首个窄范围增量，尚未完成生产目标。
- **(c) Default-off compatibility preserved? / 是否保持默认关闭兼容性？:**
  **Yes / 是。** With no backend flag, the complete generated `cpp/` tree
  matched current `origin/master`; it also matched explicit `--backend=cpp`.
  The classfile version logic, existing IR runtime repair, and constructor
  restore path were not changed. /
  未提供后端选项时，完整生成的 `cpp/` 目录与当前 `origin/master` 一致，也与
  显式 `--backend=cpp` 一致。类文件版本逻辑、现有 IR runtime repair 以及
  构造器恢复路径均未修改。
- **(d) Integration evidence / 集成证据:** The required focused suite passed
  96/96; the interpreter suite passed 9/9 and includes opcode-stream goldens,
  explicit fallback, generated-tree integration, and a real GCC/G++ C++17
  dispatcher compile and execution. The generated shared library built
  successfully. Both default-off `diff -r` commands exited 0 with no output.
  Requirement 7 is not claimed, and no such evaluation was run. /
  必需的聚焦测试 96/96 通过；解释器测试 9/9 通过，覆盖操作码流 golden、
  显式回退、生成目录集成，以及使用 GCC/G++ 对 C++17 调度器的真实编译与执行。
  生成的共享库构建成功。两次默认关闭的 `diff -r` 均以 0 退出且无输出。
  不声明满足要求 7，也未运行该类评估。

## Verification / 验证

```text
CC=gcc CXX=g++ ./gradlew :obfuscator:test \
  --tests by.radioegor146.ir.IrCompilerTest \
  --tests by.radioegor146.CodegenModeTest
# BUILD SUCCESSFUL; 96 tests, 0 skipped/failures/errors

CC=gcc CXX=g++ ./gradlew :obfuscator:test \
  --tests by.radioegor146.MainBackendOptionTest \
  --tests by.radioegor146.interpreter.InterpreterMethodEmitterTest \
  --tests by.radioegor146.interpreter.InterpreterRuntimeTest \
  --tests by.radioegor146.interpreter.InterpreterBackendIntegrationTest
# BUILD SUCCESSFUL; 9 tests, 0 skipped/failures/errors

diff -r /tmp/interpreter-on-master-default-proof-2/master/cpp \
  /tmp/interpreter-on-master-default-proof-2/branch-no-flag/cpp
# exit 0, no output

diff -r /tmp/interpreter-on-master-default-proof-2/branch-no-flag/cpp \
  /tmp/interpreter-on-master-default-proof-2/branch-explicit-cpp/cpp
# exit 0, no output

CC=gcc CXX=g++ cmake \
  -S /tmp/interpreter-on-master-shared-library/cpp \
  -B /tmp/interpreter-on-master-shared-library/native-build \
  -DCMAKE_C_COMPILER=gcc -DCMAKE_CXX_COMPILER=g++
CC=gcc CXX=g++ cmake --build \
  /tmp/interpreter-on-master-shared-library/native-build --parallel 2
# [100%] Built target native_library
```

Environment / 环境: Linux 6.12.94+, OpenJDK 21.0.10, Gradle 9.3.1,
GCC/G++ 13.3.0, CMake 3.28.3. This identifies the test environment only and
does not make a compatibility claim. /
以上仅标识测试环境，不构成兼容性声明。
