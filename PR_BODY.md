# English

## (a) What changed?

- Admit exactly two pairwise-distinct constructor suffixes when either suffix
  contains one closed unary or binary int-family conditional branch.
- Require every branch target and fallthrough path to remain inside its own
  suffix and reach `RETURN`; reject back edges, switches, cross-suffix edges,
  prefix edges, exception tables, and unproven condition inputs.
- Keep one hidden bridge, the trailing path id, and the retained bytecode
  this/super calls.
- Add unit, JVM verification, rejection-before-mutation, and CMake/g++ JNI
  parity coverage for both path ids and both branch arms.

## (b) Ship-ready?

No.

## (c) Verification

`CC=gcc CXX=g++ ./gradlew :obfuscator:test --rerun-tasks --tests by.radioegor146.ir.IrCompilerTest --tests by.radioegor146.CodegenModeTest`

Expected focused total after this increment: 225 tests (218
`IrCompilerTest` + 7 `CodegenModeTest`), with zero failures, errors, or skips.

## (d) Scope and follow-up

This increment intentionally leaves 3–8-call branched suffixes, suffix
switches, the standalone trivial-`GOTO` fixture, nonempty exception tables,
hybrid identical-plus-distinct sets, nested/`IDIV` chain inputs, mixed catches,
and `jsr`/`ret` rejected. Compiler defaults are unchanged.

# 中文

## (a) 改动内容

- 当任一后缀包含一个闭合的一元或二元整型条件分支时，允许恰好两个两两不同的
  构造器后缀。
- 要求每个分支目标及其顺序执行路径都留在各自后缀内并到达 `RETURN`；继续拒绝
  回边、switch、跨后缀边、跳回前缀的边、异常表及未经证明的条件输入。
- 保留一个隐藏桥接方法、末尾路径 id，以及字节码中的 this/super 调用。
- 增加单元测试、JVM 验证、变更前拒绝测试，以及覆盖两个路径 id 和两个分支臂的
  CMake/g++ JNI 一致性测试。

## (b) 可直接上线？

否。

## (c) 验证

`CC=gcc CXX=g++ ./gradlew :obfuscator:test --rerun-tasks --tests by.radioegor146.ir.IrCompilerTest --tests by.radioegor146.CodegenModeTest`

本次增量后的预期聚焦测试总数为 225（`IrCompilerTest` 218 项 +
`CodegenModeTest` 7 项），失败、错误和跳过均为零。

## (d) 范围与后续

本次仍拒绝 3–8 个调用时的分支后缀、后缀 switch、独立的简单 `GOTO` 固件、
非空异常表、相同与不同后缀混合集、嵌套/`IDIV` 链调用输入、混合 catch 以及
`jsr`/`ret`。编译器默认值未改变。
