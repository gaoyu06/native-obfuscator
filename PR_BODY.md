# English

## Summary

- Adds the fixture shape `new-constructor-extra-local-argument-six-fourth`.
- Admits a proven extra-local `int` copy as the fourth argument of a six-argument `GregorianCalendar` initializer.
- Keeps the complete `NEW; DUP; args; <init>` bytecode sequence in the retained JVM prefix.
- Keeps `MethodContext.proxyMethod` singular and generates one native method per constructor.

## Scope and baseline

- Fixture-only; processor changed: **No**.
- Product defaults (`--codegen`, `--ir-lower`, and `--backend`) unchanged
- Rebased onto leftover-docs #420 (`a8616b8c7c372aea80a11034fe85ccedaebb5f5d`).
- Parent re-ran **722/722** (`IrCompilerTest` 715 + `CodegenModeTest` 7), including `threeImmediateNewExtraLocalSixFourthArgChainInputsCompileAndRunWithJavaParity`. Zero failures/errors/skips.
- Ship-ready: **No**
- This is fixture admission coverage, not a JDK support badge.

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
- 产品默认值（`--codegen`、`--ir-lower` 和 `--backend`）保持不变
- 已 rebase 到 leftover-docs #420（`a8616b8c7c372aea80a11034fe85ccedaebb5f5d`）。
- 父级重跑 **722/722**（`IrCompilerTest` 715 + `CodegenModeTest` 7），含 `threeImmediateNewExtraLocalSixFourthArgChainInputsCompileAndRunWithJavaParity`。失败/错误/跳过均为零。
- 可发布状态：**否**
- 此项仅为测试夹具准入覆盖，不代表 JDK 支持徽章。

## 覆盖范围

- 准入与保留前缀的操作码断言。
- 重写后类的 JVM 验证。
- native 编译运行与 Java 的一致性，输出三行 `java.util.GregorianCalendar`。
