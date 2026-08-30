# Summary / 摘要

Admit a single `FNEG` whose operand is a proven dominating prefix extra-local
float copy of a declared `FLOAD` as a constructor float chain-input leaf.

允许构造器浮点链输入叶使用单个 `FNEG`，其操作数必须是已证明支配所有链调用、
且直接复制自声明参数 `FLOAD` 的前缀额外局部变量。

## Behavior / 行为

- Reuses `prefixFloatCopies`; no new copy proof is introduced.
- Retains the prefix `FLOAD; FSTORE` and each `FLOAD; FNEG` in JVM bytecode.
- Keeps `MethodContext.proxyMethod` singular and uses one hidden bridge.
- Does not reproduce Java float negation in C++.
- Continues rejecting `FNEG` of a constant, double `FNEG`, `FNEG` of a
  computed float, nine-or-more nested float binaries, and the existing unsafe
  constructor CFG shapes.

- 复用现有 `prefixFloatCopies` 支配写入证明，不新增复制叶证明。
- 前缀 `FLOAD; FSTORE` 以及每个 `FLOAD; FNEG` 均保留在 JVM 字节码中。
- `MethodContext.proxyMethod` 仍为单一代理方法，并且仅生成一个隐藏桥接。
- 不在 C++ 中重现 Java 浮点取负语义。
- 常量 `FNEG`、双重 `FNEG`、计算值 `FNEG`、九层及以上浮点二元嵌套和现有不安全
  构造器 CFG 形态仍保持拒绝。

## Verification / 验证

```text
CC=gcc CXX=g++ ./gradlew :obfuscator:test --rerun-tasks \
  --tests by.radioegor146.ir.IrCompilerTest \
  --tests by.radioegor146.CodegenModeTest
```

- `IrCompilerTest`: 396 tests, 0 skipped, 0 failures, 0 errors
- `CodegenModeTest`: 7 tests, 0 skipped, 0 failures, 0 errors
- Total / 总计: 403 tests, 0 skipped, 0 failures, 0 errors

PR: No

Ship-ready: No
