# Keep nine constructor path-id suffixes fail-closed

## English

### Summary

- Add a nine-path Java 8 constructor fixture with nine reachable super calls,
  original receivers, declared-int arguments, empty exception tables, and
  pairwise-distinct nonempty suffixes ending in `RETURN`.
- Assert rejection occurs before instruction, generated-buffer,
  `MethodContext.proxyMethod`, or hidden-method mutation.
- Load and execute all nine paths on the JVM to prove the untouched fixture is
  verifier-valid.
- Document the fail-closed boundary in
  `docs/architecture/ir-flex-ctor-status.md`.

`MAX_DISTINCT_SUFFIXES` remains 8. This change does not admit nine paths,
skip-super, or any new constructor shape, and does not change defaults.

### Verification

```text
CC=gcc CXX=g++ ./gradlew :obfuscator:test --rerun-tasks \
  --tests by.radioegor146.ir.IrCompilerTest \
  --tests by.radioegor146.CodegenModeTest
```

Child-only XML:

- `TEST-by.radioegor146.ir.IrCompilerTest.xml`: 466 tests, 0 skipped,
  0 failures, 0 errors
- `TEST-by.radioegor146.CodegenModeTest.xml`: 7 tests, 0 skipped,
  0 failures, 0 errors
- `IrCompilerTest`: 466 `@Test` annotations = 466 public test methods

Ship-ready: **No**

## 中文

### 摘要

- 新增 Java 8 九路径构造器夹具：包含九个可达的父类构造调用、原始接收者、
  已声明的 `int` 参数、空异常表，以及九个两两不同且以 `RETURN` 结束的
  非空后缀。
- 断言拒绝发生在任何指令对象、生成缓冲区、单一
  `MethodContext.proxyMethod` 或隐藏方法发生变更之前。
- 在 JVM 上加载夹具并执行全部九条路径，证明未经改写的字节码可通过验证。
- 仅在 `docs/architecture/ir-flex-ctor-status.md` 中记录该 fail-closed
  边界。

`MAX_DISTINCT_SUFFIXES` 仍为 8。本变更不放行九路径、不放行 skip-super，
不放行任何新的构造器形状，也不改变默认配置。

### 验证

上述门禁命令执行成功。仅子代理生成的 XML 结果：

- `TEST-by.radioegor146.ir.IrCompilerTest.xml`：466 个测试，0 跳过，
  0 失败，0 错误
- `TEST-by.radioegor146.CodegenModeTest.xml`：7 个测试，0 跳过，
  0 失败，0 错误
- `IrCompilerTest`：466 个 `@Test` 注解 = 466 个 public 测试方法

可发布：**否**
