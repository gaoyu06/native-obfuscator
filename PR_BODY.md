# English

## (a) Scope

This increment admits prefix-assigned extra-local reads in two or more
instruction-identical, nonempty straight-line constructor suffix copies. The
classifier requires a matching store before the first constructor-chain call
and one compatible incoming type at every selected call; the normalized
strict-diamond split then repeats definite-assignment/type validation at the
join and packs the extra into the existing single hidden bridge.

This also closes the remaining receiver-alias case: an identical copied suffix
may read the alias saved in the prefix. The wrapper still passes local 0 as its
first body argument, owner `Class` metadata remains shell-only, and
`MethodContext.proxyMethod` remains singular. Receiver-alias support from #205
is already on `master`; this change only adds prefix extras for identical
copies. Path-id rules are unchanged.

## (b) Ship-ready?

**No.** This is one structural IR admission increment and does not complete the
production goal. The default `--codegen` mode remains `legacy`.

## (c) Review / gate

Review the pre-normalization all-call assignment/type proof, exclusion of
suffix-only stores, packed descriptor/local remapping, alias metadata ordering,
and rejection-before-mutation coverage.

Focused gate:

```text
CC=gcc CXX=g++ ./gradlew :obfuscator:test --rerun-tasks \
  --tests by.radioegor146.ir.IrCompilerTest \
  --tests by.radioegor146.CodegenModeTest
```

## (d) Preconditions

- Current `origin/master` includes #205 receiver-alias forwarding and its
  leftover-status documentation.
- CMake, GCC, and g++ are available for the JNI parity test.
- Java runs use `-Xverify:all -Xcheck:jni`.
- No CLI default change or second hidden native method is part of this change.

# 中文

## (a) 范围

本增量允许两个或更多“指令完全相同、非空、直线型”的构造器后缀副本读取前缀中已赋值的额外局部变量。分类阶段要求：第一次构造器链调用之前存在对应写入，并且每个候选调用入口都能证明该局部变量具有同一个兼容类型；规范化为严格菱形汇合后，现有拆分逻辑会在汇合点再次执行确定赋值与类型证明，并把该额外局部变量打包进现有的单个隐藏桥接方法。

本增量也补齐接收者别名的剩余场景：相同后缀副本可以读取前缀保存的别名。包装方法仍以局部变量 0 作为桥接主体的第一个参数，所有者 `Class`
仍只作为壳层元数据传递，`MethodContext.proxyMethod` 仍然只有一个。#205
的接收者别名支持已经位于 `master`；本改动只为相同副本增加前缀额外局部变量转发，不修改 path-id 规则。

## (b) 是否可交付？

**否。** 这只是一个结构化 IR 准入增量，不代表生产目标完成。`--codegen`
默认值仍为 `legacy`。

## (c) 审查 / 门禁

请重点审查：规范化之前针对所有调用路径的赋值/类型证明、对“仅在后缀副本中写入”的排除、描述符与局部变量打包重映射、别名元数据参数顺序，以及发生字节码变更之前的拒绝覆盖。

聚焦门禁：

```text
CC=gcc CXX=g++ ./gradlew :obfuscator:test --rerun-tasks \
  --tests by.radioegor146.ir.IrCompilerTest \
  --tests by.radioegor146.CodegenModeTest
```

## (d) 前置条件

- 当前 `origin/master` 已包含 #205 的接收者别名转发及其剩余项状态文档。
- 环境中可使用 CMake、GCC 和 g++，以运行 JNI 一致性测试。
- Java 运行使用 `-Xverify:all -Xcheck:jni`。
- 本增量不修改 CLI 默认值，也不新增第二个隐藏 native 方法。
