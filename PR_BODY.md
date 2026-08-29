## Summary / 摘要

Adds the independent Sol review of
[#127](https://github.com/gaoyu06/native-obfuscator/pull/127), comparing the
ISA-v2 integer backend increment with the narrow `e1996b2` tip.

新增对 [#127](https://github.com/gaoyu06/native-obfuscator/pull/127) 的 Sol
独立审查，并将 ISA-v2 整数后端增量与窄范围 tip `e1996b2` 比较。

Verdict: **Accept with nits (docs only). / 接受（附带小问题，仅文档）。**

No blocking correctness defect was found, so this branch changes only review
documentation. The nit is that zero-divisor dispatcher status and generated
JNI exception mapping are covered separately rather than by one loaded-library
end-to-end test.

未发现阻塞性正确性缺陷，因此本分支仅修改审查文档。小问题是零除数 dispatcher
状态与生成的 JNI 异常映射目前分开覆盖，尚无单个加载共享库的端到端测试。

## Verification / 验证

- `CC=gcc CXX=g++` compiler/codegen suite: `BUILD SUCCESSFUL`;
  `IrCompilerTest` 91 and `CodegenModeTest` 5, all passing with none skipped. /
  `CC=gcc CXX=g++` 编译器/codegen 测试：`BUILD SUCCESSFUL`；
  `IrCompilerTest` 91 项、`CodegenModeTest` 5 项，全部通过且无跳过。
- `CC=gcc CXX=g++` interpreter suite: `BUILD SUCCESSFUL`; test-class counts
  2, 8, 1, and 2, all passing with none skipped. /
  `CC=gcc CXX=g++` 解释器测试：`BUILD SUCCESSFUL`；四个测试类分别为
  2、8、1、2 项，全部通过且无跳过。
- Complete generated `cpp/` trees for omitted `--backend` and explicit
  `--backend=cpp` matched (`diff -r`, exit 0, no output). /
  省略 `--backend` 与显式 `--backend=cpp` 生成的完整 `cpp/` 目录一致
  （`diff -r` 退出 0 且无输出）。
- The generated interpreter project compiled `native_jvm_interp.cpp` and built
  `native_library` successfully with GCC/G++. /
  生成的解释器项目使用 GCC/G++ 成功编译 `native_jvm_interp.cpp` 并构建
  `native_library`。

## (a)(b)(c)(d) / （a）（b）（c）（d）

- **(a) Scope / 范围:** Accept the default-off ISA-v2 integer additions and
  their versioned table, C++17 dispatcher, JNI trampolines, and per-method
  fallback. /
  接受默认关闭的 ISA-v2 整数指令增量，以及带版本的操作码表、C++17
  dispatcher、JNI 跳板与逐方法回退。
- **(b) Ship-ready? / 可直接发布？:** **No / 否。** This is still an optional,
  limited integer backend slice, not a complete backend. /
  这仍是可选且受限的整数后端子集，并非完整后端。
- **(c) Correctness / 正确性:** No blocking bug found. Opcode values and
  version checks agree; arithmetic wraparound, `count & 0x1f` shifts,
  zero-divisor exceptions, and default-off behavior are correct. /
  未发现阻塞性缺陷。操作码值与版本检查一致；算术回绕、`count & 0x1f`
  移位、零除数异常及默认关闭行为均正确。
- **(d) Integration / 集成:** Both requested Gradle commands passed with
  GCC/G++, generated-tree parity passed, and the generated interpreter shared
  library built. No compiler source or defaults changed. /
  两条指定 Gradle 命令均在 GCC/G++ 下通过，生成目录一致性验证通过，且生成的
  解释器共享库构建成功；未修改编译器源码或默认选项。
