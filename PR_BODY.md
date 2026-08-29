## English

### Summary

- Admits the former `float-nested-fadd` constructor chain-input leftover by
  raising only the float binary-tree budget from one level to two.
- Covers inner/outer `FDIV` placement and a two-sided `FADD` tree while keeping
  all float operations in the retained JVM bytecode prefix.
- Preserves three direct chain calls, one hidden native bridge, two join
  `GOTO`s, one `RETURN`, and proxy descriptor `(Ljava/lang/Object;IF)V`.
- Leaves the int and long binary-tree budgets unchanged at four levels.

### Still rejected

- Three-or-more nested float binary levels, including inner-`FDIV` trees.
- Extra-local float operands.
- `FNEG` of constants, double `FNEG`, and `FNEG` of extra-local or computed
  values; `FNEG` remains a leaf over one declared `FLOAD`.
- Computed double and reference constructor inputs.

### Verification

```text
CC=gcc CXX=g++ ./gradlew :obfuscator:test --rerun-tasks \
  --tests by.radioegor146.ir.IrCompilerTest \
  --tests by.radioegor146.CodegenModeTest
```

JUnit XML: 329 tests, 0 failures, 0 errors, 0 skipped
(`IrCompilerTest`: 322; `CodegenModeTest`: 7).

Ship-ready: **No**.

## 中文

### 摘要

- 仅将 float 二元表达式树预算从一层提高到两层，接纳原先遗留的
  `float-nested-fadd` 构造器链输入。
- 覆盖 `FDIV` 位于内层或外层以及双侧 `FADD` 树；所有 float 运算仍保留
  在 JVM 字节码前缀中执行。
- 保持三个直接链调用、一个隐藏 native 桥、两个汇合 `GOTO`、一个
  `RETURN`，代理描述符仍为 `(Ljava/lang/Object;IF)V`。
- int 与 long 的二元表达式树预算维持四层不变。

### 仍然拒绝

- 三层及以上的 float 二元表达式，包括内层 `FDIV` 树。
- 使用额外局部变量的 float 操作数。
- 常量上的 `FNEG`、双重 `FNEG`，以及额外局部变量或计算值上的 `FNEG`；
  `FNEG` 仍只作为单个已声明 `FLOAD` 上的叶节点。
- 计算得到的 double 与引用类型构造器输入。

### 验证

```text
CC=gcc CXX=g++ ./gradlew :obfuscator:test --rerun-tasks \
  --tests by.radioegor146.ir.IrCompilerTest \
  --tests by.radioegor146.CodegenModeTest
```

JUnit XML：共 329 个测试，0 failure，0 error，0 skipped
（`IrCompilerTest`：322；`CodegenModeTest`：7）。

可发布：**否**。
