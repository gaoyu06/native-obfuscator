# English

## Summary

- Admits the remaining leaf-only float constructor-chain inputs: `FDIV` and
  `FREM`, alongside the existing `FADD`, `FSUB`, and `FMUL`.
- Keeps the float binary budget at one level. Both operands must remain proven
  leaves: a declared `FLOAD`, `FCONST_0/1/2`, or an `LDC` of `Float`.
- Keeps the float expression tree in the retained JVM bytecode prefix, so JVM
  division/remainder semantics (including infinity and NaN) are not
  reimplemented in C++.
- Preserves one hidden bridge and the singular `MethodContext.proxyMethod` per
  constructor.

## Still rejected

- Nested float binaries.
- `FNEG`.
- Extra-local float operands.
- Computed double and reference chain inputs.

## Verification

Parent verification command:

```text
CC=gcc CXX=g++ ./gradlew :obfuscator:test --rerun-tasks \
  --tests by.radioegor146.ir.IrCompilerTest \
  --tests by.radioegor146.CodegenModeTest
```

JUnit XML totals:

- `IrCompilerTest`: 316 tests, 0 failures, 0 errors, 0 skipped.
- `CodegenModeTest`: 7 tests, 0 failures, 0 errors, 0 skipped.
- Total: 323 tests, 0 failures, 0 errors, 0 skipped.

Ship-ready: **No**

# 中文

## 摘要

- 补充接纳剩余的叶子级浮点构造器链输入：`FDIV` 和 `FREM`，与已接纳的
  `FADD`、`FSUB`、`FMUL` 保持一致。
- 浮点二元运算预算仍为一层。两个操作数都必须是已证明的叶子：
  已声明参数的 `FLOAD`、`FCONST_0/1/2`，或值为 `Float` 的 `LDC`。
- 浮点表达式树继续保留在 JVM 字节码前缀中，因此除法和取余的 JVM
  语义（包括无穷和 NaN）不会在 C++ 中重新实现。
- 每个构造器仍只生成一个隐藏桥，并继续使用唯一的
  `MethodContext.proxyMethod`。

## 仍然拒绝

- 嵌套浮点二元运算。
- `FNEG`。
- 额外局部变量中的浮点操作数。
- 计算得到的 double 和引用类型链输入。

## 验证

父任务验证命令：

```text
CC=gcc CXX=g++ ./gradlew :obfuscator:test --rerun-tasks \
  --tests by.radioegor146.ir.IrCompilerTest \
  --tests by.radioegor146.CodegenModeTest
```

JUnit XML 汇总：

- `IrCompilerTest`：316 个测试，0 个失败，0 个错误，0 个跳过。
- `CodegenModeTest`：7 个测试，0 个失败，0 个错误，0 个跳过。
- 总计：323 个测试，0 个失败，0 个错误，0 个跳过。

可发布：**否**
