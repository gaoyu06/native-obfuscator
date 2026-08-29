# Leaf-only `LADD` constructor chain inputs / 构造器链输入的叶子级 `LADD`

## Summary / 摘要

- Admits the targeted post-#212 `long-binary` leftover shape: when a constructor
  chain-call argument expects `long`, one outer `LADD` may combine two proven
  long leaves.
- The admitted leaves are a matching declared-argument `LLOAD`, `LCONST_0`,
  `LCONST_1`, or `LDC` of `Long`. Direct declared long loads remain admitted.
- The long walker has a separate depth budget of exactly one and does not
  change the four-level int-family budget. The arithmetic remains in the
  retained JVM bytecode prefix, preserving Java wrapping-add semantics.

- 接纳本增量针对的 #212 之后 `long-binary` 剩余形状：当构造器链调用参数需要
  `long` 时，允许一个最外层 `LADD` 组合两个已证明的 long 叶子。
- 可接纳叶子仅为匹配声明参数的 `LLOAD`、`LCONST_0`、`LCONST_1` 或
  `Long` 类型的 `LDC`；原有的声明 long 参数直接加载保持接纳。
- long walker 使用独立且严格为一层的深度预算，不改变 int-family 的四层
  预算。运算仍在保留的 JVM 字节码前缀执行，因此保持 Java long 环绕加法
  语义。

## Still rejected / 仍然拒绝

- Nested `LADD`; `LNEG`; `LSUB`, `LMUL`, `LAND`, `LOR`, `LXOR`, `LSHL`,
  `LSHR`, `LUSHR`, `LDIV`, `LREM`, and every other long computed input.
- An `LADD` operand loaded from an extra local rather than a declared long
  argument.
- Float, double, and reference computed inputs, including `FADD`, `DADD`, and
  `AALOAD`.
- Five-or-more-level int-family binary trees; skip-super paths; remaining mixed
  catch layouts that span suffixes or cover a chain call; more than eight
  distinct paths; extras unassigned on a bridge-taking path; unsafe condy
  shapes; and `jsr` / `ret`.

- 仍拒绝嵌套 `LADD`、`LNEG`、`LSUB`、`LMUL`、`LAND`、`LOR`、`LXOR`、
  `LSHL`、`LSHR`、`LUSHR`、`LDIV`、`LREM` 以及其他 long 计算输入。
- 仍拒绝从额外局部变量而不是声明 long 参数加载的 `LADD` 操作数。
- 仍拒绝 float、double、reference 计算输入，包括 `FADD`、`DADD` 和
  `AALOAD`。
- 仍拒绝五层或更多 int-family 二叉树、跳过 super 的路径、跨 suffix 或
  覆盖链调用的剩余混合 catch、超过八条不同路径、进入 bridge 的路径上未
  赋值的 extras、不安全 condy，以及 `jsr` / `ret`。

## Verification / 验证

```text
CC=gcc CXX=g++ ./gradlew :obfuscator:test --rerun-tasks \
  --tests by.radioegor146.ir.IrCompilerTest \
  --tests by.radioegor146.CodegenModeTest
```

JUnit XML under `obfuscator/build/test-results/test/` / JUnit XML 结果：

- `IrCompilerTest`: 282 tests, 0 failures, 0 errors, 0 skipped.
- `CodegenModeTest`: 7 tests, 0 failures, 0 errors, 0 skipped.
- Total / 合计: 289 tests, 0 failures, 0 errors, 0 skipped.

The gate includes dedicated IR admission, rewritten-constructor JVM
verification, and compile-and-run Java parity through the singular hidden
bridge. / 门禁包含专门的 IR 接纳测试、重写构造器 JVM 验证，以及通过唯一
hidden bridge 的编译运行 Java 行为一致性测试。

## Ship-ready / 可直接上线

**No / 否。** This is one bounded leftover increment; the unsupported shapes
listed above remain fail-closed. / 这只是一个有界剩余增量；上述不支持形状
仍保持 fail-closed。

- Pushed branch / 已推送分支: `cursor/ir-ctor-ladd-6d81-4c60`
- HEAD SHA (focused gate) / HEAD SHA（聚焦门禁）:
  `35aa6099600b4922215028fdfd0aa589cdedc0ff`
- Files changed / 变更文件:
  `ConstructorSpecialMethodProcessor.java`, `IrCompilerTest.java`,
  `docs/architecture/ir-flex-ctor-status.md`, `PR_BODY.md`
