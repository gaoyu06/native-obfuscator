## English

Admits the proven prefix extra-local int copy as the first initializer
argument of the isolated four-int-argument `NEW` constructor leaf.

- Adds the `new-constructor-extra-local-argument-four` fixture and admission,
  rewritten-verification, and Java/native parity tests.
- Retains `NEW Insets; DUP; ILOAD 3; ICONST_2; ICONST_3; ICONST_4;
  INVOKESPECIAL Insets.<init>(IIII)V` in JVM bytecode.
- Keeps the native body at `(II)V` with only `RETURN`, one hidden bridge, and
  singular `MethodContext.proxyMethod`.
- Does not change `ConstructorSpecialMethodProcessor`, the six-argument cap,
  or wide initializer-argument policy.

Validation:

```text
CC=gcc CXX=g++ ./gradlew :obfuscator:test --rerun-tasks \
  --tests by.radioegor146.ir.IrCompilerTest \
  --tests by.radioegor146.CodegenModeTest
```

Child-local result: 516 `IrCompilerTest` + 7 `CodegenModeTest` = 523 passing,
with zero failures, errors, or skips.

Admitted: Yes, this compose only. Ship-ready: No.

## 中文

本分支仅允许已证明的前缀额外局部 `int` 副本作为独立四个 `int` 参数
`NEW` 构造器叶子的第一个初始化参数。

- 新增 `new-constructor-extra-local-argument-four` fixture，以及准入、
  重写后 JVM 验证和 Java/native 输出一致性测试。
- `NEW Insets; DUP; ILOAD 3; ICONST_2; ICONST_3; ICONST_4;
  INVOKESPECIAL Insets.<init>(IIII)V` 完整保留在 JVM 字节码中。
- native body 保持为仅含 `RETURN` 的 `(II)V`，只生成一个隐藏 bridge，
  且 `MethodContext.proxyMethod` 保持唯一。
- 不修改 `ConstructorSpecialMethodProcessor`、六参数上限或宽类型初始化参数策略。

子分支测试结果：516 个 `IrCompilerTest` 加 7 个 `CodegenModeTest`，
共 523 个全部通过，失败、错误和跳过均为零。

准入：是，仅此组合。可发布：否。
