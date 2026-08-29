# Fable review: in-process interpreter on master (PR #124)

Independent reviewer: Fable. Reviewed tip
`cursor/interpreter-on-master-6d81-a8c4` at `e1996b2`
("docs: add shared library build evidence"), stacked conceptually on the
interpreter tip. Base for comparison: `origin/master` at `e997d71`.

## Verdict / 结论

**Accept with nits (docs only). / 接受（附带小问题，仅文档）。**

The change ports a first default-off `--backend=interpreter` onto post-#118
master. `--backend` defaults to `cpp` and `--codegen` still defaults to
`legacy`. Eligibility is a deliberately narrow static `int` slice (ISA v1:
`IPUSH`, `ILOAD`/`ISTORE`, `IADD`/`ISUB`, integer compares/branches, `GOTO`,
`IRETURN`, with `IINC` expanded to those opcodes). I found no blocking
correctness bug, so this is a documentation-only note; I did not modify code
and I did not flip any default.

This is a narrow first increment. It does **not** complete the production
goal, and requirement 7 (reader resistance) is **not** claimed here — it was
not run.

## What I re-ran / 我重新运行的内容

All commands run with `CC=gcc CXX=g++`. Environment: Linux 6.12.94+,
OpenJDK 21.0.10, Gradle 9.3.1, GCC/G++ 13.3.0, CMake 3.28.3. The JDK figure
describes only the test host; it is **not** a compatibility claim, and JDK 17
support is **not** claimed.

### Focused current-master suite

```text
CC=gcc CXX=g++ ./gradlew :obfuscator:test \
  --tests by.radioegor146.ir.IrCompilerTest \
  --tests by.radioegor146.CodegenModeTest
```

`BUILD SUCCESSFUL`. From the JUnit XML: `IrCompilerTest` 91 tests and
`CodegenModeTest` 5 tests, for **96/96** with 0 skipped, 0 failures, 0 errors.

### Interpreter unit + integration suite

```text
CC=gcc CXX=g++ ./gradlew :obfuscator:test \
  --tests by.radioegor146.MainBackendOptionTest \
  --tests by.radioegor146.interpreter.InterpreterMethodEmitterTest \
  --tests by.radioegor146.interpreter.InterpreterRuntimeTest \
  --tests by.radioegor146.interpreter.InterpreterBackendIntegrationTest
```

`BUILD SUCCESSFUL`. From the JUnit XML: `MainBackendOptionTest` 2,
`InterpreterMethodEmitterTest` 4, `InterpreterRuntimeTest` 1,
`InterpreterBackendIntegrationTest` 2, for **9/9** with 0 skipped, 0 failures,
0 errors. `InterpreterRuntimeTest` reported 0 skipped, so its
`g++ -std=c++17 -Wall -Wextra -Werror` compile and execution of the dispatcher
runtime really ran rather than being assumed away.

### Default-off `diff -r` proof (recreated)

I recreated the whole-tree comparison against a detached `origin/master`
worktree for the checked-in fixture
`obfuscator/src/test/resources/interpreter/DefaultOffFixture.java`
(`add`, `sumTo`, and the unsupported `multiply`):

```text
git worktree add --detach /tmp/nom-master-proof origin/master
# built obfuscator.jar in both trees, then compiled the fixture with
# javac --release 8 into /tmp/nom-proof/fixture.jar
java -jar <master>/obfuscator.jar  fixture.jar /tmp/nom-proof/master
java -jar <branch>/obfuscator.jar  fixture.jar /tmp/nom-proof/branch-no-flag
java -jar <branch>/obfuscator.jar  fixture.jar /tmp/nom-proof/branch-cpp --backend=cpp
diff -r /tmp/nom-proof/master/cpp        /tmp/nom-proof/branch-no-flag/cpp   # exit 0
diff -r /tmp/nom-proof/branch-no-flag/cpp /tmp/nom-proof/branch-cpp/cpp      # exit 0
```

Both `diff -r` invocations exited **0** with no output. With `--backend`
omitted, the complete generated `cpp/` tree matched current `origin/master`
and also matched explicit `--backend=cpp`.

### Interpreter backend generation + shared-library build (independent check)

```text
java -jar <branch>/obfuscator.jar fixture.jar /tmp/nom-proof/interp --backend=interpreter
```

The interpreter tree emitted `native_jvm_interp.cpp` / `.hpp`, and the
generated `DefaultOffFixture_0.cpp` contained two
`native_jvm::interp::execute_i(` calls (the eligible `add` and `sumTo`), with
the unsupported `multiply` left on the active legacy codegen. Configuring and
building that tree with CMake using GCC/G++ (JNI headers from the host JDK)
produced `[100%] Built target native_library` and linked
`libnative_library.so`, including the `native_jvm_interp.cpp` translation
unit.

## Focus-area findings / 重点审查发现

- **`NativeObfuscator.java` dispatch.** For `--backend=interpreter`, each
  method is offered to `InterpreterMethodEmitter.tryCompile`. On success the
  `InterpreterMethodProcessor` emits the opcode stream, method side table, and
  JNI trampoline; on `null` the method falls through to the active codegen
  (IR when `--codegen=ir`, otherwise legacy). When `--backend` is omitted the
  backend is `CPP`, `tryCompile` is never called, and the pre-existing
  IR/legacy paths are byte-for-byte unchanged (confirmed by the `diff -r`
  above).
- **Constructor restore.** The emitter rejects any `method.name` starting with
  `<` before it mutates anything, so `<init>`/`<clinit>` never reach the
  interpreter. The existing IR constructor-restore path
  (`readOriginalMethod` on `UnsupportedIrConstructException` for `<init>`) is
  untouched and remains the sole owner of rejected constructors.
- **Classfile version preservation.** The floor-to-`V1_8`-never-downgrade
  block is unchanged from master; the interpreter path does not touch
  `classNode.version`.
- **Ineligible-method fallback.** Verified both by
  `InterpreterBackendIntegrationTest` (unsupported `multiply` uses legacy under
  the default codegen and IR under `--codegen=ir`) and by my regenerated tree
  (two `execute_i` calls, `multiply` on legacy).
- **Runtime safety of the dispatcher.** `execute_i` is bounds-checked
  throughout: `read_u16`/`read_u32` guard against `pc` overrun (including the
  unsigned-underflow guard `pc > code_len` before `code_len - pc < N`), pushes
  check `sp >= max_stack`, pops check `sp`, and branch targets are validated
  against `code_len` at runtime in addition to being validated against the
  computed code length at emit time. An admitted method that somehow ran off
  the end returns `false`, which the trampoline converts into
  `env->FatalError`. I did not find a memory-safety or arithmetic-correctness
  defect in the admitted ISA v1 slice.

## Nits (non-blocking) / 小问题（不阻塞）

1. **Fatal-on-invalid-stream is abrupt.** The trampoline calls
   `env->FatalError` when `execute_i` returns `false`. For the current
   admitted slice (which always `IRETURN`s) this branch is unreachable, but a
   future, wider ISA might benefit from a less terminal failure mode. Worth a
   note as the ISA grows.
2. **Fall-through cannot terminate a valid admitted method today**, yet the
   dispatcher's end-of-code `return false` is only implicitly covered by the
   admission rules rather than by a direct negative test. A small unit test
   feeding a truncated/return-less stream would pin the contract.
3. **ISA version is checked but not negatively tested.** `execute_i` rejects a
   mismatched `isa_version`, which is good forward-compat hygiene; there is no
   test exercising that rejection path.
4. **Doc-vs-tip provenance.** `docs/architecture/interpreter-on-master-status.md`
   records the base as `origin/master` `e997d71`; the reviewed tip is
   `e1996b2`. That is expected (the status doc describes the landed scope on
   master), but a one-line pointer to the PR tip would remove ambiguity.

None of these block acceptance.

## (a)(b)(c)(d) / （a）（b）（c）（d）

- **(a) Scope / 范围:** First explicit, default-off in-process interpreter
  backend on post-#118 master: `--backend=cpp|interpreter` (default `cpp`),
  ISA v1 opcode stream + method side table + JNI trampoline + shared C++17
  `switch` dispatcher, with per-method fallback to the active `legacy`/IR
  codegen. /
  post-#118 master 上首个显式、默认关闭的进程内解释器后端：新增
  `--backend=cpp|interpreter`（默认 `cpp`），ISA v1 操作码流、方法侧表、JNI
  跳板与共享 C++17 `switch` 调度器，并逐方法回退到当前 `legacy`/IR codegen。
- **(b) Ship-ready? / 可直接发布？:** **No / 否。** A narrow first increment
  that does not complete the production goal. /
  这是首个窄范围增量，尚未完成生产目标。
- **(c) Default-off compatibility preserved? / 是否保持默认关闭兼容性？:**
  **Yes / 是。** With `--backend` omitted, the complete generated `cpp/` tree
  matched current `origin/master` and explicit `--backend=cpp` (both
  `diff -r` exited 0); constructor restore and classfile-version logic are
  unchanged. /
  省略 `--backend` 时，完整生成的 `cpp/` 目录与当前 `origin/master` 及显式
  `--backend=cpp` 一致（两次 `diff -r` 均以 0 退出）；构造器恢复与类文件版本
  逻辑均未改动。
- **(d) Integration evidence / 集成证据:** Re-ran the focused suite 96/96 and
  the interpreter suite 9/9 (with the real GCC/G++ C++17 dispatcher compile and
  execution actually run, 0 skipped); recreated both default-off `diff -r`
  proofs (exit 0, no output); and independently built the interpreter-backend
  shared library with GCC/G++ (`[100%] Built target native_library`).
  Requirement 7 is not claimed and was not run; JDK 17 support is not claimed. /
  重跑聚焦测试 96/96 与解释器测试 9/9（GCC/G++ 的 C++17 调度器编译与执行真实
  运行，0 跳过）；重建两次默认关闭的 `diff -r` 证据（退出 0、无输出）；并使用
  GCC/G++ 独立构建解释器后端共享库（`[100%] Built target native_library`）。
  不声明满足要求 7，也未运行；不声明支持 JDK 17。
