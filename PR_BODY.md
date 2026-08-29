## English

**(a) Scope:** After #171 and #183, this increment admits one additional
constructor-split mixed try/catch form: a suffix-protected range may target an
isolated prefix `POP; GOTO ret` handler when `ret` is an isolated prefix
`RETURN`, the handler `GOTO` is its only incoming edge, and neither label has
another CFG or exception-table role. The handler and return block are cloned
into the independent IR suffix and removed from the bytecode wrapper. The
existing isolated `POP; RETURN` form remains admitted. Prefix ranges with suffix
handlers, the six non-isolated mixed placements, and all other prefix-handler
work remain rejected before mutation.

**(b) Ship-ready?** **No.** This is one bounded constructor admission increment;
it does not complete the production goal or change defaults.

**(c) Review and gate:** There is no stacked review. The gate is the executed
focused `IrCompilerTest` and `CodegenModeTest` suite, including the new
compile-and-run Java/JNI parity harness under `java -Xverify:all -Xcheck:jni`.
JUnit XML reports 187 + 7 = 194 tests, with 0 failures, 0 errors, and 0 skipped.

**(d) Preconditions:** Remaining constructor leftovers, unsafe constant-dynamic
forms, and `jsr`/`ret` stay reject-before-mutation. `--codegen` remains
`legacy`.

## 中文

**(a) 范围：** 在 #171 和 #183 之后，本增量仅新增一种构造函数拆分中的混合
try/catch 形式：后缀保护区间可以指向隔离的前缀 `POP; GOTO ret` 处理器，但
`ret` 必须是隔离的前缀 `RETURN`，处理器中的 `GOTO` 必须是它唯一的入边，且
两个标签都不能承担其他 CFG 或异常表角色。处理器和返回块会克隆到独立 IR
后缀中，并从字节码包装器移除。原有隔离的 `POP; RETURN` 形式继续支持。前缀
保护区间配后缀处理器、六种非隔离混合位置以及所有其他前缀处理器工作仍在修改
前拒绝。

**(b) 可发布？** **否。** 这只是一个有界的构造函数准入增量；不会完成生产
目标，也不会更改默认值。

**(c) 审查与门禁：** 没有堆叠审查。门禁是实际执行的 `IrCompilerTest` 和
`CodegenModeTest` 聚焦测试套件，其中包括新增的、在
`java -Xverify:all -Xcheck:jni` 下运行的 Java/JNI 编译执行一致性测试。
JUnit XML 记录为 187 + 7 = 194 个测试，0 个失败、0 个错误、0 个跳过。

**(d) 前置条件：** 其余构造函数遗留项、不安全的 constant-dynamic 形式以及
`jsr`/`ret` 继续在修改前拒绝。`--codegen` 保持为 `legacy`。
