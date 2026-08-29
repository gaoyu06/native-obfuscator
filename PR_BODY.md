Title: `docs: Sol review of #140 interpreter i64`

## English

### (a) Scope

Add the independent Sol review of draft PR #140 at
`213c1eec79201067dc6f5da5c7225ae66170427d`. The review covers diff ownership,
all 16 Java/C++ i64 opcode assignments, two-slot locals and stack behavior,
shift and arithmetic edge cases, default-off output identity, and unchanged
per-method fallback.

### (b) Ship-ready?

**No.** The review verdict is **Accept**, but the implementation remains a
narrow, default-off compiler-backend increment rather than a complete
production interpreter.

### (c) Correctness result

No blocking defect or nit was found, and no compiler source fix was needed.
Java and C++ agree on opcodes 30--45. Long values use two consecutive 32-bit
slots; shifts consume an i32 count masked with `& 63`; `LSHR` and `LUSHR`
remain distinct; divide/remainder avoid C++ zero division and the
`MIN_VALUE / -1` overflow case; and `LNEG` wraps correctly. Unsupported
methods still use the selected IR or legacy compiler path. The defaults remain
`--backend=cpp` and `--codegen=legacy`.

### (d) Verification

- Focused rerun: **23/23 tests passed**, with 0 skipped, 0 failures, and
  0 errors: 16 interpreter/backend tests plus 7 `CodegenModeTest` tests.
- The C++17 runtime harness compiled with `-Wall -Wextra -Werror` and executed.
- Fresh complete-tree `diff -r` checks passed for detached `origin/master` vs
  omitted `--backend`, and omitted `--backend` vs explicit `--backend=cpp`.
- The generated project containing six i64 interpreter methods compiled and
  linked successfully with GCC/G++ 13.3.0.

## 中文

### （a）范围

增加对 draft PR #140（tip
`213c1eec79201067dc6f5da5c7225ae66170427d`）的独立 Sol 审查。审查范围包括
diff 归属、全部 16 个 Java/C++ i64 操作码编号、局部变量与操作数栈的双槽语义、
移位和算术边界、默认关闭时的输出一致性，以及逐方法回退路径。

### （b）可直接发布？

**否。** 审查结论为**接受**，但该实现仍是范围有限且默认关闭的编译器后端
增量，并非完整的生产级解释器。

### （c）正确性结论

未发现阻塞性缺陷或审查小问题，也不需要修改编译器源码。Java 与 C++ 的
30--45 号操作码完全一致；long 使用连续两个 32 位槽；移位使用来自 i32 且经
`& 63` 处理的计数；`LSHR` 与 `LUSHR` 保持不同语义；除法和取余避免 C++
零除以及 `MIN_VALUE / -1` 溢出；`LNEG` 回绕正确。不支持的方法仍使用当前
IR 或 legacy compiler path。默认值仍为 `--backend=cpp` 和
`--codegen=legacy`。

### （d）验证

- 聚焦复跑 **23/23** 项测试通过，0 skipped、0 failures、0 errors；其中
  interpreter/backend 测试 16 项，`CodegenModeTest` 7 项。
- C++17 runtime harness 使用 `-Wall -Wextra -Werror` 成功编译并执行。
- 重新生成的完整目录通过两次 `diff -r`：分离的 `origin/master` 对比未指定
  `--backend`，以及未指定 `--backend` 对比显式 `--backend=cpp`。
- 包含六个 i64 interpreter 方法的生成工程使用 GCC/G++ 13.3.0 成功编译并
  链接。
