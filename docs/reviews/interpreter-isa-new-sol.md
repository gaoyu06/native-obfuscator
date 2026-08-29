# Interpreter ISA v4 allocation/constructor slice — Sol independent review

Reviewed [PR #152](https://github.com/gaoyu06/native-obfuscator/pull/152)
at `8617b19c62349cf9b9d193c9557eaa0f1a0b71d7`, against
`origin/master` at `e4fb63be1006a6d9563d675f795f10de0bc8cdef`.

## Verdict

**Accept.**

Ship-ready: **No**. This remains a narrow, explicit, default-off interpreter
slice and is not a production-complete backend. No blocking issue or concrete
review nit was found. This review changes documentation only.

## Opcode table

Java emitter constants and the C++ `opcode` enum were compared directly with
the reviewed base:

| Value | Opcode | Review result |
|---:|---|---|
| 1–52 | Existing ISA v4 opcodes (`ATHROW=52`) | Numerically unchanged on both sides |
| 53 | reference `DUP` / `dup` | Appended; Java and C++ agree |
| 54 | `NEW` / `new_` | Appended; Java and C++ agree |
| 55 | constructor-only `INVOKESPECIAL` / `invokespecial` | Appended; Java and C++ agree |

Both sides retain `ISA_VERSION=4`, and the dispatcher rejects any method
descriptor whose version is not exactly 4.

## Correctness findings

1. **Scope and defaults.** The increment changes only the interpreter
   emitter/processor, dispatcher/header, their three tests, and status/PR
   documentation. It does not change the IR frontend, IR evaluator,
   `NativeObfuscator`, `Main`, or CLI defaults. `--backend` remains `cpp`,
   `--codegen` remains `legacy`, and `--ir-lower` is unchanged.
2. **Split allocation and construction.** `NEW` resolves the class and calls
   `AllocObject`; constructor `INVOKESPECIAL` independently resolves
   `<init>` and calls `CallNonvirtualVoidMethodA`. The interpreter path does
   not use JNI `NewObject` as a combined allocation/construction operation.
   Class and constructor indices are explicit, bounds-checked side-table
   operands.
3. **Constructor stack layout.** The constructor entry records argument kind,
   argument count, and JVM slot width. The dispatcher locates the receiver
   below all argument slots, reads `int`, `long`, and reference arguments in
   descriptor order into `jvalue[]`, invokes `<init>` nonvirtually, and then
   pops the receiver and arguments. A preceding reference `DUP` therefore
   leaves the allocated result on the stack.
4. **Admission and mutation boundary.** `tryCompile` rejects interface owners,
   instance/synchronized/abstract/native/special methods, non-`<init>`
   invocations, interface constructor calls, unsupported descriptors, fields,
   and all other unsupported instructions before returning a compiled method.
   `tryCompile` itself does not modify the `MethodNode`; the mutating
   `processMethod` is called only after a non-null preflight result.
5. **Reference `DUP`.** Admission is conservative: the immediately proven top
   must come from `NEW`, `ALOAD`, `ACONST_NULL`, or an already-proven reference
   `DUP`; labels invalidate the proof. Integer or ambiguous `DUP` returns
   `null` and uses the selected per-method fallback.
6. **Exception routing.** Failures from class lookup or `AllocObject`, and
   from constructor class lookup, `GetMethodID`, or
   `CallNonvirtualVoidMethodA`, capture and clear the pending JNI exception and
   walk the existing exception table with the throwing opcode's start PC.
   Table order and `[start_pc, end_pc)` coverage are preserved. A matching
   handler receives the exception as its sole stack value; an unmatched
   exception is retained in `frame.pending_exception` and returns
   `pending_exception` for the JNI trampoline to rethrow.

## Independent verification

The required focused command completed with `BUILD SUCCESSFUL`:

```text
CC=gcc CXX=g++ ./gradlew :obfuscator:test --rerun-tasks \
  --tests by.radioegor146.MainBackendOptionTest \
  --tests by.radioegor146.interpreter.InterpreterMethodEmitterTest \
  --tests by.radioegor146.interpreter.InterpreterRuntimeTest \
  --tests by.radioegor146.interpreter.InterpreterBackendIntegrationTest
```

JUnit XML counts from that run:

```text
MainBackendOptionTest:                    2
InterpreterMethodEmitterTest:           20
InterpreterRuntimeTest:                  1
InterpreterBackendIntegrationTest:       2
Focused total:                           25
Skipped:                                  0
Failures:                                 0
Errors:                                   0
```

The separate IR/codegen regression command also completed with
`BUILD SUCCESSFUL`:

```text
CC=gcc CXX=g++ ./gradlew :obfuscator:test --rerun-tasks \
  --tests by.radioegor146.ir.IrCompilerTest \
  --tests by.radioegor146.CodegenModeTest
```

Its JUnit XML counts were `IrCompilerTest: 102` and `CodegenModeTest: 7`, with
0 skipped, 0 failures, and 0 errors. Across both commands, **134/134** tests
passed. `InterpreterRuntimeTest` did not skip: it compiled with
`g++ -std=c++17 -Wall -Wextra -Werror`, and an exit status of zero confirms
that all 67 numbered runtime checks completed.

The default-off gate used one fixture and one freshly built compiler JAR:

```text
java -jar obfuscator/build/libs/obfuscator.jar \
  /tmp/interpreter-new-sol-review-236c/fixture.jar \
  /tmp/interpreter-new-sol-review-236c/output/default
java -jar obfuscator/build/libs/obfuscator.jar \
  /tmp/interpreter-new-sol-review-236c/fixture.jar \
  /tmp/interpreter-new-sol-review-236c/output/explicit-cpp \
  --backend=cpp
diff -r \
  /tmp/interpreter-new-sol-review-236c/output/default/cpp \
  /tmp/interpreter-new-sol-review-236c/output/explicit-cpp/cpp
```

`diff -r` exited 0 with no output.

## Nits and blocking issues

None.

## 中文摘要

**结论：接受。** Ship-ready：**No**。本增量仍是显式启用、默认关闭且功能有限的
解释器后端，不能视为生产就绪。本次审查仅修改文档，未修改编译器或解释器代码；
未发现阻塞问题或具体小问题。

Java 发射器与 C++ 调度器继续要求 ISA v4 完全匹配。与基线逐项比较后，1–52
号操作码数值均未变化，`ATHROW` 仍为 52；双方仅追加引用 `DUP=53`、
`NEW=54` 和仅限构造器的 `INVOKESPECIAL=55`。

对象分配与构造保持分离：`NEW` 使用 `FindClass` 和 `AllocObject`，构造器调用
另行使用 `GetMethodID("<init>")` 与 `CallNonvirtualVoidMethodA`，没有用
`NewObject` 合并两步。实例方法、非构造器调用、不支持的类型、字段及其他未支持
指令都会在方法变更前使 `tryCompile` 返回 `null`。分配、查找或构造失败均按
原有顺序遍历异常表；匹配时进入处理器，未匹配时返回 `pending_exception`。

独立复跑的解释器/选项测试为 **25/25**，IR/codegen 回归为 **109/109**，
合计 **134/134**；跳过、失败和错误均为 0。严格 C++17 运行时测试未跳过并
完成全部 67 项编号检查。默认输出与显式 `--backend=cpp` 输出的完整 `cpp/`
目录经 `diff -r` 比较一致，退出码为 0 且无输出。
