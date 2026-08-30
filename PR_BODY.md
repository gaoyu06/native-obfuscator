## English

### Summary

- Admit isolated constructor-chain inputs of the form `NEW owner; DUP; arg1; arg2; arg3; INVOKESPECIAL owner.<init>(III)V` when all three arguments are single-instruction proven int-family leaves and the allocated type exactly matches the chain-call argument.
- Add the dedicated `new-constructor-three-arguments` `java.awt.Color` fixture while retaining the complete allocation and initialization sequence in the JVM bytecode prefix; the native body contains neither that `NEW` nor its initializer call.
- Keep one hidden bridge through the singular `MethodContext.proxyMethod`, and retain the existing no-, one-, and two-argument admissions.
- Keep all code-generation defaults on `legacy`; wide or computed initializer inputs, four-or-more initializer arguments, array allocation, and skip-super control flow remain rejected. A four-int `java.awt.Insets` fixture covers the argument-count cap.

### Tests

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

Ship-ready: **No**

## 中文

### 摘要

- 当三个参数都是已证明的单指令 int-family 叶子，且分配类型与构造器链调用参数完全一致时，接纳隔离的 `NEW owner; DUP; arg1; arg2; arg3; INVOKESPECIAL owner.<init>(III)V` 构造器链输入。
- 添加独立的 `new-constructor-three-arguments` `java.awt.Color` 夹具；完整的对象分配和初始化序列保留在 JVM 字节码前缀中，native 方法体不包含该 `NEW` 或初始化调用。
- 通过唯一的 `MethodContext.proxyMethod` 保持一个隐藏桥接方法，并保留已有的零参数、单参数和双参数接纳。
- 所有代码生成默认值仍为 `legacy`；宽类型或计算型初始化输入、四个及以上初始化参数、数组分配和 skip-super 控制流仍被拒绝。四 int 参数的 `java.awt.Insets` 夹具覆盖参数数量上限。

### 测试

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

可发布：**否**
