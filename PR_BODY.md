# Extra-local long argument for constructor `NEW` / 构造器 `NEW` 的额外局部 long 参数

## English

### Summary

- Add fixture-only coverage for the exact `new-constructor-extra-local-long-argument` compose.
- Copy the declared long argument with the retained prefix `LLOAD 2; LSTORE 4`, then use `LLOAD 4` as the argument to each isolated `NEW Date; DUP; ...; INVOKESPECIAL Date.<init>(J)V` leaf.
- Exercise the `(IJ)V` constructor across selectors `7`, `-7`, and `0`; the native body contains only `RETURN`, while the rewritten constructor retains three allocation leaves and uses one hidden bridge with proxy descriptor `(Ljava/lang/Object;IJ)V`.
- Reuse the existing `previousProvenLongChainLeaf` support. `ConstructorSpecialMethodProcessor.java` is unchanged.
- Keep the six-argument `NEW` arity cap and every other fail-closed limit unchanged. The default modes remain `--codegen=legacy`, `--ir-lower=direct`, and `--backend=cpp`.

### Tests

Added:

- `admitsThreeImmediateReturnsWithNewExtraLocalLongArgChainInputs`
- `rewrittenThreeImmediateNewExtraLocalLongArgChainInputsPassJvmVerification`
- `threeImmediateNewExtraLocalLongArgChainInputsCompileAndRunWithJavaParity`

Focused child run:

```text
CC=gcc CXX=g++ ./gradlew :obfuscator:test --rerun-tasks \
  --tests by.radioegor146.ir.IrCompilerTest \
  --tests by.radioegor146.CodegenModeTest
```

Child-local XML only, not parent totals: `IrCompilerTest` 532 + `CodegenModeTest` 7 = 539 tests; 0 failures, 0 errors, 0 skips. The parent re-runs the focused gate.

- Admitted: Yes, this compose only — proven prefix extra-local long copy as the isolated one-argument `Date.<init>(J)V` initializer
- Ship-ready: **No**

## 中文

### 摘要

- 仅增加精确形状 `new-constructor-extra-local-long-argument` 的夹具覆盖，不修改处理器。
- 在保留的 JVM 前缀中通过 `LLOAD 2; LSTORE 4` 复制已声明的 long 参数，再以 `LLOAD 4` 作为每个隔离叶节点 `NEW Date; DUP; ...; INVOKESPECIAL Date.<init>(J)V` 的参数。
- 使用选择值 `7`、`-7` 和 `0` 覆盖 `(IJ)V` 构造器；原生方法体仅含 `RETURN`，重写后的构造器保留三个分配叶节点，并使用一个代理描述符为 `(Ljava/lang/Object;IJ)V` 的隐藏桥接。
- 复用已有的 `previousProvenLongChainLeaf` 支持，`ConstructorSpecialMethodProcessor.java` 保持不变。
- 六参数 `NEW` 上限及其他所有 fail-closed 限制均保持不变。默认模式仍为 `--codegen=legacy`、`--ir-lower=direct` 和 `--backend=cpp`。

### 测试

新增：

- `admitsThreeImmediateReturnsWithNewExtraLocalLongArgChainInputs`
- `rewrittenThreeImmediateNewExtraLocalLongArgChainInputsPassJvmVerification`
- `threeImmediateNewExtraLocalLongArgChainInputsCompileAndRunWithJavaParity`

子任务本地运行：

```text
CC=gcc CXX=g++ ./gradlew :obfuscator:test --rerun-tasks \
  --tests by.radioegor146.ir.IrCompilerTest \
  --tests by.radioegor146.CodegenModeTest
```

仅为子任务本地 XML，不是父任务总数：`IrCompilerTest` 532 + `CodegenModeTest` 7 = 539 个测试；0 failures、0 errors、0 skips。父任务会重新运行 focused gate。

- Admitted：Yes，仅限本次 compose——以已证明的前缀额外局部 long 副本作为隔离的单参数 `Date.<init>(J)V` 初始化参数
- Ship-ready：**No**
