## English

### Summary

- Admit isolated six-argument int-family `NEW` constructor-chain inputs.
- Change `ConstructorSpecialMethodProcessor.previousProvenNewChainInput` from
  rejecting `initializerArguments.length > 5` to rejecting
  `initializerArguments.length > 6`; seven or more arguments still fail closed.
- Keep the complete `NEW; DUP; six single-instruction int-family leaves;
  INVOKESPECIAL <init>` allocation in the retained JVM prefix.
- Add verifier-valid synthetic `example/SevenIntHolder.<init>(IIIIIII)V` as
  the new seven-argument reject boundary.

### Tests

- `admitsThreeImmediateReturnsWithNewSixArgChainInputs`
- `rewrittenThreeImmediateNewSixArgChainInputsPassJvmVerification`
- `threeImmediateNewSixArgChainInputsCompileAndRunWithJavaParity`

Focused gate:

```text
CC=gcc CXX=g++ ./gradlew :obfuscator:test --rerun-tasks \
  --tests by.radioegor146.ir.IrCompilerTest \
  --tests by.radioegor146.CodegenModeTest
```

Local XML:

- `obfuscator/build/test-results/test/TEST-by.radioegor146.ir.IrCompilerTest.xml`:
  505 tests, 0 failures, 0 errors, 0 skipped.
- `obfuscator/build/test-results/test/TEST-by.radioegor146.CodegenModeTest.xml`:
  7 tests, 0 failures, 0 errors, 0 skipped.

Admitted: Yes  
Ship-ready: No

## 中文

### 摘要

- 接纳独立的六参数 int-family `NEW` 构造器链输入。
- 将 `ConstructorSpecialMethodProcessor.previousProvenNewChainInput` 的拒绝
  条件从 `initializerArguments.length > 5` 调整为
  `initializerArguments.length > 6`；七个及以上参数仍保持 fail-closed。
- 完整的 `NEW; DUP; 六个单指令 int-family 叶节点;
  INVOKESPECIAL <init>` 分配序列继续保留在 JVM 前缀中。
- 新增可通过 JVM 验证的合成类
  `example/SevenIntHolder.<init>(IIIIIII)V`，作为新的七参数拒绝边界。

### 测试

- `admitsThreeImmediateReturnsWithNewSixArgChainInputs`
- `rewrittenThreeImmediateNewSixArgChainInputsPassJvmVerification`
- `threeImmediateNewSixArgChainInputsCompileAndRunWithJavaParity`

已运行上述 focused gate；本地 XML 结果为 `IrCompilerTest` 505/505 和
`CodegenModeTest` 7/7，均无失败、错误或跳过。

Admitted: Yes  
Ship-ready: No
