# test: admit extra-local int as first, second, and fourth five-arg NEW arguments

## English

### Summary

- Adds fixture-only IR admission coverage for an extra-local `int` used as the first, second, and fourth arguments of the five-argument `GregorianCalendar` `NEW` initializer.
- Adds admission, rewritten-JVM-verification, and Java/native parity tests.
- Leaves `ConstructorSpecialMethodProcessor` unchanged.

### Status

- Expected parent XML total after leftover-docs: **674** (671 + 3).
- Ship-ready: **No**.
- The default `--codegen` mode remains `legacy`.

## 中文

### 概要

- 仅在测试夹具中新增 IR 准入覆盖：将额外局部 `int` 用作五参数 `GregorianCalendar` `NEW` 初始化器的第一、第二和第四个参数。
- 新增准入、重写后 JVM 验证以及 Java/native 输出一致性测试。
- `ConstructorSpecialMethodProcessor` 保持不变。

### 状态

- leftover-docs 合入后，父分支预期 XML 总数：**674**（671 + 3）。
- 可发布状态：**否**。
- 默认 `--codegen` 模式仍为 `legacy`。
