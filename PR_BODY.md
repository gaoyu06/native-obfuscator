# Admit leaf-only long `LDIV` / `LREM` constructor chain inputs

## English

### Scope

- Admits the remaining leaf-only `LDIV` and `LREM` shapes as long constructor
  chain-call inputs: a declared long-argument `LLOAD` plus `LCONST_1`.
- Keeps `MAX_PROVEN_LONG_CHAIN_BINARY_LEVELS = 1`. Both operands must be
  non-recursive proven long leaves; division and remainder are not added to an
  unbounded recursive set or to the long-shift branch.
- The admitted operations remain in the retained JVM bytecode constructor
  prefix. Java divide-by-zero and signed-overflow behavior therefore remain
  JVM behavior, not C++ IR arithmetic.
- Adds admission, rewritten-class JVM verification, and complete
  Java-vs-native hidden-bridge parity tests for both opcodes.
- Adds fail-closed fixtures for nested outer `LDIV`, inner `LDIV`, and
  extra-local `LDIV` / `LREM` operands.

### Still rejected

- nested `LADD` / `LDIV`, extra-local long operands (including long shift value
  or count leftovers), and `LNEG`;
- float, double, and reference computed chain inputs;
- five-or-more int-family binary levels;
- skip-super paths, remaining mixed prefix/suffix catch placements, and more
  than eight constructor paths;
- extras unassigned on a bridge-taking path;
- unsafe/unproven ConstantDynamic shapes; and
- `jsr` / `ret`.

### Parent verification

```text
CC=gcc CXX=g++ ./gradlew :obfuscator:test --rerun-tasks \
  --tests by.radioegor146.ir.IrCompilerTest \
  --tests by.radioegor146.CodegenModeTest
```

JUnit XML:

- `IrCompilerTest`: 294 tests, 0 failures, 0 errors, 0 skipped.
- `CodegenModeTest`: 7 tests, 0 failures, 0 errors, 0 skipped.
- Total: 301 tests, 0 failures, 0 errors, 0 skipped.

Ship-ready: **No**

## 中文

### 范围

- 接纳剩余的叶子级 `LDIV` / `LREM` long 构造器链调用参数：
  已声明 long 参数的 `LLOAD` 加 `LCONST_1`。
- 保持 `MAX_PROVEN_LONG_CHAIN_BINARY_LEVELS = 1`。两个操作数都必须是
  非递归、已证明的 long 叶子；不会把除法或取余加入无界递归集合，也不会
  放入 long 移位分支。
- 接纳的运算仍在保留的 JVM 构造器字节码前缀中执行。因此 Java 除零和
  有符号溢出语义仍由 JVM 保证，不转成 C++ IR 算术。
- 为两个操作码新增接纳测试、改写后 JVM 验证测试，以及通过唯一隐藏桥的
  Java/原生完整编译运行一致性测试。
- 新增外层嵌套 `LDIV`、内层 `LDIV` 与额外局部变量
  `LDIV` / `LREM` 操作数的 fail-closed 拒绝夹具。

### 仍然拒绝

- 嵌套 `LADD` / `LDIV`、额外局部变量 long 操作数（包括 long 移位值或
  计数遗留形状）以及 `LNEG`；
- float、double 与 reference 计算型链调用参数；
- 五层及以上 int-family 二元运算；
- skip-super 路径、剩余 prefix/suffix 混合 catch 放置以及超过八条构造器
  路径；
- 在会进入隐藏桥的路径上未赋值的 extra；
- 不安全或未证明的 ConstantDynamic 形状；以及
- `jsr` / `ret`。

### 父级验证

```text
CC=gcc CXX=g++ ./gradlew :obfuscator:test --rerun-tasks \
  --tests by.radioegor146.ir.IrCompilerTest \
  --tests by.radioegor146.CodegenModeTest
```

JUnit XML：

- `IrCompilerTest`：294 个测试，0 failure，0 error，0 skipped。
- `CodegenModeTest`：7 个测试，0 failure，0 error，0 skipped。
- 合计：301 个测试，0 failure，0 error，0 skipped。

可直接上线（Ship-ready）：**No**

Pushed branch / 已推送分支: `cursor/ir-ctor-ldiv-lrem-6d81`

Verified implementation HEAD SHA / 已验证实现 HEAD SHA:
`cdbe594a8e5ecbb2392307ce31d152a973bb5206`

Files changed / 修改文件:
`obfuscator/src/main/java/by/radioegor146/special/ConstructorSpecialMethodProcessor.java`,
`obfuscator/src/test/java/by/radioegor146/ir/IrCompilerTest.java`,
`docs/architecture/ir-flex-ctor-status.md`, `PR_BODY.md`
