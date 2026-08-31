# English

## Summary

- Adds the fixture shape `new-constructor-extra-local-argument-six-fourth`.
- Admits a proven extra-local `int` copy as the fourth argument of a six-argument `GregorianCalendar` initializer.
- Keeps the complete `NEW; DUP; args; <init>` bytecode sequence in the retained JVM prefix.
- Keeps `MethodContext.proxyMethod` singular and generates one native method per constructor.

## Scope and baseline

- Fixture-only; processor changed: **No**.
- Leftover-docs baseline: #419 at `82ee119a`.
- Latest compiler parent XML until the parent re-runs: #419, **719** tests (`IrCompilerTest` 712 + `CodegenModeTest` 7).
- Expected parent XML after leftover-docs: **722** tests (719 + 3).
- Ship-ready: **No**.
- Default code generation remains unchanged: no `--codegen=legacy`, `--ir-lower=direct`, or `--backend=cpp` switch.
- This fixture admission is not a JDK support badge and does not mark the production goal complete.

## Coverage

- Admission and retained-prefix opcode assertions.
- Rewritten class JVM verification.
- Native compile-and-run parity with three `java.util.GregorianCalendar` outputs.

# 中文

## 摘要

- 新增夹具形状 `new-constructor-extra-local-argument-six-fourth`。
- 准入已证明的额外局部 `int` 副本，作为六参数 `GregorianCalendar` 初始化器的第四个参数。
- 完整的 `NEW; DUP; args; <init>` 字节码序列仍保留在 JVM 前缀中。
- `MethodContext.proxyMethod` 保持单数语义，每个构造器只生成一个 native 方法。

## 范围与基线

- 仅修改测试夹具；处理器变更：**否**。
- leftover-docs 基线：#419，提交 `82ee119a`。
- 在父任务重新运行前，最新 compiler 父 XML 为 #419：**719** 个测试（`IrCompilerTest` 712 + `CodegenModeTest` 7）。
- 合入 leftover-docs 后预期父 XML：**722** 个测试（719 + 3）。
- 可发布：**否**。
- 默认代码生成配置不变：未切换 `--codegen=legacy`、`--ir-lower=direct` 或 `--backend=cpp`。
- 此夹具准入不代表 JDK 支持徽章，也不表示生产目标已完成。

## 覆盖范围

- 准入与保留前缀的操作码断言。
- 重写后类的 JVM 验证。
- native 编译运行与 Java 的一致性，输出三行 `java.util.GregorianCalendar`。
