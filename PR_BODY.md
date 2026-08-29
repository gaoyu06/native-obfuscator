# Bounded two-level long constructor chain inputs / 有界两层 long 构造器链输入

## (a) Leftover admitted / 已接纳缺口

This increment admits exactly two binary levels in the existing long
constructor chain-input walker. It raises the explicit long-family budget from
one to two without uncapping it. Both long operands recurse with one level
consumed; for `LSHL`/`LSHR`/`LUSHR`, only the long value recurses and the
int-family shift count remains a leaf.

本增量在现有 long 构造器链输入 walker 中只接纳恰好最多两层二元运算。
显式 long 深度预算由 1 提高到 2，但没有取消上限。两个 long 操作数都会在
消耗一层预算后递归；对于 `LSHL`/`LSHR`/`LUSHR`，只有 long 值递归，
int-family 移位计数仍必须是叶子。

Covered positive shapes include the former `"long-nested-ladd"` leftover
`(arg + 1) + 1`, mixed trees with `LDIV` as an outer or inner node, an outer
shift `(arg + 1) << 1`, and a two-sided depth-two `LADD` tree. `LNEG` remains
only a leaf consisting of one `LNEG` over a declared `LLOAD`. The retained JVM
bytecode prefix still executes all chain-input operations, and all paths use
one singular `MethodContext.proxyMethod`.

正向覆盖包括原 `"long-nested-ladd"` 缺口 `(arg + 1) + 1`、`LDIV` 位于
外层或内层的混合树、外层移位 `(arg + 1) << 1`，以及左右两侧都递归的
两层 `LADD` 树。`LNEG` 仍只允许作为“声明参数 `LLOAD` 后接一个 `LNEG`”
的叶子。所有链输入运算仍保留在 JVM 字节码前缀执行，所有路径仍共用唯一
一个 `MethodContext.proxyMethod`。

## (b) Still rejected / 仍然拒绝

- three-or-more nested long binaries;
- extra-local long operands or shift counts;
- `LNEG` of a constant, double `LNEG`, and computed or extra-local `LNEG`;
- float, double, or reference computed chain inputs;
- five-or-more int-family binary levels;
- paths that skip the required this/super call;
- remaining mixed prefix/suffix catch placements;
- more than eight path-selected suffixes;
- extras unassigned on any bridge-taking path;
- unsafe `ConstantDynamic` shapes;
- `jsr` / `ret`.

- 三层及以上嵌套 long 二元运算；
- extra-local long 操作数或移位计数；
- 常量 `LNEG`、双重 `LNEG`、computed 或 extra-local `LNEG`；
- float、double 或 reference computed 链输入；
- 五层及以上 int-family 二元运算；
- 跳过必需 this/super 调用的路径；
- 其余跨 prefix/suffix 的混合 catch 布局；
- 超过八条 path-selected suffix 路径；
- 任一 bridge-taking 路径上未赋值的 extra；
- 不安全的 `ConstantDynamic` 形状；
- `jsr` / `ret`。

## (c) Parent verification / 父级验证

```text
CC=gcc CXX=g++ ./gradlew :obfuscator:test --rerun-tasks \
  --tests by.radioegor146.ir.IrCompilerTest \
  --tests by.radioegor146.CodegenModeTest
```

JUnit XML under `obfuscator/build/test-results/test/`:

- `IrCompilerTest`: 300 tests, 0 failures, 0 errors, 0 skipped.
- `CodegenModeTest`: 7 tests, 0 failures, 0 errors, 0 skipped.
- Total: 307 tests, 0 failures, 0 errors, 0 skipped.

JUnit XML 位于 `obfuscator/build/test-results/test/`：

- `IrCompilerTest`：300 项，0 failures，0 errors，0 skipped。
- `CodegenModeTest`：7 项，0 failures，0 errors，0 skipped。
- 总计：307 项，0 failures，0 errors，0 skipped。

## (d) Ship-ready / 可直接上线

**No / 否。**

Pushed branch / 推送分支: `cursor/ir-ctor-nested-long-6d81-f45f`  
HEAD SHA: `8c81f993744756afcfea3a09c86d2d9e6b942bb7` (verified implementation HEAD before this handoff-document commit)  
Files changed / 变更文件:
- `PR_BODY.md`
- `docs/architecture/ir-flex-ctor-status.md`
- `obfuscator/src/main/java/by/radioegor146/special/ConstructorSpecialMethodProcessor.java`
- `obfuscator/src/test/java/by/radioegor146/ir/IrCompilerTest.java`
