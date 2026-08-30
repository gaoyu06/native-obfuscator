# Isolated double `NEW` constructor arguments / 隔离的 double `NEW` 构造器参数

## English

### Summary

- Admit only isolated retained-prefix `NEW; DUP; DCONST_1; DCONST_0; INVOKESPECIAL <init>(DD)V` constructor-chain arguments.
- Thread proven prefix double copies into `previousProvenNewChainInput` and prove each double initializer argument as a single-instruction leaf.
- Keep the complete `Point2D.Double(DD)` allocation and initializer in the retained JVM prefix. The native `(I)V` body contains only `RETURN`; the rewritten constructor has one hidden bridge.
- Float `NEW` initializer arguments were already admitted before this compose.
- Remove `rejectsUnprovenWideNewChainInputsBeforeMutation` and `unprovenWideNewChainInputShapesPassJava8JvmVerification`: their double-only shape lists became empty after this admission. The broader unproven-`NEW` audit continues to cover unsupported shapes.
- The six-argument cap and all other fail-closed limits remain unchanged.

### Tests

Added:

- `admitsThreeImmediateReturnsWithNewDoubleArgChainInputs`
- `rewrittenThreeImmediateNewDoubleArgChainInputsPassJvmVerification`
- `threeImmediateNewDoubleArgChainInputsCompileAndRunWithJavaParity`

Focused child run:

```text
CC=gcc CXX=g++ ./gradlew :obfuscator:test --rerun-tasks \
  --tests by.radioegor146.ir.IrCompilerTest \
  --tests by.radioegor146.CodegenModeTest
```

Child-local XML only, not parent totals: `IrCompilerTest` 526 + `CodegenModeTest` 7 = 533 tests; 0 failures, 0 errors, 0 skips. The parent re-runs the focused gate.

- Admitted: Yes, this compose only — isolated double `NEW`
- Ship-ready: **No**

## 中文

### 摘要

- 仅准入保留 JVM 前缀中的隔离构造器链参数：`NEW; DUP; DCONST_1; DCONST_0; INVOKESPECIAL <init>(DD)V`。
- 将已证明的前缀 double 副本传入 `previousProvenNewChainInput`，并要求每个 double 初始化参数都是单指令叶节点。
- 完整的 `Point2D.Double(DD)` 分配和初始化保留在 JVM 前缀中。原生 `(I)V` 方法体仅含 `RETURN`；重写后的构造器只有一个隐藏桥接。
- float `NEW` 初始化参数已在本次 compose 之前准入。
- 删除 `rejectsUnprovenWideNewChainInputsBeforeMutation` 和 `unprovenWideNewChainInputShapesPassJava8JvmVerification`：本次准入后，它们仅含 double 的形状列表已为空。更广泛的未证明 `NEW` 审计仍覆盖不支持的形状。
- 六参数上限及其他所有 fail-closed 限制保持不变。

### 测试

新增：

- `admitsThreeImmediateReturnsWithNewDoubleArgChainInputs`
- `rewrittenThreeImmediateNewDoubleArgChainInputsPassJvmVerification`
- `threeImmediateNewDoubleArgChainInputsCompileAndRunWithJavaParity`

子任务本地运行：

```text
CC=gcc CXX=g++ ./gradlew :obfuscator:test --rerun-tasks \
  --tests by.radioegor146.ir.IrCompilerTest \
  --tests by.radioegor146.CodegenModeTest
```

仅为子任务本地 XML，不是父任务总数：`IrCompilerTest` 526 + `CodegenModeTest` 7 = 533 个测试；0 failures、0 errors、0 skips。父任务会重新运行 focused gate。

- Admitted：Yes，仅限本次 compose 的隔离 double `NEW`
- Ship-ready：**No**
