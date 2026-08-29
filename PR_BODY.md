# Leaf-only float constructor inputs: FSUB and FMUL

## English

### Admitted leftover

- Admits leaf-only `FSUB` and `FMUL` constructor-chain inputs alongside `FADD`.
- Keeps `MAX_PROVEN_FLOAT_CHAIN_BINARY_LEVELS = 1`; both operands must be a
  declared `FLOAD`, `FCONST_0/1/2`, or `LDC` of `Float`.
- Retains the float expression in JVM bytecode, preserving Java evaluation
  order, rounding, signed zero, infinity, and NaN behavior.
- Keeps one hidden bridge and one singular `MethodContext.proxyMethod` per
  constructor.

### Still rejected

- Nested float binaries, `FDIV`, `FREM`, and `FNEG`.
- Extra-local float operands and computed double/reference chain inputs.
- The int and long binary budgets are unchanged.

### Parent verification

```text
CC=gcc CXX=g++ ./gradlew :obfuscator:test --rerun-tasks \
  --tests by.radioegor146.ir.IrCompilerTest \
  --tests by.radioegor146.CodegenModeTest
```

JUnit XML totals: **320 tests, 0 failures, 0 errors, 0 skipped**.

- `IrCompilerTest`: 313 / 0 / 0 / 0
- `CodegenModeTest`: 7 / 0 / 0 / 0

Ship-ready: **No**

## 中文

### 本次接纳的剩余项

- 在 `FADD` 之外接纳仅含叶子操作数的构造器链 `FSUB` 与 `FMUL`。
- 保持 `MAX_PROVEN_FLOAT_CHAIN_BINARY_LEVELS = 1`；两个操作数都必须是已声明
  参数的 `FLOAD`、`FCONST_0/1/2` 或装载 `Float` 的 `LDC`。
- 浮点表达式继续保留在 JVM 字节码前缀中，从而保持 Java 的求值顺序、舍入、
  有符号零、无穷大与 NaN 语义。
- 每个构造器仍只使用一个隐藏桥接方法和一个
  `MethodContext.proxyMethod`。

### 仍然拒绝

- 嵌套浮点二元表达式、`FDIV`、`FREM` 与 `FNEG`。
- 额外局部变量中的浮点操作数，以及计算得到的 double/reference 链参数。
- int 与 long 的二元层级预算均未改变。

### 父任务验证命令

```text
CC=gcc CXX=g++ ./gradlew :obfuscator:test --rerun-tasks \
  --tests by.radioegor146.ir.IrCompilerTest \
  --tests by.radioegor146.CodegenModeTest
```

JUnit XML 汇总：**320 tests，0 failures，0 errors，0 skipped**。

- `IrCompilerTest`：313 / 0 / 0 / 0
- `CodegenModeTest`：7 / 0 / 0 / 0

可发布：**No**
