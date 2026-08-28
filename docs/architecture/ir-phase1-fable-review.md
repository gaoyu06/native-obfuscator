# IR phase 1 — Fable design review of the Sol implementation

Reviewer: Claude Fable 5 (author of the IR phase-1 design).
Subject: `cursor/ir-compiler-phase1-6d81` — GPT-5.6 "Sol Extra High Fast"
implementation of the typed-CFG IR compiler slice.
Design of record: `docs/architecture/ir-compiler.md`, `ir-migration-plan.md`,
`ir-examples.md`. Status claims under review: `docs/architecture/ir-phase1-status.md`.

This is a compiler/transpiler review. `native-obfuscator` is a
Java-bytecode-to-C++ transpiler that re-expresses each method's bytecode as a
JNI function; the review is scoped to code generation correctness and fidelity,
nothing else.

---

## Verdict

**Request changes as originally submitted → Accept with nits after the one-line
fix included on this review branch.**

The implementation is a faithful, well-structured realization of the typed-CFG
design: the CFG, the block-local SSA construction, phi placement and edge
lowering, branch polarity, the safe per-method fallback, and the shared JNI
shell are all correct for the declared subset, and the default (legacy) path is
byte-for-byte identical to `master`. However, the slice as submitted shipped a
hard code-generation blocker: **the emitted C++ for the entire supported opcode
subset referenced an undefined type (`juint`) and did not compile.** The whole
point of the vertical slice is to produce working native code for `add`/`sumTo`,
and it did not. I patched it on this review branch (`juint` → `uint32_t`, the
carrier the legacy `IUSHR`/`LUSHR` snippets already use); with that fix the
generated code compiles and the slice is sound.

---

## What I verified, and how

All evidence below was produced from the implementation branch (plus my fix),
with JDK 21, `g++`, and `cmake` present in the environment.

| Check | Result |
| --- | --- |
| `./gradlew :obfuscator:compileTestJava :obfuscator:shadowJar` | BUILD SUCCESSFUL |
| `./gradlew :obfuscator:test --tests …CodegenModeTest --tests …IrCompilerTest` (as submitted) | **FAILED at executor startup** — "Failed to load JUnit Platform … including the JUnit Platform launcher" (reproduced the status doc exactly) |
| Same test command after adding `junit-platform-launcher` | 6/6 tests **pass** |
| Run the tool on an `add`/`sumTo`/`unsupported(Object)` fixture with `--codegen=ir` | `unsupported` falls back to legacy with the documented log; `add`/`sumTo`/`<clinit>` get IR bodies |
| `g++ -fsyntax-only` on the IR-generated `.cpp` (as submitted) | **compile error**: `'juint' was not declared in this scope` (in `add` and `sumTo`) |
| Same after the `juint` → `uint32_t` fix | **compiles cleanly** |
| Legacy (default) output: `master` jar vs this branch, same fixture | `diff -rq` reports the **whole tree identical** (per-class `.cpp/.hpp`, `string_pool.*`, hidden-method `.cpp`) |
| Sub/mul/`Integer.MIN_VALUE` fixture through IR | emits `(jint)((uint32_t)a - (uint32_t)b)`, `… * …`, folds the MIN_VALUE expression; compiles |

---

## Correctness assessment

### CFG construction — correct for the subset
`CfgBuilder` compacts to real opcodes, computes leaders from jump targets, jump
fall-throughs, and post-return positions, and wires successors (branch target +
fall-through for conditionals, target only for `goto`, next block otherwise). A
jump into a non-leader or off the end is rejected as unsupported. Constructs it
cannot model as normal edges (switches, `athrow`) get a nominally wrong
fall-through successor in the raw graph, but that graph is never lowered:
`AsmToIr.validateInstructions` runs over **every** block (reachable and dead)
before any lowering and rejects everything outside the subset, so those methods
fall back cleanly. I confirmed the whole-method validation by reading the pass
(it iterates `graph.getBlocks()`, not just the reachable set).

### Stack heights — correct
Heights are computed by a worklist abstract interpretation seeded at entry
height 0, with a per-opcode `stackDelta` that I checked matches the actual
push/pop performed in `lowerBlock` for every supported opcode (including the
`iinc` "emit a constant but don't push" case and the 2-slot pop for
`if_icmp*`). Underflow and mismatched merge heights are rejected as unsupported.
Note this **does not** seed from ASM stack-map frames as the design text
describes (design §7 pass 2); the home-grown height analysis is fine for the
int-only subset but is a documented deviation (see Fidelity).

### Phis — correct, if deliberately non-minimal
Local phis are gated by a proper definite-assignment dataflow: a local becomes a
phi only where it is defined on every incoming edge, and a read of a
maybe-undefined local (`iload`/`iinc`) is rejected. A back-edge into the entry
block is rejected (entry carries parameters directly and has no phis). Phi edge
lowering uses fresh `edgeN` temporaries for a **parallel copy**, which correctly
handles the loop back-edge and any phi permutation/cycle. I traced the emitted
`sumTo` end to end: slot→phi mapping, branch polarity (`i >= n` exits the loop),
and the back-edge updates (`s = s + i`, `i = i + 1`) are all runtime-correct.

The one imprecision: phis are materialized at *every* non-entry block, not only
at true merges, so single-predecessor blocks get trivial one-input phis (and
unchanged parameters like `n` get a redundant loop-header phi). This is correct
but verbose, and the status doc calls it "phis at every non-entry merge" when
the code means "every non-entry block."

### Fallback before mutation — correct
`IrMethodCompiler.processMethod` runs the frontend and the emitter to completion
**before** `MethodShellEmitter.beginIr` performs any mutation (native
registration entry, `ACC_NATIVE`, special-method pre/post-processing). Only
`UnsupportedIrConstructException` is caught for fallback, and it is only ever
thrown from the frontend before that mutation; internal invariant violations
throw `IllegalStateException` and remain hard failures rather than being
mislabeled as capability misses. I confirmed the clean fallback empirically: the
`unsupported(Object)` method logs the capability miss and is emitted by the
legacy generator, with the rest of the class emitted through the IR path.

### JNI method shell — correct and correctly shared
`MethodShellEmitter` is a verbatim, order-preserving extraction of the legacy
prologue/epilogue. Legacy-only concerns (`jvalue cstackN/clocalN` slot decls,
`LOCAL_LOAD_ARG_*`, catch dispatch) stay behind `legacyState`/`finishLegacy`,
and `finishIr` asserts no catch blocks exist. The byte-identity check above is
the proof that the extraction did not perturb the shipped legacy output.

### i32 wraparound — the blocker, now fixed
Integer `iadd`/`isub`/`imul` must wrap modulo 2³² with two's-complement
semantics and without C++ signed-overflow UB. The intent — cast operands to a
32-bit unsigned carrier, operate, cast back to `jint` — is right. The defect was
the chosen carrier: `juint` is a HotSpot-internal type, **not** part of `jni.h`
or the frozen runtime ABI, so `(jint)((juint)a + (juint)b)` does not compile.
The legacy path already uses `uint32_t` for its unsigned shifts
(`cppsnippets.properties` `IUSHR`/`LUSHR`). The fix switches the IR emitter to
`uint32_t`, which is exactly 32 bits on every JNI platform (`jint` is always
32-bit), is ABI-consistent, and preserves the wraparound. Verified by compiling
the regenerated output.

### Default-legacy byte identity — verified
Whole-tree `diff -rq` between `master`'s legacy output and this branch's default
output on the same fixture: identical. The `--codegen` flag defaults to
`legacy`, and the pre-existing `NativeObfuscator.process(...)` signature still
exists (a new trailing `CodegenMode` overload adds the opt-in), so neither CLI
nor API callers change behavior unless they opt in.

---

## Fidelity to the typed-CFG design

Good fidelity overall, with reasonable, mostly-documented simplifications:

- It is a **typed CFG in block-local SSA**, not global SSA and not snippet
  templates: IR bodies never call `Snippets.getSnippet` and never read
  `cppsnippets.properties`. Confirmed by inspection and by the emitted output.
- `IrType` is only `I32`/`REFERENCE`/`VOID`; `I64`/`F32`/`F64`, `RefType`
  descriptor/nullability, and the whole node zoo (fields, invokes, arrays,
  exceptions, caches) are absent. That matches the phase-1 vertical slice, which
  the design and status doc both scope down to.
- Deviations from the design prose that are acceptable but worth recording:
  (1) stack state is recomputed rather than seeded from ASM frames;
  (2) phis at every non-entry block rather than at merges;
  (3) the "package map" of §11 is collapsed into a compact node set.
  The status doc's "Adjustments" section already owns (2) and (3); (1) is not
  called out.

---

## Tests: are they real, did they run, what is missing

**Real:** yes. `IrCompilerTest` builds real ASM `MethodNode`s for `add` and
`sumTo`, asserts the textual IR (block count, header phis with two incomings,
`Branch` terminator) and asserts emitted-C++ substrings; it also asserts the
integrated path produces the existing JNI signature shape and contains no
`cstack`/`clocal`. `CodegenModeTest` checks the CLI default and `--codegen=ir`.
These are meaningful assertions, not placeholders.

**Did they run:** not via the standard command as submitted. `./gradlew test`
fails at executor startup under Gradle 9 because `junit-platform-launcher` is
absent from the test runtime classpath — I reproduced the exact error in the
status doc. The status doc is candid that the suite was instead exercised with a
hand-written launcher. I added the launcher dependency on this branch and all
six methods now pass through `./gradlew :obfuscator:test`.

**What is missing (the gap that let the blocker through):**

- **No compile check.** The emitter tests string-match the generated C++; none
  of them compile it. The design's own testing strategy (§10.3) calls for a
  compile-only smoke build of emitted code — that is exactly the missing layer,
  and its absence is why an undefined type shipped. Worse, `buildsAndEmitsAdd`
  *asserted the buggy `(juint) …` substring*, so the test actively locked in the
  defect.
- **Thin opcode coverage.** Only `iadd` is exercised end to end; `isub`/`imul`
  have no emit assertion (I checked them manually). No negative/`MIN_VALUE`
  literal test, no instance-method test (the `this`/reference-slot phi path is
  untested), no `bipush`/`sipush`/`ldc` emit assertion.
- **No automated parity.** The legacy byte-identity and any IR-vs-legacy
  differential parity are done by hand in the status doc, not encoded as tests,
  so nothing guards them in CI.

---

## Status-doc claims: what held, what was overstated

Mostly accurate and unusually honest. Verified true: legacy default; the extra
`CodegenMode` overload; the CFG/SSA/phi feature list; snippet-free IR bodies;
the shared shell; the safe fallback semantics; the JUnit-launcher failure; the
byte-identical default output; the fallback log wording.

Overstated / incomplete:

- The status doc frames the arithmetic `juint` cast as "preserving JVM 32-bit
  wraparound," but the emitted code did not compile at all, so no arithmetic ran.
  The doc's saving grace is that it explicitly states "**No CMake build, C++
  compiler … was run**" — the miss is disclosed, but the slice still shipped
  non-compiling code. (I corrected the doc's wording to `uint32_t`.)
- "phis at every non-entry merge" should read "every non-entry block."

---

## What I patched (small, in-scope, no IR-scope expansion)

1. `IrCppEmitter`: `juint` → `uint32_t` for the `iadd`/`isub`/`imul` operand
   casts (the correctness blocker). Updated the `IrCompilerTest` assertion and
   the one status-doc sentence to match.
2. `obfuscator/build.gradle`: add `testRuntimeOnly
   org.junit.platform:junit-platform-launcher:1.4.2` so the added unit tests can
   actually start under Gradle 9 (they were unrunnable via `./gradlew test`).

Both are committed on `cursor/ir-phase1-fable-review-6d81`. I did not add opcodes,
nodes, types, or passes.

---

## Recommended follow-ups (not blocking)

- Add the compile-only emitter smoke test the design already specifies, so IR
  output is proven to compile, not just string-matched.
- Type local phis from actual dataflow (or reject storing an `int` into a
  reference-typed slot). Today a method that overwrites the instance-method
  receiver slot with an `int` — legal but not javac-typical bytecode — would
  build a `REFERENCE` phi fed an `i32` value and emit non-compiling C++ instead
  of falling back. Not triggered by ordinary compiler output, and the path is
  opt-in, so it is a robustness nit, not a blocker.
- Broaden unit coverage to `isub`/`imul`, negative/`MIN_VALUE` literals, and at
  least one instance method; encode the legacy byte-identity and an IR-vs-legacy
  differential parity check as tests.

---

## (a)(b)(c)(d)

**(a) Verdict.** Request changes as submitted (the opt-in IR path emitted
C++ referencing the undefined type `juint` and did not compile). With the fix
included on this review branch, accept with nits.

**(b) Top issues.**
1. [Blocker, fixed] Undefined `juint` in IR arithmetic → the whole supported
   subset failed to compile; the emitter test even asserted the buggy string.
2. [Fixed] JUnit tests could not start under Gradle 9 (missing
   `junit-platform-launcher`); logic is otherwise correct (6/6 pass).
3. [Nit] No compile check on emitted C++; thin opcode coverage; parity checks
   are manual only.
4. [Nit] Local phi types come from a fixed method shape; storing an `int` into a
   reference slot would emit non-compiling C++ rather than fall back.

**(c) What I patched.** `IrCppEmitter` `juint` → `uint32_t` (plus its test and a
status-doc line); added `junit-platform-launcher` so the tests run. No IR-scope
expansion.

**(d) PR / compare URL.** See the pull request opened from
`cursor/ir-phase1-fable-review-6d81` (base `cursor/ir-compiler-phase1-6d81`);
compare link recorded in the PR description.

---

## (a)(b)(c)(d)（中文）

**（a）结论。** 按原样提交应「请求修改」：可选的 IR 代码生成路径发出的 C++ 引用了未定义类型
`juint`，无法编译。应用本评审分支中附带的修复后，为「基本通过，尚有小问题」。

**（b）主要问题。**
1. 【阻断，已修复】IR 整数运算发出未定义的 `juint`，导致整个受支持指令子集无法编译；发射器测试甚至断言了这个错误字符串。
2. 【已修复】在 Gradle 9 下 JUnit 测试无法启动（缺少 `junit-platform-launcher`）；除此之外测试逻辑正确（6/6 通过）。
3. 【小问题】没有对发出的 C++ 做编译校验；指令覆盖偏少；一致性（parity）校验仅为手工执行。
4. 【小问题】局部 phi 的类型来自固定的方法「形状」；若把 `int` 存入引用槽位，会发出无法编译的 C++ 而非回退到 legacy。

**（c）我修改了什么。** 将 `IrCppEmitter` 中的 `juint` 改为 `uint32_t`（并同步其单元测试与状态文档中的一句话）；
新增 `junit-platform-launcher` 以使单元测试可运行。未扩展 IR 的功能范围。

**（d）PR / 对比链接。** 见从 `cursor/ir-phase1-fable-review-6d81`（基线为
`cursor/ir-compiler-phase1-6d81`）发起的合并请求；对比链接记录在 PR 描述中。

---

## Verification commands (for reproduction)

```text
# builds
./gradlew :obfuscator:compileTestJava :obfuscator:shadowJar          # SUCCESS

# tests as submitted -> launcher failure; after adding the launcher -> 6/6 pass
./gradlew :obfuscator:test --tests by.radioegor146.CodegenModeTest \
                           --tests by.radioegor146.ir.IrCompilerTest

# generate IR output and compile it against real JNI headers
java -jar obfuscator/build/libs/obfuscator.jar example.jar out_ir --codegen=ir
g++ -std=c++17 -fsyntax-only -I<out_ir>/cpp \
    -I$JDK/include -I$JDK/include/linux <out_ir>/cpp/output/ex_Example_0.cpp
#   before fix: error: 'juint' was not declared in this scope
#   after  fix: compiles cleanly

# default-legacy byte identity
diff -rq <master-legacy-out> <branch-legacy-out>                     # identical
```
