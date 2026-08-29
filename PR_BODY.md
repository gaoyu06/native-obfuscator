# English

## (a) Scope

This increment extends the #182–#197 constructor chain-input walker with a
fail-closed, leaf-only `IDIV`/`IREM` case. Both operands must already be proven
int-family leaves (a declared argument load, an int constant, or one `INEG`
over a declared argument load). The operation remains in the retained bytecode
prefix, so JVM trapping and signed-overflow behavior is preserved.

Nested `IDIV`/`IREM`, either opcode used as an inner binary, and extra-local
operands remain rejected before mutation. This is not a general lift for
nested trapping trees and does not change any default compiler mode.

## (b) Ship-ready?

No.

## (c) Review and gate

Executed the focused `IrCompilerTest` and `CodegenModeTest` gate, including the
real CMake/g++ parity path. No stacked Fable/Sol review is included.

## (d) Preconditions

Remaining constructor-split leftovers continue to reject before mutation.
`--codegen` remains `legacy`; `--ir-lower` remains `direct`, and `--backend`
remains `cpp`.

# 中文

## (a) 范围

本增量在 #182–#197 的构造器链调用输入检查器上增加了一个失败关闭的、
仅限叶子输入的 `IDIV`/`IREM` 情形。两个操作数都必须已被证明为 int
家族叶子（已声明参数的加载、int 常量，或对已声明参数加载执行一次
`INEG`）。运算仍保留在字节码前缀中，因此 JVM 的异常和有符号溢出语义
保持不变。

嵌套的 `IDIV`/`IREM`、作为其他二元运算内部输入的这两个操作码，以及
额外局部变量操作数仍在修改前被拒绝。本增量不支持通用的嵌套可抛异常
运算树，也不更改任何默认编译模式。

## (b) 可发布？

否。

## (c) 审查与门禁

已执行聚焦的 `IrCompilerTest` 与 `CodegenModeTest` 门禁，其中包括真实的
CMake/g++ 一致性路径。不包含堆叠的 Fable/Sol 审查。

## (d) 前置条件

其余构造器拆分遗留形状继续在修改前拒绝。`--codegen` 保持 `legacy`；
`--ir-lower` 保持 `direct`，`--backend` 保持 `cpp`。
