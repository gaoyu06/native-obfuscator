# test: admit extra-local int as first, second, third, and fourth five-arg NEW arguments

## English

This is a fixture-only change. `ConstructorSpecialMethodProcessor` and all compiler/runtime sources are unchanged.

- Shape: `new-constructor-extra-local-argument-five-first-second-third-fourth`
- Each of the three immediate-return paths emits:

```text
NEW java/util/GregorianCalendar
DUP
ILOAD 3
ILOAD 3
ILOAD 3
ILOAD 3
ICONST_5
INVOKESPECIAL java/util/GregorianCalendar.<init>(IIIII)V
ALOAD 0
INVOKESPECIAL <super>.<init>(Ljava/util/GregorianCalendar;)V
RETURN
```

Helper wiring:

- The shape is not added to the first-argument exclusion list, so the first argument remains `ILOAD 3`.
- The second, third, and fourth arguments use `ILOAD 3`.
- The fifth argument remains `ICONST_5`.
- `newConstructorArgumentCount` returns 5 by exact shape equality.
- `newArgChainDescriptor` returns `(Ljava/util/GregorianCalendar;)V` by exact shape equality.

Tests added:

- `admitsThreeImmediateReturnsWithNewExtraLocalFiveFirstSecondThirdFourthArgChainInputs`
- `rewrittenThreeImmediateNewExtraLocalFiveFirstSecondThirdFourthArgChainInputsPassJvmVerification`
- `threeImmediateNewExtraLocalFiveFirstSecondThirdFourthArgChainInputsCompileAndRunWithJavaParity`

Ship-ready: **No**. The `--codegen`, `--ir-lower`, and `--backend` defaults are unchanged.

## 中文

本次变更仅涉及测试夹具。`ConstructorSpecialMethodProcessor` 以及所有编译器/运行时源码均未修改。

- 形状：`new-constructor-extra-local-argument-five-first-second-third-fourth`
- 三条立即返回路径中的每个叶子都会生成：

```text
NEW java/util/GregorianCalendar
DUP
ILOAD 3
ILOAD 3
ILOAD 3
ILOAD 3
ICONST_5
INVOKESPECIAL java/util/GregorianCalendar.<init>(IIIII)V
ALOAD 0
INVOKESPECIAL <super>.<init>(Ljava/util/GregorianCalendar;)V
RETURN
```

辅助方法接线：

- 未将该形状加入第一个参数的排除列表，因此第一个参数保持为 `ILOAD 3`。
- 第二、第三和第四个参数使用 `ILOAD 3`。
- 第五个参数保持为 `ICONST_5`。
- `newConstructorArgumentCount` 通过精确形状相等判断返回 5。
- `newArgChainDescriptor` 通过精确形状相等判断返回 `(Ljava/util/GregorianCalendar;)V`。

新增测试：

- `admitsThreeImmediateReturnsWithNewExtraLocalFiveFirstSecondThirdFourthArgChainInputs`
- `rewrittenThreeImmediateNewExtraLocalFiveFirstSecondThirdFourthArgChainInputsPassJvmVerification`
- `threeImmediateNewExtraLocalFiveFirstSecondThirdFourthArgChainInputsCompileAndRunWithJavaParity`

可发布状态：**否**。`--codegen`、`--ir-lower` 和 `--backend` 的默认值均未改变。
