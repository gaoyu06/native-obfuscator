# Interpreter ISA v4 exception dispatch — Sol independent review

Reviewed [PR #150](https://github.com/gaoyu06/native-obfuscator/pull/150)
at `a13b0bb653df90519dd5ac098fba7e3998093498`, against
`origin/master` at `7eda1ecd0229a49f477525516fd58ddce9b8a7aa`.

## Verdict

**Accept.**

No blocking compiler/VM correctness defect or concrete review nit was found.
Ship-ready remains **No**. This review changes documentation only; it does not
modify interpreter or compiler code.

## Correctness findings

1. **Scope.** The implementation diff is limited to the interpreter Java
   emitter/processor, the interpreter C++ dispatcher/header, their three test
   classes, the exception-dispatch status note, and the implementation PR
   body. It does not change `NativeObfuscator`,
   `ConstructorSpecialMethodProcessor`, the IR frontend, evaluator, `Main`, or
   any CLI default.
2. **ISA agreement.** Java and C++ both retain exact-match ISA version 4.
   Values 1–51 are unchanged from `origin/master`; both append only
   `ATHROW=52`. The C++ dispatcher has the corresponding `athrow` case.
3. **Exception-table encoding and walk.** The emitter preserves classfile
   table order and records start, exclusive end, and handler byte offsets in
   the emitted opcode stream. The dispatcher checks coverage as
   `start_pc <= instruction_pc < end_pc`, accepts `nullptr` as catch-all, and
   otherwise resolves the internal catch name and applies `IsInstanceOf`.
   The first covering match wins.
4. **Handler entry and propagation.** A match resets the logical operand-stack
   depth to one, writes only the exception reference at reference-stack slot
   zero, and transfers to `handler_pc`. An unmatched exception is retained in
   `frame.pending_exception` and returns `pending_exception`. The generated
   JNI trampoline preserves an exception already pending in JNI or calls
   `Throw` with the retained reference before returning the JNI zero value.
5. **Throw and arithmetic paths.** `ATHROW` pops a reference, with null
   producing `NullPointerException`. `IDIV`, `IREM`, `LDIV`, and `LREM` detect
   zero before evaluating C++ division or remainder, create
   `ArithmeticException`, and use the same ordered dispatch. Existing
   `MIN_VALUE / -1` and `% -1` branches remain intact for both integer widths.
6. **Eligibility and mutation boundary.** `tryCompile` still rejects instance,
   `<init>`, and `<clinit>` methods before lowering. It sizes every instruction,
   including handler bodies, before returning a compiled method; unsupported
   nodes such as `NEW`, calls (including `INVOKESPECIAL`), and field operations
   therefore return `null`. `NativeObfuscator` invokes the mutating
   `processMethod` only after that preflight succeeds.
7. **Defaults and limits.** `Main.java` still declares `--backend` with
   `defaultValue = "cpp"` and initializes it to `CompilerBackend.CPP`;
   `--codegen` remains `legacy`. This review makes no broader compatibility
   claim.

## Independent verification

The requested focused command was rerun with GCC/G++, forced task execution,
and plain Gradle output:

```text
CC=gcc CXX=g++ ./gradlew :obfuscator:test --rerun-tasks --console=plain \
  --tests by.radioegor146.MainBackendOptionTest \
  --tests by.radioegor146.interpreter.InterpreterMethodEmitterTest \
  --tests by.radioegor146.interpreter.InterpreterRuntimeTest \
  --tests by.radioegor146.interpreter.InterpreterBackendIntegrationTest \
  --tests by.radioegor146.CodegenModeTest
```

Result: `BUILD SUCCESSFUL`; **29 tests, 0 skipped, 0 failures, 0 errors**.

```text
MainBackendOptionTest:                    2
InterpreterMethodEmitterTest:           17
InterpreterRuntimeTest:                  1
InterpreterBackendIntegrationTest:       2
CodegenModeTest:                          7
Total:                                   29
```

The runtime test did not skip: it compiled the dispatcher with
`g++ -std=c++17 -Wall -Wextra -Werror` and its executable completed all 61
numbered checks. The implementation branch separately records its 131-test
run, generated-library build, and two complete generated-tree comparisons;
those three broader checks were not rerun by this review.

## 中文摘要

**结论：接受。** 未发现阻塞性的编译器/虚拟机正确性缺陷或具体审查小问题。
Ship-ready 仍为 **No**。本次审查只修改文档，不修改解释器或编译器代码。

Java 与 C++ 均保持精确匹配的 ISA v4；1–51 号操作码的数值相对
`origin/master` 不变，双方仅追加 `ATHROW=52`。异常表保持 classfile 顺序，
起始、结束和处理器 PC 均为已发出操作码流中的字节偏移，覆盖范围为
`[start_pc, end_pc)`。类型化捕获使用 `IsInstanceOf`，`nullptr` 表示全捕获，
第一个覆盖且匹配的表项生效。

匹配时，逻辑操作数栈被重置为仅含一个异常引用，并转移到处理器。未匹配时，
异常引用保存在 `frame.pending_exception` 中并返回 `pending_exception`，JNI
跳板会保留已有的待处理异常，或抛出该引用。`ATHROW`、整数和长整数的零除/
零取余路径共用此分派逻辑；两种整数宽度的 `MIN_VALUE / -1` 与 `% -1` 分支
保持不变。

预检仍在方法变更前拒绝实例方法、`<init>`、`<clinit>` 以及任何不支持的指令，
并覆盖处理器主体。`NEW`、调用（包括 `INVOKESPECIAL`）和字段操作仍不在该
ISA 增量内。`--backend` 默认值仍为 `cpp`，`--codegen` 默认值仍为
`legacy`。

独立复跑 **29/29** 项测试通过，0 skipped、0 failures、0 errors；运行时测试
未跳过，并使用严格 C++17 编译参数完成 61 项编号检查。实现分支另行记录的
131 项测试、生成库构建和两次完整生成目录比较，本次审查未重复执行。
