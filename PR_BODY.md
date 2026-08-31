# test: admit extra-local int as first, third, and fifth five-arg NEW arguments

## English

Adds fixture-only IR admission coverage for a five-argument
`GregorianCalendar` allocation whose first, third, and fifth initializer
arguments come from the same extra local.

- Covers admission, rewritten JVM verification, and Java/native parity.
- Keeps the complete `NEW; DUP; args; <init>` sequence in the retained JVM
  constructor prefix.
- Leaves compiler and runtime sources unchanged.

Ship-ready: No

## 中文

为五参数 `GregorianCalendar` 分配新增仅测试夹具的 IR 准入覆盖，其中第一、
第三和第五个初始化参数来自同一个额外局部变量。

- 覆盖准入、重写后的 JVM 验证，以及 Java/原生执行一致性。
- 完整的 `NEW; DUP; args; <init>` 序列保留在 JVM 构造器前缀中。
- 编译器与运行时源码保持不变。

可发布：否
