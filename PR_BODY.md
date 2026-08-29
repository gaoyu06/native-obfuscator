# Summary / 摘要

## English

- Admits the former `double-binary` constructor-split leftover: one leaf-only
  `DADD` whose operands are proven declared-argument `DLOAD`, `DCONST_0`,
  `DCONST_1`, or `LDC` `Double` leaves.
- Keeps nested double binaries, extra-local double loads, `DSUB`, `DMUL`,
  `DDIV`, `DREM`, `DNEG`, and computed reference inputs rejected before
  constructor or hidden-method mutation.
- Retains admitted double arithmetic in JVM bytecode and keeps one hidden
  native bridge per constructor.

## 中文

- 接纳原有的 `double-binary` 构造器拆分遗留项：仅允许一层、叶子受证明的
  `DADD`；其操作数只能是已声明参数的 `DLOAD`、`DCONST_0`、
  `DCONST_1` 或类型为 `Double` 的 `LDC`。
- 嵌套 double 二元表达式、额外局部变量 double 加载、`DSUB`、`DMUL`、
  `DDIV`、`DREM`、`DNEG` 和计算得到的引用输入仍在修改构造器或隐藏方法前
  失败关闭。
- 已接纳的 double 运算保留在 JVM 字节码中，每个构造器仍只生成一个隐藏
  native 桥接方法。

## Verification / 验证

```text
CC=gcc CXX=g++ ./gradlew :obfuscator:test --rerun-tasks \
  --tests by.radioegor146.ir.IrCompilerTest \
  --tests by.radioegor146.CodegenModeTest
```

JUnit XML totals / JUnit XML 汇总：pending / 待运行。

Ship-ready / 可发布：**No / 否**
