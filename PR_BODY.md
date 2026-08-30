## English

### Summary

- admit constructor reference `AALOAD` indexes that are one direct declared
  `ILOAD` or one `ILOAD` of a proven prefix extra-local int copy
- keep computed, `INEG`, computed-store extra-local, and unproven extra-local
  indexes rejected
- keep the unchanged declared reference-array or proven prefix-copy source
  proof and retain `AALOAD` in the JVM prefix

### Verification

```text
CC=gcc CXX=g++ ./gradlew :obfuscator:test --rerun-tasks \
  --tests by.radioegor146.ir.IrCompilerTest \
  --tests by.radioegor146.CodegenModeTest
```

Ship-ready: **No**

## 中文

### 摘要

- 构造器引用类型 `AALOAD` 索引新增支持：声明参数的单条 `ILOAD`，或已证明的
  前缀额外局部 int 副本的单条 `ILOAD`
- 继续拒绝计算索引、`INEG` 索引、计算后写入的额外局部索引和未证明的额外
  局部索引
- 数组来源仍须是未修改的声明引用数组参数或其已证明的前缀副本，且 `AALOAD`
  仍保留在 JVM 前缀执行

### 验证

```text
CC=gcc CXX=g++ ./gradlew :obfuscator:test --rerun-tasks \
  --tests by.radioegor146.ir.IrCompilerTest \
  --tests by.radioegor146.CodegenModeTest
```

可发布：**否**
