# EN

## (a) Scope

Admit only the single-chain constructor receiver-alias leftover: the retained
prefix may save the original receiver, overwrite local 0 with another reference,
and invoke this/super through the proven alias. The wrapper still passes local 0
as the first hidden-bridge body argument, while a suffix-read alias is forwarded
by the existing extra-local mechanism. It also passes the constructor owner's
`Class` as shell-only class-loader metadata, so a null or bootstrap-loaded local
0 does not change class resolution.

## (b) Ship-ready?

**No.** This increment does not complete typed CFG IR coverage for all method
bodies.

## (c) Review and gate

The parent should re-run:

```text
CC=gcc CXX=g++ ./gradlew :obfuscator:test --rerun-tasks \
  --tests by.radioegor146.ir.IrCompilerTest \
  --tests by.radioegor146.CodegenModeTest
```

New tests:

- `admitsAndRewritesReceiverAliasForwardingBeforeSuper`
- `rejectsOverwrittenConstructorReceiverAtChainCallBeforeMutation`
- `receiverAliasForwardingCompilesAndRunsWithJavaParity`

## (d) Preconditions

Remaining constructor leftovers continue to reject before constructor,
`proxyMethod`, or hidden-method-pool mutation. Multi-call `ASTORE 0` copied and
path-id forms remain unsupported. `--codegen` stays `legacy`.

# 中文

## (a) 范围

本次只接纳单链构造函数的 receiver-alias 遗留形态：保留的字节码前缀可以先保存原始
构造接收者，再用另一个引用覆盖局部变量 0，并通过已证明的别名调用 this/super。
包装器仍把局部变量 0 作为隐藏桥接方法体的第一个参数；若后缀读取该别名，则沿用
现有 extra-local 机制转发。包装器还把构造函数所属类的 `Class` 作为仅供 JNI
外壳使用的类加载元数据，因此局部变量 0 为 null 或由 bootstrap loader 加载时，
不会改变类解析行为。

## (b) 是否可发布？

**否。** 本次增量并未完成全部方法体到 typed CFG IR 的迁移。

## (c) 审查与门禁

父代理应重新运行：

```text
CC=gcc CXX=g++ ./gradlew :obfuscator:test --rerun-tasks \
  --tests by.radioegor146.ir.IrCompilerTest \
  --tests by.radioegor146.CodegenModeTest
```

新增测试：

- `admitsAndRewritesReceiverAliasForwardingBeforeSuper`
- `rejectsOverwrittenConstructorReceiverAtChainCallBeforeMutation`
- `receiverAliasForwardingCompilesAndRunsWithJavaParity`

## (d) 前置条件

其余构造函数遗留形态仍须在修改构造函数、`proxyMethod` 或隐藏方法池之前拒绝。
多调用 `ASTORE 0` 的复制后缀和 path-id 形态仍不支持。`--codegen` 保持
`legacy`。
