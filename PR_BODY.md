# English

## Summary

- Admit isolated one-argument long constructor allocations such as
  `NEW Date; DUP; LCONST_1; INVOKESPECIAL Date.<init>(J)V`.
- Keep the full allocation/initializer sequence in the retained JVM prefix and
  compile only the shared constructor suffix behind one hidden bridge.
- Retain fail-closed behavior for float/double initializer arguments, computed
  leaves, array allocations, and constructors with seven or more arguments.
- Add admission, rewritten-verification, and Java/native parity coverage.

## Validation

```text
CC=gcc CXX=g++ ./gradlew :obfuscator:test --rerun-tasks \
  --tests by.radioegor146.ir.IrCompilerTest \
  --tests by.radioegor146.CodegenModeTest
```

Admitted: Yes — isolated one-argument long `NEW` only.  
Ship-ready: No.

# 中文

## 摘要

- 支持独立的单个 `long` 构造参数分配，例如
  `NEW Date; DUP; LCONST_1; INVOKESPECIAL Date.<init>(J)V`。
- 完整分配和初始化序列继续保留在 JVM 前缀中，只通过一个隐藏桥接方法编译共享构造后缀。
- `float`/`double` 初始化参数、计算型叶节点、数组分配以及七个或更多参数的构造方法仍然拒绝。
- 新增准入、改写后 JVM 验证以及 Java/原生运行一致性测试。

## 验证

```text
CC=gcc CXX=g++ ./gradlew :obfuscator:test --rerun-tasks \
  --tests by.radioegor146.ir.IrCompilerTest \
  --tests by.radioegor146.CodegenModeTest
```

已准入：是——仅限独立的单个 `long` 参数 `NEW`。  
可发布：否。
