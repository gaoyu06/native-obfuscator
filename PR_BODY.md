# Narrow identical-suffix multi-super constructor admission / 窄范围相同后缀多 super 构造器准入

## English

### Scope

- Admit one opt-in IR constructor-split shape beyond the existing shared-label
  diamond: exactly two reachable direct this/super calls followed by non-empty,
  straight-line, instruction-identical suffix copies.
- Prove empty chain-call entry stacks, original-receiver use, declared-argument
  inputs only, identical instructions/operands, and one call per successful
  path before changing bytecode.
- Normalize the first copy to a `GOTO` into the canonical second copy, then use
  the existing one-join/one-hidden-bridge split.
- Add split, JVM-verification, negative, and CMake/g++ Java-parity coverage for
  both retained chain-call paths.

Ship-ready: **No**. This is a narrow opt-in admission only.

Review is not stacked. The gate is the focused test command covering
`IrCompilerTest` and `CodegenModeTest`.

### Preconditions and exclusions

- Remaining constructor leftovers stay rejected, including immediate separate
  returns, unequal suffixes, unreachable candidates, zero-call paths, paths
  executing two chain calls, cross-split work, non-identity `ASTORE 0`, and
  unsupported exception-region layouts.
- Unsafe constant-dynamic forms and `jsr`/`ret` stay rejected.
- `--codegen` remains `legacy` by default. This change does not flip
  `--ir-lower`, `--backend`, or any production default.

## 中文

### 范围

- 在现有共享标签菱形之外，仅为 opt-in IR 构造器拆分新增一种形态：恰好两个
  可达的直接 this/super 调用，且其后都是非空、直线、逐指令相同的后缀副本。
- 修改字节码前，证明调用入口操作数栈为空、调用使用原始构造器接收者、后缀
  入口只依赖接收者和已声明参数、指令及操作数完全相同，并证明每条成功路径
  恰好执行一次链式构造调用。
- 将第一个后缀副本规范化为跳转到第二个规范副本，再复用已有的单汇合点、
  单隐藏桥接拆分。
- 新增拆分、JVM 验证、负例以及 CMake/g++ Java 输出一致性测试，并覆盖两个
  保留的链式调用路径。

可发布状态：**否**。本变更只是窄范围 opt-in 准入。

本次评审不是堆叠评审；门禁是同时覆盖 `IrCompilerTest` 与
`CodegenModeTest` 的聚焦测试命令。

### 前置条件与排除项

- 其余构造器遗留形态继续拒绝，包括紧随调用的独立返回、不相同后缀、不可达
  候选、零调用路径、执行两次链式调用的路径、跨拆分工作、非恒等
  `ASTORE 0` 以及不支持的异常区域布局。
- 不安全的 constant-dynamic 形态与 `jsr`/`ret` 继续拒绝。
- `--codegen` 默认值仍为 `legacy`；本变更不切换 `--ir-lower`、
  `--backend` 或任何生产默认值。
