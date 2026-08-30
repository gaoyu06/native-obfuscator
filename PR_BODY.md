# Verify primitive constructor GETFIELD through proven object copies

## English

### Summary

- Cover isolated constructor chain-input `GETFIELD` for all primitive JVM carriers (`I`, `J`, `F`, and `D`) when the receiver is a proven prefix extra-local copy of an unchanged declared holder argument.
- Reuse the existing production proof: `previousProvenGetfieldChainInput` already composes `provenPrefixObjectCopyLocals` with `sameInvocationCarrier`, so no processor change is needed.
- Keep `ALOAD holder; ASTORE extra`, each receiver load, and each `GETFIELD` in retained JVM bytecode. The native body contains no `GETFIELD`.
- Exercise category-2 `J` and `D` values while preserving the exact four-local layout (`this`, selector, holder, extra holder).
- Preserve the existing fail-closed exclusions for `GETSTATIC`, local 0, copies of `this`, overwritten extra/source locals, computed holders, and mismatched carriers.
- Keep one hidden bridge and one `MethodContext.proxyMethod` per rewritten constructor.

### Tests

New coverage:

- `admitsThreeImmediateReturnsWithGetfieldExtraLocalPrimitiveHolders()`
- `rewrittenThreeImmediateGetfieldExtraLocalPrimitiveHoldersPassJvmVerification()`
- `threeImmediateGetfieldExtraLocalPrimitiveHoldersCompileAndRunWithJavaParity()`

The fixtures use Java 8/classfile 52, selectors `7`, `-7`, and `0`, and all four primitive carriers. The verification test preserves null-holder `NullPointerException` behavior. The runtime test compares plain Java with CMake/g++ JNI under `-Xverify:all -Xcheck:jni`.

Required child gate:

```text
CC=gcc CXX=g++ ./gradlew :obfuscator:test --rerun-tasks \
  --tests by.radioegor146.ir.IrCompilerTest \
  --tests by.radioegor146.CodegenModeTest
```

JUnit XML results from `obfuscator/build/test-results/test/`:

- `IrCompilerTest`: 478 tests, 0 skipped, 0 failures, 0 errors.
- `CodegenModeTest`: 7 tests, 0 skipped, 0 failures, 0 errors.
- Total: 485 tests, 0 skipped, 0 failures, 0 errors.

Defaults remain `--codegen=legacy`, `--ir-lower=direct`, and `--backend=cpp`.

Admitted: **Yes**  
Pushed by child: **Yes**  
Ship-ready: **No**

## 中文

### 摘要

- 覆盖构造器链调用参数中的独立 `GETFIELD`：当接收者是“未被修改的已声明 holder 参数”的已证明前缀额外局部变量副本时，允许 `I`、`J`、`F`、`D` 四种 JVM 原始载体。
- 复用现有生产证明：`previousProvenGetfieldChainInput` 已将 `provenPrefixObjectCopyLocals` 与 `sameInvocationCarrier` 组合，因此不需要修改处理器。
- `ALOAD holder; ASTORE extra`、各路径的接收者加载和 `GETFIELD` 都保留在 JVM 字节码前缀中；原生方法体不包含 `GETFIELD`。
- 覆盖 category-2 的 `J` 和 `D` 值，同时保持精确的四局部变量布局（`this`、selector、holder、额外 holder）。
- 继续以 fail-closed 方式拒绝 `GETSTATIC`、local 0、`this` 副本、被覆盖的额外/源局部变量、计算得到的 holder，以及不匹配的载体。
- 每个重写构造器仍只有一个隐藏桥接方法和一个 `MethodContext.proxyMethod`。

### 测试

新增的三个测试分别覆盖准入、Java 8 JVM 校验以及 Java/CMake/g++ JNI 输出一致性，并覆盖全部四种原始载体、selector `7`、`-7`、`0`。JVM 校验测试还确认 null holder 仍由 JVM `GETFIELD` 抛出 `NullPointerException`。

子任务已执行上面的必需测试门禁。`obfuscator/build/test-results/test/` 中的 JUnit XML 结果：

- `IrCompilerTest`：478 个测试，0 跳过，0 失败，0 错误。
- `CodegenModeTest`：7 个测试，0 跳过，0 失败，0 错误。
- 总计：485 个测试，0 跳过，0 失败，0 错误。

默认值仍为 `--codegen=legacy`、`--ir-lower=direct` 和 `--backend=cpp`。

已准入：**是**  
子任务已推送：**是**  
可发布：**否**
