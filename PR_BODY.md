# Summary / 摘要

## English

This increment admits leaf-only `DDIV` and `DREM` constructor-chain inputs.
The double binary budget remains one level, so each operand must still be a
proven double leaf. The arithmetic remains in the retained JVM bytecode prefix;
the rewrite continues to use one hidden JNI bridge and its path id.

Still rejected: `DNEG`, nested double binaries, extra-local double loads, and
other unproven inputs. The int, long, and float budgets remain unchanged.

## 中文

本次增量允许构造器链参数使用仅含叶子操作数的 `DDIV` 和 `DREM`。double
二元运算预算仍为一层，因此两个操作数都必须是已证明的 double 叶子。运算仍保留在
JVM 字节码前缀中；重写后继续只使用一个隐藏 JNI 桥接方法及其路径编号。

仍然拒绝：`DNEG`、嵌套 double 二元运算、额外局部变量中的 double 加载，以及其他
未证明的输入。int、long 和 float 的预算均未改变。

## Verification / 验证

```text
CC=gcc CXX=g++ ./gradlew :obfuscator:test --rerun-tasks \
  --tests by.radioegor146.ir.IrCompilerTest \
  --tests by.radioegor146.CodegenModeTest
```

JUnit XML totals: `IrCompilerTest` 338 tests and `CodegenModeTest` 7 tests;
345 total, 0 skipped, 0 failures, 0 errors.

JUnit XML 汇总：`IrCompilerTest` 338 项，`CodegenModeTest` 7 项；共 345 项，
0 项跳过，0 项失败，0 项错误。

Ship-ready / 可交付：**No / 否**
