# (a) Summary / 摘要

Admit constructor try/catch entries only when `start`, `end`, and `handler`
all remain in the retained bytecode prefix. The rewritten constructor keeps
those entries, while `createNativeBody` excludes them.

仅当构造函数异常表项的 `start`、`end` 和 `handler` 全部位于保留的字节码前缀时才接纳；
重写后的构造函数保留该表项，`createNativeBody` 不复制它。

# (b) Safety boundary / 安全边界

All-suffix entries remain supported. Every mixed prefix/suffix label placement
is rejected, so exceptions from the native suffix or hidden bridge cannot
enter a bytecode handler before the bridge. Existing constructor CFG,
`ASTORE 0`, `jsr`/`ret`, multi-super diamond, and extra-local checks are
unchanged.

纯后缀异常表项继续受支持。任何前缀与后缀混合的标签位置仍会被拒绝，因此原生后缀或隐藏桥接
抛出的异常不能进入桥接之前的字节码处理器。现有的构造函数 CFG、`ASTORE 0`、`jsr`/`ret`、
多 `super` 菱形以及额外局部变量检查均保持不变。

# (c) Verification / 验证

Focused gate:

```text
CC=gcc CXX=g++ ./gradlew :obfuscator:test --rerun-tasks \
  --tests by.radioegor146.ir.IrCompilerTest \
  --tests by.radioegor146.CodegenModeTest
```

- `IrCompilerTest`: 141 tests, 0 failures, 0 errors, 0 skipped.
- `CodegenModeTest`: 7 tests, 0 failures, 0 errors, 0 skipped.
- Total: 148 tests, 0 failures, 0 errors, 0 skipped.
- Includes JVM verification and CMake/g++ Java parity under
  `-Xverify:all -Xcheck:jni`.

聚焦测试共 148 项，0 失败、0 错误、0 跳过；其中包含 JVM 验证，以及使用
`-Xverify:all -Xcheck:jni` 的 CMake/g++ Java 行为一致性测试。

# (d) Delivery / 交付

- Ship-ready / 可直接发布: **No**
- Stacked review / 堆叠审查: **No**
- Default flip / 默认切换: **No**

This branch does not change the `legacy` codegen, `direct` IR lowering, or
`cpp` backend defaults.

此分支不更改 `legacy` 代码生成、`direct` IR 降低或 `cpp` 后端的默认设置。
