## English

### Summary

- Admit the isolated one-argument allocation shape `NEW StringBuilder; DUP; ILOAD extra; INVOKESPECIAL StringBuilder.<init>(I)V` when the extra local is a proven dominating prefix copy of an unchanged declared int argument.
- Keep the complete allocation and initializer sequence in retained JVM bytecode. The native body contains neither that `NEW` nor its initializer call, and the rewrite uses one hidden bridge through the singular `MethodContext.proxyMethod`.
- This is fixture-only: `previousProvenNewChainInput` already composes `previousProvenIntChainLeaf(..., prefixIntCopies)`, so no processor change was necessary.
- Add fail-closed coverage for an overwritten extra local, an `INEG`-computed extra local, a proven copy paired with a second unproven initializer argument, and a copy of local 0.
- Keep all defaults unchanged and the initializer arity cap at two. This does not admit skip-super control flow, three-or-more initializer arguments, array allocation, or computed/`GETSTATIC`/`INEG` initializer inputs.

### Tests

Added:

- `admitsThreeImmediateReturnsWithNewExtraLocalArgChainInputs`
- `rewrittenThreeImmediateNewExtraLocalArgChainInputsPassJvmVerification`
- `threeImmediateNewExtraLocalArgChainInputsCompileAndRunWithJavaParity`

Gate:

```text
CC=gcc CXX=g++ ./gradlew :obfuscator:test --rerun-tasks \
  --tests by.radioegor146.ir.IrCompilerTest \
  --tests by.radioegor146.CodegenModeTest
```

JUnit XML under `obfuscator/build/test-results/test/`:

- `IrCompilerTest`: 490 tests, 0 failures, 0 errors, 0 skipped.
- `CodegenModeTest`: 7 tests, 0 failures, 0 errors, 0 skipped.
- Total: 497 tests, 0 failures, 0 errors, 0 skipped.

### Readiness

Admitted: **Yes**

Ship-ready: **No**

## 中文

### 摘要

- 当额外局部变量是未修改的已声明 int 参数在支配前缀中的已证明副本时，接纳隔离的单参数分配形态：`NEW StringBuilder; DUP; ILOAD extra; INVOKESPECIAL StringBuilder.<init>(I)V`。
- 完整的分配和初始化序列保留在 JVM 字节码前缀中。native 方法体既不包含该 `NEW`，也不包含其初始化调用；改写通过唯一的 `MethodContext.proxyMethod` 使用一个隐藏桥接方法。
- 本次仅增加夹具：`previousProvenNewChainInput` 已经组合调用 `previousProvenIntChainLeaf(..., prefixIntCopies)`，因此无需修改处理器。
- 增加保守拒绝覆盖：被覆盖的额外局部变量、由 `INEG` 计算后写入的额外局部变量、已证明副本与第二个未证明初始化参数的组合，以及 local 0 的副本。
- 所有默认值保持不变，初始化参数数量上限仍为两个。本次不接纳 skip-super 控制流、三个及以上初始化参数、数组分配或计算型/`GETSTATIC`/`INEG` 初始化输入。

### 测试

新增：

- `admitsThreeImmediateReturnsWithNewExtraLocalArgChainInputs`
- `rewrittenThreeImmediateNewExtraLocalArgChainInputsPassJvmVerification`
- `threeImmediateNewExtraLocalArgChainInputsCompileAndRunWithJavaParity`

门禁命令：

```text
CC=gcc CXX=g++ ./gradlew :obfuscator:test --rerun-tasks \
  --tests by.radioegor146.ir.IrCompilerTest \
  --tests by.radioegor146.CodegenModeTest
```

`obfuscator/build/test-results/test/` 下的 JUnit XML 汇总：

- `IrCompilerTest`：490 个测试，0 个失败，0 个错误，0 个跳过。
- `CodegenModeTest`：7 个测试，0 个失败，0 个错误，0 个跳过。
- 总计：497 个测试，0 个失败，0 个错误，0 个跳过。

### 就绪状态

已接纳：**是**

可发布：**否**
