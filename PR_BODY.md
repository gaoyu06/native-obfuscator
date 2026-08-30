## English

### Summary

- Admit the isolated two-argument allocation shape `NEW Point; DUP; ILOAD 3; ICONST_2; INVOKESPECIAL Point.<init>(II)V` when local 3 is a proven dominating prefix copy of the unchanged declared int source argument in local 2.
- Keep `ILOAD 2; ISTORE 3`, the complete allocation, and its initializer call in retained JVM bytecode. The native body contains neither the `NEW` nor its `INVOKESPECIAL`, and the rewrite uses one hidden bridge through the singular `MethodContext.proxyMethod`.
- Extend the existing fixture helpers with shape `new-constructor-extra-local-argument-two`; `previousProvenNewChainInput` already proves each initializer through `previousProvenIntChainLeaf(..., prefixIntCopies)`, so no processor change is needed.
- Keep the initializer cap at six. This change does not admit seven-or-more arguments, long/float/double initializer carriers, array allocation, or unproven/computed initializer inputs.
- Keep defaults unchanged: `--codegen=legacy`, `--ir-lower=direct`, and `--backend=cpp`.

### Tests

Added:

- `admitsThreeImmediateReturnsWithNewExtraLocalTwoArgChainInputs`
- `rewrittenThreeImmediateNewExtraLocalTwoArgChainInputsPassJvmVerification`
- `threeImmediateNewExtraLocalTwoArgChainInputsCompileAndRunWithJavaParity`

Gate:

```text
CC=gcc CXX=g++ ./gradlew :obfuscator:test --rerun-tasks \
  --tests by.radioegor146.ir.IrCompilerTest \
  --tests by.radioegor146.CodegenModeTest
```

Child-local JUnit XML under `obfuscator/build/test-results/test/`:

- `IrCompilerTest`: 510 tests, 0 failures, 0 errors, 0 skipped.
- `CodegenModeTest`: 7 tests, 0 failures, 0 errors, 0 skipped.
- Total: 517 tests, 0 failures, 0 errors, 0 skipped.

The parent must rerun this gate after integration and use its own JUnit XML
totals. These child-local counts are not parent totals.

### Readiness

Admitted: **Yes**

Ship-ready: **No**

## 中文

### 摘要

- 当 local 3 是未修改的已声明 int 源参数 local 2 在支配前缀中的已证明副本时，接纳隔离的双参数分配形态：`NEW Point; DUP; ILOAD 3; ICONST_2; INVOKESPECIAL Point.<init>(II)V`。
- 将 `ILOAD 2; ISTORE 3`、完整分配序列及初始化调用保留在 JVM 字节码前缀中。native 方法体既不包含该 `NEW`，也不包含其 `INVOKESPECIAL`；改写通过唯一的 `MethodContext.proxyMethod` 使用一个隐藏桥接方法。
- 使用 `new-constructor-extra-local-argument-two` 扩展现有夹具辅助方法；`previousProvenNewChainInput` 已经通过 `previousProvenIntChainLeaf(..., prefixIntCopies)` 逐一证明初始化参数，因此无需修改处理器。
- 初始化参数上限仍为六个。本次不接纳七个或更多参数、long/float/double 初始化参数、数组分配，或未证明/计算型初始化输入。
- 默认值保持不变：`--codegen=legacy`、`--ir-lower=direct` 和 `--backend=cpp`。

### 测试

新增：

- `admitsThreeImmediateReturnsWithNewExtraLocalTwoArgChainInputs`
- `rewrittenThreeImmediateNewExtraLocalTwoArgChainInputsPassJvmVerification`
- `threeImmediateNewExtraLocalTwoArgChainInputsCompileAndRunWithJavaParity`

门禁命令：

```text
CC=gcc CXX=g++ ./gradlew :obfuscator:test --rerun-tasks \
  --tests by.radioegor146.ir.IrCompilerTest \
  --tests by.radioegor146.CodegenModeTest
```

`obfuscator/build/test-results/test/` 下的子代理本地 JUnit XML 汇总：

- `IrCompilerTest`：510 个测试，0 个失败，0 个错误，0 个跳过。
- `CodegenModeTest`：7 个测试，0 个失败，0 个错误，0 个跳过。
- 总计：517 个测试，0 个失败，0 个错误，0 个跳过。

父级在集成后必须重新运行该门禁并使用自己的 JUnit XML 汇总。这些子代理本地计数不是父级汇总。

### 就绪状态

已接纳：**是**

可发布：**否**
