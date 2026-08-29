# Project status on master / master 现状

Last updated after landing the JDK 25 IR E2E
[#141](https://github.com/gaoyu06/native-obfuscator/pull/141)
(Fable accept-with-nits
[#144](https://github.com/gaoyu06/native-obfuscator/pull/144))
on the post-[#140](https://github.com/gaoyu06/native-obfuscator/pull/140)
interpreter ISA v3 tree.
This page is the current public status. It does not complete the original
production goal and must not be read as a support matrix. The long
maintainer brief in [goal-status-and-options.md](goal-status-and-options.md)
now includes the pre-landing #108–#117 notes; it is still not this page.

本页是当前公开现状。原生产目标仍未完成，也不能当成支持矩阵。

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
  Unsupported methods fall back per-method. Rejected constructors are restored
  from the original class bytes so indy preprocessor markers are not left in
  output.
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
  narrow integer slice to an opcode stream plus a C++17 `switch` dispatcher.
  ISA v2 is static `int`. ISA v3
  ([#140](https://github.com/gaoyu06/native-obfuscator/pull/140); Sol accept
  [#143](https://github.com/gaoyu06/native-obfuscator/pull/143)) adds an i64
  slice (`LPUSH`/`LLOAD`/`LSTORE`, arith/bitwise/shift/`LNEG`/`LRETURN`,
  `LDIV`/`LREM` with the same `/0` and `MIN/-1` rules as IR phase 20).
  Objects and exception dispatch are still outside this backend.
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
| Phase-18 focused tests (Sol + Fable) | 88 `IrCompilerTest` + 4 `CodegenModeTest` = 92 | A complete compiler test suite |
| Runtime-fix focused tests (Sol / Fable on #115) | 85 + 4 = 89 before later phase-18 tests were stacked | — |
| #53 eval-lower bench | Eval fell back; median **N/A** | Do not back-fill |
| Reader evals | `add` / `sumTo` / `subMul` / `mix` recovered on live IR and opcode artifacts | A passed “resist Sol-class recovery” bar |

Admission 不是行为正确性。五个 fixture 的 5/5 只是一台 Linux VM 上的记录。

## What did not land as compiler code / 未作为编译器代码落地

Old sibling evaluator PRs #42–#87 **conflict with the phase-18 + #124 line**
and must not be merged. The current-master port is #137 (landed). Their
reader/bench notes remain historical.

- Older opcode interpreter / compact encoding / link-only output, PRs #17–#28
  (superseded as a *first increment* by #124; those sibling flags are still
  not the current CLI)
- Standalone NativeStrings-on-master #27 (superseded by the SDK stack)

不要把旧 #42–#87 或 #17–#28 的 CLI 旗标当成当前功能。

## Defaults and policies that remain / 仍然有效的默认与政策

1. Do not flip the default off `legacy` or `direct`.
2. Do not publish “supports JDK 17/21/25” from admission counts or five fixtures.
3. Do not claim a general native speedup versus HotSpot.
4. Do not treat reader “mix not recovered” on DCE as success. Requirement 7
   (resist unaided Sol-class recovery of critical logic) is **unmet**.
5. Keep #53’s eval median as `N/A`.
6. Option A in older briefs is a v1 *recommendation* only. The written
   production goal is unchanged and incomplete.

默认 `legacy` 不要改。不要用接纳率或五个用例宣称 JDK 支持。需求 7 未满足。
#53 的 eval 中位数保持 `N/A`。完整书面目标仍未完成。

## Suggested next engineering (not scheduled here) / 后续工程（此处不排期）

- Admit intra-prefix constructor control flow so JEP 513 prologues can split
  (`Main$Validated.<init>(I)V` leftover from #141).
- Document and/or emit a JEP 472 `--enable-native-access` / manifest story
  for generated `native0.Loader`. Not a JDK 25 support badge.
- Widen the interpreter to objects and exception dispatch.
- Human decisions in `human-decision-matrix.md` before any support badge.

## (a)(b)(c)(d) for this document / 本文发布问答

- **(a) Scope / 范围:** Status refresh after landing #141/#144 (JDK 25 IR
  E2E). / 落地 #141/#144 之后的现状刷新。
- **(b) Ship-ready? / 可直接上线？** **No.** / **否。**
- **(c) Review / 是否需要审查？** Yes — check that no support badge leaked
  into the README. / 是，确认 README 没有写成产品支持。
- **(d) Preconditions / 前置条件:** Cite only committed measurement files;
  keep `legacy` default; do not mark the production goal complete. /
  只引用已提交的测量文件；保持 `legacy`；不要把生产目标标成完成。
