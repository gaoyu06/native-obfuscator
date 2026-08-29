# English

**(a) Scope:** After #183/#184, this increment widens the bounded
constructor-chain argument proof to admit exactly one `IAND`, `IOR`, or `IXOR`
whose two operands are already-proven int-family leaves. The bitwise opcodes
are not leaves themselves, so nested binary expressions remain rejected.
Trapping `IDIV`/`IREM`, shifts, and all other unlisted inputs also remain
rejected.

**(b) Ship-ready?** **No.** This is one narrow constructor-split increment and
does not complete the production goal or change defaults.

**(c) Review and gate:** There is no stacked review. The gate is the executed
focused suite, including the new bitwise compile-and-run Java parity harness:

```text
CC=gcc CXX=g++ ./gradlew :obfuscator:test --rerun-tasks \
  --tests by.radioegor146.ir.IrCompilerTest \
  --tests by.radioegor146.CodegenModeTest
```

JUnit XML: `IrCompilerTest` 190 tests and `CodegenModeTest` 7 tests; total 197,
with 0 failures, 0 errors, and 0 skipped.

**(d) Preconditions:** Remaining constructor leftovers, unsafe constant
dynamic inputs, and `jsr`/`ret` stay reject-before-mutation. `--codegen` stays
`legacy`.

# 中文

**(a) 范围：** 在 #183/#184 之后，本增量只扩展有界的构造函数链参数证明：
当两个操作数均为已证明的 int-family 叶子时，允许恰好一层 `IAND`、`IOR`
或 `IXOR`。这些位运算操作码本身不属于叶子，因此嵌套二元表达式仍会被拒绝。
可能抛出异常的 `IDIV`/`IREM`、移位以及其他未列出的输入也仍会被拒绝。

**(b) 是否可交付？** **否。** 这只是一个窄范围的构造函数拆分增量，
不会完成生产目标，也不会更改默认选项。

**(c) 审查与门禁：** 没有堆叠审查。门禁为已执行的聚焦测试套件，
其中包含新增的位运算编译运行 Java 一致性测试：

```text
CC=gcc CXX=g++ ./gradlew :obfuscator:test --rerun-tasks \
  --tests by.radioegor146.ir.IrCompilerTest \
  --tests by.radioegor146.CodegenModeTest
```

JUnit XML：`IrCompilerTest` 190 项，`CodegenModeTest` 7 项，共 197 项；
失败 0、错误 0、跳过 0。

**(d) 前置条件：** 其余构造函数遗留形态、不安全的 constant dynamic
输入以及 `jsr`/`ret` 继续在修改前拒绝。`--codegen` 保持为 `legacy`。
