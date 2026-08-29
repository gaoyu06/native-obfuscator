# Leaf-only long shifts in constructor chain inputs / 构造器链输入中的叶子级 long 移位

## (a) Admitted leftover / 已接纳缺口

- Admits leaf-only `LSHL`, `LSHR`, and `LUSHR` as long constructor
  chain-call inputs.
- The value must be a proven long leaf; the shift count must be a proven
  int-family leaf. Both operands are evaluated by the retained JVM bytecode
  prefix, so JVM shift-count masking remains unchanged and is not reproduced
  in C++.

- 接纳叶子级 `LSHL`、`LSHR`、`LUSHR` 作为 long 构造器链调用输入。
- 左侧值必须是已证明的 long 叶子，右侧移位计数必须是已证明的 int-family
  叶子。两个操作数仍由保留的 JVM 字节码前缀执行，因此 JVM 的移位计数掩码
  语义保持不变，C++ 不重复实现 mask-63。

## (b) Still rejected / 仍然拒绝

- Nested `LADD`; extra-local value or count operands; `LDIV`, `LREM`, and
  `LNEG`; float/double/reference computed inputs; five-or-more int binary
  levels; skip-super paths; remaining mixed catch placements; more than eight
  paths; unassigned extras; unsafe condy; and `jsr`/`ret`.
- 嵌套 `LADD`；extra-local 的值或计数操作数；`LDIV`、`LREM`、`LNEG`；
  float/double/reference 计算输入；五层或更多 int 二元运算；skip-super
  路径；其余 mixed catch 布局；超过八条路径；未赋值 extras；不安全
  condy；以及 `jsr`/`ret`。

The long binary depth budget remains exactly one. Nested long binaries and
unlisted input forms continue to fail closed before constructor or hidden-method
mutation.

long 二元深度预算仍严格为一。嵌套 long 二元运算和未列出的输入形式继续在
修改构造器或分配 hidden method 前 fail closed。

## (c) Verification / 验证

```text
CC=gcc CXX=g++ ./gradlew :obfuscator:test --rerun-tasks \
  --tests by.radioegor146.ir.IrCompilerTest \
  --tests by.radioegor146.CodegenModeTest
```

JUnit XML under `obfuscator/build/test-results/test/`:

- `IrCompilerTest`: 291 tests, 0 failures, 0 errors, 0 skipped.
- `CodegenModeTest`: 7 tests, 0 failures, 0 errors, 0 skipped.
- Total: 298 tests, 0 failures, 0 errors, 0 skipped.

JUnit XML 结果：`IrCompilerTest` 291 项、`CodegenModeTest` 7 项，共 298
项；failure、error、skip 均为 0。验证包括 IR 编译、hidden bridge、JVM
verification，以及 `LSHL`/`LSHR`/`LUSHR` 的 Java/native stdout parity。

## (d) Ship-ready / 可直接上线

**No / 否。**

- Pushed branch name: `cursor/ir-ctor-lshl-6d81-0c9b`
- HEAD SHA: `ef7df737678f7c5fbcbe5b8ca569af85115bbee3` (tested implementation commit)
- Files changed: `PR_BODY.md`, `docs/architecture/ir-flex-ctor-status.md`, `obfuscator/src/main/java/by/radioegor146/special/ConstructorSpecialMethodProcessor.java`, `obfuscator/src/test/java/by/radioegor146/ir/IrCompilerTest.java`
