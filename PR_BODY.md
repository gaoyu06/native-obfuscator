# IR: admit invokedynamic / bootstrap linkage on the typed CFG IR path

Admit `invokedynamic` (and the `LDC` of `MethodType` / `MethodHandle`
that the shared preprocessor produces for it) on the typed CFG IR path
so that methods using the existing indy preprocessor / `link_call_site`
/ `ObjectMethods.bootstrap` / `invokeExact` trampolines can stay on
`--codegen=ir` instead of falling back to the legacy snippet generator.

在 typed CFG IR 路径上接纳 `invokedynamic`（以及共享预处理器为它生成的
`MethodType`/`MethodHandle` 的 `LDC`），使使用现有 indy 预处理器 /
`link_call_site` / `ObjectMethods.bootstrap` / `invokeExact` trampoline
的方法能留在 `--codegen=ir`，而不必回退到 legacy 生成器。

## What / 内容

- `AsmToIr.build` now runs `admitInvokeDynamic(owner, method)` first.
  If the method contains an `InvokeDynamicInsnNode`, the frontend copies
  the method and runs the shared `IndyPreprocessor` on the copy with
  `Platform.STD_JAVA`, lowering each call site to a self-contained
  bootstrap plus `MethodHandle.invokeWithArguments` — a sequence the IR
  backend already emits through lookup / class-loader intrinsics and
  ordinary invokes, never through `native.magic.*` symbolic markers.
- Working on a copy preserves reject-before-mutation: if the bootstrap
  is not lowerable, `UnsupportedIrConstructException` is raised before
  the caller's `MethodNode` is touched, so the method falls back cleanly.
- `ConstantDynamic` (condy) `LDC` stays deliberately unsupported — no
  preprocessor lowers a dynamic constant — and becomes the new
  reject-before-mutation sentinel for
  `rejectsUnsupportedWideOperationBeforeMutation`.
- New doc: `docs/architecture/ir-indy-status.md`.

- `AsmToIr.build` 首步调用 `admitInvokeDynamic`：方法含 `invokedynamic`
  时，在副本上以 `Platform.STD_JAVA` 跑共享 `IndyPreprocessor`，把调用点
  降解为 IR 已能接纳的序列。副本工作保证改写前拒绝：无法降解时在改动原
  方法前抛出 `UnsupportedIrConstructException`。condy 仍不支持并成为新的
  拒绝哨兵。

## Indy shapes admitted vs still rejected / 接纳与仍拒绝的形态

- **Admitted / 接纳:** `invokedynamic` whose bootstrap the shared
  preprocessor lowers (string concat, lambda metafactory,
  `ObjectMethods.bootstrap` record accessors); `LDC` of `MethodType` /
  `MethodHandle` produced by that pass.
- **Still rejected before mutation / 仍在改写前拒绝:** `LDC` of a
  `ConstantDynamic`; `invokedynamic` whose bootstrap the preprocessor
  cannot lower; raw `MethodHandle` / `MethodType` `LDC` that is not part
  of an admitted indy lowering.

## (a)(b)(c)(d)

- **(a) Scope / 范围:** One IR admission increment — `invokedynamic`
  bootstrap linkage on the typed CFG IR path. Changes are confined to
  `obfuscator/src/main/java/by/radioegor146/ir/frontend/AsmToIr.java`,
  the test `IrCompilerTest.java`, and new
  `docs/architecture/ir-indy-status.md`. No interpreter, evaluator, CLI
  default, README, project-status, or current-goal edits. /
  一次 IR 接纳增量，仅改动 IR 前端、`IrCompilerTest` 与新增状态文档。
- **(b) Ship-ready? / 可直接上线？** **No / 否.** This does not flip the
  `--codegen` default and does not delete the legacy path. condy and
  non-lowerable bootstraps still fall back per method. /
  未改默认值，未删 legacy，condy 与不可降解的 bootstrap 仍逐方法回退。
- **(c) Acceptance (executed tests) / 验收（已执行的测试）:**

  ```text
  CC=gcc CXX=g++ ./gradlew :obfuscator:test --rerun-tasks \
    --tests by.radioegor146.ir.IrCompilerTest \
    --tests by.radioegor146.CodegenModeTest
  ```

  JUnit XML counts: `IrCompilerTest` 114 tests, 0 skipped, 0 failures,
  0 errors; `CodegenModeTest` 7 tests, 0 skipped, 0 failures, 0 errors.
  Includes a compile-and-run test
  (`executesStringConcatIndyThroughIrWhenToolchainAvailable`) that
  compiles the lowered C++ with `g++` and runs it against a stubbed JNI
  environment for a real `StringConcatFactory` call site. /
  JUnit 计数如上；含一项用 `g++` 编译并运行降解后 C++ 的真实字符串拼接
  indy 用例。
- **(d) Preconditions / 前置条件:** Only the known leftover constructs
  are cited (indy, MethodHandle/MethodType/ConstantDynamic `LDC`); no
  full JVM coverage matrix is claimed, and no JDK-support badge is
  implied. /
  仅引用已知缺口，不声明完整 JVM 覆盖表，不暗示任何 JDK 支持徽章。
