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

- Baseline: leftover-docs #446 at `7e95adc3` (`7e95adc3811e137f0e6d6949fbf6f7a6c059b2d0`).
- Latest compiler parent XML until parent re-run: #446 (758), with `IrCompilerTest` 751 and `CodegenModeTest` 7.
- Expected parent XML after leftover-docs: 761, with `IrCompilerTest` 754 and `CodegenModeTest` 7.
- Child-only XML: 761 tests, 0 failures, 0 errors (`IrCompilerTest` 754; `CodegenModeTest` 7).
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

- 基线：leftover-docs #446，提交 `7e95adc3`（`7e95adc3811e137f0e6d6949fbf6f7a6c059b2d0`）。
- 父分支重新运行前的最新编译器 XML：#446（758），其中 `IrCompilerTest` 751、`CodegenModeTest` 7。
- leftover-docs 之后预期的父分支 XML：761，其中 `IrCompilerTest` 754、`CodegenModeTest` 7。
- 仅子分支 XML：761 个测试，0 failures，0 errors（`IrCompilerTest` 754；`CodegenModeTest` 7）。
- 默认值未变：未切换 `--codegen=legacy`、`--ir-lower=direct` 或 `--backend=cpp`。
- Ship-ready：No。
- 生产目标尚未完成。
