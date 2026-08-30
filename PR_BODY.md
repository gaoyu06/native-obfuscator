## English

### Summary

- Admit one reference chain-input leaf only:
  `NEW owner; DUP; INVOKESPECIAL owner.<init>()V`.
- Require an adjacent `DUP`, the same allocation/initializer owner, an exact
  no-argument `()V` initializer, and an allocated descriptor exactly matching
  the chain-call argument.
- Keep the complete allocation sequence in the retained JVM constructor
  prefix, with one hidden native bridge and one `MethodContext.proxyMethod`.
- Continue rejecting uninitialized `NEW`, constructor arguments, mismatched
  allocation types, and all array-allocation opcodes. The sixteen-level binary
  budget is unchanged.

### Tests

Added:

- `admitsThreeImmediateReturnsWithNewArgChainInputs`
- `rewrittenThreeImmediateNewArgChainInputsPassJvmVerification`
- `threeImmediateNewArgChainInputsCompileAndRunWithJavaParity`
- `rejectsUnprovenNewChainInputsBeforeMutation`

Focused gate:

```text
CC=gcc CXX=g++ ./gradlew :obfuscator:test --rerun-tasks \
  --tests by.radioegor146.ir.IrCompilerTest \
  --tests by.radioegor146.CodegenModeTest
```

Child-only JUnit XML:

- `IrCompilerTest`: 468 tests, 0 failures, 0 errors, 0 skipped.
- `CodegenModeTest`: 7 tests, 0 failures, 0 errors, 0 skipped.
- Total: 475 tests, 0 failures, 0 errors, 0 skipped.

The default codegen mode remains `legacy`.

Ship-ready: **No**

## 中文

### 摘要

- 仅新增一种引用类型的构造器链调用参数叶子：
  `NEW owner; DUP; INVOKESPECIAL owner.<init>()V`。
- 要求 `DUP` 紧邻、`NEW` 与初始化调用的 owner 完全一致、内部构造器描述符
  必须为无参 `()V`，且分配类型描述符与链调用参数完全匹配。
- 完整对象分配序列保留在 JVM 构造器前缀中；重写后仍只有一个隐藏 native
  bridge，且 `MethodContext.proxyMethod` 唯一。
- 仍拒绝未初始化的 `NEW`、带参数的内部构造器、类型不匹配的分配，以及所有
  数组分配指令。十六层二元表达式预算保持不变。

### 测试

新增：

- `admitsThreeImmediateReturnsWithNewArgChainInputs`
- `rewrittenThreeImmediateNewArgChainInputsPassJvmVerification`
- `threeImmediateNewArgChainInputsCompileAndRunWithJavaParity`
- `rejectsUnprovenNewChainInputsBeforeMutation`

已通过上方 focused gate。子任务专属 JUnit XML 结果：

- `IrCompilerTest`：468 个测试，0 failure，0 error，0 skipped。
- `CodegenModeTest`：7 个测试，0 failure，0 error，0 skipped。
- 合计：475 个测试，0 failure，0 error，0 skipped。

默认 codegen 模式仍为 `legacy`。

Ship-ready: **No**
