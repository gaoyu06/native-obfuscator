# Fixture-only IR admission: five-argument third extra-local input

## English

### Summary

- Adds the fixture-only shape `new-constructor-extra-local-argument-five-third`.
- Retains the JVM leaf sequence `NEW GregorianCalendar; DUP; ICONST_1; ICONST_2; ILOAD 3; ICONST_4; ICONST_5; INVOKESPECIAL <init>(IIIII)V`.
- Covers IR admission, rewritten-bytecode JVM verification, and Java/native runtime parity.
- Changes no `ConstructorSpecialMethodProcessor` or other production compiler code.
- Keeps the default code-generation options unchanged, including `--codegen=legacy`.

### Validation

- Annotation invariant: 622 `@Test` annotations = 622 public test methods.
- Focused child gate passed: `CC=gcc CXX=g++ ./gradlew :obfuscator:test --rerun-tasks --tests by.radioegor146.ir.IrCompilerTest --tests by.radioegor146.CodegenModeTest`.
- Child XML: 629 tests total (622 `IrCompilerTest` + 7 `CodegenModeTest`), with 0 failures, 0 errors, and 0 skipped tests.
- The parent will rerun the focused gate and discard this child XML.

### Release status

- Processor changed: No
- Ship-ready: No

## 中文

### 摘要

- 新增仅测试夹具形状 `new-constructor-extra-local-argument-five-third`。
- 保留 JVM 叶节点指令序列：`NEW GregorianCalendar; DUP; ICONST_1; ICONST_2; ILOAD 3; ICONST_4; ICONST_5; INVOKESPECIAL <init>(IIIII)V`。
- 覆盖 IR 准入、重写后字节码的 JVM 校验，以及 Java/原生运行时一致性。
- 未修改 `ConstructorSpecialMethodProcessor` 或其他生产编译器代码。
- 默认代码生成选项保持不变，包括 `--codegen=legacy`。

### 验证

- 注解不变量：622 个 `@Test` 注解 = 622 个 public 测试方法。
- 子代理聚焦门禁已通过：`CC=gcc CXX=g++ ./gradlew :obfuscator:test --rerun-tasks --tests by.radioegor146.ir.IrCompilerTest --tests by.radioegor146.CodegenModeTest`。
- 子代理 XML：共 629 个测试（622 个 `IrCompilerTest` + 7 个 `CodegenModeTest`），0 个失败、0 个错误、0 个跳过。
- 父代理会重新运行聚焦门禁，并丢弃本次子代理 XML。

### 发布状态

- Processor changed：No
- Ship-ready：No
