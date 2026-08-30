## English

### Summary

- Adds fixture-only coverage for the proven prefix extra-local float copy used
  as the first argument of an isolated two-argument
  `Point2D.Float.<init>(FF)V` `NEW` leaf.
- Exercises constructor descriptor `(IF)V`, prefix `FLOAD 2; FSTORE 3`, three
  constructor-chain paths, one hidden bridge, JVM verification, and
  Java/native runtime parity.
- Keeps the native body to `RETURN`; all allocation and initializer work
  remains in retained JVM bytecode.

### Scope

- `ConstructorSpecialMethodProcessor.java` changed: No.
- Six-argument `NEW` arity cap changed: No.
- Extra-local double `NEW` admitted: No.
- Seven-or-more initializer arguments, skip-super, seventeen-level binaries,
  and `MAX_DISTINCT_SUFFIXES` changed: No.
- Codegen defaults changed: No (`legacy`, `direct`, `cpp` remain the defaults).
- Admitted: Yes, this compose only.
- Ship-ready: No.

### Tests

```text
CC=gcc CXX=g++ ./gradlew :obfuscator:test --rerun-tasks \
  --tests by.radioegor146.ir.IrCompilerTest \
  --tests by.radioegor146.CodegenModeTest
```

Child-local JUnit XML: `IrCompilerTest` 535 +
`CodegenModeTest` 7 = 542 total; failures 0, errors 0, skips 0.
These are child-local results, not parent totals.

## 中文

### 摘要

- 仅扩展测试夹具，覆盖已证明的前缀额外局部 `float` 副本作为隔离的双参数
  `Point2D.Float.<init>(FF)V` `NEW` 叶子的第一个初始化参数。
- 覆盖 `(IF)V` 构造器、前缀 `FLOAD 2; FSTORE 3`、三个构造器链路径、一个隐藏
  桥接方法、JVM 验证，以及 Java/原生运行时输出一致性。
- 原生方法体仅含 `RETURN`；分配和初始化调用全部保留在 JVM 字节码中。

### 范围

- 修改 `ConstructorSpecialMethodProcessor.java`：否。
- 修改六参数 `NEW` 上限：否。
- 接纳额外局部 `double` 的 `NEW`：否。
- 修改七个或更多初始化参数、跳过父类构造器、十七层二元表达式或
  `MAX_DISTINCT_SUFFIXES`：否。
- 修改代码生成默认值：否（仍为 `legacy`、`direct`、`cpp`）。
- 已接纳：是，仅限本次组合。
- 可发布：否。

### 测试

子分支本地 JUnit XML：`IrCompilerTest` 535 +
`CodegenModeTest` 7 = 总计 542；失败 0、错误 0、跳过 0。
这些是子分支本地结果，不是父分支总数。
