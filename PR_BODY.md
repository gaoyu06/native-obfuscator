# English

(a) Does this complete the production goal? **No.**

(b) Is this ship-ready? **No.**

(c) What changed?

- Admits one exception table whose protected range is wholly inside one proven
  path-id suffix and whose handler is one of the six exact isolated forms in a
  tail immediately after the final suffix.
- Keeps the tail out of suffix discovery, clones it into the independent IR
  body with the table, and omits both from the constructor wrapper.
- Applies the same isolated-tail proof to the canonical suffix used by
  identical-copy normalization.
- Keeps cross-suffix and chain-covering tables, extra-work or normally reachable
  tails, `ASTORE 0`, unproven control flow, and unassigned extras rejected.
- #200, #201, and #208 already cover prefix tables, suffix tables, and relocated
  prefix handlers. This increment is limited to the method-end handler tail.
- The default remains `--codegen legacy`.

(d) How was it verified?

- Added focused path-id admission/rewrite, unsafe-tail rejection,
  identical-copy admission/rewrite, and CMake/g++ Java-parity coverage.
- Focused Gradle/JUnit totals will be recorded from the generated XML after the
  required rerun.

# 中文

(a) 这是否完成生产目标？**否。**

(b) 这是否已可交付？**否。**

(c) 本次改动：

- 当异常表保护范围完全位于一个已证明的 path-id 后缀内，且处理器位于最后
  一个后缀之后的方法尾部并严格匹配六种隔离形式之一时，允许该异常表。
- 后缀发现不会把该尾部当作新的后缀；独立 IR 方法会复制处理器和异常表，
  构造函数包装层会省略二者。
- 相同的隔离尾部证明也用于 identical-copy 规范化后的 canonical 后缀。
- 跨后缀、覆盖构造链调用、包含额外指令、可由普通控制流到达、使用
  `ASTORE 0`、控制流未证明或额外局部变量未赋值的情况仍然拒绝。
- #200、#201、#208 已覆盖前缀异常表、后缀异常表和重定位前缀处理器；
  本次仅处理方法尾部处理器。
- 默认选项仍为 `--codegen legacy`。

(d) 验证方式：

- 新增 path-id 接纳与重写、危险尾部拒绝、identical-copy 接纳与重写，
  以及基于 CMake/g++ 的 Java 输出一致性测试。
- 必需的聚焦测试完成后，将依据生成的 JUnit XML 记录真实统计。
