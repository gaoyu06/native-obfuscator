## English

### (a) Review scope

This documentation-only branch is the independent Sol review of
[PR #150](https://github.com/gaoyu06/native-obfuscator/pull/150) at
`a13b0bb653df90519dd5ac098fba7e3998093498`. It reviews that exception-table
and `ATHROW` increment only.

**Verdict: Accept.** Java and C++ retain exact-match ISA v4 and opcode values
1–51, append `ATHROW=52`, and agree on ordered `[start, end)` exception-table
dispatch. Matching handlers receive only the exception reference; unmatched
exceptions return `pending_exception`. Unsupported methods and handler bodies
still fall back before interpreter mutation. The defaults remain
`--backend=cpp` and `--codegen=legacy`.

The detailed findings and independent 29-test rerun are recorded in
`docs/reviews/interpreter-isa-exceptions-sol.md`. No interpreter or compiler
source is changed by this review.

### (b) Ship readiness

**No.** Ship-ready remains **No**.

### (c) Review status

This pull request is the requested Sol review itself. The review verdict is
complete; it is not another implementation increment.

### (d) Stack base

Stack this review only on
`cursor/interpreter-exceptions-6d81-a565`, the implementation branch for
PR #150. Do not base this review directly on `master`.

## 中文

### （a）审查范围

此纯文档分支是对
[PR #150](https://github.com/gaoyu06/native-obfuscator/pull/150) 在
`a13b0bb653df90519dd5ac098fba7e3998093498` 的独立 Sol 审查，仅审查该异常表
与 `ATHROW` 增量。

**结论：接受。** Java 与 C++ 均保持精确匹配的 ISA v4 和 1–51 号操作码数值，
并追加 `ATHROW=52`；双方的有序 `[start, end)` 异常表分派一致。匹配处理器的
入口栈仅包含异常引用；未匹配异常返回 `pending_exception`。不支持的方法或
处理器主体仍会在解释器修改方法前回退。默认值仍为 `--backend=cpp` 和
`--codegen=legacy`。

详细结论和独立复跑的 29 项测试记录在
`docs/reviews/interpreter-isa-exceptions-sol.md`。本次审查不修改解释器或
编译器源码。

### （b）发布就绪状态

**否。** Ship-ready 仍为 **No**。

### （c）审查状态

此拉取请求本身就是所要求的 Sol 审查。审查结论已经完成，并非新的实现增量。

### （d）堆叠基线

本审查只能堆叠在 PR #150 的实现分支
`cursor/interpreter-exceptions-6d81-a565` 上；不要直接以 `master` 为基线。
