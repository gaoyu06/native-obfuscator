# test: admit extra-local int as the second and fourth six-arg NEW arguments

## English

### Summary

- Adds the fixture shape `new-constructor-extra-local-argument-six-second-fourth`.
- Exercises an extra-local `int` as the second and fourth arguments of the six-argument `GregorianCalendar` constructor.
- Adds admission, rewritten JVM-verification, and Java/native parity coverage.

### Wiring

- First-argument exclude list: yes; the first argument remains `ICONST_1`.
- Second-argument `ILOAD 3` list: yes.
- Third-argument `ILOAD 3` list: no; the third argument remains `ICONST_3`.
- Fourth-argument `ILOAD 3` list: yes.
- Fifth-argument `ILOAD 3` list: no; the fifth argument remains `ICONST_5`.
- Sixth-argument `ILOAD 3` list: no; the sixth argument remains `BIPUSH 6`.
- Constructor argument count: 6.
- Chain descriptor: `(Ljava/util/GregorianCalendar;)V`.

### Status

- Rebased onto leftover-docs #441 at `8ce7d407` (`8ce7d4070189aed44df82d536199cc20a8cb606a`).
- Parent XML: **752** (`IrCompilerTest` 745 + `CodegenModeTest` 7), 0 failures/errors/skipped.
- Parent re-ran `threeImmediateNewExtraLocalSixSecondFourthArgChainInputsCompileAndRunWithJavaParity`.
- Processor changed: No.
- Defaults changed: No (`--codegen=legacy`, `--ir-lower=direct`, and `--backend=cpp` remain unchanged).
- Ship-ready: No.
- The production goal is not complete.

## 中文

### 摘要

- 新增夹具形状 `new-constructor-extra-local-argument-six-second-fourth`。
- 覆盖额外局部 `int` 作为六参数 `GregorianCalendar` 构造函数的第二和第四个参数。
- 新增准入、重写后 JVM 校验，以及 Java/native 输出一致性测试。

### 接线

- 第一个参数排除列表：是；第一个参数保持为 `ICONST_1`。
- 第二个参数 `ILOAD 3` 列表：是。
- 第三个参数 `ILOAD 3` 列表：否；第三个参数保持为 `ICONST_3`。
- 第四个参数 `ILOAD 3` 列表：是。
- 第五个参数 `ILOAD 3` 列表：否；第五个参数保持为 `ICONST_5`。
- 第六个参数 `ILOAD 3` 列表：否；第六个参数保持为 `BIPUSH 6`。
- 构造函数参数数量：6。
- 调用链描述符：`(Ljava/util/GregorianCalendar;)V`。

### 状态

- 已变基到 leftover-docs #441，`8ce7d407`（`8ce7d4070189aed44df82d536199cc20a8cb606a`）。
- 父级 XML：**752**（`IrCompilerTest` 745 + `CodegenModeTest` 7），失败/错误/跳过均为 0。
- 父级复跑包含 `threeImmediateNewExtraLocalSixSecondFourthArgChainInputsCompileAndRunWithJavaParity`。
- Processor 改动：无。
- 默认值改动：无（`--codegen=legacy`、`--ir-lower=direct` 和 `--backend=cpp` 保持不变）。
- 可发布：否。
- 生产目标尚未完成。
