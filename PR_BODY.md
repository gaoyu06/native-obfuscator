# test: admit extra-local int as the fourth and fifth six-arg NEW arguments

## English

### Summary

- Add fixture-only IR admission coverage for an extra-local proven int copy used as the fourth and fifth initializer arguments of a six-argument `GregorianCalendar` `NEW`.
- Add admission, JVM verification, and Java/native parity tests.
- Keep compiler defaults and production code unchanged.

### Fixture wiring

- First-argument exclude: Yes
- `ILOAD 3` for second argument: No
- `ILOAD 3` for third argument: No
- `ILOAD 3` for fourth argument: Yes
- `ILOAD 3` for fifth argument: Yes
- `ILOAD 3` for sixth argument: No
- Constructor argument count: `6`
- Chain descriptor: `(Ljava/util/GregorianCalendar;)V`

### Baseline and status

- Merge base: leftover-docs #452, `a6896796` (`a689679644c73bd577a6087993ca191fc2bf7e4d`).
- Parent XML: **770** (`IrCompilerTest` 763 + `CodegenModeTest` 7), 0 failures/errors/skipped.
- Parent re-ran `threeImmediateNewExtraLocalSixFourthFifthArgChainInputsCompileAndRunWithJavaParity`.
- Processor changed: No.
- Ship-ready: No.
- The production goal remains incomplete.
- Defaults are unchanged.

## 中文

### 摘要

- 仅扩展 IR 测试夹具准入覆盖：把已证明的额外局部 `int` 副本同时用作六参数 `GregorianCalendar` `NEW` 初始化器的第四和第五个参数。
- 新增准入、JVM 验证以及 Java/本地执行一致性测试。
- 编译器默认值和生产代码保持不变。

### 测试夹具接线

- 首参数排除列表：是
- 第二参数使用 `ILOAD 3`：否
- 第三参数使用 `ILOAD 3`：否
- 第四参数使用 `ILOAD 3`：是
- 第五参数使用 `ILOAD 3`：是
- 第六参数使用 `ILOAD 3`：否
- 构造器参数数量：`6`
- 链描述符：`(Ljava/util/GregorianCalendar;)V`

### 基线与状态

- 合并基线：leftover-docs #452，`a6896796`（`a689679644c73bd577a6087993ca191fc2bf7e4d`）。
- 父级 XML：**770**（`IrCompilerTest` 763 + `CodegenModeTest` 7），失败/错误/跳过均为 0。
- 父级复跑包含 `threeImmediateNewExtraLocalSixFourthFifthArgChainInputsCompileAndRunWithJavaParity`。
- Processor changed：No。
- Ship-ready：No。
- 生产目标仍未完成。
- 默认值未更改。
