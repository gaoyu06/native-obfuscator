# Admit proven extra-local double `DNEG` constructor inputs

Ship-ready: **No**

## English

### Summary

- Extend the existing double chain-leaf proof so one `DNEG` may consume a
  `DLOAD` from `prefixDoubleCopies`.
- Reuse the established dominating-write proof; no new copy proof is added.
- Keep the prefix `DSTORE` copy and every `DNEG` in retained JVM constructor
  bytecode, with one hidden bridge and no C++ double-negate reproduction.
- Add admission, rewritten JVM verification, and CMake/g++ JNI Java-parity
  coverage for `double-dneg-extra-local`.

### Safety boundaries

`DNEG` of a constant, double `DNEG`, `DNEG` of a computed double, unproven
extra locals, and nine-or-more nested double binaries remain rejected.
Extra-local `FNEG` and `INEG` are unchanged.

### Verification

```text
CC=gcc CXX=g++ ./gradlew :obfuscator:test --rerun-tasks \
  --tests by.radioegor146.ir.IrCompilerTest \
  --tests by.radioegor146.CodegenModeTest
```

Test XML totals will be recorded after the required run.

## 中文

### 概要

- 扩展已有 double 链叶子证明，使单层 `DNEG` 可以读取
  `prefixDoubleCopies` 中已证明的额外局部变量 `DLOAD`。
- 直接复用现有支配写证明，不引入新的 copy-leaf 证明。
- 前缀 `DSTORE` 副本及所有 `DNEG` 均保留在 JVM 构造器字节码中；仍只生成一个
  hidden bridge，不在 C++ 中复刻 double 取反语义。
- 为 `double-dneg-extra-local` 增加接纳、重写后 JVM 校验以及
  CMake/g++ JNI Java 一致性覆盖。

### 安全边界

常量上的 `DNEG`、双重 `DNEG`、计算所得 double 上的 `DNEG`、未证明的额外
局部变量，以及九层或更多嵌套 double 二元运算仍保持拒绝。
额外局部变量 `FNEG` 和 `INEG` 不受影响。

### 验证

将运行上方完整命令，并在完成后记录测试 XML 汇总。
