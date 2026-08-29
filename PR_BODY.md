# (a) Scope / 范围

English:

- Admit receiver-alias forwarding for two or more instruction-identical,
  straight-line constructor suffix copies, including the existing immediate
  `RETURN` variant. Every `ASTORE 0` must be before the first chain call and
  must be identity-preserving or directly store `ACONST_NULL` / a declared
  reference argument. Receiver-state analysis must still prove that every
  selected this/super call consumes the original constructor receiver.
- Three-or-more-copy classification accepts a direct alias `ALOAD` only after
  that prefix overwrite is proven. Normalization remains earlier-copy `GOTO`s,
  one shared join, one canonical extra-free suffix, and one hidden native
  bridge. A copied suffix still cannot read the alias extra.
- Reuse the existing receiver-alias wrapper and shell-only owner-`Class`
  metadata. Local 0 remains the first hidden-bridge body argument. The path-id
  distinct-suffix alias family was added separately in
  [#204](https://github.com/gaoyu06/native-obfuscator/pull/204).
- Keep overwritten-receiver calls, suffix `ASTORE 0`, non-strict post-call
  branches, unproven prefix-to-suffix edges, mixed catches, and unproven
  extras rejected.

中文：

- 允许两个或更多“指令完全相同、直线执行”的构造器后缀副本转发接收者别名，
  包括已有的立即 `RETURN` 形式。所有 `ASTORE 0` 都必须位于第一次链式构造
  调用之前，并且必须保持接收者身份，或直接写入 `ACONST_NULL` / 已声明的
  引用参数。接收者状态分析仍须证明每个选中的 this/super 调用都使用原始
  构造器接收者。
- 对三个及以上副本，只有在前缀覆盖已被证明后，分类器才接受直接加载别名
  的 `ALOAD`。归一化仍保持“较早副本改为 `GOTO`、一个共享汇合点、一个不读
  额外局部变量的规范后缀、一个隐藏 native 桥”。副本后缀仍不能读取别名
  额外局部变量。
- 复用现有接收者别名 wrapper 和仅供 shell 使用的 owner `Class` 元数据。
  local 0 仍是隐藏桥 body 的第一个参数。path-id 不同后缀的别名支持已由
  [#204](https://github.com/gaoyu06/native-obfuscator/pull/204) 单独加入。
- 继续拒绝使用已覆盖 local 0 的链式调用、后缀中的 `ASTORE 0`、非严格的
  调用后分支、未经证明的前缀到后缀跳转、混合 catch 和未经证明的额外局部
  变量。

# (b) Ship-ready? / 可发布？

**No / 否。**

English: This is one structural IR constructor-split increment. Remaining
leftovers stay rejected, and the default `--codegen` mode remains `legacy`.

中文：这是一个结构化 IR 构造器拆分增量。其余 leftover 继续拒绝，默认
`--codegen` 模式仍为 `legacy`。

# (c) Review and gate / 审查与门禁

English:

- Review the prefix-only `ASTORE 0` syntax gate, the mandatory per-call
  receiver proof, and the N-call strict-diamond hook after normalization.
- Focused GCC/G++ rerun-task gate: `IrCompilerTest` 256/256 and
  `CodegenModeTest` 7/7; 0 failures, 0 errors, 0 skipped.
- New coverage checks two-copy and three-copy normalization, rejection before
  mutation when one call uses overwritten local 0, JVM verification, owner
  class-loader metadata, one hidden bridge, and full Java/native stdout parity.

中文：

- 重点审查仅限前缀的 `ASTORE 0` 语法门禁、每个链式调用都必须通过的接收者
  证明，以及归一化后 N 路严格 diamond 的入口。
- GCC/G++ 聚焦重跑门禁结果：`IrCompilerTest` 256/256，
  `CodegenModeTest` 7/7；失败 0、错误 0、跳过 0。
- 新覆盖验证两副本和三副本归一化、某一路使用已覆盖 local 0 时在修改前
  拒绝、JVM 校验、owner 类加载元数据、单一隐藏桥，以及完整 Java/native
  标准输出一致性。

# (d) Preconditions / 前置条件

English:

- Base: current `origin/master` at `72c5cac`, whose leftover documentation
  reflects landed [#204](https://github.com/gaoyu06/native-obfuscator/pull/204).
- The runtime parity gate requires `cmake`, GCC, G++, and a working JDK/JNI
  toolchain.
- This change does not broaden suffix extras, mixed-catch handling, path-id
  bounds/walkers, non-strict diamonds, or CLI defaults.

中文：

- 基线：当前 `origin/master` 的 `72c5cac`；其 leftover 文档已反映落地的
  [#204](https://github.com/gaoyu06/native-obfuscator/pull/204)。
- 运行时一致性门禁需要 `cmake`、GCC、G++ 以及可用的 JDK/JNI 工具链。
- 本变更不扩展后缀额外局部变量、混合 catch、path-id 范围/walker、非严格
  diamond 或 CLI 默认值。
