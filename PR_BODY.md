## Summary / 摘要

Independent Fable review of the in-process interpreter tip
(`cursor/interpreter-on-master-6d81-a8c4` at `e1996b2`, PR #124), stacked
conceptually on that interpreter tip. This adds a docs-only accept-with-nits
note at `docs/reviews/interpreter-on-master-fable.md`. No code was changed and
no default was flipped. The reviewed change ports a first default-off
`--backend=interpreter` onto post-#118 master; `--backend` defaults to `cpp`
and `--codegen` still defaults to `legacy`.

对进程内解释器分支（`cursor/interpreter-on-master-6d81-a8c4`，`e1996b2`，即
PR #124）的独立 Fable 评审，概念上叠加在该解释器分支之上。本变更仅新增文档评审
`docs/reviews/interpreter-on-master-fable.md`，未修改代码，也未更改任何默认值。
被评审改动在 post-#118 master 上引入首个默认关闭的 `--backend=interpreter`；
`--backend` 默认 `cpp`，`--codegen` 仍默认 `legacy`。

## (a)(b)(c)(d) / （a）（b）（c）（d）

- **(a) Scope / 范围:** Docs-only reviewer note recording verdict, re-run
  evidence, and nits for the first default-off interpreter backend
  (ISA v1 integer slice, per-method fallback to the active codegen). /
  仅文档的评审说明，记录结论、重跑证据与小问题，针对首个默认关闭的解释器后端
  （ISA v1 整数子集，逐方法回退到当前 codegen）。
- **(b) Ship-ready? / 可直接发布？:** **No / 否。** The reviewed change is a
  narrow first increment and does not complete the production goal. /
  被评审改动是首个窄范围增量，尚未完成生产目标。
- **(c) Default-off compatibility preserved? / 是否保持默认关闭兼容性？:**
  **Yes / 是。** With `--backend` omitted, the complete generated `cpp/` tree
  matched current `origin/master` and explicit `--backend=cpp` (both `diff -r`
  exited 0); constructor restore and classfile-version logic are unchanged. /
  省略 `--backend` 时，完整生成的 `cpp/` 目录与当前 `origin/master` 及显式
  `--backend=cpp` 一致（两次 `diff -r` 均以 0 退出）；构造器恢复与类文件版本逻辑
  未改动。
- **(d) Integration evidence / 集成证据:** Re-ran the focused suite 96/96 and
  the interpreter suite 9/9 (real GCC/G++ C++17 dispatcher compile+execution
  actually ran, 0 skipped); recreated both default-off `diff -r` proofs (exit
  0, no output); independently built the interpreter-backend shared library
  with GCC/G++ (`[100%] Built target native_library`). Requirement 7 is not
  claimed and was not run; JDK 17 support is not claimed. /
  重跑聚焦测试 96/96 与解释器测试 9/9（GCC/G++ 的 C++17 调度器编译与执行真实运行，
  0 跳过）；重建两次默认关闭的 `diff -r` 证据（退出 0、无输出）；使用 GCC/G++
  独立构建解释器后端共享库（`[100%] Built target native_library`）。不声明满足
  要求 7，也未运行；不声明支持 JDK 17。

## Verification / 验证

```text
CC=gcc CXX=g++ ./gradlew :obfuscator:test \
  --tests by.radioegor146.ir.IrCompilerTest \
  --tests by.radioegor146.CodegenModeTest
# BUILD SUCCESSFUL; IrCompilerTest 91 + CodegenModeTest 5 = 96/96, 0 skipped/failures/errors

CC=gcc CXX=g++ ./gradlew :obfuscator:test \
  --tests by.radioegor146.MainBackendOptionTest \
  --tests by.radioegor146.interpreter.InterpreterMethodEmitterTest \
  --tests by.radioegor146.interpreter.InterpreterRuntimeTest \
  --tests by.radioegor146.interpreter.InterpreterBackendIntegrationTest
# BUILD SUCCESSFUL; 9/9, 0 skipped/failures/errors (g++ runtime test ran)

diff -r <master>/cpp <branch-no-flag>/cpp      # exit 0, no output
diff -r <branch-no-flag>/cpp <branch-cpp>/cpp  # exit 0, no output

# interpreter-backend shared library (independent)
cmake -S <interp>/cpp -B build -DCMAKE_C_COMPILER=gcc -DCMAKE_CXX_COMPILER=g++
cmake --build build --parallel 2
# [100%] Built target native_library  (includes native_jvm_interp.cpp)
```

Environment / 环境: Linux 6.12.94+, OpenJDK 21.0.10, Gradle 9.3.1,
GCC/G++ 13.3.0, CMake 3.28.3. Identifies the test host only; not a
compatibility claim. / 仅标识测试主机，不构成兼容性声明。
