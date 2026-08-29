<!-- CURSOR_AGENT_PR_BODY_BEGIN -->
Admit gapped constructor-prefix extra locals on the opt-in IR path. Only
prefix-stored, suffix-read, definitely assigned extras are appended to the
independent suffix and hidden-bridge descriptors; suffix local accesses are
remapped to packed trailing parameter slots while the retained Java constructor
continues to use the original local indexes.

The focused gate passed **136/136** from JUnit XML:
`IrCompilerTest` 129 and `CodegenModeTest` 7, with 0 failures, 0 errors, and
0 skipped. This includes JVM verification and Java/native compile-and-run
parity with CMake, g++, `-Xverify:all`, and `-Xcheck:jni`.

Ship-ready: **No**. No stacked review. No default flip.

## (a) Change scope / 改动范围

- Select only constructor-prefix extras that are stored before this/super, read
  by the suffix, and have one compatible definitely assigned type on every
  prefix CFG path to the chain call.
- Pack those extras after declared constructor arguments and remap loads,
  stores, and `IINC` only in the independent suffix `MethodNode`.
- Keep bridge-call loads on original wrapper local indexes.
- Add gapped-reference, gapped-`IINC`, JVM-verification, and full CMake/g++
  Java-parity coverage; retain contiguous-extra identity coverage.
- Record the exact focused-suite results in
  `docs/architecture/ir-flex-ctor-status.md`.

仅转发在构造器前缀中写入、在后缀中读取、并且在所有到达 this/super
调用的前缀 CFG 路径上都以单一兼容类型确定赋值的 extra local。extra
参数在声明参数后紧凑排列，只重映射独立后缀 `MethodNode`；保留的 Java
构造器前缀和 bridge 调用仍使用原始 local 索引。新增空洞、`IINC`、JVM
校验以及 CMake/g++ Java 对等运行测试，并记录精确 XML 计数。

## (b) Ship-ready? / 是否可直接上线？

**No / 否.**

This is an incremental constructor-split admission change on the opt-in IR
path. / 这是 opt-in IR 路径上的增量构造器切分接纳改动。

## (c) Review / 是否需要审查？

**No stacked review. / 无叠加审查。**

The focused suite was executed directly on this branch. / 聚焦测试已在本分支
直接执行。

## (d) Preconditions / 前置条件

`--codegen` remains `legacy`; IR lowering remains `direct` and the backend
remains `cpp`. There is no default flip.

`--codegen` 仍默认为 `legacy`，IR lowering 仍为 `direct`，backend 仍为
`cpp`；不改默认值。

Still rejected: prefix `ASTORE 0`, prefix branch/switch into the suffix,
try/catch across the split, multiple this/super candidates, `jsr`/`ret`,
extras without definite single-type assignment, incompatible extra types, and
category-2 overlap.

仍拒绝：前缀 `ASTORE 0`、前缀分支或 switch 跳入后缀、跨切分 try/catch、
多个 this/super 候选、`jsr`/`ret`、未以单一类型确定赋值或类型冲突的
extra，以及 category-2 重叠。
<!-- CURSOR_AGENT_PR_BODY_END -->
