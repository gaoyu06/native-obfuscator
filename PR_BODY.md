# Summary / 摘要

## English

- Admit one `LNEG` whose sole operand is a proven prefix extra-local long copy
  of a declared `LLOAD`.
- Reuse the existing `prefixLongCopies` dominating-write proof without adding
  a new copy-leaf proof.
- Keep the prefix `LSTORE`, each `LLOAD 4; LNEG`, and all constructor chain
  calls in retained JVM bytecode behind one hidden bridge.
- Add focused admission, rewritten JVM verification, and CMake/g++ JNI parity
  coverage, including `Long.MIN_VALUE`.
- Continue rejecting constant, double, and computed-input `LNEG`, extra-local
  shift counts, nine-or-more nested long binaries, and unrelated families.

## 中文

- 支持单个 `LNEG`，其唯一操作数必须是由已声明 `LLOAD` 在构造器前缀中复制
  得到、且已经证明安全的 extra-local long。
- 直接复用现有 `prefixLongCopies` 支配写入证明，不新增 copy-leaf 证明。
- 前缀 `LSTORE`、每个 `LLOAD 4; LNEG` 以及全部构造器链调用仍保留在 JVM
  字节码中，并且只使用一个隐藏桥接方法。
- 增加定向准入、重写后 JVM 校验以及 CMake/g++ JNI Java 一致性测试，包含
  `Long.MIN_VALUE`。
- 常量、双重和计算结果输入的 `LNEG`、extra-local 位移计数、九层及以上 long
  二元嵌套和无关类型族仍保持拒绝。

# Verification / 验证

```text
CC=gcc CXX=g++ ./gradlew :obfuscator:test --rerun-tasks \
  --tests by.radioegor146.ir.IrCompilerTest \
  --tests by.radioegor146.CodegenModeTest
```

Result / 结果: passed / 通过

- `IrCompilerTest`: 387 tests, 0 skipped, 0 failures, 0 errors
- `CodegenModeTest`: 7 tests, 0 skipped, 0 failures, 0 errors
- Total / 总计: 394 tests, 0 skipped, 0 failures, 0 errors

Ship-ready / 可发布: **No / 否**
