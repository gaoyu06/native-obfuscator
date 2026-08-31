# English

## Summary

- Adds fixture-only coverage for an extra-local `int` used as the second and third arguments of a six-argument `GregorianCalendar` `NEW` constructor call.
- Adds admission, rewritten-JVM-verification, and Java/native parity tests.
- Keeps the processor and production defaults unchanged.

## Baseline and test accounting

- Baseline leftover-docs: #438 at `a158191a` (`a158191a3f3f5bcf77b5f01dbd59b022fd3c1cf1`).
- Latest compiler parent XML: #438 (746), comprising `IrCompilerTest` 739 and `CodegenModeTest` 7.
- Expected parent XML after these three tests: 749, comprising `IrCompilerTest` 742 and `CodegenModeTest` 7.

## Fixture wiring

- Shape: `new-constructor-extra-local-argument-six-second-third`
- First-argument exclude list: yes; the first argument remains `ICONST_1`.
- Second-argument `ILOAD 3` list: yes.
- Third-argument `ILOAD 3` list: yes.
- Fourth-, fifth-, and sixth-argument `ILOAD 3` lists: no; they remain `ICONST_4`, `ICONST_5`, and `BIPUSH 6`.
- Constructor argument count: 6.
- Chain descriptor: `(Ljava/util/GregorianCalendar;)V`.

Processor changed: No.
Ship-ready: No.
Defaults changed: No (`--codegen=legacy`, `--ir-lower=direct`, and `--backend=cpp` remain unchanged).

# 中文

## 概述

- 新增纯测试夹具覆盖：将额外局部 `int` 用作六参数 `GregorianCalendar` `NEW` 构造调用的第二和第三个参数。
- 新增准入、重写后 JVM 验证以及 Java/native 输出一致性测试。
- 处理器和生产默认值均保持不变。

## 基线与测试计数

- leftover-docs 基线：#438，提交 `a158191a`（`a158191a3f3f5bcf77b5f01dbd59b022fd3c1cf1`）。
- 最新编译器父级 XML：#438（746），其中 `IrCompilerTest` 739、`CodegenModeTest` 7。
- 新增三个测试后的预期父级 XML：749，其中 `IrCompilerTest` 742、`CodegenModeTest` 7。

## 夹具接线

- 形状：`new-constructor-extra-local-argument-six-second-third`
- 第一参数排除列表：是；第一参数保持为 `ICONST_1`。
- 第二参数 `ILOAD 3` 列表：是。
- 第三参数 `ILOAD 3` 列表：是。
- 第四、第五和第六参数 `ILOAD 3` 列表：否；分别保持为 `ICONST_4`、`ICONST_5` 和 `BIPUSH 6`。
- 构造参数数量：6。
- 链描述符：`(Ljava/util/GregorianCalendar;)V`。

处理器有改动：否。
可发布：否。
默认值有改动：否（`--codegen=legacy`、`--ir-lower=direct` 和 `--backend=cpp` 均保持不变）。
