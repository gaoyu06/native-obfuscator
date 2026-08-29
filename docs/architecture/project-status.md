# Project status on master / master 现状

Last updated after landing gapped constructor prefix extras
[#164](https://github.com/gaoyu06/native-obfuscator/pull/164)
(parent re-ran 136/136: 129 `IrCompilerTest` + 7 `CodegenModeTest`,
including `gappedPrefixExtraReferenceLocalCompilesAndRunsWithJavaParity`)
on the post-[#163](https://github.com/gaoyu06/native-obfuscator/pull/163)
extra-local tree. Active process:
[current-goal.md](current-goal.md) (fast-model increments, test gate,
Fable 5 reserved for hard work).
This page is the current public status. It must not be read as a support
matrix. The long maintainer brief in
[goal-status-and-options.md](goal-status-and-options.md) is historical.

本页是当前公开现状。现行目标见
[current-goal.md](current-goal.md)：先把方法体全部迁到 IR，再废弃
legacy。不能当成 JDK 支持矩阵。

## What landed / 已落地

- **Legacy generator (default).** Snippet substitution through
  `cppsnippets.properties` remains the CLI and API default (`--codegen=legacy`).
- **Opt-in IR.** `--codegen=ir` lowers admitted methods through a typed CFG
  (i32 / i64 / f32 / f64 / reference) to structured C++/JNI. Phase 19 adds
  `LAND`/`LOR`/`LXOR` and `LSHL`/`LSHR`/`LUSHR`. Phase 20
  ([#134](https://github.com/gaoyu06/native-obfuscator/pull/134); Sol accept
  [#135](https://github.com/gaoyu06/native-obfuscator/pull/135); Fable
  accept-with-nits [#136](https://github.com/gaoyu06/native-obfuscator/pull/136))
  adds `LDIV`/`LREM`/`LNEG` via dedicated `LongDivRem` / `LongUnary` nodes.
  [#153](https://github.com/gaoyu06/native-obfuscator/pull/153) (Fable accept
  [#156](https://github.com/gaoyu06/native-obfuscator/pull/156)) admits
  `LCMP` as `LongCompare` (I64/I64 → I32, signed ternary, not a subtract).
  [#157](https://github.com/gaoyu06/native-obfuscator/pull/157) admits
  `IF_ACMPEQ` / `IF_ACMPNE` as `ReferenceCompareBranch` (identity
  `==` / `!=`).
  [#158](https://github.com/gaoyu06/native-obfuscator/pull/158) admits
  `monitorenter` / `monitorexit` and synchronized methods (JNI
  `MonitorEnter` / `MonitorExit`, LIFO pairing check).
  [#159](https://github.com/gaoyu06/native-obfuscator/pull/159) admits
  preprocessor-lowerable `invokedynamic` (string concat, lambda
  metafactory, `ObjectMethods.bootstrap`) via a method copy.
  [#161](https://github.com/gaoyu06/native-obfuscator/pull/161) admits
  proven static `ConstantDynamic` `LDC` through a one-time cached
  resolver, plus raw `MethodHandle` / `MethodType` `LDC`. Unsafe condy
  shapes (non-static, varargs, interface-owner, malformed) stay
  reject-before-mutation. Unsupported methods fall back per-method.
  Rejected constructors are restored from the original class bytes so
  indy preprocessor markers are not left in output.
  [#146](https://github.com/gaoyu06/native-obfuscator/pull/146) admits
  prefix-local branches (every target still in the prefix) so a JEP 513-style
  prologue like `if (...) throw; super(...)` can split. Prefix + this/super
  stay in bytecode.
  [#160](https://github.com/gaoyu06/native-obfuscator/pull/160) admits
  prefix `ASTORE` into reference/array parameter slots.
  [#163](https://github.com/gaoyu06/native-obfuscator/pull/163) forwards
  definitely assigned prefix extra locals (reference + exact primitive
  carriers, including category-2 slots) through trailing hidden-bridge
  arguments.
  [#164](https://github.com/gaoyu06/native-obfuscator/pull/164) packs
  gapped extras and remaps suffix accesses onto those packed slots.
  `ASTORE 0`, a prefix branch into the suffix, try/catch across the
  split, multiple this/super, and conditionally assigned extras are
  still rejected. This is not a JDK 25 support badge and was not re-run
  as a Temurin 25 E2E.
- **Classfile versions.** Processed classes keep their input major version.
  Only classes older than Java 8 are raised to the Java 8 floor. Nest, record,
  and `PermittedSubclasses` attributes are no longer dropped by stamping 52.
- **JDK 17 IR runtime repair.** Lookup / class-loader / `link_call_site`
  markers are IR intrinsics. `ObjectMethods.bootstrap` accepts a third
  `TypeDescriptor` parameter. Signature-polymorphic `invoke` / `invokeExact`
  use caller-local trampolines. HotSpot JARs include
  `native0/hidden/Hidden0.class` for reverse-invoke helpers.
- **C++ SDK in generated JARs.** `NativePrimitives` (SHA-256, HMAC-SHA-256,
  AES-256-GCM, constant-time equality) and `NativeStrings` (length, hashCode,
  concat). AES preferred tip includes the 32-bit `plaintext.size+16` overflow
  fix. This is not a separately shipped SDK product.
- **E2E fixtures.** ClassicTest plus JDK 17 / 21 / 25 sample programs under
  `obfuscator/test_data/tests/`. Compiling a fixture with `javac --release 25`
  is not “JDK 25 supported.” The four-fixture IR-mode run in
  [#141](https://github.com/gaoyu06/native-obfuscator/pull/141) is one
  behavioral measurement (20/21 IR, one hybrid constructor, JEP 472 warning),
  not a support badge.
- **Harnesses.** `benchmarks/run.py` now runs JVM, `--codegen=legacy`, and
  `--codegen=ir` in one `:obfuscator:bench` invocation. JNI member-lookup
  caching remains on the legacy path.
- **Opt-in interpreter.** `--backend=interpreter` (default `cpp`) lowers a
  narrow slice to an opcode stream plus a C++17 `switch` dispatcher.
  ISA v2 is static `int`. ISA v3
  ([#140](https://github.com/gaoyu06/native-obfuscator/pull/140); Sol accept
  [#143](https://github.com/gaoyu06/native-obfuscator/pull/143)) adds an i64
  slice. ISA v4
  ([#148](https://github.com/gaoyu06/native-obfuscator/pull/148); Sol accept
  [#149](https://github.com/gaoyu06/native-obfuscator/pull/149)) adds a first
  reference slice (`ACONST_NULL`/`ALOAD`/`ASTORE`/`ARETURN`/`IFNULL`/`IFNONNULL`,
  parallel `jobject` slots, `execute_l`). [#150](https://github.com/gaoyu06/native-obfuscator/pull/150)
  (Sol accept [#151](https://github.com/gaoyu06/native-obfuscator/pull/151))
  adds `ATHROW` (52) and an ordered exception table (typed / catch-all;
  `/0` can transfer to a covering handler). Still static-only.
  `NEW`, invoke, and fields remain outside this backend.
- **Opt-in evaluator.** `--ir-lower=eval` (default `direct`) is consulted only
  when `--codegen=ir` successfully builds an `IrMethod`.
  [#137](https://github.com/gaoyu06/native-obfuscator/pull/137) (Sol
  accept-with-nits [#138](https://github.com/gaoyu06/native-obfuscator/pull/138))
  serializes a narrow i32/i64 slice to a method-data stream plus a C++17
  trampoline. [#139](https://github.com/gaoyu06/native-obfuscator/pull/139)
  (Sol accept [#142](https://github.com/gaoyu06/native-obfuscator/pull/142))
  wires `0x2b`/`0x2c` to phase-20 `LongDivRem`; try/catch around those ops
  still falls back.
  Sources are copied into generated `cpp/` only for this lowering.
- **JEP 472 packaging.** Output JARs now always write
  `Enable-Native-Access: ALL-UNNAMED` unless the input already set that
  attribute ([#145](https://github.com/gaoyu06/native-obfuscator/pull/145);
  Fable accept-with-nits
  [#147](https://github.com/gaoyu06/native-obfuscator/pull/147)). `java -jar`
  honors it; classpath launches still need
  `--enable-native-access=ALL-UNNAMED`. `System.load` is unchanged. This is
  not a JDK 25 support badge, and the #141 E2E warning was not re-run.
- **Zig.** `install-zig` and `--use-zig` from the pre-integration `master`.

默认仍是 `--codegen=legacy`、`--ir-lower=direct` 与 `--backend=cpp`。
IR、evaluator 与解释器都需显式打开。SDK 随生成 JAR 提供，不是独立产品。
classfile 不再无条件写成 major 52。

## Recorded measurements (do not invent more) / 已记录测量（勿编造）

Sources: `docs/benchmarks/ir-admission-phase18-corpus.md`,
`docs/architecture/ir-jdk17-runtime-fix.md`,
`docs/benchmarks/ir-jdk17-e2e-phase17.md`,
`docs/benchmarks/results-ir-eval-lower.md`,
`docs/benchmarks/results-ir-vs-legacy-master.md`,
`docs/benchmarks/results-ir-vs-legacy-phase19.md`,
`docs/benchmarks/ir-jdk17-e2e-corpus.md`,
`docs/benchmarks/ir-jdk25-e2e-corpus.md`.

| Measurement | Result | Must not be read as |
| --- | --- | --- |
| Phase-18 IR admission, ClassicTest corpus | 108 inventory, **108 IR**, 0 fallback | Full JVM coverage or a release gate |
| Phase-18 IR admission, five JDK 17 fixtures | 36 inventory, **36 IR**, 0 fallback | “JDK 17 supported” |
| Phase-18 IR admission, JDK 21 extra | 38 inventory, **36 IR**, 2 fallback (`ISTORE`/`ASTORE` local-type mismatches on `RecordPatternsE2E`) | JDK 21 support |
| IR-mode E2E of those five fixtures on phase 17 (#112) | 5/5 CMake, **0/5** native (crashes) | Anything other than “admission ≠ behavior” |
| Same five fixtures after the runtime repair (#115 / Sol rerun) | 5/5 stdout parity on one Linux x86-64 VM | Product JDK 17 support |
| Expanded JDK 17 IR E2E (#123) | 11/11 stdout parity, 82/82 IR admit, one Linux x86-64 VM (OpenJDK 21 host, `--release 17`) | “JDK 17 supported” |
| JDK 21 IR E2E (#126 via #129) | 6/6 stdout parity, 47/47 IR after local-type split, one Linux VM | “JDK 21 supported” |
| JDK 25 IR E2E (#141; Fable accept-with-nits #144) | 4/4 stdout parity on one Linux VM (host OpenJDK 21.0.10; Temurin 25.0.4.1+1 for compile/oracle/native). **20/21** IR; `FlexibleConstructorBodiesE2E` `Main$Validated.<init>(I)V` left in Java (control flow before `super(...)`, opcode 154). 0 legacy fallbacks. Every transformed run printed the JEP 472 `System::load` restricted-native-access warning. File: `ir-jdk25-e2e-corpus.md` | “JDK 25 supported” |
| Pre-phase-19 bench (#122) | 5 warmup / 10 samples; only `string-concat-hash` stayed fully IR; `integer-loop` LUSHR fallback; `recursion` mixed | Post-phase-19 IR timings. Kept as the pre-phase-19 record |
| Post-phase-19 bench (#132; Fable accept-with-nits #133) | Same harness on `76ebedd`; all three kernels stayed fully IR (four `// IR codegen:` markers; zero fallback log lines). File: `results-ir-vs-legacy-phase19.md` | A portable speedup or “native beats HotSpot” |
| Phase-20 focused tests (#134) | 97 `IrCompilerTest` + 5 `CodegenModeTest` = 102 | A complete compiler test suite |
| Interpreter ISA v4 focused+regression (#148; Sol accept #149) | 128 tests (2 option + 14 emitter + 1 runtime + 2 integration + 102 IR + 7 codegen). Sol re-ran 26/26 (omitted IrCompilerTest). Default-off `diff -r` of generated `cpp/` exited 0 | A production interpreter or object/exception coverage |
| Interpreter exception dispatch (#150; Sol accept #151) | 131 tests (22 interpreter/option + 109 IR/codegen). Sol re-ran 29/29. Runtime harness 61 checks. Default-off `diff -r` exited 0 | Complete catch/finally or instance methods |
| IR `LCMP` (#153; Fable accept #156) | 112 tests (`IrCompilerTest` 105 + `CodegenModeTest` 7). Fable re-ran 112/112. Compiled-and-executed long-compare harness included | Complete IR coverage or a default flip |
| IR `IF_ACMPEQ` / `IF_ACMPNE` (#157) | 114 tests (`IrCompilerTest` 107 + `CodegenModeTest` 7). Parent re-ran 114/114 including `executesReferenceCompareSemanticsWhenToolchainAvailable` | Complete IR coverage or a default flip |
| IR monitors / synchronized (#158) | 118 tests (`IrCompilerTest` 111 + `CodegenModeTest` 7). Parent re-ran 118/118 including `executesMonitorAndSynchronizedSemanticsWhenToolchainAvailable` | Complete IR coverage or a default flip |
| IR `invokedynamic` (#159) | 121 tests (`IrCompilerTest` 114 + `CodegenModeTest` 7). Parent re-ran 121/121 including `executesStringConcatIndyThroughIrWhenToolchainAvailable` | Complete indy/condy coverage or a default flip |
| Constructor prefix parameter `ASTORE` (#160) | 123 tests (`IrCompilerTest` 116 + `CodegenModeTest` 7). Parent re-ran 123/123 including `prefixReferenceParameterAstoreCompilesAndRunsWithJavaParity` | Remaining ctor-split rejects are gone |
| IR proven `ConstantDynamic` + raw MH/MT `LDC` (#161) | 128 tests (`IrCompilerTest` 121 + `CodegenModeTest` 7). Parent re-ran 128/128 including `executesStringConstantDynamicThroughIrWhenToolchainAvailable` and `executesRawMethodTypeLdcThroughIrWhenToolchainAvailable` | Complete condy coverage or a default flip |
| Constructor prefix extra locals (#163) | 133 tests (`IrCompilerTest` 126 + `CodegenModeTest` 7). Parent re-ran 133/133 including `prefixExtraReferenceLocalCompilesAndRunsWithJavaParity` | Remaining ctor-split rejects are gone |
| Gapped constructor prefix extras (#164) | 136 tests (`IrCompilerTest` 129 + `CodegenModeTest` 7). Parent re-ran 136/136 including `gappedPrefixExtraReferenceLocalCompilesAndRunsWithJavaParity` | Remaining ctor-split rejects are gone |
| Phase-18 focused tests (Sol + Fable) | 88 `IrCompilerTest` + 4 `CodegenModeTest` = 92 | A complete compiler test suite |
| Runtime-fix focused tests (Sol / Fable on #115) | 85 + 4 = 89 before later phase-18 tests were stacked | — |
| #53 eval-lower bench | Eval fell back; median **N/A** | Do not back-fill |
| Reader evals | `add` / `sumTo` / `subMul` / `mix` recovered on live IR and opcode artifacts | A passed “resist Sol-class recovery” bar |

Admission 不是行为正确性。五个 fixture 的 5/5 只是一台 Linux VM 上的记录。

## What did not land as compiler code / 未作为编译器代码落地

Old sibling evaluator PRs #42–#87 **conflict with the phase-18 + #124 line**
and must not be merged. The current-master port is #137 (landed). Their
reader/bench notes remain historical. Open drafts #9–#117 and #121 are the
pre-#118 stacked tips; merging them onto current `master` would regress the
tree. Close them as superseded, do not merge.

- Older opcode interpreter / compact encoding / link-only output, PRs #17–#28
  (superseded as a *first increment* by #124; those sibling flags are still
  not the current CLI)
- Standalone NativeStrings-on-master #27 (superseded by the SDK stack)

不要把旧 #42–#87 或 #17–#28 的 CLI 旗标当成当前功能。

## Defaults and policies that remain / 仍然有效的默认与政策

1. Do not flip `--codegen` off `legacy` (or `--ir-lower` off `direct`)
   until IR no longer needs per-method snippet fallback for the methods
   the product intends to support. The approved *destination* is then to
   flip the default to `ir` and delete the legacy path (D7).
2. Do not publish “supports JDK 17/21/25” from admission counts or five fixtures.
3. Do not claim a general native speedup versus HotSpot.
4. The old requirement-7 reader bar is historical and unmet. Do not
   launch another encoding-tweak reader. It is not the active goal.
5. Keep #53’s eval median as `N/A`.
6. Option A in older briefs is historical. The active goal is
   [current-goal.md](current-goal.md), not the eight-requirement write-up.

在 IR 覆盖完成前不要改默认 `legacy`。不要用接纳率或五个用例宣称 JDK
支持。#53 的 eval 中位数保持 `N/A`。现行目标尚未完成。

## Suggested next engineering / 后续工程

Active-goal work (IR admission, then default flip, then legacy deletion):

- Admit remaining IR leftovers so methods stop falling back:
  leftover constructor-split rejects (`ASTORE 0`, prefix→suffix branch,
  try/catch across the split, multiple this/super, conditionally
  assigned extras) and `jsr` / `ret` (reject-before-mutation is
  acceptable for obsolete subroutines). Unsafe condy shapes stay
  fail-closed. In-tree fixture admission
  ([#162](https://github.com/gaoyu06/native-obfuscator/pull/162),
  measured on post-#161 master) observed 0 leftovers; that is not
  coverage-complete.
- After coverage: reversible `--codegen` default flip to `ir`, soak,
  then delete `Snippets` / `cppsnippets.properties` / string-concat
  handlers.

Not a substitute for the active goal:

- Interpreter and evaluator remain default-off side paths.
- Human decisions in `human-decision-matrix.md` before any support badge.

## (a)(b)(c)(d) for this document / 本文发布问答

- **(a) Scope / 范围:** Status refresh after landing #164 (gapped ctor
  extras). / 落地 #164 之后的现状刷新。
- **(b) Ship-ready? / 可直接上线？** **No.** / **否。**
- **(c) Review / 是否需要审查？** Yes — check that no support badge
  leaked and that the CLI default was not flipped. /
  是，确认 README 没有写成产品支持，也没有改掉默认值。
- **(d) Preconditions / 前置条件:** Cite only committed measurement
  files; do not mark the new goal complete. /
  只引用已提交的测量文件；不要把新目标标成完成。
