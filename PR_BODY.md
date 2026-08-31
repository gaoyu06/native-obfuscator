# English

## Summary

- Add the fixture shape `new-constructor-extra-local-argument-six-fifth`.
- Admit a proven extra-local int copy as the fifth initializer argument of a six-argument `GregorianCalendar` `NEW` bytecode sequence.
- Add admission, rewritten-bytecode verification, and Java/JNI parity coverage.

## Baseline and scope

- Leftover-docs baseline: #422 at `553c0043`.
- Parent XML: **725** tests (`IrCompilerTest` 718 + `CodegenModeTest` 7), 0 failures, 0 errors, 0 skips, including `threeImmediateNewExtraLocalSixFifthArgChainInputsCompileAndRunWithJavaParity`.
- Processor changed: No.
- Ship-ready: No.
- Defaults are unchanged: no `--codegen=legacy`, `--ir-lower=direct`, or `--backend=cpp` flip.
- This is fixture admission for one bytecode/CFG/JNI shape, not a JDK support badge.
- `MethodContext.proxyMethod` remains singular, with one native method per constructor.

# 中文

## 摘要

- 新增测试夹具形状 `new-constructor-extra-local-argument-six-fifth`。
- 覆盖六参数 `GregorianCalendar` `NEW` 字节码序列中，第五个初始化参数使用已证明的额外局部 int 副本。
- 新增准入、重写后字节码验证以及 Java/JNI 一致性测试。

## 基线与范围

- leftover-docs 基线：#422，提交 `553c0043`。
- 父级 XML：**725** 个测试（`IrCompilerTest` 718 + `CodegenModeTest` 7），0 失败 / 0 错误 / 0 跳过，含 `threeImmediateNewExtraLocalSixFifthArgChainInputsCompileAndRunWithJavaParity`。
- Processor changed：No。
- Ship-ready：No。
- 默认选项不变：未切换 `--codegen=legacy`、`--ir-lower=direct` 或 `--backend=cpp`。
- 这是特定字节码/CFG/JNI 形状的测试夹具准入，不是 JDK 支持徽章。
- `MethodContext.proxyMethod` 保持单数语义；每个构造器对应一个 native 方法。
