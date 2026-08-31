# test: admit extra-local int as the third and sixth six-arg NEW arguments

## English

### Summary

- Adds fixture-only admission coverage for an extra-local `int` used as the third and sixth initializer arguments of a six-argument `GregorianCalendar` `NEW`.
- Keeps constructor lowering, bytecode/CFG handling, JNI generation, and defaults unchanged.
- Based on leftover-docs [#450](https://github.com/gaoyu06/native-obfuscator/pull/450) at `4ca8cfd2` (`4ca8cfd21efab528cd49f2cd4202d129a7be6cba`).

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

- Latest compiler parent XML until the parent re-run: **#450 (764)** (`IrCompilerTest` 757 + `CodegenModeTest` 7).
- Expected parent XML after leftover-docs: **767** (`IrCompilerTest` 760 + `CodegenModeTest` 7).
- Child XML is discarded; only the parent re-run is authoritative.
- Processor changed: **No**
- Ship-ready: **No**
- Production goal remains incomplete.
- Defaults remain unchanged: no `--codegen=legacy`, `--ir-lower=direct`, or `--backend=cpp` default changes.

## 中文

### 摘要

- 仅增加夹具准入覆盖：在六参数 `GregorianCalendar` `NEW` 初始化器中，将额外局部 `int` 用作第三和第六个参数。
- 构造器 lowering、字节码/CFG 处理、JNI 生成及默认配置均保持不变。
- 基于 leftover-docs [#450](https://github.com/gaoyu06/native-obfuscator/pull/450) 的 `4ca8cfd2`（`4ca8cfd21efab528cd49f2cd4202d129a7be6cba`）。

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

- 父分支重新运行前的最新编译器 XML：**#450（764）**（`IrCompilerTest` 757 + `CodegenModeTest` 7）。
- leftover-docs 之后预期的父分支 XML：**767**（`IrCompilerTest` 760 + `CodegenModeTest` 7）。
- 子分支 XML 将被丢弃；只有父分支重新运行的结果有效。
- Processor changed：**No**
- Ship-ready：**No**
- 生产目标仍未完成。
- 默认配置保持不变：未更改 `--codegen=legacy`、`--ir-lower=direct` 或 `--backend=cpp` 的默认值。
