# Summary / 摘要

## English

- Admit constructor chain-input leaves of the exact retained-prefix form
  `ALOAD declaredArray; int constant; LALOAD|FALOAD|DALOAD`.
- Require exact `[J`/`LALOAD`, `[F`/`FALOAD`, and `[D`/`DALOAD` pairing, an
  unchanged directly loaded declared constructor argument, and no earlier
  array-store opcode.
- Reuse the existing long/float/double chain proofs as non-recursive leaves
  without increasing their 16-level binary budget. Null, bounds, and
  category-two behavior remain JVM-executed in the retained constructor
  prefix.
- Keep extra-local array sources, computed or extra-local indexes, mismatched
  types, overwritten arrays, prior stores, skip-super paths, and the legacy
  CLI default fail-closed.

## 中文

- 新增严格限定的构造器链输入叶：
  `ALOAD 声明数组参数; int 常量; LALOAD|FALOAD|DALOAD`，并将完整加载保留在
  JVM 构造器前缀中执行。
- 强制 `[J`/`LALOAD`、`[F`/`FALOAD`、`[D`/`DALOAD` 精确配对；数组必须是
  未被覆盖的声明构造器参数且通过直接 `ALOAD` 读取，加载前不得出现任何数组写入。
- 作为非递归叶接入既有 long/float/double 链证明，不提高 16 层二叉预算；空指针、
  越界及 long/double 双槽行为仍由 JVM 保证。
- extra-local 数组源、计算或 extra-local 索引、类型错配、参数覆盖、先前数组写入、
  skip-super 路径以及 CLI 默认模式变更均继续拒绝。

# Verification / 验证

```text
CC=gcc CXX=g++ ./gradlew :obfuscator:test --rerun-tasks \
  --tests by.radioegor146.ir.IrCompilerTest \
  --tests by.radioegor146.CodegenModeTest
```

- Result / 结果: `BUILD SUCCESSFUL`
- JUnit XML: 438 tests, 0 skipped, 0 failures, 0 errors
- Runtime parity / 运行时一致性:
  `threeImmediateWidePrimitiveArrayLoadsCompileAndRunWithJavaParity()`

Ship-ready / 可发布: **No / 否**
