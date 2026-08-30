# Admit constructor GETFIELD through a proven extra-local object copy

## English

### Summary

- Admit isolated constructor chain-input `GETFIELD` when its receiver is a proven prefix extra-local copy of an unchanged declared object argument.
- Track object copies separately from array copies as `extra local -> declared source local`.
- Keep the receiver `ALOAD` and `GETFIELD` in the retained JVM prefix; the native body contains no `GETFIELD`.
- Preserve fail-closed rejection for local 0, copies of `this`, array or computed receivers, overwritten extra/source locals, incompatible owners or field carriers, and `GETSTATIC`.
- Keep one hidden native bridge per constructor and leave the production goal incomplete.

### Tests

Required child gate:

```text
CC=gcc CXX=g++ ./gradlew :obfuscator:test --rerun-tasks \
  --tests by.radioegor146.ir.IrCompilerTest \
  --tests by.radioegor146.CodegenModeTest
```

JUnit XML results from `obfuscator/build/test-results/test/`:

- `IrCompilerTest`: 474 tests, 0 skipped, 0 failures, 0 errors.
- `CodegenModeTest`: 7 tests, 0 skipped, 0 failures, 0 errors.

New positive coverage:

- `admitsThreeImmediateReturnsWithGetfieldExtraLocalHolder()`
- `rewrittenThreeImmediateGetfieldExtraLocalHolderPassJvmVerification()`
- `threeImmediateGetfieldExtraLocalHolderCompileAndRunWithJavaParity()`

The grouped rejection coverage now also checks an overwritten extra-local holder and an extra-local copy of `this`. The parent will rerun the gate after rebasing.

Defaults remain `--codegen=legacy`, `--ir-lower=direct`, and `--backend=cpp`.

Admitted: **Yes**  
Pushed by child: **Yes**  
Ship-ready: **No**

## 中文

### 摘要

- 当构造器链调用参数中的独立 `GETFIELD` 接收者，是“未被修改的已声明对象参数”的已证明前缀额外局部变量副本时，允许该形态进入 IR。
- 对象副本使用独立的“额外局部变量 -> 已声明源局部变量”映射，不复用数组副本证明。
- 接收者 `ALOAD` 与 `GETFIELD` 仍保留在 JVM 前缀中执行；原生方法体不包含 `GETFIELD`。
- 继续以 fail-closed 方式拒绝 local 0、`this` 的副本、数组或计算得到的接收者、被覆盖的额外/源局部变量、owner 或字段载体不兼容，以及 `GETSTATIC`。
- 每个构造器仍只有一个隐藏原生桥接方法；生产目标保持未完成状态。

### 测试

子任务已执行上面的必需测试门禁。`obfuscator/build/test-results/test/` 中的 JUnit XML 结果：

- `IrCompilerTest`：474 个测试，0 跳过，0 失败，0 错误。
- `CodegenModeTest`：7 个测试，0 跳过，0 失败，0 错误。

新增的三个正向测试覆盖准入、JVM 校验以及 Java/CMake/g++ JNI 输出一致性；分组拒绝测试还新增了“额外局部变量被覆盖”和“额外局部变量复制自 `this`”两种情况。父任务会在 rebase 后重新执行测试门禁。

默认值仍为 `--codegen=legacy`、`--ir-lower=direct` 和 `--backend=cpp`。

已准入：**是**  
子任务已推送：**是**  
可发布：**否**
