## English

### Summary

- admit constructor reference `AALOAD` indexes that are one direct declared
  `ILOAD` or one `ILOAD` of a proven prefix extra-local int copy
- keep computed, `INEG`, computed-store extra-local, and unproven extra-local
  indexes rejected
- keep the unchanged declared reference-array or proven prefix-copy source
  proof and retain `AALOAD` in the JVM prefix
- rebased onto current `master` after #253 so declared `BALOAD`/`CALOAD`/`SALOAD`
  leaves and extra-local `int[]` `IALOAD` copies both remain

### Verification

```text
CC=gcc CXX=g++ ./gradlew :obfuscator:test --rerun-tasks \
  --tests by.radioegor146.ir.IrCompilerTest \
  --tests by.radioegor146.CodegenModeTest
```

Parent re-run XML on this rebased branch will replace the pre-#252
child count. Do not treat the old 431 figure as current.

Ship-ready: **No**

## 中文

### 摘要

- 构造器引用类型 `AALOAD` 索引新增支持：声明参数的单条 `ILOAD`，或已证明的
  前缀额外局部 int 副本的单条 `ILOAD`
- 继续拒绝计算索引、`INEG` 索引、计算后写入的额外局部索引和未证明的额外
  局部索引
- 数组来源仍须是未修改的声明引用数组参数或其已证明的前缀副本，且 `AALOAD`
  仍保留在 JVM 前缀执行
- 已变基到包含 #253 的当前 `master`，声明数组 `BALOAD`/`CALOAD`/`SALOAD`
  与额外局部 `int[]` `IALOAD` 同时保留

### 验证

```text
CC=gcc CXX=g++ ./gradlew :obfuscator:test --rerun-tasks \
  --tests by.radioegor146.ir.IrCompilerTest \
  --tests by.radioegor146.CodegenModeTest
```

父进程将在变基后的分支上重跑 XML，以替换 #252 之前的子代理计数。
请勿把旧的 431 当作当前数字。

可发布：**否**
