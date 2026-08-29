# English

## (a) Scope

This increment widens the proven three-or-more-call immediate-`RETURN`
constructor split after #178. Each direct this/super call must still consume
the original receiver from `ALOAD 0`, but an int-family call argument may now
be an `ICONST_M1` through `ICONST_5`, `BIPUSH`, `SIPUSH`, or `LDC` of
`Integer`, or one `INEG` over a direct declared int-family argument `ILOAD`.
Direct declared-argument loads with matching JVM carriers remain admitted.

Extra-local or aliased inputs, rewritten or computed receivers, stack
duplication, binary arithmetic, `IINC`, non-int-family constants, non-`Integer`
`LDC`, fields, calls, post-call work, nonidentical nonempty suffixes, skip-super
edges, nonempty exception tables, unproven cross-split control flow, and every
other unlisted computation remain rejected before mutation.

## (b) Ship-ready?

**No.** This does not change any production default.

## (c) Review and gate

There is no stacked review. The gate is the executed focused
`IrCompilerTest` plus `CodegenModeTest` suite, including the new full
CMake/g++ compile-and-run Java parity harness under
`java -Xverify:all -Xcheck:jni`.

## (d) Preconditions

Remaining constructor leftovers, unsafe constant dynamic, and `jsr`/`ret`
remain reject-before-mutation. `--codegen` remains `legacy`.

# 中文

## (a) 范围

本增量在 #178 之后，扩展了已证明安全的“三个及以上调用、调用后立即
`RETURN`”构造器拆分。每个直接 this/super 调用仍须通过 `ALOAD 0`
使用原始接收者；int-family 调用参数现在还可以是 `ICONST_M1` 到
`ICONST_5`、`BIPUSH`、`SIPUSH`、值为 `Integer` 的 `LDC`，或对直接
加载已声明 int-family 构造器参数的 `ILOAD` 执行一次 `INEG`。JVM
载体匹配的已声明参数直接加载仍然允许。

额外局部变量或别名输入、被改写或计算出的接收者、栈复制、二元算术、
`IINC`、非 int-family 常量、非 `Integer` 的 `LDC`、字段、方法调用、
调用后工作、不相同的非空后缀、跳过 super 的边、非空异常表、未经证明的
跨拆分控制流，以及所有其他未列出的计算，仍会在任何变更前被拒绝。

## (b) 可发布？

**否。** 本增量不更改任何生产默认值。

## (c) 审查与门禁

没有堆叠审查。门禁是实际执行的 `IrCompilerTest` 与 `CodegenModeTest`
聚焦测试套件，其中包括新增的完整 CMake/g++ 编译运行 Java 一致性测试，
并使用 `java -Xverify:all -Xcheck:jni`。

## (d) 前置条件

其余构造器遗留形态、不安全的 constant dynamic，以及 `jsr`/`ret`
仍须在任何变更前拒绝。`--codegen` 仍为 `legacy`。
