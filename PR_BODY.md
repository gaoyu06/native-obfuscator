## Summary / 摘要

- Admits the remaining exactly-three-level long constructor chain inputs by
  raising the explicit long binary proof budget from 2 to 3. The int-family
  budget remains 4.
- 将构造器调用链 long 输入的显式二元运算证明预算从 2 提升到 3，从而接纳剩余的恰好三层形状；int-family 预算仍为 4。
- Covers left-associated `LADD`, inner and outer `LDIV`, and `LSHL` with an
  int-family count leaf. The retained JVM prefix still executes these trees,
  and each constructor still has one `MethodContext.proxyMethod`.
- 覆盖左结合 `LADD`、内层与外层 `LDIV`，以及使用 int-family 叶子作为位移量的 `LSHL`。这些树仍在 JVM 前缀中执行，每个构造器仍只有一个 `MethodContext.proxyMethod`。

## Still rejected / 仍然拒绝

- Four-or-more nested long binaries remain fail-closed; dedicated four-level
  `LADD` and inner-`LDIV` fixtures verify rejection before mutation.
- 四层及以上 long 二元运算仍按 fail-closed 原则拒绝；新增四层 `LADD` 和内层 `LDIV` 固件验证在修改前拒绝。
- Computed or extra-local shift counts, extra-local long operands, unsupported
  `LNEG` forms, and float/double/reference computed inputs remain rejected.
- 计算得到或来自额外局部变量的位移量、额外局部变量 long 操作数、不受支持的 `LNEG` 形式，以及 float/double/reference 计算输入仍然拒绝。

## Verification / 验证

```text
CC=gcc CXX=g++ ./gradlew :obfuscator:test --rerun-tasks \
  --tests by.radioegor146.ir.IrCompilerTest \
  --tests by.radioegor146.CodegenModeTest
```

- `IrCompilerTest`: tests 303, failures 0, errors 0, skipped 0.
- `CodegenModeTest`: tests 7, failures 0, errors 0, skipped 0.
- Total / 合计: tests 310, failures 0, errors 0, skipped 0.
- Runtime coverage compares stdout from plain Java with the CMake/g++ JNI
  transform under `-Xverify:all -Xcheck:jni`.
- 运行时覆盖在 `-Xverify:all -Xcheck:jni` 下比较普通 Java 与 CMake/g++ JNI 转换后的 stdout。

## Ship-ready / 可发布

**No / 否.** This is one bounded compiler-admission increment; broader
constructor leftovers and the legacy-default migration remain outside this
slice.

**No / 否。** 这是一次有边界的编译器接纳增量；更广泛的构造器剩余项和 legacy 默认路径迁移不在本次范围内。
