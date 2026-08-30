# Summary / 摘要

- Fail-closed, no admit: no additional prefix-to-suffix jump or switch shape is
  enabled.
- Strengthen rejection-before-mutation coverage for direct and switch paths
  that skip initialization, plus computed-key, extra-work, and exception-table
  post-chain switch variants.
- Verify the valid rejected post-chain variants as Java 8 classes on prefix
  return, first-call suffix, and second-call suffix paths.

- 保持失败关闭，不新增接纳：本增量不启用额外的构造器前缀到后缀跳转或开关形状。
- 加强初始化跳过路径以及计算键、额外工作、异常表等链调用后开关变体的修改前拒绝测试。
- 将仍被拒绝但字节码有效的链调用后变体作为 Java 8 类加载，并执行前缀返回、
  第一个链调用后缀和第二个链调用后缀路径。

# Safety / 安全性

Any crossing edge before a selected `this`/`super` call can bypass required
initialization. Crossing edges after one selected call are admitted only by the
existing exact shared-join `GOTO`, conditional, and switch proofs, all converging
on one shared suffix entry and one hidden bridge. Mixed or otherwise unproven
forms remain rejected before constructor, hidden-method, generated-source, or
cache mutation.

位于已选择的 `this`/`super` 调用之前的跨界边可能绕过必需的初始化。调用之后的跨界边
仍仅由已有的精确共享汇合 `GOTO`、条件分支和开关证明接纳，并汇合到单个共享后缀入口
和一个隐藏桥。混合或其他未经证明的形状继续在修改构造器、隐藏方法、生成源码或缓存
之前被拒绝。

# Verification / 验证

```text
CC=gcc CXX=g++ ./gradlew :obfuscator:test --rerun-tasks \
  --tests by.radioegor146.ir.IrCompilerTest \
  --tests by.radioegor146.CodegenModeTest
```

- `IrCompilerTest`: 454 tests, 0 failures, 0 errors, 0 skipped.
- `CodegenModeTest`: 7 tests, 0 failures, 0 errors, 0 skipped.
- Total observed: 461 tests, 0 failures, 0 errors, 0 skipped.
- `@Test` annotations and public test methods: 454 / 454; brace-count extracts
  balanced.

Ship-ready: **No**

PR: **No**
