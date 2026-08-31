# test: admit extra-local int as first, second, fourth, and fifth five-arg NEW arguments

## English

### Summary

- Fixture-only change; `ConstructorSpecialMethodProcessor` and all compiler/runtime sources are unchanged.
- Adds the shape `new-constructor-extra-local-argument-five-first-second-fourth-fifth`.
- Processor changed: **No**.
- Ship-ready: **No**.
- The `--codegen`, `--ir-lower`, and `--backend` defaults are unchanged.

### Leaf bytecode

```text
NEW java/util/GregorianCalendar
DUP
ILOAD 3
ILOAD 3
ICONST_3
ILOAD 3
ILOAD 3
INVOKESPECIAL java/util/GregorianCalendar.<init>(IIIII)V
ALOAD 0
INVOKESPECIAL <super>.<init>(Ljava/util/GregorianCalendar;)V
RETURN
```

The existing int extra-local fixture stores `BIPUSH 8` in local 3 before the
three immediate-return paths.

### Helper wiring

- Keeps the shape out of the first-argument exclusion list, so the first argument remains `ILOAD 3`.
- Emits `ILOAD 3` for the second, fourth, and fifth arguments.
- Leaves the third argument as `ICONST_3`.
- Returns an argument count of 5.
- Uses the chain descriptor `(Ljava/util/GregorianCalendar;)V`.

### Tests

- `admitsThreeImmediateReturnsWithNewExtraLocalFiveFirstSecondFourthFifthArgChainInputs`
- `rewrittenThreeImmediateNewExtraLocalFiveFirstSecondFourthFifthArgChainInputsPassJvmVerification`
- `threeImmediateNewExtraLocalFiveFirstSecondFourthFifthArgChainInputsCompileAndRunWithJavaParity`

## 中文

### 摘要

- 仅修改测试夹具；`ConstructorSpecialMethodProcessor` 以及所有编译器/运行时源码均未修改。
- 新增形状 `new-constructor-extra-local-argument-five-first-second-fourth-fifth`。
- 处理器变更：**否**。
- 可发布状态：**否**。
- `--codegen`、`--ir-lower` 和 `--backend` 的默认值保持不变。

### 叶节点字节码

```text
NEW java/util/GregorianCalendar
DUP
ILOAD 3
ILOAD 3
ICONST_3
ILOAD 3
ILOAD 3
INVOKESPECIAL java/util/GregorianCalendar.<init>(IIIII)V
ALOAD 0
INVOKESPECIAL <super>.<init>(Ljava/util/GregorianCalendar;)V
RETURN
```

现有 int 额外局部变量夹具会在三个立即返回路径之前，先通过 `BIPUSH 8`
把值写入局部变量 3。

### 辅助方法接线

- 不把该形状加入第一个参数的排除列表，因此第一个参数保持为 `ILOAD 3`。
- 第二、第四和第五个参数使用 `ILOAD 3`。
- 第三个参数保持为 `ICONST_3`。
- 参数数量返回 5。
- 构造器链描述符使用 `(Ljava/util/GregorianCalendar;)V`。

### 测试

- `admitsThreeImmediateReturnsWithNewExtraLocalFiveFirstSecondFourthFifthArgChainInputs`
- `rewrittenThreeImmediateNewExtraLocalFiveFirstSecondFourthFifthArgChainInputsPassJvmVerification`
- `threeImmediateNewExtraLocalFiveFirstSecondFourthFifthArgChainInputsCompileAndRunWithJavaParity`
