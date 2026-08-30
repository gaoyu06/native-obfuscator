# Leftover admitted

- Admit proven prefix extra-local int copies as chain-input leaves: one
  pre-first-call `ISTORE` directly fed by a declared int-family `ILOAD`, with
  one dominating overlapping write and `LOCAL_INT` at every chain call.
- Thread the proof through every already-admitted int binary, including
  `IDIV`, `IREM`, shifts, inner division, and four-level trees.
- Keep the copy and int arithmetic in retained JVM bytecode, with one hidden
  bridge and the existing path id.

# Still rejected

- `INEG` over an extra local, constant, or computed value.
- Five-or-more nested int binaries.
- Extra-local long shift counts/values and extra-local `LDIV`/`LREM` operands.
- Constants, computed expressions, aliases, multiple writes, non-dominating
  stores, and otherwise unproven stores used as extra-local copy sources.

# Verification

- Focused Gradle verification:
  `CC=gcc CXX=g++ ./gradlew :obfuscator:test --rerun-tasks --tests by.radioegor146.ir.IrCompilerTest --tests by.radioegor146.CodegenModeTest`
- Parsed JUnit XML: `IrCompilerTest` 363 and `CodegenModeTest` 7, for 370
  combined; 0 failures, 0 errors, and 0 skipped.
- Ship-ready: **No**. The production goal remains incomplete and the default
  remains `legacy`.

# 中文：已接纳的遗留形状

- 接纳经过证明的前缀 int 额外局部变量副本作为调用链输入叶子：首次调用前的
  `ISTORE` 必须直接读取已声明的 int-family `ILOAD`，重叠写入必须唯一且支配
  所有调用，并且每个调用点的局部变量状态都必须精确为 `LOCAL_INT`。
- 该证明适用于所有已接纳的 int 二元运算，包括 `IDIV`、`IREM`、移位、内层
  除法以及四层表达式树。
- 副本和 int 运算继续保留在 JVM 字节码前缀中，仍只使用一个隐藏桥接方法和
  现有路径编号。

# 中文：仍然拒绝

- 对额外局部变量、常量或计算值执行 `INEG`。
- 五层及以上的 int 二元表达式树。
- 额外局部 long 移位计数/数值以及额外局部 `LDIV`/`LREM` 操作数。
- 以常量、计算表达式、别名、多次写入、非支配写入或其他未经证明的写入作为
  额外局部副本来源。

# 中文：验证

- 聚焦 Gradle 命令：
  `CC=gcc CXX=g++ ./gradlew :obfuscator:test --rerun-tasks --tests by.radioegor146.ir.IrCompilerTest --tests by.radioegor146.CodegenModeTest`
- JUnit XML 实际结果：`IrCompilerTest` 363 项，`CodegenModeTest` 7 项，
  合计 370 项；失败 0，错误 0，跳过 0。
- 可发布：**否**。生产目标尚未完成，默认路径仍为 `legacy`。
