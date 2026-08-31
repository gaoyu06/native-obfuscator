# test: admit extra-local int as the second and fifth six-arg NEW arguments

## English

### Summary

- Adds the fixture shape `new-constructor-extra-local-argument-six-second-fifth`.
- Covers admission, rewritten JVM verification, and Java/native runtime parity for a six-argument `GregorianCalendar` initializer whose second and fifth arguments load the extra int local.
- Keeps one native method per constructor, one hidden bridge, and the singular `MethodContext.proxyMethod` JNI proxy.

### Wiring checklist

- First-argument exclude: Yes; the first argument remains `ICONST_1`.
- Second argument `ILOAD 3`: Yes.
- Third argument `ILOAD 3`: No; it remains `ICONST_3`.
- Fourth argument `ILOAD 3`: No; it remains `ICONST_4`.
- Fifth argument `ILOAD 3`: Yes.
- Sixth argument `ILOAD 3`: No; it remains `BIPUSH 6`.
- Constructor argument count: `6`.
- Chain descriptor: `(Ljava/util/GregorianCalendar;)V`.

### Baseline and verification

- Rebased onto leftover-docs #443 at `626f494f` (`626f494f4bf705a9ea015545857dae438683f507`).
- Latest compiler parent XML until this parent re-run: #442 (752), comprising `IrCompilerTest` 745 + `CodegenModeTest` 7.
- Expected parent XML after leftover-docs: 755, comprising `IrCompilerTest` 748 + `CodegenModeTest` 7.
- Child XML is discarded; only the parent re-run is authoritative.
- Processor changed: No.
- Defaults unchanged: `--codegen=legacy`, `--ir-lower=direct`, and `--backend=cpp`.
- Ship-ready: No.
- Production goal incomplete.

## 中文

### 摘要

- 新增夹具形状 `new-constructor-extra-local-argument-six-second-fifth`。
- 覆盖六参数 `GregorianCalendar` 初始化器的准入、重写后 JVM 验证以及 Java/原生运行时一致性；第二和第五个参数读取额外的 int 局部变量。
- 每个构造函数仍只生成一个原生方法和一个隐藏桥接，并使用单数的 `MethodContext.proxyMethod` JNI 代理。

### 接线清单

- 第一参数排除项：是；第一参数保持为 `ICONST_1`。
- 第二参数使用 `ILOAD 3`：是。
- 第三参数使用 `ILOAD 3`：否；保持为 `ICONST_3`。
- 第四参数使用 `ILOAD 3`：否；保持为 `ICONST_4`。
- 第五参数使用 `ILOAD 3`：是。
- 第六参数使用 `ILOAD 3`：否；保持为 `BIPUSH 6`。
- 构造函数参数数量：`6`。
- 链描述符：`(Ljava/util/GregorianCalendar;)V`。

### 基线与验证

- 已变基到 leftover-docs #443，`626f494f`（`626f494f4bf705a9ea015545857dae438683f507`）。
- 本次父级复跑前的最新编译器父 XML：#442（752），其中 `IrCompilerTest` 745 + `CodegenModeTest` 7。
- leftover-docs 之后父分支预期 XML：755，其中 `IrCompilerTest` 748 + `CodegenModeTest` 7。
- 子分支 XML 作废；以父级复跑为准。
- 处理器已更改：否。
- 默认值未更改：`--codegen=legacy`、`--ir-lower=direct` 和 `--backend=cpp`。
- 可发布：否。
- 生产目标尚未完成。
