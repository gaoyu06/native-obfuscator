# Interpreter ISA i64 — Sol independent review

Reviewed draft [PR #140](https://github.com/gaoyu06/native-obfuscator/pull/140)
at `213c1eec79201067dc6f5da5c7225ae66170427d`, against
`origin/master` at `a7e5453`.

## Verdict

**Accept.**

No blocking compiler-correctness defect or review nit was found. The increment
is confined to the explicit, default-off interpreter backend, preserves the
existing per-method fallback boundary, and leaves the CLI and API defaults
unchanged. No compiler source was changed by this review.

## Correctness findings

1. **Scope and defaults.** The implementation-bearing diff is limited to the
   interpreter Java emitter/processor, the interpreter C++ runtime, and their
   tests. It does not modify the IR frontend/emitter, `IrNodes`,
   `native_jvm_eval.*`, `Main`, `CompilerBackend`, or `NativeObfuscator`.
   Existing convenience calls still select `CompilerBackend.CPP`, while the
   CLI tests confirm that an omitted `--backend` selects `cpp`.
2. **Opcode agreement.** Java and C++ both use ISA version 3. All 16 appended
   opcodes agree exactly between `InterpreterMethodEmitter` and
   `native_jvm_interp.hpp`: `LPUSH=30`, `LLOAD=31`, `LSTORE=32`, `LADD=33`,
   `LSUB=34`, `LMUL=35`, `LAND=36`, `LOR=37`, `LXOR=38`, `LSHL=39`,
   `LSHR=40`, `LUSHR=41`, `LNEG=42`, `LRETURN=43`, `LDIV=44`, and
   `LREM=45`. `native_jvm_interp.cpp` dispatches every one.
3. **Two-slot values.** A long occupies two consecutive 32-bit slots in both
   locals and the operand stack. `LLOAD` and `LSTORE` preserve the bytecode
   local index, copy `[index, index + 1]`, and reject a pair that would cross
   `max_locals`. JNI arguments advance by `Type.getSize()`, so a long following
   another long begins at local slot 2.
4. **Arithmetic and shifts.** Long add, subtract, multiply, bitwise operations,
   left shift, and negate use `uint64_t` carriers, giving defined JVM
   wraparound. Shift distance is read from one i32 slot and masked with
   `0x3fU`; `LSHR` explicitly sign-fills while `LUSHR` shifts the unsigned
   carrier. Unsigned subtraction in `LNEG` leaves `Long.MIN_VALUE` unchanged.
5. **Division and remainder.** `LDIV` and `LREM` test the signed divisor before
   evaluating C++ division or remainder. Zero returns `arithmetic_exception`;
   the generated JNI trampoline calls `utils::throw_re` for
   `java/lang/ArithmeticException` and returns while that exception is pending.
   The `Long.MIN_VALUE / -1` and `% -1` cases return `Long.MIN_VALUE` and zero
   without evaluating the undefined C++ expressions.
6. **Fallback.** Eligibility is decided before method mutation. An unsupported
   instruction still makes `tryCompile` return null, after which the unchanged
   `NativeObfuscator` dispatch selects the active IR or legacy compiler path.

## Independent verification

The following focused command was rerun with GCC/G++, forced task execution,
and plain Gradle output:

```text
CC=gcc CXX=g++ ./gradlew :obfuscator:test --rerun-tasks --console=plain \
  --tests by.radioegor146.CodegenModeTest \
  --tests by.radioegor146.MainBackendOptionTest \
  --tests 'by.radioegor146.interpreter.*'
```

Result: `BUILD SUCCESSFUL`; **23 tests, 0 skipped, 0 failures, 0 errors**.

```text
InterpreterMethodEmitterTest: tests=11, skipped=0, failures=0, errors=0
InterpreterRuntimeTest: tests=1, skipped=0, failures=0, errors=0
InterpreterBackendIntegrationTest: tests=2, skipped=0, failures=0, errors=0
MainBackendOptionTest: tests=2, skipped=0, failures=0, errors=0
CodegenModeTest: tests=7, skipped=0, failures=0, errors=0
```

Thus the interpreter/backend suite accounts for 16/16 passing tests, with the
requested codegen regression contributing another 7/7. The runtime test did
not skip: it compiled the dispatcher with
`g++ -std=c++17 -Wall -Wextra -Werror` and executed the i64 operations and
edge cases.

A fresh `DefaultOffFixture` JAR was processed by detached `origin/master`, by
this branch with `--backend` omitted, and by this branch with
`--backend=cpp`. Both complete-tree comparisons exited 0 with no output:

```text
diff -r master/cpp branch-default/cpp
# exit 0
diff -r branch-default/cpp branch-cpp/cpp
# exit 0
```

Finally, the generated integration project containing six i64 interpreter
methods was configured and built with GCC/G++ 13.3.0. CMake compiled both the
generated JNI source and `native_jvm_interp.cpp`, linked
`libnative_library.so`, and reported `[100%] Built target native_library`.

## 中文摘要

**结论：接受。** 未发现阻塞性的编译器正确性缺陷或审查小问题。Java 与 C++ 的
16 个新增 i64 操作码编号完全一致；long 在局部变量和操作数栈中均使用连续两个
32 位槽；移位计数来自 i32 并使用 `& 63`；`LSHR` 与 `LUSHR` 语义区分正确；
`LDIV`/`LREM` 在执行 C++ 除法或取余前处理零除数和
`Long.MIN_VALUE / -1`；`LNEG` 对 `Long.MIN_VALUE` 正确回绕。不支持的方法
仍逐方法回退到当前 IR 或 legacy compiler path，默认后端仍为 `cpp`。

独立复跑共 **23/23** 项测试通过，其中 interpreter/backend suite 为 16 项，
`CodegenModeTest` 为 7 项；全部 0 skipped、0 failures、0 errors。两次完整
`cpp/` 目录比较均以 0 退出，生成的 i64 interpreter 共享库也成功编译和链接。
本次审查未修改编译器源码。
