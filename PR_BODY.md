## English

### What changed

- Admits the remaining bounded constructor chain-input cases with exactly four nested long binary levels.
- Covers four-level `LADD`, inner and outer `LDIV`, and `LSHL` with an int-family count leaf.
- Keeps all admitted bytecode trees in the retained JVM prefix and continues to use one `MethodContext.proxyMethod`.
- Leaves the int-family budget at four and the long-family budget explicitly capped at four.

### Still rejected

- Five-or-more nested long binary levels, including a five-level inner-`LDIV` tree.
- Extra-local long operands and extra-local int shift counts.
- Computed shift counts and unproven shift values.
- `LNEG` of constants, double `LNEG`, and extra-local or computed `LNEG`.
- Float, double, and reference computed constructor chain inputs.

### Verification

```text
CC=gcc CXX=g++ ./gradlew :obfuscator:test --rerun-tasks \
  --tests by.radioegor146.ir.IrCompilerTest \
  --tests by.radioegor146.CodegenModeTest
```

- `IrCompilerTest`: tests=306, failures=0, errors=0, skipped=0
- `CodegenModeTest`: tests=7, failures=0, errors=0, skipped=0
- Combined: tests=313, failures=0, errors=0, skipped=0

Ship-ready: **No**

## 中文

### 变更内容

- 接纳构造器链输入中剩余的、有明确边界的四层 long 二元表达式。
- 覆盖四层 `LADD`、内层和外层 `LDIV`，以及计数值为 int-family 叶子的 `LSHL`。
- 所有已接纳的字节码树仍保留在 JVM 前缀中，并继续只使用一个 `MethodContext.proxyMethod`。
- int-family 深度上限保持为四，long-family 深度上限明确保持为四。

### 仍然拒绝

- 五层及以上的 long 二元表达式，包括含内层 `LDIV` 的五层树。
- 使用额外局部变量的 long 操作数和 int 移位计数。
- 计算得到的移位计数和未证明的移位值。
- 对常量执行的 `LNEG`、双重 `LNEG`，以及使用额外局部变量或计算值的 `LNEG`。
- float、double 和引用类型的计算型构造器链输入。

### 验证

```text
CC=gcc CXX=g++ ./gradlew :obfuscator:test --rerun-tasks \
  --tests by.radioegor146.ir.IrCompilerTest \
  --tests by.radioegor146.CodegenModeTest
```

- `IrCompilerTest`：tests=306，failures=0，errors=0，skipped=0
- `CodegenModeTest`：tests=7，failures=0，errors=0，skipped=0
- 合计：tests=313，failures=0，errors=0，skipped=0

可交付状态（Ship-ready）：**No**
