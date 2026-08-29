# EN

## (a) Scope

Admit only receiver-alias forwarding for the existing two-call `GOTO`
shared-join constructor diamond. Receiver-frame analysis must prove that both
this/super calls consume the original constructor receiver through the alias.
The wrapper continues to pass local 0 as the first hidden-bridge body argument,
forwards a suffix-read alias as an extra local, and passes the constructor
owner's `Class` as shell-only class-loader metadata.

## (b) Ship-ready?

**No.** This is one structural IR leftover within the broader typed-CFG
method-body migration.

## (c) Review and gate

The parent re-runs:

```text
CC=gcc CXX=g++ ./gradlew :obfuscator:test --rerun-tasks \
  --tests by.radioegor146.ir.IrCompilerTest \
  --tests by.radioegor146.CodegenModeTest
```

New tests:

- `admitsAndRewritesMultipleSuperDiamondWithReceiverAlias`
- `rejectsSharedJoinDiamondWhenOneCallUsesOverwrittenReceiverBeforeMutation`
- `receiverAliasMultipleSuperDiamondCompilesAndRunsWithJavaParity`

## (d) Preconditions

Remaining constructor leftovers stay reject-before-mutation, including
path-id and identical-copy non-identity `ASTORE 0`, skip-super paths,
spanning/chain-covering/method-end exception tables, and unassigned extras.
`--codegen` remains `legacy`.

# 中文

## (a) 范围

仅放行现有双调用 `GOTO` 共享汇合构造器菱形中的接收者别名转发。
接收者帧分析必须证明两个 this/super 调用都通过别名使用原始构造器接收者。
包装方法仍把局部变量 0 作为隐藏桥方法体的第一个参数；若后缀读取别名，
则将其作为额外局部变量转发；同时传递构造器所属类的 `Class`，仅供 shell
解析类加载器。

## (b) 是否可发布？

**否。** 这只是方法体迁移到类型化 CFG IR 过程中的一个结构性剩余项。

## (c) 审查与门禁

父代理会重新运行：

```text
CC=gcc CXX=g++ ./gradlew :obfuscator:test --rerun-tasks \
  --tests by.radioegor146.ir.IrCompilerTest \
  --tests by.radioegor146.CodegenModeTest
```

新增测试：

- `admitsAndRewritesMultipleSuperDiamondWithReceiverAlias`
- `rejectsSharedJoinDiamondWhenOneCallUsesOverwrittenReceiverBeforeMutation`
- `receiverAliasMultipleSuperDiamondCompilesAndRunsWithJavaParity`

## (d) 前置条件

其余构造器剩余形状继续在修改前拒绝，包括 path-id 与 identical-copy
中的非恒等 `ASTORE 0`、跳过 super 的路径、跨越/覆盖调用链/延伸到方法末尾
的异常表，以及未赋值的额外局部变量。`--codegen` 仍为 `legacy`。
