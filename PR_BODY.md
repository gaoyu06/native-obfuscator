## English

### Summary

- Admit constructor chain-input leaves of the exact form
  `ALOAD declaredArray; constant; BALOAD|CALOAD|SALOAD`.
- Require `[B` or `[Z` for `BALOAD`, `[C` for `CALOAD`, and `[S` for
  `SALOAD`; retain the existing `[I` requirement for `IALOAD`.
- Keep every primitive array load in the JVM prefix so null checks, bounds
  checks, and JVM widening semantics are not reimplemented in native code.

### Safety and coverage

- Computed indexes, extra-local indexes, extra-local array sources, and
  opcode/type mismatches are rejected before constructor or hidden-method
  mutation.
- Admit, rewritten-JVM-verification, and combined-JAR Java/native parity
  coverage includes `byte[]`, `boolean[]`, `char[]`, and `short[]`.
- The binary-depth budget and defaults are unchanged.
- Rebased onto current `master` after #252 so extra-local `int[]`
  `IALOAD` copies and these declared `BALOAD`/`CALOAD`/`SALOAD` leaves
  both remain.

### Verification

```sh
CC=gcc CXX=g++ ./gradlew :obfuscator:test --rerun-tasks \
  --tests by.radioegor146.ir.IrCompilerTest \
  --tests by.radioegor146.CodegenModeTest
```

Parent re-run XML on this rebased branch will replace the pre-#252
child count. Do not treat the old 431 figure as current.

Ship-ready: **No**

## 中文

### 概要

- 支持构造器链调用参数中严格形如
  `ALOAD 声明数组; 常量; BALOAD|CALOAD|SALOAD` 的叶节点。
- `BALOAD` 仅接受 `[B`/`[Z`，`CALOAD` 仅接受 `[C`，`SALOAD` 仅接受
  `[S`；原有 `IALOAD` 仍仅接受 `[I`。
- 所有基本类型数组读取均保留在 JVM 前缀中，不在原生代码中重写空值、
  越界检查或 JVM 扩展语义。

### 安全性与覆盖

- 计算索引、额外局部索引、额外局部数组来源及操作码/声明类型不匹配，
  均在构造器或隐藏方法发生变更前拒绝。
- 接受测试、重写后 JVM 验证和组合 JAR 的 Java/原生一致性测试覆盖
  `byte[]`、`boolean[]`、`char[]` 和 `short[]`。
- 二元表达式深度预算与默认配置均未改变。
- 已变基到包含 #252 的当前 `master`，额外局部 `int[]` 的 `IALOAD`
  拷贝与本次声明数组 `BALOAD`/`CALOAD`/`SALOAD` 叶节点同时保留。

### 验证

```sh
CC=gcc CXX=g++ ./gradlew :obfuscator:test --rerun-tasks \
  --tests by.radioegor146.ir.IrCompilerTest \
  --tests by.radioegor146.CodegenModeTest
```

父进程将在变基后的分支上重跑 XML，以替换 #252 之前的子代理计数。
请勿把旧的 431 当作当前数字。

可发布：**否**
