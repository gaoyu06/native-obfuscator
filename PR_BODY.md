# Summary / 摘要

- Admit exactly one `FNEG` over a declared-argument `FLOAD` as a proven float leaf for constructor-chain inputs.
- 构造器链参数现在仅允许一个以声明参数 `FLOAD` 为操作数的 `FNEG`，并将其证明为浮点叶节点。
- Keep admitted float computation in the retained JVM bytecode prefix, with one `MethodContext.proxyMethod` and one hidden bridge.
- 已接纳的浮点计算继续保留在 JVM 字节码前缀中，并且只使用一个 `MethodContext.proxyMethod` 和一个隐藏桥接方法。
- Keep `MAX_PROVEN_FLOAT_CHAIN_BINARY_LEVELS` at 1; `FNEG` does not consume or raise the float binary budget.
- `MAX_PROVEN_FLOAT_CHAIN_BINARY_LEVELS` 保持为 1；`FNEG` 不消耗也不提高浮点二元运算预算。

# Still rejected / 仍然拒绝

- `FNEG` of `FCONST_*` or `LDC Float`, double `FNEG`, extra-local `FLOAD`, and `FNEG` of a computed float tree.
- 对 `FCONST_*` 或 `LDC Float` 的 `FNEG`、双重 `FNEG`、额外局部变量 `FLOAD`，以及对已计算浮点树的 `FNEG`。
- Nested float binaries, extra-local float binaries, and computed double or reference inputs.
- 嵌套浮点二元运算、使用额外局部变量的浮点二元运算，以及计算得到的 double 或引用输入。

# Verification / 验证

Parent verification command / 父任务验证命令：

```text
CC=gcc CXX=g++ ./gradlew :obfuscator:test --rerun-tasks \
  --tests by.radioegor146.ir.IrCompilerTest \
  --tests by.radioegor146.CodegenModeTest
```

JUnit XML totals / JUnit XML 统计：

- `IrCompilerTest`: tests 319, failures 0, errors 0, skipped 0
- `CodegenModeTest`: tests 7, failures 0, errors 0, skipped 0
- Total / 总计: tests 326, failures 0, errors 0, skipped 0

# Readiness / 就绪状态

Ship-ready: **No**  
可发布：**否**
