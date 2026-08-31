## English

### Summary

- Admits the fixture shape `new-constructor-extra-local-argument-six-first-second`.
- Keeps the complete six-argument `GregorianCalendar` `NEW; DUP; args; <init>` sequence in the retained JVM constructor prefix.
- Covers admission, rewritten JVM verification, and Java/native runtime parity with three focused tests.
- This is fixture admission, not a JDK support badge.

### Baseline and scope

- Leftover-docs baseline: [#429](https://github.com/gaoyu06/native-obfuscator/pull/429) at `49efdedf` (`49efdedf44ad1bb112cfbc3e38aa47413642a3c5`).
- Parent XML: **734** (`IrCompilerTest` 727 + `CodegenModeTest` 7), including `threeImmediateNewExtraLocalSixFirstSecondArgChainInputsCompileAndRunWithJavaParity`.
- Processor changed: **No**.
- Ship-ready: **No**.
- Defaults unchanged: no change to `--codegen=legacy`, `--ir-lower=direct`, or `--backend=cpp`.
- `MethodContext.proxyMethod` remains singular; each constructor has one native method.

### Local verification

Parent re-ran 734/734 with zero failures, errors, or skips. Child XML is discarded.

```bash
CC=gcc CXX=g++ ./gradlew :obfuscator:test --tests by.radioegor146.ir.IrCompilerTest --tests by.radioegor146.CodegenModeTest
```

## 中文

### 摘要

- 接纳测试夹具形状 `new-constructor-extra-local-argument-six-first-second`。
- 完整的六参数 `GregorianCalendar` `NEW; DUP; args; <init>` 序列保留在 JVM 构造函数前缀中。
- 新增三个聚焦测试，覆盖接纳、改写后的 JVM 验证以及 Java/原生运行时一致性。
- 这是测试夹具接纳，不代表新增 JDK 支持标识。

### 基线与范围

- leftover-docs 基线：[#429](https://github.com/gaoyu06/native-obfuscator/pull/429)，提交 `49efdedf`（`49efdedf44ad1bb112cfbc3e38aa47413642a3c5`）。
- 父任务 XML：**734**（`IrCompilerTest` 727 + `CodegenModeTest` 7），含 `threeImmediateNewExtraLocalSixFirstSecondArgChainInputsCompileAndRunWithJavaParity`。
- Processor changed：**No**。
- Ship-ready：**No**。
- 默认值保持不变：未改动 `--codegen=legacy`、`--ir-lower=direct` 或 `--backend=cpp`。
- `MethodContext.proxyMethod` 保持单一；每个构造函数只有一个原生方法。

### 本地验证

父任务重跑 734/734，失败、错误和跳过均为零。子任务 XML 作废。

```bash
CC=gcc CXX=g++ ./gradlew :obfuscator:test --tests by.radioegor146.ir.IrCompilerTest --tests by.radioegor146.CodegenModeTest
```
