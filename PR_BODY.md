## English

### Summary

- Admit constructor-prefix `IALOAD`, `BALOAD`, `CALOAD`, and `SALOAD` indexes when the index is exactly one declared `ILOAD` or one `ILOAD` of a proven prefix extra-local int copy.
- Keep primitive array loads in retained JVM bytecode so null checks, bounds checks, and primitive widening remain JVM-defined.
- Replace the old misleading computed-index fixtures with real `ILOAD + ICONST_1 + IADD` indexes, and keep `INEG`, computed-store copies, and overwritten copies rejected before mutation.
- Add admission, fail-closed, Java 8 verification, and combined-JAR Java/native parity coverage.
- Leave the 16-level binary budget and the default `--codegen legacy` mode unchanged.

### Verification

```text
CC=gcc CXX=g++ ./gradlew :obfuscator:test --rerun-tasks \
  --tests by.radioegor146.ir.IrCompilerTest \
  --tests by.radioegor146.CodegenModeTest
```

JUnit XML:

- `IrCompilerTest`: 434 tests, 0 skipped, 0 failures, 0 errors
- `CodegenModeTest`: 7 tests, 0 skipped, 0 failures, 0 errors
- Total: 441 tests, 0 skipped, 0 failures, 0 errors
- Runtime parity test: `threeImmediateIntArrayLoadIndexesCompileAndRunWithJavaParity`

Ship-ready: No.

## 中文

### 摘要

- 当索引恰好是一个已声明参数的 `ILOAD`，或一个经过前缀证明的额外局部 int 副本的 `ILOAD` 时，允许构造器前缀中的 `IALOAD`、`BALOAD`、`CALOAD` 和 `SALOAD`。
- 原始类型数组读取继续保留在 JVM 字节码中，因此空指针检查、越界检查和原始类型扩展语义仍由 JVM 执行。
- 将旧的误导性 computed-index 测试夹具改为真正的 `ILOAD + ICONST_1 + IADD` 索引，并确保 `INEG`、计算后写入的副本和被覆盖的副本在修改前继续拒绝。
- 增加入场、失败关闭、Java 8 验证，以及组合 JAR 的 Java/native 一致性测试。
- 不修改 16 层二叉表达式预算，也不修改默认的 `--codegen legacy` 模式。

### 验证

```text
CC=gcc CXX=g++ ./gradlew :obfuscator:test --rerun-tasks \
  --tests by.radioegor146.ir.IrCompilerTest \
  --tests by.radioegor146.CodegenModeTest
```

JUnit XML：

- `IrCompilerTest`：434 个测试，0 跳过，0 失败，0 错误
- `CodegenModeTest`：7 个测试，0 跳过，0 失败，0 错误
- 合计：441 个测试，0 跳过，0 失败，0 错误
- 运行时一致性测试：`threeImmediateIntArrayLoadIndexesCompileAndRunWithJavaParity`

可发布状态：否（Ship-ready: No）。
