## English

### (a) Review scope

Independent Sol review of implementation
[PR #152](https://github.com/gaoyu06/native-obfuscator/pull/152) at
`8617b19c62349cf9b9d193c9557eaa0f1a0b71d7`. This stacked change adds only
`docs/reviews/interpreter-isa-new-sol.md` and replaces the scratch PR body; it
does not modify compiler or interpreter code.

### (b) Verdict and ship status

**Accept.** Ship-ready: **No**.

Java and C++ retain exact-match ISA v4. Opcodes 1–52 are unchanged
(`ATHROW=52`), and both sides append only reference `DUP=53`, `NEW=54`, and
constructor-only `INVOKESPECIAL=55`. Allocation and construction remain split
between `AllocObject` and `CallNonvirtualVoidMethodA`; the interpreter path
does not use `NewObject`.

### (c) Independent verification

- Required interpreter/option suite: 25/25 passed.
- `IrCompilerTest` plus `CodegenModeTest`: 109/109 passed.
- JUnit XML total: 134 tests, 0 skipped, 0 failures, 0 errors.
- Strict C++17 runtime harness: all 67 numbered checks completed.
- Default versus explicit `--backend=cpp`: complete generated `cpp/` trees
  matched; `diff -r` exited 0 with no output.

Static review also confirmed reject-before-mutation admission and shared
exception-table routing for allocation, lookup, and constructor failures.
There are no nits or blocking issues.

### (d) Stack and non-goals

The future PR must use `cursor/interpreter-new-6d81-ac59` as its base, not
`master`. This review does not flip defaults or add fields, instance methods,
or virtual/static/interface invocation.

## 中文

### （a）审查范围

对实现 [PR #152](https://github.com/gaoyu06/native-obfuscator/pull/152)
（`8617b19c62349cf9b9d193c9557eaa0f1a0b71d7`）进行独立 Sol 审查。
此堆叠变更仅新增 `docs/reviews/interpreter-isa-new-sol.md` 并替换临时
PR 说明，不修改编译器或解释器代码。

### （b）结论与发布状态

**接受。** Ship-ready：**No**。

Java 与 C++ 均保持 ISA v4 完全匹配。1–52 号操作码不变（`ATHROW=52`），
双方仅追加引用 `DUP=53`、`NEW=54` 和仅限构造器的
`INVOKESPECIAL=55`。对象分配和构造仍分别使用 `AllocObject` 与
`CallNonvirtualVoidMethodA`；解释器路径未使用 `NewObject` 合并两步。

### （c）独立验证

- 必需的解释器/选项测试：25/25 通过。
- `IrCompilerTest` 与 `CodegenModeTest`：109/109 通过。
- JUnit XML 合计：134 项，0 跳过、0 失败、0 错误。
- 严格 C++17 运行时测试：全部 67 项编号检查完成。
- 默认输出与显式 `--backend=cpp` 输出：完整 `cpp/` 目录一致，
  `diff -r` 退出码为 0 且无输出。

静态审查同时确认了变更前拒绝边界，以及分配、查找、构造失败共用原有异常表
分派。没有小问题或阻塞问题。

### （d）堆叠关系与非目标

后续 PR 必须以 `cursor/interpreter-new-6d81-ac59` 为 base，而不是
`master`。本审查不修改默认值，也不添加字段、实例方法或虚/静态/接口调用。
