## English

### Summary

- Document constructor-prefix `JSR`/`RET` leftovers as fail-closed; this is not
  an admission.
- Keep exception tables that cover the `JSR`, subroutine body, `RET`, or
  generated inline clone rejected before mutation.
- Keep nested subroutines and control flow that reaches extra work after a
  lexical `RET` rejected before mutation.
- Execute every loadable untouched reject fixture through JVM verification at
  the required legacy classfile version 50. The branch that jumps across a
  lexical `RET` is inherently verifier-invalid and remains reject-only. The
  processor is unchanged.

### Tests

- `rejectsConstructorJsrRetWithExceptionTableBeforeMutation`
- `unprovenConstructorJsrRetShapesPassJava8JvmVerification`

Gate:

```text
CC=gcc CXX=g++ ./gradlew :obfuscator:test --rerun-tasks \
  --tests by.radioegor146.ir.IrCompilerTest \
  --tests by.radioegor146.CodegenModeTest
```

Parent JUnit XML under `obfuscator/build/test-results/test/`:

- `IrCompilerTest`: 502 tests, 0 failures, 0 errors, 0 skipped.
- `CodegenModeTest`: 7 tests, 0 failures, 0 errors, 0 skipped.
- Total: 509 tests, 0 failures, 0 errors, 0 skipped.

Cite `unprovenConstructorJsrRetShapesPassJava8JvmVerification`. Child totals are discarded.

Admitted: **No**

Ship-ready: **No**

## 中文

### 摘要

- 将构造器前缀 `JSR`/`RET` leftover 记录为 fail-closed；本次不是准入。
- 覆盖 `JSR`、子程序主体、`RET` 或生成的内联副本的异常表继续在修改前拒绝。
- 嵌套子程序以及在词法 `RET` 后到达额外工作的控制流继续在修改前拒绝。
- 使用旧式字节码所需的 classfile 50，对每个可加载且未经改写的拒绝夹具执行
  JVM 验证。跨越词法 `RET` 的分支本身无法通过验证器，因此仅保留修改前拒绝
  覆盖。处理器未改。

### 测试

- `rejectsConstructorJsrRetWithExceptionTableBeforeMutation`
- `unprovenConstructorJsrRetShapesPassJava8JvmVerification`

门禁命令：

```text
CC=gcc CXX=g++ ./gradlew :obfuscator:test --rerun-tasks \
  --tests by.radioegor146.ir.IrCompilerTest \
  --tests by.radioegor146.CodegenModeTest
```

父级 `obfuscator/build/test-results/test/` JUnit XML 汇总：

- `IrCompilerTest`：502 个测试，0 个失败，0 个错误，0 个跳过。
- `CodegenModeTest`：7 个测试，0 个失败，0 个错误，0 个跳过。
- 总计：509 个测试，0 个失败，0 个错误，0 个跳过。

引用 `unprovenConstructorJsrRetShapesPassJava8JvmVerification`。子代理汇总作废。

已接纳：**否**

可发布：**否**
