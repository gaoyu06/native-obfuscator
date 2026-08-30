## English

### What changed

- Admitted isolated five-argument int-family constructor-chain inputs:
  `NEW java/util/GregorianCalendar; DUP; ICONST_1; ICONST_2; ICONST_3;
  ICONST_4; ICONST_5;
  INVOKESPECIAL java/util/GregorianCalendar.<init>(IIIII)V`.
- Changed `previousProvenNewChainInput`'s initializer-argument cap from
  `> 4` to `> 5`. Each argument must still be a single-instruction proven
  int-family leaf, and six or more arguments remain rejected.
- Kept the complete allocation and initializer sequence in the retained JVM
  prefix. The native body contains neither the allocation nor its initializer,
  and the rewrite uses one hidden bridge through the singular
  `MethodContext.proxyMethod`.
- Replaced the promoted five-argument reject fixture with the JVM-loadable
  `new-constructor-six-arguments` boundary fixture using
  `GregorianCalendar.<init>(IIIIII)V`.

### Tests

- `admitsThreeImmediateReturnsWithNewFiveArgChainInputs`
- `rewrittenThreeImmediateNewFiveArgChainInputsPassJvmVerification`
- `threeImmediateNewFiveArgChainInputsCompileAndRunWithJavaParity`

Gate:

```bash
CC=gcc CXX=g++ ./gradlew :obfuscator:test --rerun-tasks \
  --tests by.radioegor146.ir.IrCompilerTest \
  --tests by.radioegor146.CodegenModeTest
```

Local JUnit XML totals from this child run:

- `TEST-by.radioegor146.ir.IrCompilerTest.xml`: 500 tests, 0 failures,
  0 errors, 0 skipped.
- `TEST-by.radioegor146.CodegenModeTest.xml`: 7 tests, 0 failures, 0 errors,
  0 skipped.
- Total: 507 tests, 0 failures, 0 errors, 0 skipped.

The parent will re-run the gate and discard these child totals.

Admitted: **Yes**

Ship-ready: **No**

## 中文

### 变更内容

- 放行隔离的五参数 int-family 构造器链输入：
  `NEW java/util/GregorianCalendar; DUP; ICONST_1; ICONST_2; ICONST_3;
  ICONST_4; ICONST_5;
  INVOKESPECIAL java/util/GregorianCalendar.<init>(IIIII)V`。
- 将 `previousProvenNewChainInput` 的初始化参数上限从 `> 4` 改为
  `> 5`。每个参数仍必须是单条指令且已证明的 int-family 叶子；六个及
  更多参数继续拒绝。
- 完整分配及初始化序列保留在 JVM 前缀。原生方法体不包含该 `NEW` 或
  初始化调用；改写仅通过唯一的 `MethodContext.proxyMethod` 使用一个
  隐藏桥接方法。
- 用可通过 JVM 加载验证的六参数边界拒绝夹具
  `new-constructor-six-arguments` 取代已放行的五参数拒绝夹具；该夹具
  使用 `GregorianCalendar.<init>(IIIIII)V`。

### 测试

- `admitsThreeImmediateReturnsWithNewFiveArgChainInputs`
- `rewrittenThreeImmediateNewFiveArgChainInputsPassJvmVerification`
- `threeImmediateNewFiveArgChainInputsCompileAndRunWithJavaParity`

门禁命令：

```bash
CC=gcc CXX=g++ ./gradlew :obfuscator:test --rerun-tasks \
  --tests by.radioegor146.ir.IrCompilerTest \
  --tests by.radioegor146.CodegenModeTest
```

本次子任务运行的本地 JUnit XML 汇总：

- `TEST-by.radioegor146.ir.IrCompilerTest.xml`：500 个测试，0 失败，
  0 错误，0 跳过。
- `TEST-by.radioegor146.CodegenModeTest.xml`：7 个测试，0 失败，
  0 错误，0 跳过。
- 总计：507 个测试，0 失败，0 错误，0 跳过。

父任务会重新运行门禁并丢弃这些子任务汇总数据。

Admitted（已放行）：**Yes**

Ship-ready（可发布）：**No**
