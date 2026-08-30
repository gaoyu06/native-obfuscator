# English

## Summary

- Adds fixture-only coverage for `new-constructor-extra-local-argument-six`.
- Composes a proven prefix extra-local integer copy as the first initializer argument of the isolated six-integer `NEW` leaf:
  `NEW GregorianCalendar; DUP; ILOAD 3; ICONST_2; ICONST_3; ICONST_4; ICONST_5; BIPUSH 6; INVOKESPECIAL GregorianCalendar.<init>(IIIIII)V`.
- Keeps the complete allocation and initializer sequence in the retained JVM prefix. The native body is `(II)V` with only `RETURN`, with one hidden bridge and one native method per constructor.
- This is fixture-only: `ConstructorSpecialMethodProcessor.java` and all compiler/runtime sources are unchanged.
- The six-argument arity cap is unchanged; seven-or-more initializer arguments remain rejected.
- Defaults remain `--codegen=legacy`, `--ir-lower=direct`, and `--backend=cpp`.

## Tests

Added:

- `admitsThreeImmediateReturnsWithNewExtraLocalSixArgChainInputs`
- `rewrittenThreeImmediateNewExtraLocalSixArgChainInputsPassJvmVerification`
- `threeImmediateNewExtraLocalSixArgChainInputsCompileAndRunWithJavaParity`

Focused gate:

```text
CC=gcc CXX=g++ ./gradlew :obfuscator:test --rerun-tasks \
  --tests by.radioegor146.ir.IrCompilerTest \
  --tests by.radioegor146.CodegenModeTest
```

Child-local XML only: `IrCompilerTest` 525 + `CodegenModeTest` 7 = 532 total; 0 failures, 0 errors, 0 skipped. The parent re-runs the focused gate.

## Status

- Admitted: Yes, this compose only.
- Ship-ready: **No**.

# 中文

## 摘要

- 仅新增测试夹具覆盖：`new-constructor-extra-local-argument-six`。
- 将前缀中已证明安全的额外局部整数副本，组合为隔离的六整数参数 `NEW` 叶节点的第一个初始化参数：
  `NEW GregorianCalendar; DUP; ILOAD 3; ICONST_2; ICONST_3; ICONST_4; ICONST_5; BIPUSH 6; INVOKESPECIAL GregorianCalendar.<init>(IIIIII)V`。
- 完整的对象分配与初始化序列保留在 JVM 前缀中。原生方法体描述符为 `(II)V`，且仅含 `RETURN`；每个构造器只有一个隐藏桥接和一个原生方法。
- 本变更仅涉及测试夹具；未修改 `ConstructorSpecialMethodProcessor.java` 或任何编译器/运行时源码。
- 六参数上限未修改；七个或更多初始化参数仍会被拒绝。
- 默认值仍为 `--codegen=legacy`、`--ir-lower=direct`、`--backend=cpp`。

## 测试

新增：

- `admitsThreeImmediateReturnsWithNewExtraLocalSixArgChainInputs`
- `rewrittenThreeImmediateNewExtraLocalSixArgChainInputsPassJvmVerification`
- `threeImmediateNewExtraLocalSixArgChainInputsCompileAndRunWithJavaParity`

已运行上述 focused gate。

仅限子分支本地 XML：`IrCompilerTest` 525 + `CodegenModeTest` 7 = 532；失败 0、错误 0、跳过 0。父任务会重新运行 focused gate。

## 状态

- Admitted：是，仅限本次 compose。
- Ship-ready：**No**。
