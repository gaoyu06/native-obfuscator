# JDK compatibility

## How to read this document

This is a compatibility assessment of this repository, not of ASM or JNI in
general. Labels mean:

- **Known—repo:** directly established by source/configuration in `master`.
- **Known—spec:** established by a cited platform specification or upstream
  release note.
- **Inferred:** a likely consequence of inspected code, not a runtime result.
- **Unknown:** no sufficiently scoped evidence was found.

“Parses,” “transpiles,” and “runs” are different claims. A JDK can run Java 8
class files while the transpiler still fails on classes emitted by that JDK.
Likewise, the CI matrix can launch tests on a JDK without covering that JDK's
new class-file constructs.

No GitHub Actions run was available through the repository API during this
review, and this docs-only PR does not fabricate or substitute test results.

## Current implementation facts

| Fact | Evidence | Classification |
|---|---|---|
| The Gradle Java source/target level is 8 for both modules. | `obfuscator/build.gradle`, `annotations/build.gradle` | Known—repo |
| ASM is pinned to 9.8. Upstream ASM 9.8 added `Opcodes.V25`. | `obfuscator/build.gradle`; [ASM versions](https://asm.ow2.io/versions.html) | Known—repo/spec |
| CI is configured for JDK 8, 11, 17, 21, and 25 on Linux, macOS, and Windows (with one JDK-8 macOS substitution). | `.github/workflows/main.yml` | Known—repo configuration, not a pass result |
| Every processed class is assigned `classNode.version = 52` before writing. | `NativeObfuscator` | Known—repo |
| `invokedynamic` is rewritten before codegen; reaching its instruction handler is an error. | `IndyPreprocessor`, `InvokeDynamicHandler` | Known—repo |
| `LdcHandler` accepts string, numeric, and `Type` constants, but not ASM `ConstantDynamic`. | `LdcHandler`, `LdcPreprocessor` | Known—repo |
| End-to-end tests compile source, build a JAR, transpile it for all `Platform` values, compile C++, execute it, and compare stdout. | `ClassicTest` | Known—repo |
| The test suite has interface-default, indy/lambda, string-concat, exception, reflection, and general Java fixtures, but no explicit nestmate, condy, record, sealed, module, multi-release, virtual-thread, or JDK-25 feature fixture. | `obfuscator/test_data/tests` and searches of test sources | Known—repo |
| The default `HOTSPOT` path calls internal `java/lang/invoke/MethodHandleNatives.linkCallSite`; its code distinguishes two observed descriptors only. | `native_jvm.cpp`, `IndyPreprocessor` | Known—repo |
| `JNI_OnLoad` requests and returns `JNI_VERSION_1_8`. | `native_jvm_output.cpp` | Known—repo; this does not limit the host JVM to JDK 8 |
| The README says Java 8 is fully supported and Java 9+ is experimental. | `README.md` | Known—repo claim, not independent verification |

## Release-level assessment

Class-file majors are 52, 55, 61, 65, and 69 for Java 8, 11, 17, 21, and 25
respectively ([JVMS 25, Table 1.2-A](https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-1.html#jvms-1.2)).

| JDK | Parser/tooling | Features material to this transpiler | Current assessment |
|---|---|---|---|
| 8 (major 52) | ASM 9.8 can parse it; the tool and injected loader classes target 8. **Known—repo** | Lambdas/method references use indy; interface default methods and Java-8 verifier frames are represented in the corpus. | **Claimed by README, partially implemented, not re-verified here.** Tests exist, but no current run result was available. Historical `jsr`/`ret` class files and malformed/edge verifier cases are not explicitly covered. |
| 11 (major 55) | ASM 9.8 can parse it. **Known—spec/repo** | Indy string concat, nestmate access, dynamic constants, modules, and multi-release JARs matter. | **Not production-supported by current evidence.** Condy in a selected method is known unsupported. Forced major 52 is incompatible with relying on major-55 nestmate semantics. Module and multi-release behavior are unknown. |
| 17 (major 61) | ASM 9.8 can parse it. **Known—spec/repo** | Records, sealed classes, modern nestmates/indy, strong encapsulation, and class-file 61 must be preserved. | **Required future baseline, not currently proven.** Record/sealed/nest attributes may remain on an output class forced to major 52; that is not a valid compatibility strategy. No feature-scoped 17 evidence exists in this review. |
| 21 (major 65) | ASM 9.8 can parse it. **Known—spec/repo** | Record patterns, pattern switch, virtual threads, preview class files, and JDK 21 runtime behavior are relevant. FFM is preview, not a stable SDK target. | **Evaluation only.** Basic matrix configuration exists; feature coverage, virtual-thread/native behavior, preview rejection, and generated-artifact runtime results are unknown. |
| 25 (major 69) | ASM 9.8 explicitly added V25 parser support. **Known—spec/repo** | Major 69 input, modern native-access policy, current verifier/runtime behavior, and new compiler output must be tested. | **Evaluation only.** Parser capability is not transpiler compatibility. JDK 24+ warns by default for unenabled JNI loading/linking; denial is available and a future release may make denial the default. No current run result was available. |

## Feature-by-feature assessment

| Feature | First relevant release | What this transpiler does now | Status |
|---|---:|---|---|
| Primitive arithmetic/control flow | 1–8 | Emits direct C++ expressions and gotos from snippets while simulating JVM slots. | Implemented; semantic breadth is **unknown** without edge-case differential tests. C++ overflow/shift/conversion behavior needs explicit helpers. |
| Java strings | all | Most string operations remain JNI method calls; literals use a modified-UTF-8-oriented string pool and `NewStringUTF`. | Partial. Full Unicode, embedded NUL, unpaired surrogate, long-string, and compact-string-independent behavior are **unknown**. |
| Lambdas/method references | 8 | Generic indy preprocessing builds/invokes method handles; an indy fixture exists. | Implemented path, current pass result **unknown**. Custom bootstrap breadth is not proven. |
| Interface default/private methods | 8 / 9 | Default-method fixtures exist; method invocation is handled generically. | Java 8 default path is covered by a fixture. Java 9 private-interface/nest semantics are **unknown**. |
| Indy string concatenation | 9 | Generic indy preprocessor contains vararg handling; a `StringConcatFactory` fixture exists. | Implemented path, not a complete recipe/constant matrix; current pass result **unknown**. |
| Modules (`module-info.class`) | 9 | A class with no processable method is copied, but the archive pipeline has no module-aware policy and injects loader/native declarations. | **Unknown.** Named-module loading, package ownership, signing, and native-access configuration need tests. |
| Multi-release JARs | 9 | Entries are traversed as ordinary class entries; there is no release-selection or duplicate-class policy. | **Unknown.** Must test base/versioned variants, metadata resolution, deterministic output, and the runtime-selected registration path. |
| Nest-based private access | 11 | ASM can retain nest attributes, but processed classes are rewritten to major 52. | **Unsupported/unproven.** Nest semantics are tied to a suitable class-file version ([JEP 181](https://openjdk.org/jeps/181)); blind downgrade can invalidate access assumptions. |
| Dynamic constants (`CONSTANT_Dynamic`) | 11 | No condy lowering exists; an unprocessed `ConstantDynamic` reaches the unsupported `LdcHandler` branch. | **Known unsupported in selected methods.** See [JEP 309](https://openjdk.org/jeps/309). |
| Records / `Record` attribute | 16 | ASM nodes can preserve the attribute; no compatibility policy exists and output is forced to 52. | **Unsupported/unproven** for processed record classes. Record methods that happen to be ordinary bytecode do not prove metadata compatibility. |
| Sealed classes / `PermittedSubclasses` | 17 | Same preservation-plus-downgrade problem; no fixture exists. | **Unsupported/unproven** for processed sealed hierarchies. |
| Preview class files (`minor=65535`) | 12+ | No explicit detect/reject/preserve policy was found; processed output is reset to 52. | **Unsupported by policy.** Future compiler must reject by default with a diagnostic, then add version-specific opt-in tests if desired. |
| Strong encapsulation | 16/17 | Native runtime uses JNI plus HotSpot internals for one platform mode; no `--add-opens` policy is documented. | `STD_JAVA` and `HOTSPOT` must be tested separately. Continued internal-linkage compatibility is **unknown**. |
| Virtual threads | 21 | Native methods can execute, but no scheduler/pinning/cancellation/throughput fixture exists. | Functional and performance behavior **unknown**. It requires concurrency and observability tests, not a compile-only check. |
| FFM API | incubating 17, preview 19–21, standard 22 | Not used. Current binding is JNI. | Not implemented. A release adapter should use the standard JDK 22+ API ([JEP 454](https://openjdk.org/jeps/454)), not JDK 21 preview APIs. |
| JNI native-access restrictions | 24 | Current loader calls `System.load`/`System.loadLibrary`, and transformed classes declare native methods. Packaging now writes `Enable-Native-Access: ALL-UNNAMED` into the output `META-INF/MANIFEST.MF` (preserving any more specific input value), which a `java -jar` launch honors for the unnamed module. | JDK 24+ default is still warning/allow, not a hard failure; `--illegal-native-access=deny` is available and future default denial is still a risk ([JEP 472](https://openjdk.org/jeps/472)). The emitted manifest attribute is not honored on classpath launches, which still require `--enable-native-access=ALL-UNNAMED`. This is a packaging aid, not a JDK 25 support badge. |
| Android | separate runtime | The repository has an `ANDROID` mode and runs it in the same desktop test process. | Outside this JDK table. A desktop JVM test does not establish Android ART/device compatibility. Treat as a separate product profile. |

## Required compatibility model

The replacement compiler must publish these dimensions independently:

1. **Tool host JDK:** JDK used to run Gradle/transpiler.
2. **Input class-file profile:** major/minor, attributes, constant-pool forms,
   bootstraps, and compiler source.
3. **Output runtime JDK:** JVM used to load and execute the transformed JAR.
4. **Compiler backend:** structured C++ or optional interpreter.
5. **Runtime mode:** `STD_JAVA`, `HOTSPOT`, and (separately) Android.
6. **Native target/toolchain:** OS, architecture, compiler, standard library,
   optimization mode, and sanitizer mode.

No result from one cell is inherited by another. In particular, running
Java-8-targeted classes on JDK 25 does not prove major-69 input support.

### Proposed support tiers

- **Tier 1:** JDK 17 tool host, major-61-or-lower non-preview input within the
  published feature table, and JDK 17 output runtime. This is the first
  production gate.
- **Tier 2 candidates:** JDK 21 and 25 host/input/runtime profiles after the same
  feature corpus passes and native-access operation is documented.
- **Legacy:** JDK 8 and 11, each with a frozen feature set. Legacy status is
  earned by tests, not inferred from source compatibility.
- **Preview:** rejected before output unless a separately versioned experimental
  profile is approved. A preview class for one JDK must never be claimed
  compatible with another.

The compiler preserves input class-file version and compatible metadata.
Unsupported features produce a capability report and fail selected-method
compilation. It must not silently set version 52, strip semantics, or report
success after per-entry errors.

## Compatibility test plan

### Per-release fixtures

| Profile | Minimum dedicated fixtures |
|---|---|
| 8 | primitive edge values; arrays; exceptions/finally; monitors; default methods; lambdas/method references; reflection-visible annotations/signatures; Unicode and modified-UTF-8 edge cases; synthetic old `jsr`/`ret` refusal or normalization |
| 11 | all applicable 8 fixtures plus nestmate private field/method/constructor access; condy primitive/reference/bootstrap failure; indy concat recipes/constants; module-path execution; multi-release JAR selection |
| 17 | records with reflection/serialization-sensitive metadata; sealed hierarchies; nestmates; hidden-class interaction where relevant; switch expressions; text blocks as Unicode constants; strong-encapsulation launch modes |
| 21 | record patterns; pattern switch; virtual-thread invocation/concurrency/cancellation; current indy recipes; preview-class deterministic rejection |
| 25 | major-69 classes from `javac`; all stable prior fixtures; `jnativescan`; native-access warning and deny modes; current JVM verification and loader behavior |

Where a language feature lowers only to pre-existing bytecode, its fixture still
matters: it guards the actual compiler patterns users submit. Separately
generated bytecode fixtures must cover constant-pool and verifier cases `javac`
does not routinely emit.

### Oracle

For a seeded input, run the unmodified JAR and each backend in fresh JVMs. The
oracle compares:

- return values including raw floating-point bits and array/object graphs where
  stable;
- exception class, cause chain, and contractually relevant message;
- static/instance state, class-initialization order, monitor/concurrency
  outcomes, and reflection-visible metadata;
- stdout/stderr and exit code for application fixtures;
- timeout/deadlock/crash status and native sanitizer/JNI-check diagnostics.

Each case records input digest, `javac` version/options, class-file version,
transpiler commit/options, runtime JDK, OS/architecture, C++ compiler/options,
backend, and output digest.

### CI promotion rule

JDK 17 feature cells are required. JDK 21/25 begin as non-blocking evidence but
become required before support is advertised. JDK 8/11 can be blocking only for
their declared legacy set. Matrix jobs must upload per-case results even when a
later case fails; an aggregate green job without feature identities is
insufficient evidence.
