# test: admit extra-local int as the third and fifth six-arg NEW arguments

## English

### Summary

- Adds the fixture shape `new-constructor-extra-local-argument-six-third-fifth`.
- Admits an extra-local `int` as the third and fifth initializer arguments of a six-argument `GregorianCalendar` `NEW` chain.
- Adds admission, JVM-verification, and Java/JNI parity coverage.
- Rebased onto leftover-docs [#449](https://github.com/gaoyu06/native-obfuscator/pull/449) at `0e0c8660` (`0e0c8660bbb15e85bf2fc9c28e1a871b9bcdd4e7`).

### Wiring checklist

- First-argument exclude: Yes
- Second argument `ILOAD 3`: No
- Third argument `ILOAD 3`: Yes
- Fourth argument `ILOAD 3`: No
- Fifth argument `ILOAD 3`: Yes
- Sixth argument `ILOAD 3`: No
- Constructor argument count: `6`
- Chain descriptor: `(Ljava/util/GregorianCalendar;)V`

### Verification notes

- Parent XML: **764** (`IrCompilerTest` 757 + `CodegenModeTest` 7), 0 failures/errors/skipped.
- Parent re-ran `threeImmediateNewExtraLocalSixThirdFifthArgChainInputsCompileAndRunWithJavaParity`.
- Processor changed: No.
- Ship-ready: No.
- The production goal remains incomplete.

## 中文

### 摘要

- 新增夹具形状 `new-constructor-extra-local-argument-six-third-fifth`。
- 允许额外局部 `int` 作为六参数 `GregorianCalendar` `NEW` 初始化链的第三个和第五个参数。
- 新增准入、JVM 验证以及 Java/JNI 一致性覆盖。
- 已变基到 leftover-docs [#449](https://github.com/gaoyu06/native-obfuscator/pull/449)，`0e0c8660`（`0e0c8660bbb15e85bf2fc9c28e1a871b9bcdd4e7`）。

### 接线检查清单

- 排除第一个参数：是
- 第二个参数使用 `ILOAD 3`：否
- 第三个参数使用 `ILOAD 3`：是
- 第四个参数使用 `ILOAD 3`：否
- 第五个参数使用 `ILOAD 3`：是
- 第六个参数使用 `ILOAD 3`：否
- 构造器参数数量：`6`
- 调用链描述符：`(Ljava/util/GregorianCalendar;)V`

### 验证说明

- 父级 XML：**764**（`IrCompilerTest` 757 + `CodegenModeTest` 7），失败/错误/跳过均为 0。
- 父级复跑包含 `threeImmediateNewExtraLocalSixThirdFifthArgChainInputsCompileAndRunWithJavaParity`。
- Processor changed：No。
- Ship-ready：No。
- 生产目标仍未完成。
