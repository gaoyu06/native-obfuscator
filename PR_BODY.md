## English

### (a) Scope

This increment extends the #182–#186 leaf-only constructor chain-input walker
to a bounded two-level tree of the already admitted non-trapping int binaries:
`IADD`, `ISUB`, `IMUL`, `IAND`, `IOR`, `IXOR`, `ISHL`, `ISHR`, and `IUSHR`.
Each inner binary still requires direct declared int-family loads, int
constants, or a single `INEG` over a declared load as its leaves. The arithmetic
remains in the retained bytecode prefix, and the immediate-return three-call
shape continues to use one normalized join and one hidden bridge.

This is not an unbounded expression-tree parser. A third binary level remains
rejected, as do `IDIV`, `IREM`, and extra-local operands.

### (b) Ship-ready?

No.

### (c) Review and gate

Executed:

```text
CC=gcc CXX=g++ ./gradlew :obfuscator:test --rerun-tasks \
  --tests by.radioegor146.ir.IrCompilerTest \
  --tests by.radioegor146.CodegenModeTest
```

Result: `IrCompilerTest` 232/232 and `CodegenModeTest` 7/7, with zero failures,
errors, or skips. This increment does not depend on a stacked Fable/Sol review.

### (d) Preconditions

Remaining constructor-split leftovers continue to reject before mutation.
`--codegen` remains `legacy`; no production default is changed.

## 中文

### (a) 范围

本增量把 #182–#186 的仅叶节点构造器链调用输入检查扩展为有界的两层树。
允许的仍然只是已支持的非抛异常整数二元指令：`IADD`、`ISUB`、`IMUL`、
`IAND`、`IOR`、`IXOR`、`ISHL`、`ISHR` 和 `IUSHR`。内层二元指令的叶节点
仍必须是已声明整数族参数的直接加载、整数常量，或对已声明参数直接加载
执行一次 `INEG`。这些算术指令继续保留在字节码前缀中；三个立即返回链调用
仍使用一个规范化汇合点和一个隐藏桥接方法。

这不是无界表达式树解析器。第三层二元指令仍被拒绝，`IDIV`、`IREM` 和额外
局部变量操作数也仍被拒绝。

### (b) 可发布？

否。

### (c) 审查与门禁

已执行：

```text
CC=gcc CXX=g++ ./gradlew :obfuscator:test --rerun-tasks \
  --tests by.radioegor146.ir.IrCompilerTest \
  --tests by.radioegor146.CodegenModeTest
```

结果：`IrCompilerTest` 232/232，`CodegenModeTest` 7/7；失败、错误和跳过均为
零。本增量不依赖堆叠的 Fable/Sol 审查。

### (d) 前置条件

其余构造器拆分遗留形状继续在修改前拒绝。`--codegen` 仍默认为 `legacy`；
没有修改生产默认配置。
