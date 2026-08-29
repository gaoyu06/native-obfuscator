# Leaf-only long bitwise constructor chain inputs / 仅叶节点 long 位运算构造器链输入

## Summary / 摘要

**EN**

- Admits one bounded leftover: leaf-only `LAND`, `LOR`, and `LXOR` as
  constructor chain-call inputs when the expected argument is `long`.
- Both operands must remain proven long leaves: a declared-argument `LLOAD`,
  `LCONST_0`, `LCONST_1`, or an `LDC` containing a `Long`.
- The long binary depth budget remains `1`; the int-family budget remains `4`.
  These operations execute in the retained JVM bytecode prefix.

**中文**

- 接纳一个有界剩余项：当构造器链调用的预期参数为 `long` 时，允许仅由已证明
  叶节点组成的一层 `LAND`、`LOR` 和 `LXOR`。
- 两个操作数仍必须是已证明的 long 叶节点：已声明参数的 `LLOAD`、
  `LCONST_0`、`LCONST_1`，或常量为 `Long` 的 `LDC`。
- long 二元运算深度预算保持为 `1`，int-family 预算保持为 `4`。这些运算仍在
  保留的 JVM 字节码前缀中执行。

## Still rejected / 仍然拒绝

**EN**

- nested long binaries, including nested `LADD`;
- extra-local long operands;
- `LDIV`, `LREM`, `LSHL`, `LSHR`, `LUSHR`, and `LNEG`;
- float, double, and reference computed chain inputs;
- five-or-more int-family binary levels;
- paths that skip the this/super call;
- remaining mixed prefix/suffix catch-table placements;
- more than eight distinct constructor paths;
- extras unassigned on a bridge-taking path;
- unsafe or unproven constant-dynamic shapes; and
- `jsr` / `ret`.

**中文**

- 嵌套 long 二元表达式，包括嵌套 `LADD`；
- 使用额外局部变量的 long 操作数；
- `LDIV`、`LREM`、`LSHL`、`LSHR`、`LUSHR` 和 `LNEG`；
- float、double 和引用类型的计算型链调用输入；
- 五层或更多 int-family 二元表达式；
- 跳过 this/super 调用的路径；
- 尚未支持的前缀/后缀混合 catch 表布局；
- 超过八条不同构造器路径；
- 在进入隐藏桥接的方法路径上未赋值的额外局部变量；
- 不安全或未经证明的 ConstantDynamic 形态；以及
- `jsr` / `ret`。

## Verification / 验证

Parent-verify command / 父级验证命令：

```bash
CC=gcc CXX=g++ ./gradlew :obfuscator:test --rerun-tasks \
  --tests by.radioegor146.ir.IrCompilerTest \
  --tests by.radioegor146.CodegenModeTest
```

JUnit XML results / JUnit XML 结果：

- `IrCompilerTest`: 288 tests, 0 failures, 0 errors, 0 skipped.
- `CodegenModeTest`: 7 tests, 0 failures, 0 errors, 0 skipped.
- Total / 合计: 295 tests, 0 failures, 0 errors, 0 skipped.

Ship-ready / 可直接上线：**No / 否**

Pushed branch / 已推送分支：`cursor/ir-ctor-land-lor-lxor-6d81-8bd3`  
HEAD SHA（tested implementation / 已验证实现）：`c50fc9dd32963c65dd3d83886f7d45efbd8ed02a`  
Files changed / 修改文件：`PR_BODY.md`, `docs/architecture/ir-flex-ctor-status.md`, `obfuscator/src/main/java/by/radioegor146/special/ConstructorSpecialMethodProcessor.java`, `obfuscator/src/test/java/by/radioegor146/ir/IrCompilerTest.java`
