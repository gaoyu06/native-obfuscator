<div align="center">

**English** | [简体中文](README.zh-CN.md)

<img src="docs/assets/banner.svg" alt="native-obfuscator — Java bytecode to C++/JNI transpiler" width="100%">

# native-obfuscator

**Java `.class` to C++ converter for use with JNI.**

[![Main pipeline](https://github.com/gaoyu06/native-obfuscator/actions/workflows/main.yml/badge.svg)](https://github.com/gaoyu06/native-obfuscator/actions/workflows/main.yml)
[![License: GPL-3.0](https://img.shields.io/github/license/gaoyu06/native-obfuscator)](LICENSE)
[![Java](https://img.shields.io/badge/Java-8%20baseline-blue)](#current-status)
[![Build](https://img.shields.io/badge/native%20build-CMake%20%7C%20Zig-informational)](#zig-toolchain)
[![Upstream](https://img.shields.io/badge/upstream-radioegor146%2Fnative--obfuscator-lightgrey)](https://github.com/radioegor146/native-obfuscator)

</div>

`native-obfuscator` reads a JAR, transpiles selected method bodies into JNI C++, and replaces those
methods with `native` stubs. The JVM then dispatches into the generated shared library.

The default generator is typed CFG IR (`--codegen=ir`). Unsupported methods keep their original
bytecode. `--codegen=legacy` is gone. `--ir-lower=eval` and `--backend=interpreter` exist and are
**off** by default; they are not a support claim. Java 8 is the only version this project has called
fully supported.

> [!IMPORTANT]
> This tool **transpiles** bytecode to native. It does not pack binaries and does not, by itself,
> hide algorithm identity from analysis. Use a blacklist/whitelist — transpiling a whole application
> JAR (for example a game client) is usually the wrong default.

---

## Table of contents

- [How it works](#how-it-works)
- [Current status](#current-status)
- [IR runtime](#ir-runtime)
- [Codegen modes](#codegen-modes)
- [Quick start](#quick-start)
- [Prerequisites](#prerequisites)
- [Usage](#usage)
  - [Arguments](#arguments)
  - [Platforms](#platforms)
  - [Annotations](#annotations)
  - [Basic flow](#basic-flow)
  - [Native-access on JDK 24+](#native-access-on-jdk-24)
- [Zig toolchain](#zig-toolchain)
- [C++ SDK (generated JARs)](#c-sdk-generated-jars)
- [Repository layout](#repository-layout)
- [IR compiler internals](#ir-compiler-internals)
- [Building and tests](#building-and-tests)
- [Documentation](#documentation)
- [Contributing](#contributing)
- [Issues](#issues)

---

## How it works

<img src="docs/assets/architecture-pipeline.svg" alt="End-to-end pipeline: input JAR, filter, preprocess, per-method codegen (IR / interpreter), class assembly, CMake or Zig build, shared library, output JAR with native stubs" width="100%">

1. **Load + filter** — `NativeObfuscator.process` streams the JAR, parses classes with ASM into
   `ClassNode`s, and applies `ClassMethodFilter` (blacklist/whitelist plus `@Native`/`@NotNative`).
2. **Bytecode preprocessing** — `IndyPreprocessor` and `LdcPreprocessor` lower `invokedynamic` and
   handle/type constants; the class is re-serialized with `COMPUTE_MAXS | COMPUTE_FRAMES` so frames
   are authoritative before codegen. Classes that still contain `jsr`/`ret` after a failed inline
   use `COMPUTE_MAXS` only (ASM cannot compute frames for those instructions).
3. **Per-method codegen** — `NativeObfuscator`'s method loop is the dispatch point. Each selected
   method goes to the typed CFG IR path (`IrMethodCompiler`) or, when `--backend=interpreter` admits
   it, the in-process interpreter (`InterpreterMethodEmitter`, default off). Interpreter misses retry
   IR. IR misses restore the original method bytecode. `--ir-lower=eval` is a lowering *inside* the
   IR compiler (`InterpreterStreamStrategy`); it is not the interpreter backend. Eval misses retry
   direct IR.
4. **Class assembly** — `ClassSourceBuilder`, `CMakeFilesBuilder`, `MainSourceBuilder`, and
   `StringPool` write the `cpp/` tree and rewrite the selected methods into `native` stubs.
5. **Runtime helpers** — `native_jvm.{cpp,hpp}` and `string_pool.{cpp,hpp}` are copied alongside the
   generated sources.
6. **Native build** — CMake, or Zig when `--use-zig` is set. This is a **separate step**; the tool
   does not compile binaries by itself in the default flow.

At runtime, the loader class loads the shared library and the JVM dispatches the `native` stubs into
it over JNI:

```mermaid
sequenceDiagram
    participant App as java -jar output.jar
    participant JVM as JVM
    participant Lib as Generated shared library

    App->>JVM: start main class
    JVM->>JVM: loader class runs System.load(native0/...)
    JVM->>Lib: register generated methods (__ngen_register_methods)
    JVM->>Lib: invoke native stub method
    Lib->>JVM: JNIEnv callbacks (fields, methods, objects)
    Lib-->>JVM: return value or pending exception
```

A longer visual walkthrough lives in
[`docs/architecture/overview.md`](docs/architecture/overview.md).

## Current status

Recorded on `master` after
[#118](https://github.com/gaoyu06/native-obfuscator/pull/118)/[#119](https://github.com/gaoyu06/native-obfuscator/pull/119)
and the follow-up landings through
[#456](https://github.com/gaoyu06/native-obfuscator/pull/456). Active goal:
[`docs/architecture/current-goal.md`](docs/architecture/current-goal.md). Status detail:
[`docs/architecture/project-status.md`](docs/architecture/project-status.md).

| Topic | What is true |
| --- | --- |
| Active goal | Method-body codegen is IR. The snippet path is deleted. Remaining unsupported shapes restore original bytecode |
| Default generator | `ir` (typed CFG). `--codegen=legacy` is removed |
| IR coverage | typed CFG through phase 20 plus `LCMP`, `IF_ACMP*`, monitors / synchronized, preprocessor-lowerable `invokedynamic`, proven `ConstantDynamic` (class and interface), raw MethodHandle / MethodType `LDC`, primitive `Class` `LDC`, and well-formed `jsr`/`ret` inlining. Unsupported methods restore original bytecode |
| Classfile metadata | Input major versions are preserved (Java 8 floor only). Nest / record / sealed attributes are no longer wiped by forcing version 52 |
| Java baseline | Historical README claim remains: **Java 8 is the only version this project has ever called fully supported.** 9+ and Android stay experimental |
| JDK 17 IR fixtures | 11 `--release 17` programs matched HotSpot stdout on **one** Linux x86-64 VM under `--codegen=ir`. That is not a product “supports JDK 17” badge |
| JDK 21 IR fixtures | 6 `--release 21` programs matched on **one** Linux VM after the local-type split. Not “supports JDK 21” |
| JDK 25 IR fixtures | 4 `--release 25` programs matched on **one** Linux VM (Temurin 25.0.4.1+1). 20/21 IR; one hybrid constructor left in Java; JEP 472 warning on every transformed run. Not “supports JDK 25” |
| ClassicTest IR admission | 108/108 methods admitted on the phase-18 corpus (admission ≠ behavioral E2E) |
| Native-access packaging | Output JARs emit `Enable-Native-Access: ALL-UNNAMED` (`java -jar`). Classpath still needs `--enable-native-access=ALL-UNNAMED`. Not “supports JDK 25” |
| C++ SDK | `NativePrimitives` + `NativeStrings` in generated JARs. Not a shipped standalone product SDK |
| Interpreter | `--backend=interpreter` default off (`cpp`). ISA v4: static `int`/`long` plus references and a first exception table (`ATHROW`). No `NEW`, invoke, or fields. Not a protection product |
| Shared evaluator | `--ir-lower=eval` is default off (`direct`) and applies only to successfully built IR methods in its narrow integer slice. Not ship-ready |
| Reader / analysis bar | Unmet. Live IR and opcode artifacts were recovered by unaided readers in the recorded evals |
| Performance | Latest three-mode run: [`docs/benchmarks/results-ir-vs-legacy-phase19.md`](docs/benchmarks/results-ir-vs-legacy-phase19.md). Not a portable speedup. Prefer a whitelist |

## IR runtime

Default `--codegen=ir` C++ does the following. None of it is a HotSpot speedup.

- Hoist class, field, and method IDs at method entry
- Skip redundant `ExceptionCheck` polls
- Buffer own-class non-volatile `int` instance fields
- Pin `int[]` with `GetIntArrayElements`
- Skip unused `lookup` / local-ref bookkeeping; instance methods still resolve the declaring class
- `--ir-direct-native=on` (off by default): same-class static IR calls become C++ calls instead of `CallStatic*Method`. Deep recursion then uses the C stack and may abort instead of throwing `StackOverflowError`
- Benchmark harness: `mixed-pricing` and `thin-pricing` are checksum/regression kernels, not speedup evidence. `BENCH_DIRECT_NATIVE=on` turns on direct calls for a bench run

## Codegen modes

<img src="docs/assets/codegen-modes.svg" alt="Three codegen/lowering modes: IR direct (default), IR eval (default off), interpreter (default off), with per-method restore-original fallback" width="100%">

How the flags interact for each selected method:

```mermaid
flowchart TD
    A["Method selected for transpilation"] --> B{"--backend interpreter and ISA admits?"}
    B -- "yes" --> C["InterpreterMethodEmitter"]
    B -- "no" --> D{"IR admits the method?"}
    D -- "no" --> E["Restore original bytecode"]
    D -- "yes" --> F{"--ir-lower ?"}
    F -- "direct (default)" --> G["DirectCppStrategy: structured C++"]
    F -- "eval (default off)" --> H{"Evaluator admits?"}
    H -- "yes" --> I["Shared evaluator lowering"]
    H -- "no" --> G
    C --> J["Generated C++ into cpp/ tree"]
    G --> J
    I --> J
```

`--backend=interpreter` is a separate, default-off switch (`--backend=cpp` is the default): a narrow
in-process interpreter at ISA v4 (static `int`/`long`, references, `ATHROW`/exception table; no
`NEW`, invokes, or fields). It is not a protection product.

## Quick start

<img src="docs/assets/usage-flow.svg" alt="User workflow: transpile with java -jar, compile with cmake or zig, copy the shared library, run the output JAR" width="100%">

```bash
# 1. Transpile
java -jar native-obfuscator.jar app.jar out/

# 2. Compile the generated C++ (recorded IR builds used CC=gcc CXX=g++)
cd out/cpp && cmake . && cmake --build . --config Release

# 3. Copy the shared library from build/libs/ into the loader dir printed on stdout (native0/)

# 4. Run
java -jar out/app.jar
```

Or replace steps 2–3 with the built-in [Zig toolchain](#zig-toolchain) (`--use-zig`).

## Prerequisites

1. **JDK** — JDK 8 is enough for the historical path. The current test/E2E work was also run with
   newer JDKs (for example 21) as the *host* compiler. You still need a JDK with `jni.h` when
   compiling generated C++.
2. **CMake** — [cmake.org](https://cmake.org/download/) or your distro package.
3. **C/C++ toolchain** — MSVC or MinGW on Windows; `g++` on Linux/macOS. Default Clang in some
   environments cannot link `libstdc++`; the recorded IR E2E used `CC=gcc CXX=g++`.
4. Optional: **Zig** for `--use-zig` (see below).
5. Optional for the full test suite: [Krakatau](https://github.com/Storyyeller/Krakatau) (`krak2`)
   on `PATH`.

## Usage

```text
Usage: native-obfuscator [-ahV] [--debug] [--codegen=<mode>]
                         [--ir-lower=<lowering>] [--backend=<backend>]
                         [-b=<blackListFile>] [--custom-lib-dir=<dir>]
                         [-l=<librariesDirectory>] [-p=<platform>]
                         [--plain-lib-name=<libraryName>] [-w=<whiteListFile>]
                         [--use-zig] [--zig-targets=<targets>]
                         [--zig-path=<file>] [--jdk-home=<file>]
                         [--zig-install-dir=<dir>]
                         <jarFile> <outputDirectory>
```

### Arguments

| Argument | Meaning |
| --- | --- |
| `<jarFile>` | Input JAR |
| `<outputDirectory>` | Where the transformed JAR and `cpp/` tree are written |
| `-l` | Directory of dependent libraries (optional, recommended) |
| `-p` | `hotspot` (default), `std_java`, or `android` |
| `--codegen` | `ir` (default; only remaining value) |
| `--ir-lower` | `direct` (default) or `eval` |
| `--native-intrinsics` | `safe` (default), `off`, or `fast`. Replaces selected JDK calls (`String.length`/`hashCode`/`charAt`/`isEmpty`, `System.arraycopy`, `Math.abs/min/max` for int/long). `fast` also replaces `Integer`/`Long` bitCount and numberOfLeadingZeros. File I/O is never rewritten. |
| `--backend` | `cpp` (default) or `interpreter` (narrow int/i64/reference slice; default off) |
| `--ir-cf-obf` | `off` (default) or `basic`. `basic` inserts always-true fake branches, flattens IR control flow through a dispatcher, and permutes non-entry blocks. Skipped for `--ir-lower=eval` and `--backend=interpreter`. |
| `--ir-direct-native` | `off` (default) or `on`. When `on`, same-class `invokestatic` of another IR-transpiled static method becomes a direct C++ call instead of `CallStatic*Method`. Skips `synchronized`, `<init>`, `<clinit>`, interfaces, and virtual/interface calls. Omits the extra Java native frame. Java-to-native entries are unchanged. Deep same-class recursion uses the C stack and may abort instead of throwing `StackOverflowError`. |
| `-a` | Enable `@Native` / `@NotNative` annotation processing |
| `-w` / `-b` | Whitelist / blacklist files |
| `--plain-lib-name` | Library name for `LoaderPlain` when you ship natives separately or for Android |
| `--custom-lib-dir` | Directory inside the JAR for packed libraries (default printed as `native0/` unless overridden) |
| `--debug` | Also write a non-executable debug JAR |
| `--use-zig` | Compile generated C++ with Zig and pack the shared libraries |
| `--zig-targets` | Comma-separated Zig targets (default `host`) |
| `--zig-path` | Zig executable (overrides installed / `PATH`) |
| `--jdk-home` | JDK with `include/jni.h` (defaults to `JAVA_HOME`) |
| `--zig-install-dir` | Where Zig was installed (default `~/.native-obfuscator/zig/`) |

`--codegen=ir` is the default and only remaining generator. Its `direct` lowering remains the
default; `--ir-lower=eval` selects a narrow shared evaluator lowering and retries direct IR on a
miss. Unsupported methods restore original bytecode (including `invokedynamic`) instead of being
left with internal preprocessor markers. `--backend=interpreter` is separately opt-in and default
off; interpreter misses retry IR.

### Platforms

- `hotspot` — HotSpot internals; works with many existing obfuscators, including some stack-trace
  checks.
- `std_java` — fewer JVM internals; intended to be more portable across JVMs.
- `android` — no `DefineClass` for hidden methods. Stack-based string/name schemes that need those
  hidden methods will not work.

### Annotations

Maven coordinates: `com.github.radioegor146.native-obfuscator:annotations:master-SNAPSHOT`
(add [JitPack](https://jitpack.io)).

- `@Native` — include the class or method
- `@NotNative` — skip a method inside a `@Native` class
- `@Native(lowering=…)`, `@Native(intrinsics=…)`, `@Native(backend=…)`,
  `@Native(cfObfuscation=…)`, `@Native(directNative=…)` — override `--ir-lower`, `--native-intrinsics`,
  `--backend`, `--ir-cf-obf`, and `--ir-direct-native` for that class or method.
  Defaults are `INHERIT` (CLI). Method values win over class values. `-a` still
  selects which methods are nativized; these attributes apply whenever a selected
  method or its class carries `@Native`.

The annotations JAR also ships `by.radioegor146.nativeobfuscator.NativePrimitives`
and `NativeStrings`. You can call them from ordinary Java (they have JDK
fallbacks). After native obfuscation, calls from nativized methods are replaced
with the generated C++ implementations. File I/O is never rewritten.

Whitelist/blacklist win over annotations.

Format:

```text
<class>
<class>#<method name>#<method descriptor>
mypackage/myotherpackage/Class1
mypackage/myotherpackage/Class1#doSomething!()V
mypackage/myotherpackage/Class1$SubClass#doOther!(I)V
```

Wildcards: `*` is one `/`-separated segment; `**` is any remaining segments.

### Basic flow

1. `java -jar native-obfuscator.jar <input.jar> <output-dir>`
2. Optional evaluator: add `--ir-lower=eval`
3. `cmake .` in the generated `cpp/` directory (recorded IR builds used `CC=gcc CXX=g++`)
4. `cmake --build . --config Release`
5. Copy the shared library from `build/libs/` into the loader path printed on stdout (`native0/` by
   default), named like:

   ```text
   x64-windows.dll
   x64-linux.so
   x86-windows.dll
   x64-macos.dylib
   arm64-linux.so
   arm64-windows.dll
   ```

6. `java -jar <output.jar>`

Omit `--plain-lib-name` if you want natives packed into the JAR after you copy them into that loader
directory.

### Native-access on JDK 24+

The loader classes call `System.load`/`System.loadLibrary`, which
[JEP 472](https://openjdk.org/jeps/472) makes restricted operations. On JDK 24+ an unenabled call
warns by default, and a future release may turn that into an error. The output JAR is written with
`Enable-Native-Access: ALL-UNNAMED` in `META-INF/MANIFEST.MF`, so a `java -jar <output.jar>` launch
grants native access to the unnamed module without extra flags (any more specific value already
present in the input manifest is preserved). A classpath launch does not honor that manifest
attribute and still needs the flag:

```text
java --enable-native-access=ALL-UNNAMED -cp <output.jar> <main-class>
```

This is a packaging convenience, not a claim that JDK 25 is supported.

## Zig toolchain

Steps 2–5 can be replaced with `--use-zig` (no CMake / no host compiler; cross-compilation is built
in).

```text
java -jar native-obfuscator.jar install-zig [--version <x.y.z>] [--install-dir <path>] [--force]
```

Downloads an official Zig release (SHA-256 verified) into `~/.native-obfuscator/zig/` by default.

```text
java -jar native-obfuscator.jar --use-zig \
     [--zig-targets x64-windows,x64-linux,arm64-linux] \
     [--jdk-home <path-to-jdk>] \
     <input.jar> <output-dir>
```

Known targets include `x64-linux`, `x64-windows`, `x64-macos`, `arm64-linux`, `arm64-windows`,
`arm64-macos`, `x86-linux`, `x86-windows`, `arm32-linux`, and `host`.

## C++ SDK (generated JARs)

Depend on the annotations artifact and call
`by.radioegor146.nativeobfuscator.NativePrimitives` / `NativeStrings` from Java.
Generated JARs also still pack the older `by.radioegor146.sdk` names as
deprecated delegates. This is **not** a separately versioned product SDK.

Primitives (see [`docs/sdk/v1-status.md`](docs/sdk/v1-status.md)):

- `abiVersion()`
- `sha256(byte[])`
- `hmacSha256(byte[] key, byte[] message)`
- `aes256GcmEncrypt` / `aes256GcmDecrypt` (32-byte key, 12-byte nonce, 16-byte tag; do not reuse a
  nonce with the same key)
- `constantTimeEquals(byte[], byte[])`

Strings: Java-compatible UTF-16 `length`, `hashCode`, and `concat`. Recorded string benches were
**slower than HotSpot**.

## Repository layout

<img src="docs/assets/repo-modules.svg" alt="Gradle modules obfuscator, annotations, sdk, docs, and the generated artifacts: cpp/ tree, output JAR, native0/" width="100%">

| Path | Role |
| --- | --- |
| `obfuscator/` | The CLI and transpiler (`by.radioegor146.*`, `ir/`, `interpreter/`, `zig/`, runtime sources) |
| `annotations/` | `@Native` / `@NotNative` plus `NativePrimitives` / `NativeStrings`, consumable via JitPack |
| `sdk/` | Deprecated `by.radioegor146.sdk` delegates, packed into generated JARs |
| `docs/` | Status, design, benchmark, and review documents — start at [`docs/README.md`](docs/README.md) |

## IR compiler internals

<img src="docs/assets/ir-compiler.svg" alt="IR compiler internals: ASM tree, JSR/RET inlining, typed CFG, lowering strategies, C++ AST emission" width="100%">

The default `--codegen=ir` path builds a typed control-flow graph (`i32`/`i64`/`f32`/`f64`/reference
values) from the preprocessed ASM tree (`AsmToIr`, `CfgBuilder`, with well-formed `jsr`/`ret`
inlined first), lowers it through a strategy (`DirectCppStrategy` by default, or
`InterpreterStreamStrategy` when `--ir-lower=eval`), and emits structured C++ via `IrCppEmitter`.
`--backend=interpreter` is a separate `NativeObfuscator` path (`InterpreterMethodEmitter`), not an
IR-compiler lowering. Methods with unsupported constructs raise `UnsupportedIrConstructException`
and restore original bytecode.

Design and status: [`docs/architecture/ir-compiler.md`](docs/architecture/ir-compiler.md) ·
[`docs/architecture/current-goal.md`](docs/architecture/current-goal.md) ·
[`docs/architecture/project-status.md`](docs/architecture/project-status.md).

## Building and tests

```text
./gradlew assemble          # skip tests
./gradlew build             # assemble + full suite (needs krak2 for some cases)
```

Focused IR suite used during the integration:

```text
CC=gcc CXX=g++ ./gradlew :obfuscator:test \
  --tests by.radioegor146.ir.IrCompilerTest \
  --tests by.radioegor146.CodegenModeTest
```

ClassicTest-style fixtures live under `obfuscator/test_data/`. Some of that corpus comes from
[huzpsb/JavaObfuscatorTest](https://github.com/huzpsb/JavaObfuscatorTest).

CI runs the "Main pipeline" workflow
([`.github/workflows/main.yml`](.github/workflows/main.yml)) on JDK 8/11/17/21/25 across
Ubuntu/macOS/Windows.

## Documentation

Start at [`docs/README.md`](docs/README.md).

| Doc | Role |
| --- | --- |
| [Architecture overview](docs/architecture/overview.md) | Visual walkthrough of the pipeline (bilingual) |
| [Current goal](docs/architecture/current-goal.md) | Replacement landed: IR is the default generator; snippet path deleted |
| [Project status](docs/architecture/project-status.md) | What landed on master, what did not, what must not be claimed |
| [IR compiler](docs/architecture/ir-compiler.md) | Typed CFG design |
| [IR phase 18](docs/architecture/ir-phase18-status.md) | Primitive arrays and `MULTIANEWARRAY` |
| [JDK 17 IR runtime repair](docs/architecture/ir-jdk17-runtime-fix.md) | Version / indy / `invokeExact` |
| [SDK v1](docs/sdk/v1-status.md) | Java API and C ABI |
| [Benchmarks](docs/benchmarks/README.md) | How to run the harness; do not invent numbers |
| [Historical options brief](docs/architecture/goal-status-and-options.md) | Pre-landing maintainer snapshot (now superseded as *current* status) |

## Contributing

See [`CONTRIBUTING.md`](CONTRIBUTING.md). Short version: read
[`docs/architecture/current-goal.md`](docs/architecture/current-goal.md) first, gate changes on
executed tests, and never inflate status claims.

## Issues

Open an issue on this repository, or contact the original author at [re146.dev](https://re146.dev).

## License

[GPL-3.0](LICENSE).

### Stargazers over time

[![Stargazers over time](https://starchart.cc/radioegor146/native-obfuscator.svg?variant=adaptive)](https://starchart.cc/radioegor146/native-obfuscator)
