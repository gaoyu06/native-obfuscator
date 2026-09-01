# test: admit extra-local int as the fifth and sixth six-arg NEW arguments

## English

### Summary

- Add fixture-only IR admission coverage for an extra-local proven int copy used as the fifth and sixth initializer arguments of a six-argument `GregorianCalendar` `NEW`.
- Leaf: `NEW GregorianCalendar; DUP; ICONST_1; ICONST_2; ICONST_3; ICONST_4; ILOAD 3; ILOAD 3; INVOKESPECIAL <init>(IIIIII)V`.
- Add admission, JVM verification, and Java/native parity tests.
- Keep compiler defaults, `ConstructorSpecialMethodProcessor`, and production code unchanged.

### Fixture wiring

- First-argument exclude: Yes
- `ILOAD 3` for second argument: No
- `ILOAD 3` for third argument: No
- `ILOAD 3` for fourth argument: No
- `ILOAD 3` for fifth argument: Yes
- `ILOAD 3` for sixth argument: Yes
- Constructor argument count: `6`
- Chain descriptor: `(Ljava/util/GregorianCalendar;)V`

### Baseline and status

- Rebased onto leftover-docs [#455](https://github.com/gaoyu06/native-obfuscator/pull/455) at `14d68873` (`14d68873b06dbf98ad4c3fb9314c4a82bc546544`).
- Parent XML **776** (`IrCompilerTest` 769 + `CodegenModeTest` 7), 0 failures/errors/skipped. Parent re-ran `threeImmediateNewExtraLocalSixFifthSixthArgChainInputsCompileAndRunWithJavaParity`.
- Processor changed: No.
- Ship-ready: No.
- The production goal remains incomplete.
- Defaults are unchanged.

## 中文

### 摘要

- 仅扩展 IR 测试夹具准入覆盖：把已证明的额外局部 `int` 副本同时用作六参数 `GregorianCalendar` `NEW` 初始化器的第五和第六个参数。
- 叶子形态：`NEW GregorianCalendar; DUP; ICONST_1; ICONST_2; ICONST_3; ICONST_4; ILOAD 3; ILOAD 3; INVOKESPECIAL <init>(IIIIII)V`。
- 新增准入、JVM 验证以及 Java/本地执行一致性测试。
- 编译器默认值、`ConstructorSpecialMethodProcessor` 和生产代码保持不变。

### 测试夹具接线

- 首参数排除列表：是
- 第二参数使用 `ILOAD 3`：否
- 第三参数使用 `ILOAD 3`：否
- 第四参数使用 `ILOAD 3`：否
- 第五参数使用 `ILOAD 3`：是
- 第六参数使用 `ILOAD 3`：是
- 构造器参数数量：`6`
- 链描述符：`(Ljava/util/GregorianCalendar;)V`

### 基线与状态

- 已变基到 leftover-docs [#455](https://github.com/gaoyu06/native-obfuscator/pull/455)，`14d68873`（`14d68873b06dbf98ad4c3fb9314c4a82bc546544`）。
- 父级 XML **776**（`IrCompilerTest` 769 + `CodegenModeTest` 7），0 失败/错误/跳过。父级复跑包含 `threeImmediateNewExtraLocalSixFifthSixthArgChainInputsCompileAndRunWithJavaParity`。
- Processor changed：No。
- Ship-ready：No。
- 生产目标仍未完成。
- 默认值未更改。
