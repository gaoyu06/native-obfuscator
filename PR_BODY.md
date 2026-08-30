## English

### Summary

- Admit `LALOAD`, `FALOAD`, and `DALOAD` constructor chain-input leaves when
  both the matching declared wide-array argument and a declared int index are
  copied once into proven prefix extra locals.
- Reuse the already-landed independent array-copy and single-instruction
  `ILOAD` proofs; no production admission rule or binary-depth budget changes
  are needed.
- Fix the stale wide combination fixture and move the valid combination from
  reject coverage to admit, JVM-verification, and CMake/g++ Java-parity
  coverage.

### Exact admitted shape

For `(II[J)V`, `(II[F)V`, or `(II[D)V`, where local 1 is the selector, local 2
is the declared index, and local 3 is the declared array:

```text
ALOAD 3
ASTORE 4
ILOAD 2
ISTORE 5
...
ALOAD 4
ILOAD 5
LALOAD | FALOAD | DALOAD
INVOKESPECIAL <init>
```

The array copy, index copy, and all three array loads remain in retained JVM
bytecode. Each rewritten constructor uses one hidden native bridge.

### Still rejected

- computed or `INEG` indexes;
- computed extra-local array or index stores;
- overwritten copied locals or overwritten declared array sources;
- prior array stores;
- wrong array source types;
- skip-super paths; and
- trees beyond the existing 16-level binary budget.

The CLI default remains `legacy`.

### Validation

```text
CC=gcc CXX=g++ ./gradlew :obfuscator:test --rerun-tasks \
  --tests by.radioegor146.ir.IrCompilerTest \
  --tests by.radioegor146.CodegenModeTest
```

Rebased onto `origin/master` `a37801f` (post-#261 fail-closed prefix-suffix
audit). Parent-verified JUnit XML after rebase: `IrCompilerTest` 458/458 and
`CodegenModeTest` 7/7, with zero failures, errors, or skips (total 465). The
runtime test is
`threeImmediateWideExtraArrayExtraIndexCompileAndRunWithJavaParity`.

Ship-ready: **No**

## 中文

### 概要

- 构造器链参数现在可接收 `LALOAD`、`FALOAD`、`DALOAD` 叶子：匹配的已声明宽
  数组参数先复制到已证明的前缀额外局部变量，同时已声明的整数索引也复制到已
  证明的前缀额外局部变量。
- 直接组合已经合入的数组复制证明和单指令 `ILOAD` 证明；无需修改生产代码的
  准入规则，也不提高二叉树深度预算。
- 修正过期的宽数组组合 fixture，并将有效组合从拒绝用例移入准入、JVM 验证及
  CMake/g++ Java 一致性覆盖。

### 精确准入形状

对于 `(II[J)V`、`(II[F)V` 或 `(II[D)V`，局部变量 1 是选择器，2 是已声明
索引，3 是已声明数组：

```text
ALOAD 3
ASTORE 4
ILOAD 2
ISTORE 5
...
ALOAD 4
ILOAD 5
LALOAD | FALOAD | DALOAD
INVOKESPECIAL <init>
```

数组复制、索引复制和三个数组读取都保留在 JVM 字节码中。每个重写后的构造器
只使用一个隐藏原生桥接方法。

### 仍然拒绝

- 计算得到的索引或 `INEG` 索引；
- 通过计算结果写入的数组或索引额外局部变量；
- 被覆盖的复制局部变量或被覆盖的已声明数组源；
- 之前发生过数组写入；
- 类型错误的数组源；
- 跳过 super 构造器的路径；
- 超过现有 16 层二叉预算的树。

命令行默认代码生成模式仍为 `legacy`。

### 验证

```text
CC=gcc CXX=g++ ./gradlew :obfuscator:test --rerun-tasks \
  --tests by.radioegor146.ir.IrCompilerTest \
  --tests by.radioegor146.CodegenModeTest
```

已变基到 `origin/master` `a37801f`（#261 之后的前缀到后缀失败关闭审计）。
父代理变基后聚焦 JUnit XML：`IrCompilerTest` 458/458、`CodegenModeTest` 7/7，
失败、错误和跳过均为 0（合计 465）。运行时测试为
`threeImmediateWideExtraArrayExtraIndexCompileAndRunWithJavaParity`。

可发布：**否**
