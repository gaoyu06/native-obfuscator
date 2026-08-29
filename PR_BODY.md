## English

### Leftover admitted

- Raises only the fail-closed float constructor-chain binary budget from three
  to four levels.
- Admits four-level `FADD`, inner `FDIV`, and outer `FDIV` fixtures while
  retaining all float arithmetic in JVM bytecode.
- Keeps one hidden bridge with a path id and one native method per constructor.

### Still rejected

- Five-or-more nested float binaries, extra-local float operands, and unsafe
  `FNEG` forms remain rejected before mutation.
- Int and long budgets remain four. Double-family inputs remain out of scope.

### Verification

```text
CC=gcc CXX=g++ ./gradlew :obfuscator:test --rerun-tasks \
  --tests by.radioegor146.ir.IrCompilerTest \
  --tests by.radioegor146.CodegenModeTest
```

JUnit XML totals: pending.

Ship-ready: **No**

## 中文

### 本次纳入的遗留项

- 仅将构造器链 float 二元表达式的失败关闭预算从三层提高到四层。
- 纳入四层 `FADD`、内层 `FDIV` 和外层 `FDIV` 测试形状，所有 float
  运算仍保留在 JVM 字节码中。
- 每个构造器仍使用一个携带路径编号的隐藏桥接方法和一个 native 方法。

### 仍然拒绝

- 五层及以上 float 二元表达式、额外局部变量 float 操作数以及不安全的
  `FNEG` 形式仍在修改前被拒绝。
- int 和 long 预算仍为四层；double 系列不在本次范围内。

### 验证

```text
CC=gcc CXX=g++ ./gradlew :obfuscator:test --rerun-tasks \
  --tests by.radioegor146.ir.IrCompilerTest \
  --tests by.radioegor146.CodegenModeTest
```

JUnit XML 汇总：待运行。

可发布：**否**
