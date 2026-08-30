# English

## Summary

- Adds fixture-only IR admission coverage for the proven extra-local int copy as the third and fourth arguments of a four-argument `java.awt.Insets` `NEW` initializer.
- Keeps the complete `NEW; DUP; args; <init>` sequence in the retained JVM prefix.
- Adds admission, JVM verification, and Java/native parity tests.
- Processor changed: No.
- Defaults changed: No (`--codegen`, `--ir-lower`, and `--backend` remain unchanged).
- Ship-ready: No. The admitted scope is only the third-and-fourth extra-local `Insets` composition.

## Validation

```text
CC=gcc CXX=g++ ./gradlew :obfuscator:test --rerun-tasks \
  --tests by.radioegor146.ir.IrCompilerTest \
  --tests by.radioegor146.CodegenModeTest

BUILD SUCCESSFUL
IrCompilerTest: 604 tests, 0 failures
CodegenModeTest: 7 tests, 0 failures
```

Expected parent XML total: **611**. The child XML also reports 611 tests and will be discarded by the parent.

Java 8 remains the only fully supported version. This increment does not authorize a default flip or broaden admission to unsupported constructor bytecode shapes.

# 中文

## 摘要

- 仅增加 IR fixture 覆盖：允许已证明的额外局部 int 副本同时作为四参数 `java.awt.Insets` `NEW` 初始化器的第三和第四个参数。
- 完整的 `NEW; DUP; args; <init>` 序列仍保留在 JVM 前缀中。
- 增加准入、JVM 验证和 Java/native 一致性测试。
- 是否修改 processor：否。
- 是否修改默认值：否（`--codegen`、`--ir-lower` 和 `--backend` 均保持不变）。
- 是否可直接发布：否。此次仅准入第三加第四参数使用额外局部值的 `Insets` 组合。

## 验证

```text
CC=gcc CXX=g++ ./gradlew :obfuscator:test --rerun-tasks \
  --tests by.radioegor146.ir.IrCompilerTest \
  --tests by.radioegor146.CodegenModeTest

BUILD SUCCESSFUL
IrCompilerTest: 604 tests, 0 failures
CodegenModeTest: 7 tests, 0 failures
```

预期父分支 XML 总数：**611**。子分支 XML 同样报告 611 个测试，父 agent 会丢弃该 XML。

Java 8 仍是唯一完全支持的版本。本增量不授权切换默认值，也不扩大到尚不支持的构造器 bytecode 形状。
