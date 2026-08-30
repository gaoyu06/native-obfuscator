# Admit isolated constructor NEW with one proven int argument

## English

### Summary

- Extend the constructor-split chain-input proof from the existing isolated
  `NEW owner; DUP; INVOKESPECIAL owner.<init>()V` leaf to
  `NEW owner; DUP; arg; INVOKESPECIAL owner.<init>(X)V`.
- Require exactly one int-family initializer parameter and exactly one
  executable initializer-argument instruction accepted by the existing
  int-family leaf proof. The strict one-instruction boundary admits constants
  and proven `ILOAD` leaves while rejecting `INEG`, array loads, binary
  computations, and other wider expressions.
- Keep exact allocation/initializer ownership and exact allocated-type versus
  chain-argument matching.
- Keep the complete `NEW; DUP; arg; <init>` sequence in retained JVM bytecode;
  the native body contains neither the allocation `NEW` nor its
  `INVOKESPECIAL`.
- Preserve the no-argument admission and fail closed for missing `DUP`, two or
  more initializer arguments, uninitialized or mismatched allocations,
  unproven/computed initializer inputs, and all array-allocation opcodes.
- Keep one hidden bridge and one singular `MethodContext.proxyMethod` per
  rewritten constructor.

### Tests

New coverage:

- `admitsThreeImmediateReturnsWithNewOneArgChainInputs()`
- `rewrittenThreeImmediateNewOneArgChainInputsPassJvmVerification()`
- `threeImmediateNewOneArgChainInputsCompileAndRunWithJavaParity()`

The admitted fixture uses classfile 52 bytecode and
`StringBuilder.<init>(I)V` with `ICONST_1`. It exercises selectors `7`, `-7`,
and `0`, and compares plain Java with CMake/g++ JNI under
`-Xverify:all -Xcheck:jni`.

The existing rejection and untouched-JVM-verification tests now cover
two-argument, computed-argument, and `GETSTATIC`-argument initializers. Missing
`DUP` remains a pre-mutation reject but is excluded from JVM loading because
that bytecode is verifier-invalid.

Required child gate:

```text
CC=gcc CXX=g++ ./gradlew :obfuscator:test --rerun-tasks \
  --tests by.radioegor146.ir.IrCompilerTest \
  --tests by.radioegor146.CodegenModeTest
```

JUnit XML results from `obfuscator/build/test-results/test/`:

- `IrCompilerTest`: 482 tests, 0 skipped, 0 failures, 0 errors.
- `CodegenModeTest`: 7 tests, 0 skipped, 0 failures, 0 errors.
- Total: 489 tests, 0 skipped, 0 failures, 0 errors.

Defaults remain `--codegen=legacy`, `--ir-lower=direct`, and `--backend=cpp`.

Admitted: **Yes**  
Pushed by child: **Yes**  
Ship-ready: **No**

## 中文

### 摘要

- 将构造器拆分的链调用参数证明，从现有的独立
  `NEW owner; DUP; INVOKESPECIAL owner.<init>()V` 叶子扩展到
  `NEW owner; DUP; arg; INVOKESPECIAL owner.<init>(X)V`。
- 严格要求初始化器只有一个 int-family 参数，且 `arg` 只能是现有
  int-family 叶子证明接受的一条可执行指令。这个单指令边界允许常量和
  已证明的 `ILOAD`，同时拒绝 `INEG`、数组读取、二元计算及其他更宽的
  表达式。
- 保持分配类型与初始化器 owner 精确一致，并要求分配类型与链调用参数
  类型精确匹配。
- 完整的 `NEW; DUP; arg; <init>` 序列保留在 JVM 字节码前缀中；原生
  方法体不包含该分配的 `NEW` 或 `INVOKESPECIAL`。
- 保留现有无参准入，并继续以 fail-closed 方式拒绝缺少 `DUP`、两个或
  更多初始化参数、未初始化或类型不匹配的分配、未证明/计算得到的初始化
  参数，以及所有数组分配操作码。
- 每个重写构造器仍只有一个隐藏桥接方法和一个
  `MethodContext.proxyMethod`。

### 测试

新增三个测试，分别覆盖准入、Java 8 JVM 校验，以及普通 Java 与
CMake/g++ JNI 的输出一致性。准入样例使用 classfile 52、
`StringBuilder.<init>(I)V` 和 `ICONST_1`，覆盖 selector `7`、`-7`、`0`，
并在 `-Xverify:all -Xcheck:jni` 下运行。

现有拒绝测试和未变换 JVM 校验测试新增覆盖双参数、计算参数和
`GETSTATIC` 参数初始化器。缺少 `DUP` 的形状仍在变换前被拒绝，但因为
其字节码本身无法通过校验，所以不进入 JVM 加载测试。

子任务已执行上述必需门禁。`obfuscator/build/test-results/test/` 中的
JUnit XML 结果：

- `IrCompilerTest`：482 个测试，0 跳过，0 失败，0 错误。
- `CodegenModeTest`：7 个测试，0 跳过，0 失败，0 错误。
- 总计：489 个测试，0 跳过，0 失败，0 错误。

默认值仍为 `--codegen=legacy`、`--ir-lower=direct` 和 `--backend=cpp`。

已准入：**是**  
子任务已推送：**是**  
可发布：**否**
