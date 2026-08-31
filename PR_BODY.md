## English

Adds fixture-only IR admission coverage for a five-argument
`GregorianCalendar` constructor whose first, second, and third initializer
arguments come from the same extra local.

- adds admission, JVM verification, and Java/native parity tests
- keeps the retained constructor leaf on the IR path
- expected parent XML total: 671
- ship-ready: No

## 中文

新增仅测试夹具的 IR 准入覆盖：五参数 `GregorianCalendar` 构造器的第一、
第二和第三个初始化参数均来自同一个额外局部变量。

- 新增准入、JVM 校验及 Java/native 一致性测试
- 保持保留的构造器叶节点使用 IR 路径
- 预期父级 XML 总数：671
- 可发布：否
