## English

### Summary

- Admits only the former `double-extra-local` leftover: an extra-local double
  chain-input leaf whose prefix `DSTORE` is a direct copy of a declared
  `DLOAD`.
- Requires exactly one such dominating store before the first chain call and
  rejects any overlapping overwrite through the final chain call.
- Keeps all double arithmetic in the retained JVM bytecode prefix and reuses
  one hidden bridge plus the existing path id.

### Still rejected

- Extra-local int, long, and float chain operands.
- `DNEG` of an extra-local, computed value, or constant.
- Extra-local stores fed by computed trees or another extra local.
- Five-or-more nested binary levels in the int, long, float, and double
  families.
- Other unlisted constructor-chain inputs.

### Verification

```text
CC=gcc CXX=g++ ./gradlew :obfuscator:test --rerun-tasks \
  --tests by.radioegor146.ir.IrCompilerTest \
  --tests by.radioegor146.CodegenModeTest
```

JUnit XML under `obfuscator/build/test-results/test/`:

- `IrCompilerTest`: 353 tests, 0 failures, 0 errors, 0 skipped.
- `CodegenModeTest`: 7 tests, 0 failures, 0 errors, 0 skipped.
- Total: 360 tests, 0 failures, 0 errors, 0 skipped.

### Ship-ready

**No.** This increment does not change the `--codegen`, `--ir-lower`, or
`--backend` defaults.

## 中文

### 摘要

- 仅接纳原 `double-extra-local` 遗留项：double 构造器链输入的额外局部变量
  叶子，且其前缀 `DSTORE` 必须直接复制已声明参数的 `DLOAD`。
- 要求首次构造器链调用前恰好存在一次支配所有调用路径的写入，并拒绝直到
  最后一次链调用为止的任何重叠覆写。
- 所有 double 运算仍保留在 JVM 字节码前缀中，并复用单个隐藏桥接方法及
  现有路径编号。

### 仍然拒绝

- 使用额外局部变量的 int、long 和 float 构造器链操作数。
- 对额外局部变量、计算值或常量执行 `DNEG`。
- 从计算树或另一个额外局部变量写入的额外局部变量。
- int、long、float 和 double 各族中五层及以上的嵌套二元运算。
- 其他未列出的构造器链输入。

### 验证

```text
CC=gcc CXX=g++ ./gradlew :obfuscator:test --rerun-tasks \
  --tests by.radioegor146.ir.IrCompilerTest \
  --tests by.radioegor146.CodegenModeTest
```

`obfuscator/build/test-results/test/` 下的 JUnit XML：

- `IrCompilerTest`：353 个测试，0 失败，0 错误，0 跳过。
- `CodegenModeTest`：7 个测试，0 失败，0 错误，0 跳过。
- 合计：360 个测试，0 失败，0 错误，0 跳过。

### 可发布

**否。** 本增量不修改 `--codegen`、`--ir-lower` 或 `--backend` 的默认值。
