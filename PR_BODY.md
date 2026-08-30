# English

## Summary

- Adds fixture-only IR admission coverage for a proven copied `int` used as the second and fourth arguments of a four-argument `java.awt.Insets` `NEW` initializer.
- Keeps `NEW; DUP; ICONST_1; ILOAD 3; ICONST_3; ILOAD 3; INVOKESPECIAL <init>(IIII)V` in the retained JVM prefix.
- Adds admission, JVM verification, and native Java-parity tests.

## Scope

- Processor changed: **No**
- Defaults changed: **No**
- Ship-ready: **No**
- Admitted scope: second-and-fourth extra-local `Insets` initializer arguments only.
- Java 8 remains the only fully supported version.

## Verification

```text
CC=gcc CXX=g++ ./gradlew :obfuscator:test --rerun-tasks \
  --tests by.radioegor146.ir.IrCompilerTest \
  --tests by.radioegor146.CodegenModeTest
```

Result: `BUILD SUCCESSFUL`

- Child XML: `IrCompilerTest` 601 + `CodegenModeTest` 7 = **608** (the parent will discard this child XML).
- Expected parent XML after this increment: **608**.

# 中文

## 摘要

- 仅增加 IR 测试夹具准入覆盖：已证明的 `int` 副本作为四参数 `java.awt.Insets` `NEW` 初始化器的第二和第四个参数。
- `NEW; DUP; ICONST_1; ILOAD 3; ICONST_3; ILOAD 3; INVOKESPECIAL <init>(IIII)V` 完整保留在 JVM 前缀中。
- 增加准入、JVM 验证和原生 Java 一致性测试。

## 范围

- 处理器修改：**否**
- 默认配置修改：**否**
- 可发布：**否**
- 准入范围：仅限 `Insets` 初始化器的第二和第四个 extra-local 参数。
- Java 8 仍是唯一完全支持的版本。

## 验证

```text
CC=gcc CXX=g++ ./gradlew :obfuscator:test --rerun-tasks \
  --tests by.radioegor146.ir.IrCompilerTest \
  --tests by.radioegor146.CodegenModeTest
```

结果：`BUILD SUCCESSFUL`

- 子分支 XML：`IrCompilerTest` 601 + `CodegenModeTest` 7 = **608**（父代理将丢弃此子分支 XML）。
- 合并此增量后的父分支预期 XML：**608**。
