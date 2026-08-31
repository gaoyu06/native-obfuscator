## English

### Summary

- Admits the fixture shape `new-constructor-extra-local-argument-six-first-second`.
- Keeps the complete six-argument `GregorianCalendar` `NEW; DUP; args; <init>` sequence in the retained JVM constructor prefix.
- Covers admission, rewritten JVM verification, and Java/native runtime parity with three focused tests.
- This is fixture admission, not a JDK support badge.

### Baseline and scope

- Leftover-docs baseline: [#427](https://github.com/gaoyu06/native-obfuscator/pull/427) at `96de67dd` (`96de67dd3473d45aa818064d17cc0b3034466e04`).
- Latest compiler parent XML until the parent re-runs: [#427](https://github.com/gaoyu06/native-obfuscator/pull/427) — **731** (`IrCompilerTest` 724 + `CodegenModeTest` 7).
- Expected parent XML after leftover-docs: **734** (731 + 3).
- Processor changed: **No**.
- Ship-ready: **No**.
- Defaults unchanged: no change to `--codegen=legacy`, `--ir-lower=direct`, or `--backend=cpp`.
- `MethodContext.proxyMethod` remains singular; each constructor has one native method.

### Local verification

Pending child run:

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

- leftover-docs 基线：[#427](https://github.com/gaoyu06/native-obfuscator/pull/427)，提交 `96de67dd`（`96de67dd3473d45aa818064d17cc0b3034466e04`）。
- 在父任务重新运行之前，最新编译器父 XML 为 [#427](https://github.com/gaoyu06/native-obfuscator/pull/427) 的 **731**（`IrCompilerTest` 724 + `CodegenModeTest` 7）。
- leftover-docs 之后预期父 XML：**734**（731 + 3）。
- Processor changed：**No**。
- Ship-ready：**No**。
- 默认值保持不变：未改动 `--codegen=legacy`、`--ir-lower=direct` 或 `--backend=cpp`。
- `MethodContext.proxyMethod` 保持单一；每个构造函数只有一个原生方法。

### 本地验证

子任务测试待运行：

```bash
CC=gcc CXX=g++ ./gradlew :obfuscator:test --tests by.radioegor146.ir.IrCompilerTest --tests by.radioegor146.CodegenModeTest
```
