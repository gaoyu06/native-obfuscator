# test: admit extra-local int as first, third, and fourth five-arg NEW arguments

## English

This fixture-only change adds IR admission coverage for a five-argument
`GregorianCalendar` `NEW` initializer whose first, third, and fourth arguments
load the extra local. The second and fifth arguments remain constants.

- Adds admission, JVM-verification, and Java/native parity fixtures.
- Wires the exact fixture shape into the third-argument, fourth-argument,
  argument-count, and chain-descriptor test helpers.
- Leaves `ConstructorSpecialMethodProcessor` unchanged.
- Expected parent XML total after leftover-docs: **680** (**677 + 3**).
- Ship-ready: **No**. The `--codegen` default remains `legacy`.

## 中文

此变更仅涉及测试夹具，为五参数 `GregorianCalendar` 的 `NEW` 初始化器补充
IR 准入覆盖：第一、第三和第四个参数从额外局部变量加载，第二和第五个参数
保持常量。

- 新增准入、JVM 验证和 Java/本地执行一致性测试夹具。
- 将精确夹具形状接入第三参数、第四参数、参数数量和调用链描述符测试辅助逻辑。
- `ConstructorSpecialMethodProcessor` 保持不变。
- leftover-docs 合入后，父分支预期 XML 总数：**680**（**677 + 3**）。
- 可发布状态：**否**。`--codegen` 默认值仍为 `legacy`。
