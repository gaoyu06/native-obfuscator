# Bounded constructor-chain `LNEG` leaf / 有界构造器链 `LNEG` 叶节点

## English

### (a) Leftover admitted

This increment admits one `LNEG` over a declared long-argument `LLOAD` as a
constructor chain-input long leaf. The operation stays in the retained JVM
bytecode prefix. It does not consume the unchanged one-level long binary
budget.

### (b) Still rejected

- nested `LADD`/`LDIV` trees and extra-local operands;
- `LNEG` of a constant or computed tree, double `LNEG`, and extra-local
  `LNEG` operands;
- float, double, and reference computed chain inputs;
- five-or-more int-family binary levels and skip-super paths;
- remaining mixed prefix/suffix catch shapes, more than eight paths, and
  unassigned extras on bridge-taking paths;
- unsafe `ConstantDynamic` shapes and `jsr`/`ret`.

### (c) Parent verification

```text
CC=gcc CXX=g++ ./gradlew :obfuscator:test --rerun-tasks \
  --tests by.radioegor146.ir.IrCompilerTest \
  --tests by.radioegor146.CodegenModeTest
```

JUnit XML:

- `IrCompilerTest`: 297 tests, 0 failures, 0 errors, 0 skipped.
- `CodegenModeTest`: 7 tests, 0 failures, 0 errors, 0 skipped.
- Total: 304 tests, 0 failures, 0 errors, 0 skipped.

### (d) Ship-ready

**No.**

## 中文

### (a) 本次接纳的剩余项

本增量只接纳一种构造器链长整型输入叶节点：对已声明 long 参数的直接
`LLOAD` 执行一次 `LNEG`。该运算仍保留在 JVM 字节码前缀中，且不消耗保持
为一层的 long 二元运算预算。

### (b) 仍然拒绝

- 嵌套 `LADD`/`LDIV` 树和额外局部变量操作数；
- 对常量或计算树执行 `LNEG`、双重 `LNEG`，以及对额外局部变量执行
  `LNEG`；
- float、double 和引用类型的计算输入；
- 五层及以上 int-family 二元树和跳过 super 调用的路径；
- 其余跨前缀/后缀的混合 catch、超过八条路径，以及到达 bridge
  的路径上未赋值的额外局部变量；
- 不安全的 `ConstantDynamic` 形状和 `jsr`/`ret`。

### (c) 父任务验证

执行了上面的完整命令。JUnit XML 记录：

- `IrCompilerTest`：297 个测试，0 failure，0 error，0 skipped。
- `CodegenModeTest`：7 个测试，0 failure，0 error，0 skipped。
- 合计：304 个测试，0 failure，0 error，0 skipped。

### (d) 可直接上线

**否。**

Pushed branch / 已推送分支: `cursor/ir-ctor-lneg-6d81-afca`

Tested HEAD before this PR-body-only update / 本 PR 说明更新前已测试 HEAD:
`94dd4e473922ebb5505ac8ba68309564a3ef2455`

Files changed / 修改文件:
`PR_BODY.md`,
`docs/architecture/ir-flex-ctor-status.md`,
`obfuscator/src/main/java/by/radioegor146/special/ConstructorSpecialMethodProcessor.java`,
`obfuscator/src/test/java/by/radioegor146/ir/IrCompilerTest.java`
