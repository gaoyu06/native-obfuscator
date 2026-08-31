# test: admit extra-local int as first, second, third, and fifth five-arg NEW arguments

## English

### Summary

- Fixture-only change; `ConstructorSpecialMethodProcessor` and compiler/runtime sources are unchanged.
- Adds shape `new-constructor-extra-local-argument-five-first-second-third-fifth`.
- Exercises bytecode/CFG/JNI handling for an extra-local `int` used as the first, second, third, and fifth arguments of a five-argument `GregorianCalendar` `NEW` initializer.
- Ship-ready: **No**.
- The `--codegen`, `--ir-lower`, and `--backend` defaults remain unchanged.

### Leaf bytecode

```text
NEW java/util/GregorianCalendar
DUP
ILOAD 3
ILOAD 3
ILOAD 3
ICONST_4
ILOAD 3
INVOKESPECIAL java/util/GregorianCalendar.<init>(IIIII)V
ALOAD 0
INVOKESPECIAL <super>.<init>(Ljava/util/GregorianCalendar;)V
RETURN
```

### Helper wiring

- Keeps the shape out of the first-argument exclusion list, so the first argument remains `ILOAD 3`.
- Adds exact-equals wiring for `ILOAD 3` in the second, third, and fifth argument positions.
- Leaves the fourth argument as `ICONST_4`.
- Wires the shape to argument count 5 and chain descriptor `(Ljava/util/GregorianCalendar;)V`.

### Tests

- `admitsThreeImmediateReturnsWithNewExtraLocalFiveFirstSecondThirdFifthArgChainInputs`
- `rewrittenThreeImmediateNewExtraLocalFiveFirstSecondThirdFifthArgChainInputsPassJvmVerification`
- `threeImmediateNewExtraLocalFiveFirstSecondThirdFifthArgChainInputsCompileAndRunWithJavaParity`

## 中文

### 摘要

- 仅修改测试夹具；`ConstructorSpecialMethodProcessor` 以及编译器/运行时源码均未改动。
- 新增形状 `new-constructor-extra-local-argument-five-first-second-third-fifth`。
- 覆盖额外局部 `int` 同时作为五参数 `GregorianCalendar` `NEW` 初始化器第一、第二、第三和第五个参数时的字节码、CFG 与 JNI 处理。
- 可发布状态：**否**。
- `--codegen`、`--ir-lower` 和 `--backend` 的默认值保持不变。

### 叶节点字节码

```text
NEW java/util/GregorianCalendar
DUP
ILOAD 3
ILOAD 3
ILOAD 3
ICONST_4
ILOAD 3
INVOKESPECIAL java/util/GregorianCalendar.<init>(IIIII)V
ALOAD 0
INVOKESPECIAL <super>.<init>(Ljava/util/GregorianCalendar;)V
RETURN
```

### 辅助方法接线

- 不把该形状加入第一个参数的排除列表，因此第一个参数仍为 `ILOAD 3`。
- 通过精确相等判断，让第二、第三和第五个参数使用 `ILOAD 3`。
- 第四个参数保持为 `ICONST_4`。
- 将参数数量接线为 5，并使用链描述符 `(Ljava/util/GregorianCalendar;)V`。

### 测试

- `admitsThreeImmediateReturnsWithNewExtraLocalFiveFirstSecondThirdFifthArgChainInputs`
- `rewrittenThreeImmediateNewExtraLocalFiveFirstSecondThirdFifthArgChainInputsPassJvmVerification`
- `threeImmediateNewExtraLocalFiveFirstSecondThirdFifthArgChainInputsCompileAndRunWithJavaParity`
