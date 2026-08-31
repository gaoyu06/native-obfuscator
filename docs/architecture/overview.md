# Architecture overview / 架构总览

A visual walkthrough of `native-obfuscator`. Facts below match
[project-status.md](project-status.md); when anything disagrees, the status
page wins. The active engineering goal is in
[current-goal.md](current-goal.md); the IR design is in
[ir-compiler.md](ir-compiler.md).

本页是配图的架构导览。与 [project-status.md](project-status.md) 冲突时以现状页为准；
现行工程目标见 [current-goal.md](current-goal.md)，IR 设计见 [ir-compiler.md](ir-compiler.md)。

---

## 1. What the tool is / 工具是什么

`native-obfuscator` is a **Java bytecode → C++/JNI transpiler**. It reads a
JAR, turns selected method bodies into JNI C++ that reproduces the bytecode
semantics through `JNIEnv`, and replaces those methods with `native` stubs.
At runtime the JVM dispatches into the generated shared library.

It does **not** pack binaries in the default flow (the CMake/Zig compile is a
separate step), and it does **not**, by itself, hide algorithm identity from
analysis. Prefer a whitelist/blacklist; transpiling a whole application JAR is
usually the wrong default.

`native-obfuscator` 是 **Java 字节码 → C++/JNI 转译器**：读取 JAR，把选定方法体
转成通过 `JNIEnv` 复现字节码语义的 JNI C++，并把这些方法替换为 `native` 桩，
运行时由 JVM 调入生成的共享库。默认流程里它**不**打包二进制（CMake/Zig 编译是
独立一步），也不能单靠自己隐藏算法特征。优先使用白名单/黑名单。

<img src="../assets/banner.svg" alt="native-obfuscator banner" width="100%">

## 2. End-to-end pipeline / 端到端流水线

<img src="../assets/architecture-pipeline.svg" alt="End-to-end pipeline from input JAR to output JAR and shared library" width="100%">

Stage by stage (paths under `obfuscator/src/main/java/by/radioegor146`):

1. **Load + filter / 加载与筛选** — `NativeObfuscator.process` streams jar
   entries, parses each class with ASM into a `ClassNode`, and applies
   `ClassMethodFilter` (blacklist/whitelist + `@Native`/`@NotNative`).
2. **Bytecode preprocessing / 字节码预处理** — `IndyPreprocessor` and
   `LdcPreprocessor` lower `invokedynamic` and handle/type `LDC` constants;
   the class is re-serialized with `COMPUTE_MAXS | COMPUTE_FRAMES` and
   re-read so frames and `maxStack`/`maxLocals` are authoritative.
3. **Per-method codegen / 逐方法代码生成** — `NativeObfuscator`'s method
   loop dispatches each selected method. Default: the legacy snippet
   generator (`MethodProcessor` / `GenericInstructionHandler` + `Snippets`
   + `cppsnippets.properties`). Opt-in: the typed CFG IR (`IrMethodCompiler`
   when `--codegen=ir`). Default off: the in-process interpreter
   (`InterpreterMethodEmitter` when `--backend=interpreter`). `--ir-lower=eval`
   is an IR-compiler lowering (`InterpreterStreamStrategy`), not that
   interpreter backend.
4. **Class assembly / 类级装配** — `ClassSourceBuilder` writes the per-class
   `.cpp`/`.hpp`, `CMakeFilesBuilder` writes `CMakeLists.txt`,
   `MainSourceBuilder` writes `native_jvm_output.cpp`, and `StringPool` emits
   the modified-UTF-8 blob.
5. **Runtime helpers / 运行时辅助** — `native_jvm.{cpp,hpp}` (the `utils::*`
   helpers) and `string_pool.{cpp,hpp}` ship alongside the generated sources.
6. **Native build / 本地编译** — CMake, or `ZigBuilder` with `--use-zig`.

## 3. Codegen and lowering modes / 生成与降级模式

<img src="../assets/codegen-modes.svg" alt="Codegen and lowering modes with defaults marked" width="100%">

Defaults on `master`: `--codegen=legacy`, `--ir-lower=direct`,
`--backend=cpp`. The IR path is opt-in; `--ir-lower` is consulted only under
`--codegen=ir`; `--ir-lower=eval` and `--backend=interpreter` are default off.
IR methods that hit an unsupported construct fall back per method to legacy;
rejected `<init>` bodies are restored to their original bytecode.

`master` 上的默认值：`--codegen=legacy`、`--ir-lower=direct`、`--backend=cpp`。
IR 路径需显式开启；`--ir-lower` 仅在 IR 模式下生效；`eval` 与解释器均默认关闭。
IR 不支持的构造逐方法回退到 legacy；被拒绝的 `<init>` 恢复为原始字节码。

The active goal is to move **all** method-body codegen onto IR and then delete
the legacy path. That goal is **not** complete — see
[current-goal.md](current-goal.md). 现行目标（IR 全覆盖后删除 legacy）**尚未完成**。

## 4. IR compiler internals / IR 编译器内部

<img src="../assets/ir-compiler.svg" alt="IR compiler internals: frontend, typed CFG, lowering strategies, emitter" width="100%">

Under `--codegen=ir` (package `ir/`):

- **Frontend / 前端** — `JsrRetInliner` inlines well-formed `jsr`/`ret`
  subroutines; `AsmToIr` and `CfgBuilder` build a typed CFG (`IrMethod`:
  `IrBlock`, `IrValue` typed `i32`/`i64`/`f32`/`f64`/reference, `IrPhi`,
  `IrTerminator`, `IrExceptionEdge`).
- **Lowering / 降级** — `IrMethodCompiler` selects a
  `MethodLoweringStrategy`: `DirectCppStrategy` (default `--ir-lower=direct`)
  or the default-off shared evaluator (`InterpreterStreamStrategy` when
  `--ir-lower=eval`, narrow integer slice). `--backend=interpreter` is a
  sibling path in `NativeObfuscator` (`InterpreterMethodEmitter`, ISA v4),
  not an `IrMethodCompiler` lowering.
- **Emit / 生成** — `IrCppEmitter` with `CppAst` and `MethodShellEmitter`
  produce structured `.cpp`/`.hpp` handed to class assembly.
- **Rejection / 拒绝路径** — unsupported constructs raise
  `UnsupportedIrConstructException` and fall back per method to legacy;
  rejected `<init>` bodies are restored to their original bytecode.

## 5. Repository layout / 仓库结构

<img src="../assets/repo-modules.svg" alt="Gradle modules and generated artifacts" width="100%">

Gradle modules (`settings.gradle`): `obfuscator` (CLI + transpiler),
`annotations` (`@Native`/`@NotNative`, JitPack coordinates
`com.github.radioegor146.native-obfuscator:annotations:master-SNAPSHOT`),
and `sdk` (`NativePrimitives`/`NativeStrings`, packed into generated JARs —
not a standalone product SDK). The `docs/` tree holds status, design,
benchmark, and review documents; start at [../README.md](../README.md).

## 6. User workflow / 使用流程

<img src="../assets/usage-flow.svg" alt="User workflow: transpile, compile, copy, run" width="100%">

Transpile with `java -jar`, compile the generated `cpp/` tree with CMake (or
skip straight to `--use-zig`), copy the shared library into the loader
directory printed on stdout (`native0/` by default), then run the output JAR.
On JDK 24+, `java -jar` launches get native access from the packaged
`Enable-Native-Access: ALL-UNNAMED` manifest attribute; classpath launches
still need `--enable-native-access=ALL-UNNAMED`
([jep472-native-access.md](jep472-native-access.md)).

## 7. Claims that must stay accurate / 必须保持准确的措辞

- Java 8 is the only version this project has ever called fully supported;
  9+ and Android stay experimental. / Java 8 是唯一称过完整支持的版本。
- JDK 17/21/25 IR fixture matches (11/11, 6/6, 4/4) are single-VM
  measurements, **not** "supports JDK 17/21/25". /
  JDK 17/21/25 的对齐记录只是单机测量，**不能**写成“已支持”。
- ClassicTest 108/108 is IR *admission*, not behavioral E2E. /
  108/108 是接纳率，不是行为级端到端验证。
- Benchmark numbers live in
  [../benchmarks/results-ir-vs-legacy-phase19.md](../benchmarks/results-ir-vs-legacy-phase19.md);
  do not invent numbers, and none of them are a portable speedup. /
  不要编造数字；已有数字不可外推。
- The reader/analysis bar is unmet; this is not a protection product. /
  阅读者门槛未达标；这不是保护类产品。
