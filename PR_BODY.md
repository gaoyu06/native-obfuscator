# Extra-local float as the second NEW initializer argument

## English

- Admits this fixture compose only: a proven prefix extra-local float copy as
  the **second** initializer argument of isolated two-argument float `NEW`;
  the first argument remains `FCONST_1`.
- Adds admission, JVM-verification, and Java/native IR parity coverage.
- Processor changed: **No**.
- Expected parent XML after landing: **548** tests
  (`IrCompilerTest` 541 + `CodegenModeTest` 7).
- Ship-ready: **No**.

Leftover inventory #310 is not a JDK support badge. This fixture increment does
not authorize a codegen, IR-lowering, or backend default flip.

## 中文

- 本次仅放行这一种测试夹具组合：将前缀中已证明的额外局部 `float` 副本作为
  隔离双参数浮点 `NEW` 的**第二个**初始化参数；第一个参数仍为
  `FCONST_1`。
- 新增放行、JVM 验证以及 Java/native IR 输出一致性覆盖。
- 是否修改处理器：**否**。
- 合入父分支后的预期 XML：**548** 项测试
  （`IrCompilerTest` 541 + `CodegenModeTest` 7）。
- 可发布：**否**。

剩余项清单 #310 不是 JDK 支持徽章。本测试夹具增量不授权切换 codegen、
IR lowering 或 backend 的默认值。
