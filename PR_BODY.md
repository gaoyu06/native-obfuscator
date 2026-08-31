# test: admit extra-local int as the third and fifth six-arg NEW arguments

## English

### Summary

- Adds the fixture shape `new-constructor-extra-local-argument-six-third-fifth`.
- Admits an extra-local `int` as the third and fifth initializer arguments of a six-argument `GregorianCalendar` `NEW` chain.
- Adds admission, JVM-verification, and Java/JNI parity coverage.
- Based on [leftover-docs #448](https://github.com/gaoyu06/native-obfuscator/pull/448) at `9a95f300` (`9a95f30030a6dbfd2772484c8a3148f11f855e71`).

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

- Latest compiler parent XML until the parent re-run: #448 (761), comprising `IrCompilerTest` 754 and `CodegenModeTest` 7.
- Expected parent XML after leftover-docs: 764, comprising `IrCompilerTest` 757 and `CodegenModeTest` 7.
- Child XML is discarded; only the parent re-run is authoritative.
- Processor changed: No.
- Ship-ready: No.
- The production goal remains incomplete.

## 中文

### 摘要

- 新增夹具形状 `new-constructor-extra-local-argument-six-third-fifth`。
- 允许额外局部 `int` 作为六参数 `GregorianCalendar` `NEW` 初始化链的第三个和第五个参数。
- 新增准入、JVM 验证以及 Java/JNI 一致性覆盖。
- 基于 [leftover-docs #448](https://github.com/gaoyu06/native-obfuscator/pull/448) 的 `9a95f300`（`9a95f30030a6dbfd2772484c8a3148f11f855e71`）。

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

- 在父分支重新运行前，最新编译器父 XML 为 #448（761），其中 `IrCompilerTest` 754、`CodegenModeTest` 7。
- leftover-docs 后预期父 XML 为 764，其中 `IrCompilerTest` 757、`CodegenModeTest` 7。
- 子分支 XML 会被丢弃；只有父分支重新运行的结果具有权威性。
- Processor changed：No。
- Ship-ready：No。
- 生产目标仍未完成。
