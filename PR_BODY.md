# English

## Leftover admitted

- Raises only the fail-closed float constructor-chain binary budget from two to
  three levels.
- Admits three-level nested `FADD` inputs and three-level chains with `FDIV` in
  either the inner or outer position.
- Keeps all float arithmetic in the retained JVM bytecode prefix and shares one
  hidden bridge through a path id.

## Still rejected

- Four-or-more nested float binaries.
- Extra-local float operands and unsafe `FNEG` forms.
- Computed double/reference inputs and all other existing unsupported
  constructor shapes.

## Verification

```text
CC=gcc CXX=g++ ./gradlew :obfuscator:test --rerun-tasks \
  --tests by.radioegor146.ir.IrCompilerTest \
  --tests by.radioegor146.CodegenModeTest
```

JUnit XML totals: **332 tests / 0 failures / 0 errors / 0 skipped**.

- `IrCompilerTest`: 325 / 0 / 0 / 0
- `CodegenModeTest`: 7 / 0 / 0 / 0
- New testcases passed:
  `admitsThreeLevelNestedFloatChainInputs()`,
  `rewrittenThreeLevelNestedFloatChainInputsPassJvmVerification()`, and
  `threeLevelNestedFloatChainInputsCompileAndRunWithJavaParity()`.

Ship-ready: **No**

# 中文

## 本次接纳的遗留项

- 仅将构造函数链参数的浮点二元运算失效关闭预算从两层提高到三层。
- 接纳三层嵌套 `FADD`，以及 `FDIV` 位于最内层或最外层的三层运算链。
- 所有浮点运算仍保留在 JVM 字节码前缀中，并通过路径编号共用一个隐藏桥接方法。

## 仍然拒绝

- 四层及以上的嵌套浮点二元运算。
- 使用额外局部变量的浮点操作数和不安全的 `FNEG` 形式。
- 计算得到的 double/reference 输入，以及其他现有的不支持构造函数形状。

## 验证

```text
CC=gcc CXX=g++ ./gradlew :obfuscator:test --rerun-tasks \
  --tests by.radioegor146.ir.IrCompilerTest \
  --tests by.radioegor146.CodegenModeTest
```

JUnit XML 汇总：**332 个测试 / 0 个失败 / 0 个错误 / 0 个跳过**。

- `IrCompilerTest`：325 / 0 / 0 / 0
- `CodegenModeTest`：7 / 0 / 0 / 0
- 新增测试均已通过：
  `admitsThreeLevelNestedFloatChainInputs()`、
  `rewrittenThreeLevelNestedFloatChainInputsPassJvmVerification()` 和
  `threeLevelNestedFloatChainInputsCompileAndRunWithJavaParity()`。

可发布：**否**
