# EN

## (a) Scope

This increment admits exception tables for instruction-identical constructor
suffix-copy normalization when the table is wholly in the canonical final
suffix, or when its canonical-suffix protected range targets an already-proven
isolated prefix handler. Normalization still produces one strict diamond, one
join, one hidden native bridge, and one IR exception pipeline. The wrapper
retains prefix-only tables and omits admitted suffix tables and relocated
handlers.

Path-id constructor catch support was added separately by
[#200](https://github.com/gaoyu06/native-obfuscator/pull/200) and
[#201](https://github.com/gaoyu06/native-obfuscator/pull/201). This change is
limited to identical-copy normalization.

Cross-copy and chain-covering tables, mixed labels outside the exact relocated
forms, discarded-copy table labels, method-end handlers, and unsafe prefix
handlers remain rejected before mutation.

## (b) Ship-ready?

**No.** This is one structural IR admission increment. It does not complete the
production goal, and the default `--codegen` mode remains `legacy`.

## (c) Review / gate

Review the canonical-range classifier in
`ConstructorSpecialMethodProcessor.duplicatedSuffix()`, the metadata-only label
handling used to compare linear copies, and the reuse of the existing generic
strict-diamond catch relocation path after normalization.

Focused gate:

```text
CC=gcc CXX=g++ ./gradlew :obfuscator:test --rerun-tasks \
  --tests by.radioegor146.ir.IrCompilerTest \
  --tests by.radioegor146.CodegenModeTest
```

The runtime fixture requires `cmake` and `g++` and compares plain Java with the
IR JNI output under `-Xverify:all -Xcheck:jni`.

## (d) Preconditions

- Base: `origin/master` at `0e35043f593cad6e5b765a6cd3d71583e8bcc07c`.
- The constructor has at least two nonempty, executable-instruction-identical
  linear suffix copies.
- Every admitted suffix protected range is wholly in the canonical final copy.
- A relocated handler must match one of the six existing isolated forms and
  satisfy the existing no-incoming-edge and local-slot checks.
- Existing path-id bounds and constructor input, local, and CFG proofs are
  unchanged.

# 中文

## (a) 范围

本增量允许“构造器指令相同的后缀副本归一化”携带异常表，条件是异常表完整位于最终的规范后缀中，或者其受保护范围完整位于该规范后缀中、且目标是已经证明安全的隔离前缀处理器。归一化后仍然只有一个严格菱形 CFG、一个汇合点、一个隐藏 native 桥接方法和一条 IR 异常处理流水线。包装构造器保留仅属于前缀的异常表，并移除已接纳的后缀异常表及被迁移的处理器。

路径 ID 构造器的异常表支持已分别由
[#200](https://github.com/gaoyu06/native-obfuscator/pull/200) 和
[#201](https://github.com/gaoyu06/native-obfuscator/pull/201) 引入；本次变更只处理相同副本归一化。

跨副本或覆盖构造器链调用的异常表、不符合精确迁移形式的混合标签、位于被丢弃副本中的异常表标签、方法末尾处理器以及不安全的前缀处理器，仍会在任何修改发生前被拒绝。

## (b) 可发布？

**否。** 这只是一个结构化 IR 接纳增量，不代表生产目标完成；默认
`--codegen` 模式仍为 `legacy`。

## (c) 审查 / 门禁

请重点审查 `ConstructorSpecialMethodProcessor.duplicatedSuffix()` 中对规范范围的分类、线性副本比较时仅忽略非执行元数据标签的处理，以及归一化后继续复用现有严格菱形 CFG 异常迁移路径的逻辑。

聚焦门禁：

```text
CC=gcc CXX=g++ ./gradlew :obfuscator:test --rerun-tasks \
  --tests by.radioegor146.ir.IrCompilerTest \
  --tests by.radioegor146.CodegenModeTest
```

运行时夹具需要 `cmake` 和 `g++`，并在 `-Xverify:all -Xcheck:jni`
下比较普通 Java 与 IR JNI 输出。

## (d) 前置条件

- 基线为 `origin/master` 的
  `0e35043f593cad6e5b765a6cd3d71583e8bcc07c`。
- 构造器至少有两个非空、执行指令逐条相同的线性后缀副本。
- 每个被接纳的后缀受保护范围都完整位于最终规范副本中。
- 被迁移处理器必须属于现有六种隔离形式之一，并通过现有的无额外入边和局部变量槽检查。
- 现有路径 ID 上限以及构造器输入、局部变量和 CFG 证明保持不变。
