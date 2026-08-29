## English

### (a) Scope

Admits only the bounded 2–8 path-id distinct-suffix constructor shape where
the original receiver is saved in a prefix alias, local 0 is overwritten by a
declared reference argument or `null`, and every selected this/super call is
proven to consume the saved receiver. The existing packed-extra proof forwards
the alias when a suffix reads it. Identical-copy normalization remains
unchanged and still rejects `ASTORE 0`.

### (b) Ship-ready?

No. This is one structural IR leftover; the production migration is not
complete.

### (c) Review and gate

The parent agent re-runs the focused Gradle suite for `IrCompilerTest` and
`CodegenModeTest`.

New tests:

- `admitsAndRewritesReceiverAliasPathIdDistinctSuffixes()`
- `rejectsPathIdDistinctSuffixUsingOverwrittenReceiverBeforeMutation()`
- `receiverAliasDistinctSuffixesCompileAndRunWithJavaParity()`

### (d) Preconditions

Remaining unsupported constructor shapes continue to reject before bytecode,
hidden-method, or C++ mutation. `--codegen` remains `legacy`.

## 中文

### (a) 范围

本次只接纳 2–8 路 path-id 不同后缀的构造器形态：前缀先把原始接收者保存到
别名局部变量，再用声明的引用参数或 `null` 覆盖局部变量 0，并证明每个选中的
this/super 调用都通过该别名使用原始接收者。若后缀读取该别名，则继续使用现有
packed-extra 证明进行传递。相同副本的单汇合规范化保持不变，仍拒绝
`ASTORE 0`。

### (b) 可发布？

否。本次只处理一个结构性 IR 遗留项，生产迁移尚未完成。

### (c) 评审与门禁

父代理会重新运行聚焦的 Gradle 测试套件：`IrCompilerTest` 和
`CodegenModeTest`。

新增测试：

- `admitsAndRewritesReceiverAliasPathIdDistinctSuffixes()`
- `rejectsPathIdDistinctSuffixUsingOverwrittenReceiverBeforeMutation()`
- `receiverAliasDistinctSuffixesCompileAndRunWithJavaParity()`

### (d) 前置条件

其余不支持的构造器形态继续在修改字节码、隐藏方法或 C++ 状态之前拒绝。
`--codegen` 继续保持 `legacy`。
