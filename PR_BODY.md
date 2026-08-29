Title: `compiler: interpreter ISA i64 increment`

## English

### (a) Scope

Widen the explicit, default-off `--backend=interpreter` compiler path from its
i32 ISA to a first i64 slice. ISA v3 appends `LPUSH`, `LLOAD`, `LSTORE`,
`LADD`, `LSUB`, `LMUL`, `LAND`, `LOR`, `LXOR`, `LSHL`, `LSHR`, `LUSHR`,
`LNEG`, `LRETURN`, `LDIV`, and `LREM`.

Long values retain JVM two-slot locals/stack layout. Arithmetic uses unsigned
64-bit carriers for defined wraparound, shift counts are i32 values masked
with `& 63`, and right shifts distinguish arithmetic `LSHR` from logical
`LUSHR`. `LDIV`/`LREM` share the JVM zero-divisor and
`Long.MIN_VALUE / -1` edge rules. Unsupported methods still fall through per
method to the active IR or legacy codegen.

### (b) Ship-ready?

**No.** This is a narrow, default-off compiler-backend increment, not a
complete production interpreter.

### (c) Default-off compatibility

Preserved. Full generated `cpp/` tree comparisons both returned exit 0:

- detached `origin/master` vs this branch with `--backend` omitted;
- this branch with `--backend` omitted vs explicit `--backend=cpp`.

The defaults remain `--backend=cpp` and `--codegen=legacy`; interpreter
sources are not emitted when the backend flag is off.

### (d) Verification

- Interpreter suite: **16 tests**, 0 skipped, 0 failures, 0 errors.
- IR/codegen regression suite: **104 tests**, 0 skipped, 0 failures, 0 errors.
- The runtime harness compiled under
  `g++ -std=c++17 -Wall -Wextra -Werror` and executed all i64 operation and
  edge cases.
- A generated project containing six i64 interpreter methods configured and
  built successfully with GCC/G++ 13.3.0, including
  `native_jvm_interp.cpp` and the generated JNI trampolines.

## 中文

### （a）范围

将显式启用、默认关闭的 `--backend=interpreter` 编译路径从 i32 ISA 扩展到首个
i64 子集。ISA v3 追加 `LPUSH`、`LLOAD`、`LSTORE`、`LADD`、`LSUB`、
`LMUL`、`LAND`、`LOR`、`LXOR`、`LSHL`、`LSHR`、`LUSHR`、`LNEG`、
`LRETURN`、`LDIV` 和 `LREM`。

long 在局部变量和操作数栈中继续占用两个 JVM 槽。算术使用无符号 64 位载体以
保证回绕语义；移位计数来自 i32 并以 `& 63` 掩码；`LSHR` 为算术右移，
`LUSHR` 为逻辑右移。`LDIV`/`LREM` 处理除数为零和
`Long.MIN_VALUE / -1` 的 JVM 边界。不支持的方法仍逐方法回退到当前 IR 或
legacy codegen。

### （b）可直接发布？

**否。** 这是范围有限且默认关闭的编译器后端增量，不是完整的生产级解释器。

### （c）默认关闭兼容性

已保持。两次完整生成 `cpp/` 目录比较均以 0 退出：

- 分离的 `origin/master` 与本分支未指定 `--backend` 的输出；
- 本分支未指定 `--backend` 与显式 `--backend=cpp` 的输出。

默认值仍为 `--backend=cpp` 和 `--codegen=legacy`；关闭该后端标志时不会生成
解释器源文件。

### （d）验证

- 解释器测试：**16 项**，0 跳过、0 失败、0 错误。
- IR/codegen 回归测试：**104 项**，0 跳过、0 失败、0 错误。
- 运行时测试以 `g++ -std=c++17 -Wall -Wextra -Werror` 编译，并执行了所有
  i64 操作与边界用例。
- 包含六个 i64 解释器方法的生成工程已使用 GCC/G++ 13.3.0 成功配置和构建，
  包括 `native_jvm_interp.cpp` 与生成的 JNI 跳板。
