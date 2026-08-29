## English

### (a) Scope

This widens the explicit, default-off in-process interpreter while keeping ISA
version 4 and opcode values 1–51 stable.

- Appends `ATHROW` as opcode 52.
- Emits ordered exception-table entries with start/end/handler opcode-stream
  PCs and a typed catch name or catch-all.
- Dispatches `ATHROW` and integer/long divide or remainder by zero to a
  covering handler, with the exception reference as the sole handler-entry
  stack value.
- Returns `pending_exception` for an unmatched exception and lets the JNI
  trampoline preserve or throw it.
- Admits otherwise-supported static try/catch methods. Unsupported handler
  instructions still cause per-method fallback before mutation.

`NEW`, invoke instructions (including `INVOKESPECIAL`), and field access remain
out of scope. The defaults remain `--backend=cpp` and `--codegen=legacy`.

### (b) Readiness and review

**Ship-ready: No. Review required: Yes. Sol-only review is acceptable.**

This is the first exception-dispatch slice, not a complete interpreter
backend.

### (c) Compatibility

The exception table landed. ISA version 4 remains exact-match, opcodes 1–51
are unchanged, and opcode 52 is appended. Both complete default-off output
comparisons exited 0 with no output:

```text
diff -r /tmp/interpreter-exceptions-proof-final-a565/master/cpp \
  /tmp/interpreter-exceptions-proof-final-a565/branch-default/cpp
diff -r /tmp/interpreter-exceptions-proof-final-a565/branch-default/cpp \
  /tmp/interpreter-exceptions-proof-final-a565/branch-cpp/cpp
```

### (d) Evidence

The required Gradle selection passed 131 tests with 0 skipped, 0 failures, and
0 errors:

- option/interpreter suite: 22
  (`MainBackendOptionTest` 2, emitter 17, runtime 1, integration 2)
- IR/codegen regression: 109 (`IrCompilerTest` 102, `CodegenModeTest` 7)

The runtime harness compiled with
`g++ -std=c++17 -Wall -Wextra -Werror` and executed 61 checks, including
unhandled `ATHROW`, catch-all i32/i64 zero division, typed catch match/miss,
and null `ATHROW`. The generated exception-table integration library also
built successfully with GCC/G++ 13.3.0.

Full commands and counts are in
`docs/architecture/interpreter-isa-exceptions-status.md`.

## 中文

### （a）范围

本次变更扩展了需要显式启用、默认关闭的进程内解释器，同时保持 ISA 版本 4
以及操作码 1–51 的数值不变。

- 将 `ATHROW` 追加为操作码 52。
- 发出有序异常表；每项包含起始、结束、处理器的操作码流 PC，以及类型化捕获名
  或全捕获标记。
- `ATHROW` 以及整数/长整数除法或取余的零除异常可转移到覆盖当前位置的处理器；
  处理器入口栈仅包含异常引用。
- 未匹配的异常返回 `pending_exception`，由 JNI 跳板保留或抛出。
- 当静态方法及其处理器中的全部指令均已受支持时，允许该方法包含 try/catch；
  不支持的处理器指令仍会在修改方法前触发逐方法回退。

`NEW`、调用指令（包括 `INVOKESPECIAL`）和字段访问仍不在本次范围内。默认值仍为
`--backend=cpp` 和 `--codegen=legacy`。

### （b）发布与审查

**可直接发布：否。需要审查：是。可以仅由 Sol 审查。**

这是首个异常分派增量，并非完整的解释器后端。

### （c）兼容性

异常表已实现。ISA 版本 4 仍要求精确匹配，操作码 1–51 不变，操作码 52 为新增
追加项。两次默认关闭的完整输出目录比较都以 0 退出且无输出：

```text
diff -r /tmp/interpreter-exceptions-proof-final-a565/master/cpp \
  /tmp/interpreter-exceptions-proof-final-a565/branch-default/cpp
diff -r /tmp/interpreter-exceptions-proof-final-a565/branch-default/cpp \
  /tmp/interpreter-exceptions-proof-final-a565/branch-cpp/cpp
```

### （d）证据

指定的 Gradle 测试共 131 项，全部通过，跳过 0、失败 0、错误 0：

- 选项/解释器测试：22 项
  （`MainBackendOptionTest` 2、发射器 17、运行时 1、集成 2）
- IR/codegen 回归测试：109 项（`IrCompilerTest` 102、`CodegenModeTest` 7）

运行时测试使用 `g++ -std=c++17 -Wall -Wextra -Werror` 编译并执行了 61 项检查，
覆盖未处理的 `ATHROW`、i32/i64 零除的全捕获、类型化捕获的匹配与未匹配，以及
空引用 `ATHROW`。包含异常表的生成集成库也已使用 GCC/G++ 13.3.0 成功构建。

完整命令和计数记录在
`docs/architecture/interpreter-isa-exceptions-status.md`。
