# test: admit extra-local int as the third and sixth six-arg NEW arguments

## English

### Summary

- Adds fixture-only admission coverage for an extra-local `int` used as the third and sixth initializer arguments of a six-argument `GregorianCalendar` `NEW`.
- Keeps constructor lowering, bytecode/CFG handling, JNI generation, and defaults unchanged.
- Rebased onto leftover-docs [#451](https://github.com/gaoyu06/native-obfuscator/pull/451) at `a38ebbc7` (`a38ebbc7410b349982bade827866eb5736518052`).

### Wiring checklist

- First-argument exclude: Yes
- Second argument uses `ILOAD 3`: No
- Third argument uses `ILOAD 3`: Yes
- Fourth argument uses `ILOAD 3`: No
- Fifth argument uses `ILOAD 3`: No
- Sixth argument uses `ILOAD 3`: Yes
- Argument count: `6`
- Chain descriptor: `(Ljava/util/GregorianCalendar;)V`

### Validation and status

- Parent XML: **767** (`IrCompilerTest` 760 + `CodegenModeTest` 7), 0 failures/errors/skipped.
- Parent re-ran `threeImmediateNewExtraLocalSixThirdSixthArgChainInputsCompileAndRunWithJavaParity`.
- Processor changed: **No**
- Ship-ready: **No**
- Production goal remains incomplete.
- Defaults remain unchanged: no `--codegen=legacy`, `--ir-lower=direct`, or `--backend=cpp` default changes.

## 中文

### 摘要

- 仅增加夹具准入覆盖：在六参数 `GregorianCalendar` `NEW` 初始化器中，将额外局部 `int` 用作第三和第六个参数。
- 构造器 lowering、字节码/CFG 处理、JNI 生成及默认配置均保持不变。
- 已变基到 leftover-docs [#451](https://github.com/gaoyu06/native-obfuscator/pull/451)，`a38ebbc7`（`a38ebbc7410b349982bade827866eb5736518052`）。

### 接线检查清单

- 第一参数排除列表：是
- 第二参数使用 `ILOAD 3`：否
- 第三参数使用 `ILOAD 3`：是
- 第四参数使用 `ILOAD 3`：否
- 第五参数使用 `ILOAD 3`：否
- 第六参数使用 `ILOAD 3`：是
- 参数数量：`6`
- 链描述符：`(Ljava/util/GregorianCalendar;)V`

### 验证与状态

- 父级 XML：**767**（`IrCompilerTest` 760 + `CodegenModeTest` 7），失败/错误/跳过均为 0。
- 父级复跑包含 `threeImmediateNewExtraLocalSixThirdSixthArgChainInputsCompileAndRunWithJavaParity`。
- Processor changed：**No**
- Ship-ready：**No**
- 生产目标仍未完成。
- 默认配置保持不变：未更改 `--codegen=legacy`、`--ir-lower=direct` 或 `--backend=cpp` 的默认值。
