## English

### Summary

- Add fixture-only IR admission coverage for a four-argument `java.awt.Insets` `NEW` initializer whose first, second, and fourth arguments come from the extra int local.
- Keep `NEW; DUP; ILOAD 3; ILOAD 3; ICONST_3; ILOAD 3; INVOKESPECIAL <init>(IIII)V` in the retained JVM prefix.
- Verify that the native `(II)V` body contains only `RETURN`, the proxy descriptor is `(Ljava/lang/Object;II)V`, and exactly one hidden bridge is used.
- No production compiler or processor code changes.

### Verification

```text
CC=gcc CXX=g++ ./gradlew :obfuscator:test --rerun-tasks \
  --tests by.radioegor146.ir.IrCompilerTest \
  --tests by.radioegor146.CodegenModeTest
```

Child XML (discarded by the parent): 610 `IrCompilerTest` + 7 `CodegenModeTest` = 617 tests, with 0 failures, 0 errors, and 0 skipped.

Ship-ready: No.

## 中文

### 摘要

- 仅增加 IR 测试夹具覆盖：四参数 `java.awt.Insets` 的 `NEW` 初始化中，第一个、第二个和第四个参数来自额外的 int 局部变量。
- 保留 JVM 前缀中的完整 `NEW; DUP; ILOAD 3; ILOAD 3; ICONST_3; ILOAD 3; INVOKESPECIAL <init>(IIII)V`。
- 验证原生 `(II)V` 方法体仅包含 `RETURN`，代理描述符为 `(Ljava/lang/Object;II)V`，且只使用一个隐藏桥接方法。
- 未修改生产编译器或处理器代码。

### 验证

子任务 XML（父任务会丢弃）：610 个 `IrCompilerTest` + 7 个 `CodegenModeTest` = 617 个测试；失败、错误和跳过均为 0。

可发布：否。
