# Summary / 摘要

- Admit one `INEG` whose sole operand is an `ILOAD` from the existing proven
  prefix extra-local int-copy set.
- 允许单个 `INEG` 使用现有证明过的前缀额外 int 局部副本 `ILOAD` 作为唯一操作数。
- Reuse the existing dominating-write proof; no new copy-leaf proof is added.
- 复用现有支配写入证明，不新增副本叶子证明。
- Keep the prefix `ISTORE`, all `INEG` operations, and constructor chain calls
  in retained JVM bytecode behind one hidden bridge.
- 前缀 `ISTORE`、全部 `INEG` 以及构造器链调用均保留在 JVM 字节码中，并共用一个隐藏桥接方法。

# Safety boundaries / 安全边界

- `INEG` of a constant, double `INEG`, and `INEG` of a computed int remain
  rejected without mutation.
- 常量的 `INEG`、双重 `INEG`、以及计算结果上的 `INEG` 仍在修改前被拒绝。
- Extra-local `FNEG` / `DNEG`, nine-or-more nested int binaries, skip-super,
  spanning catches, and unassigned bridge extras remain outside this change.
- 额外局部变量上的 `FNEG` / `DNEG`、九层及以上嵌套 int 二元运算、跳过 super、
  跨越 catch、以及桥接路径未赋值的额外变量均不在本次变更范围内。

# Verification / 验证

```text
CC=gcc CXX=g++ ./gradlew :obfuscator:test --rerun-tasks \
  --tests by.radioegor146.ir.IrCompilerTest \
  --tests by.radioegor146.CodegenModeTest
```

- `IrCompilerTest`: 397 tests, 0 skipped, 0 failures, 0 errors.
- `CodegenModeTest`: 7 tests, 0 skipped, 0 failures, 0 errors.
- Total / 总计: 404 tests, 0 skipped, 0 failures, 0 errors.

Ship-ready: **No** — the broader production goal remains incomplete.

可交付状态：**否** — 更广泛的生产目标尚未完成。
