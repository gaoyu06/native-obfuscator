# test: admit extra-local int as the third and fourth six-arg NEW arguments

## English

### Summary

- Admits shape `new-constructor-extra-local-argument-six-third-fourth`.
- Exercises three constructor CFG leaves whose six-argument `GregorianCalendar` initializer uses the extra local as the third and fourth arguments.
- Keeps one native method per constructor and one hidden bridge through the singular `MethodContext.proxyMethod`.
- Processor changed: No.

### Wiring checklist

- First-argument exclude: Yes; the first argument remains `ICONST_1`.
- Second-argument `ILOAD 3`: No; the second argument remains `ICONST_2`.
- Third-argument `ILOAD 3`: Yes.
- Fourth-argument `ILOAD 3`: Yes.
- Fifth-argument `ILOAD 3`: No; the fifth argument remains `ICONST_5`.
- Sixth-argument `ILOAD 3`: No; the sixth argument remains `BIPUSH 6`.
- Argument count: `6`.
- Chain descriptor: `(Ljava/util/GregorianCalendar;)V`.

### Baseline and verification

- Rebased onto leftover-docs #447 at `7a4f96f5` (`7a4f96f58f19c68d908deec153758bcec2cc415c`).
- Parent XML: **761** (`IrCompilerTest` 754 + `CodegenModeTest` 7), 0 failures/errors/skipped.
- Parent re-ran `threeImmediateNewExtraLocalSixThirdFourthArgChainInputsCompileAndRunWithJavaParity`.
- Defaults unchanged: `--codegen=legacy`, `--ir-lower=direct`, and `--backend=cpp` were not flipped.
- Ship-ready: No.
- Production goal incomplete.

## 中文

### 摘要

- 准入形状 `new-constructor-extra-local-argument-six-third-fourth`。
- 覆盖三个构造函数字节码 CFG 叶子；六参数 `GregorianCalendar` 初始化器的第三、第四参数使用额外局部变量。
- 每个构造函数仍只有一个 native 方法，并通过单数的 `MethodContext.proxyMethod` 保持一个隐藏桥接。
- Processor changed：No。

### 接线检查

- 第一参数排除：是；第一参数保持 `ICONST_1`。
- 第二参数 `ILOAD 3`：否；第二参数保持 `ICONST_2`。
- 第三参数 `ILOAD 3`：是。
- 第四参数 `ILOAD 3`：是。
- 第五参数 `ILOAD 3`：否；第五参数保持 `ICONST_5`。
- 第六参数 `ILOAD 3`：否；第六参数保持 `BIPUSH 6`。
- 参数数量：`6`。
- 调用链描述符：`(Ljava/util/GregorianCalendar;)V`。

### 基线与验证

- 已变基到 leftover-docs #447，`7a4f96f5`（`7a4f96f58f19c68d908deec153758bcec2cc415c`）。
- 父级 XML：**761**（`IrCompilerTest` 754 + `CodegenModeTest` 7），失败/错误/跳过均为 0。
- 父级复跑包含 `threeImmediateNewExtraLocalSixThirdFourthArgChainInputsCompileAndRunWithJavaParity`。
- 默认值未变：未切换 `--codegen=legacy`、`--ir-lower=direct` 或 `--backend=cpp`。
- Ship-ready：No。
- 生产目标尚未完成。
