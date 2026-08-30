# Constructor chain-input AALOAD from a proven array copy

## English

### Summary

- Prove an extra reference local only when one pre-first-chain-call `ASTORE`
  directly copies an unchanged declared reference-array argument.
- Admit constant-index `AALOAD` chain inputs from that proven copy while
  retaining the prefix `ASTORE`, all `AALOAD` operations, and JVM exception
  semantics in bytecode behind one hidden bridge.
- Keep computed stores, overwritten extra/source locals, prior array stores,
  primitive-array results, and non-constant indexes fail-closed.
- Add admission, mutation-safety rejection, JVM verification, and full
  CMake/g++ JNI Java-parity coverage.

### Verification

```text
CC=gcc CXX=g++ ./gradlew :obfuscator:test --rerun-tasks \
  --tests by.radioegor146.ir.IrCompilerTest \
  --tests by.radioegor146.CodegenModeTest
```

Ship-ready: **No**

## 中文

### 摘要

- 仅当前缀中、首次构造器链调用之前的一次 `ASTORE` 直接复制未改写的已声明引用数组参数时，证明该额外引用局部变量。
- 允许该已证明副本作为常量索引 `AALOAD` 的数组来源；前缀 `ASTORE`、全部 `AALOAD` 及 JVM 异常语义仍保留在字节码中，并共用一个隐藏桥。
- 计算得到的存储值、被覆盖的额外/源局部变量、先前数组写入、原始类型数组结果以及非常量索引仍保持拒绝。
- 增加接纳、拒绝前不变性、JVM 验证及完整 CMake/g++ JNI Java 一致性测试。

### 验证

```text
CC=gcc CXX=g++ ./gradlew :obfuscator:test --rerun-tasks \
  --tests by.radioegor146.ir.IrCompilerTest \
  --tests by.radioegor146.CodegenModeTest
```

可发布：**否**
