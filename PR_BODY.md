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

Rebased onto `origin/master` `7a5d000` (post-#267 fail-closed nine-path
audit). Parent-verified JUnit XML after rebase: `IrCompilerTest` 470/470 and
`CodegenModeTest` 7/7, with zero failures, errors, or skips (total 477). The
runtime test is `threeImmediateNewArgChainInputsCompileAndRunWithJavaParity`.

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

已变基到 `origin/master` `7a5d000`（#267 九路径失败关闭审计之后）。父代理
变基后聚焦 JUnit XML：`IrCompilerTest` 470/470、`CodegenModeTest` 7/7，失败、
错误和跳过均为 0（合计 477）。运行时测试为
`threeImmediateNewArgChainInputsCompileAndRunWithJavaParity`。

默认 codegen 模式仍为 `legacy`。

Ship-ready: **No**
