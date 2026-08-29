# IR invokedynamic admission status / IR invokedynamic 接纳状态

> **Not a JDK-support badge.** This page records which `invokedynamic`
> and dynamic-constant shapes the typed CFG IR frontend now admits, and
> which it still rejects before mutation. `--codegen` still defaults to
> `legacy`; every IR run opts in with `--codegen=ir`. See
> [current-goal.md](current-goal.md) and [project-status.md](project-status.md).
>
> **本页不是“支持某某 JDK”的徽章。** 这里只记录 typed CFG IR 前端现在
> 接纳哪些 `invokedynamic` 与动态常量形态，以及哪些仍在改写前拒绝。
> `--codegen` 默认仍是 `legacy`。

## Scope / 范围

Before this change the IR frontend (`AsmToIr`) fell through to the
generic "unsupported instruction" path on every `INVOKEDYNAMIC`, so any
method with a dynamic call site fell back to the legacy snippet
generator. That included the shapes this tree already knows how to
preprocess for the legacy path: `StringConcatFactory` string
concatenation, `LambdaMetafactory` lambdas, and `ObjectMethods.bootstrap`
record accessors.

在此改动之前，IR 前端遇到任何 `INVOKEDYNAMIC` 都会落到通用的
“不支持指令”分支，于是带动态调用点的方法整体回退到 legacy 生成器。

## What changed / 改了什么

The whole change is contained in the IR frontend. `AsmToIr.build`
now calls `admitInvokeDynamic(owner, method)` as its first step:

- If the method has no `InvokeDynamicInsnNode`, the original
  `MethodNode` is returned untouched.
- Otherwise the frontend copies the method (`method.accept(copy)`) and
  runs the shared `IndyPreprocessor` on the **copy** with
  `Platform.STD_JAVA`. STD_JAVA lowers every call site to a
  self-contained bootstrap plus `MethodHandle.invokeWithArguments`; it
  needs no reverse-invoke hidden class and runs on any conforming JVM,
  so it is the portable choice for a frontend that does not carry the
  runtime platform.
- The lowered copy is a sequence the IR already admits: the lookup /
  class-loader / current-class preprocessor intrinsics, `LDC` of
  `MethodType` / `MethodHandle` (rewritten by the same pass into
  `MethodHandles.Lookup` calls), and ordinary invokes. Emission goes
  through the existing IR backend, not the `native.magic.*` symbolic
  markers.
- If `IndyPreprocessor` throws, the frontend raises
  `UnsupportedIrConstructException` **before** the original method is
  mutated, preserving the reject-before-mutation guarantee.

Working on a copy is what keeps rejection safe: the caller's
`MethodNode` is never altered when preprocessing or later IR validation
fails, so a rejected method can still fall back cleanly to legacy.

整个改动只落在 IR 前端。`AsmToIr.build` 第一步调用
`admitInvokeDynamic`：没有 `invokedynamic` 就原样返回；有的话在方法的
**副本**上以 `Platform.STD_JAVA` 跑共享的 `IndyPreprocessor`，把调用点
降解成 IR 已能接纳的 lookup/classloader 内建、`MethodType`/`MethodHandle`
的 `LDC` 改写以及普通 invoke。预处理抛异常时在改写原方法**之前**抛出
`UnsupportedIrConstructException`。在副本上工作保证了拒绝时原方法不被改动。

## Admitted vs still rejected / 接纳与仍拒绝

| Shape / 形态 | Status / 状态 |
| --- | --- |
| `invokedynamic` whose bootstrap the shared `IndyPreprocessor` lowers (string concat, lambda metafactory, `ObjectMethods.bootstrap` record accessors) | **Admitted / 接纳** |
| `LDC` of `MethodType` / `MethodHandle` produced by that preprocessing pass | **Admitted / 接纳** |
| `invokedynamic` whose bootstrap the preprocessor cannot lower | **Rejected before mutation / 改写前拒绝** |
| `LDC` of a `ConstantDynamic` (condy) — no preprocessor lowers a dynamic constant | **Rejected before mutation / 改写前拒绝** |
| Raw `LDC` of `MethodHandle` / `MethodType` that is not part of an admitted `invokedynamic` lowering | **Rejected before mutation / 改写前拒绝** |

`ConstantDynamic` stays unsupported because neither `IndyPreprocessor`
nor `LdcPreprocessor` rewrites a dynamic constant; admitting it would
require emitting a bootstrap-resolved constant the IR backend has no
lowering for. It is the new reject-before-mutation sentinel used by
`rejectsUnsupportedWideOperationBeforeMutation`.

`ConstantDynamic` 仍不支持：没有预处理器会改写动态常量，接纳它需要 IR
后端能降解一个由 bootstrap 解析的常量，而它没有。因此它成为
`rejectsUnsupportedWideOperationBeforeMutation` 的新“改写前拒绝”哨兵。

## Tests / 测试

Focused acceptance (executed with `CC=gcc CXX=g++`):

```text
CC=gcc CXX=g++ ./gradlew :obfuscator:test --rerun-tasks \
  --tests by.radioegor146.ir.IrCompilerTest \
  --tests by.radioegor146.CodegenModeTest
```

Result from the JUnit XML: `IrCompilerTest` 114 tests, 0 skipped, 0
failures, 0 errors; `CodegenModeTest` 7 tests, 0 skipped, 0 failures,
0 errors.

Coverage added on the IR path:

- `admitsInvokedynamicThroughIndyPreprocessorInIr` — a raw
  `invokedynamic` reaches the IR frontend and is admitted via the
  internal preprocessing, emitting lookup intrinsics rather than
  `native.magic` markers.
- `admitsStringConcatIndyThroughFullPreprocessorPipeline` — a real
  `StringConcatFactory` call site, lowered by the production
  `PreprocessorRunner` and then compiled on the IR path.
- `executesStringConcatIndyThroughIrWhenToolchainAvailable` — when
  `g++` and JNI headers are present, the lowered C++ is compiled and
  **run** against a stubbed JNI environment; the test asserts the
  bootstrap and the resolved call-site target both execute and return
  the expected reference.
- `generatedCppPassesGppSyntaxCheckWhenToolchainAvailable` — now also
  compiles the preprocessed indy method for a syntax-only g++ check.
- `rejectsConstantDynamicLdcBeforeMutation` and
  `rejectsUnsupportedWideOperationBeforeMutation` — assert condy is
  rejected with the `LDC` opcode and the method is left unmutated.

## Not done / 未完成

This does not flip the CLI default off `legacy` and does not delete the
snippet path. Condy and non-lowerable bootstraps still fall back per
method. This is one admission increment toward the active goal, not a
JDK-support or ship-ready claim.

本改动没有改掉 CLI 默认值，也没有删除 snippet 路径。condy 与无法降解的
bootstrap 仍逐方法回退。这只是朝现行目标的一次接纳增量。
