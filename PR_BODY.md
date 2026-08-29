# Summary / 摘要

- Admits the former `double-dneg` leftover: one `DNEG` whose sole operand is a matching declared-argument `DLOAD`.
- 接纳原 `double-dneg` 遗留项：仅允许一个 `DNEG`，且其唯一操作数必须是匹配的构造函数声明参数 `DLOAD`。
- Keeps `MAX_PROVEN_DOUBLE_CHAIN_BINARY_LEVELS = 1`; `DNEG` does not consume that binary budget, and all admitted double arithmetic remains in the retained JVM bytecode prefix.
- 保持 `MAX_PROVEN_DOUBLE_CHAIN_BINARY_LEVELS = 1`；`DNEG` 不消耗该二元层级预算，所有已接纳的 double 运算仍保留在 JVM 字节码前缀中执行。
- Rewrites the three immediate-return fixture to three direct chain calls, two join `GOTO`s, one `RETURN`, and one hidden bridge with descriptor `(Ljava/lang/Object;ID)V`.
- 三个立即返回夹具重写后包含三个直接构造链调用、两个汇合 `GOTO`、一个 `RETURN`，以及一个描述符为 `(Ljava/lang/Object;ID)V` 的隐藏桥接方法。

# Still rejected / 仍然拒绝

- Nested double binaries and extra-local double operands.
- 嵌套 double 二元表达式和额外局部变量中的 double 操作数。
- `DNEG` of a constant, double `DNEG`, and `DNEG` of an extra-local or computed value.
- 对常量执行 `DNEG`、双重 `DNEG`，以及对额外局部变量或计算结果执行 `DNEG`。
- Computed reference inputs and every other unproven constructor-chain input.
- 计算得到的引用输入及其他所有未经证明的构造链输入。

# Verification / 验证

```text
CC=gcc CXX=g++ ./gradlew :obfuscator:test --rerun-tasks \
  --tests by.radioegor146.ir.IrCompilerTest \
  --tests by.radioegor146.CodegenModeTest
```

Result / 结果: `BUILD SUCCESSFUL`

JUnit XML under `obfuscator/build/test-results/test/` / 该目录下的 JUnit XML：

- `IrCompilerTest`: 341 tests, 0 failures, 0 errors, 0 skipped.
- `CodegenModeTest`: 7 tests, 0 failures, 0 errors, 0 skipped.
- Total / 合计: 348 tests, 0 failures, 0 errors, 0 skipped.
- New testcase records / 新增测试记录:
  - `admitsThreeImmediateReturnsWithDnegOfProvenChainInputs()`
  - `rewrittenThreeImmediateDnegSuperReturnsPassJvmVerification()`
  - `threeImmediateDnegSuperReturnsCompileAndRunWithJavaParity()`

# Ship-ready / 可发布

**No / 否**
