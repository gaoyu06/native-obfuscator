# test: admit extra-local int as the first and sixth six-arg NEW arguments

## English

### Summary

- Adds the fixture shape `new-constructor-extra-local-argument-six-first-sixth`.
- Adds admission, rewritten-bytecode verification, and Java/native parity coverage for a six-argument `GregorianCalendar` `NEW` whose first and sixth arguments load extra-local int slot 3.
- Keeps the constructor fixture at `(II)V` with the `ILOAD 2; ISTORE 3` prefix, `maxLocals = 4`, one native constructor method, one hidden bridge, and singular `MethodContext.proxyMethod`.

### Fixture wiring

- First-argument exclude list: unchanged; the first argument remains `ILOAD 3`.
- Second/third/fourth/fifth `ILOAD 3` lists: unchanged; those arguments remain `ICONST_2` through `ICONST_5`.
- Sixth `ILOAD 3` list: adds this shape.
- Constructor argument count: 6.
- Chain descriptor: `(Ljava/util/GregorianCalendar;)V`.

### Tests

- `admitsThreeImmediateReturnsWithNewExtraLocalSixFirstSixthArgChainInputs`
- `rewrittenThreeImmediateNewExtraLocalSixFirstSixthArgChainInputsPassJvmVerification`
- `threeImmediateNewExtraLocalSixFirstSixthArgChainInputsCompileAndRunWithJavaParity`

Validation command:

```bash
CC=gcc CXX=g++ ./gradlew :obfuscator:test --rerun-tasks \
  --tests by.radioegor146.ir.IrCompilerTest \
  --tests by.radioegor146.CodegenModeTest
```

Child validation passed with 746 tests, 0 failures, 0 errors, and 0 skipped: `IrCompilerTest` 739 plus `CodegenModeTest` 7.

Baseline is leftover-docs #436 at `4fed275e` (`4fed275ea8e31858920ebb6ae76bf015c773f273`). The latest compiler parent XML remains #436 (743): `IrCompilerTest` 736 plus `CodegenModeTest` 7. Expected parent XML after this fixture is 746: `IrCompilerTest` 739 plus `CodegenModeTest` 7.

Processor changed: No. Ship-ready: No. Defaults remain unchanged (`--codegen=legacy`, `--ir-lower=direct`, `--backend=cpp`).

## 中文

### 摘要

- 新增夹具形状 `new-constructor-extra-local-argument-six-first-sixth`。
- 为六参数 `GregorianCalendar` 的 `NEW` 增加入场、重写后字节码验证以及 Java/native 输出一致性覆盖；其中第一和第六个参数从额外的 int 局部槽 3 加载。
- 构造器夹具保持 `(II)V`，前缀为 `ILOAD 2; ISTORE 3`，`maxLocals = 4`；每个构造器仅有一个 native 方法、一个隐藏桥接，并保持单一 `MethodContext.proxyMethod`。

### 夹具接线

- 第一参数排除列表：不变；第一参数仍为 `ILOAD 3`。
- 第二/第三/第四/第五参数的 `ILOAD 3` 列表：不变；这些参数仍分别为 `ICONST_2` 到 `ICONST_5`。
- 第六参数的 `ILOAD 3` 列表：加入本形状。
- 构造器参数数量：6。
- 链描述符：`(Ljava/util/GregorianCalendar;)V`。

### 测试

- `admitsThreeImmediateReturnsWithNewExtraLocalSixFirstSixthArgChainInputs`
- `rewrittenThreeImmediateNewExtraLocalSixFirstSixthArgChainInputsPassJvmVerification`
- `threeImmediateNewExtraLocalSixFirstSixthArgChainInputsCompileAndRunWithJavaParity`

验证命令：

```bash
CC=gcc CXX=g++ ./gradlew :obfuscator:test --rerun-tasks \
  --tests by.radioegor146.ir.IrCompilerTest \
  --tests by.radioegor146.CodegenModeTest
```

Child 验证已通过，共 746 个测试，0 failures、0 errors、0 skipped：`IrCompilerTest` 739 加 `CodegenModeTest` 7。

基线为 leftover-docs #436 的 `4fed275e`（`4fed275ea8e31858920ebb6ae76bf015c773f273`）。最新 compiler parent XML 仍为 #436（743）：`IrCompilerTest` 736 加 `CodegenModeTest` 7。加入本夹具后，预期 parent XML 为 746：`IrCompilerTest` 739 加 `CodegenModeTest` 7。

Processor changed：No。Ship-ready：No。默认值保持不变（`--codegen=legacy`、`--ir-lower=direct`、`--backend=cpp`）。
