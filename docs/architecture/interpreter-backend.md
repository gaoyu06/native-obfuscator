# Interpreter backend

## Status and scope

This document specifies an **optional second backend** for the transpiler: a
lowering path that turns selected JVM methods into a compact, portable
**opcode stream** plus side tables, executed in process by a small
interpreter written in C++ and shipped inside the already-generated native
library.

This is a compiler execution strategy — a project-owned bytecode VM / threaded
interpreter linked into the native library the tool already produces. It is
**not** a packer, **not** a third-party protector (VMProtect/Themida/OLLVM are
out of scope here), and it introduces **no** encrypted payload,
self-modification, executable-memory generation, anti-debugging behavior, or
process injection. The opcode stream is ordinary read-only data compiled into a
normal shared object; it is loaded and interpreted, never decrypted or executed
as machine code.

This is a design document. It changes documentation only and does not implement
the backend, the IR it consumes, or the runtime. Companion documents:

- [`interpreter-isa.md`](interpreter-isa.md) — the instruction set, encoding,
  constant pool, exception table, and invoke/JNI helper contracts.
- [`interpreter-eval-protocol.md`](interpreter-eval-protocol.md) — the
  software-quality evaluation methodology (structure-recovery from generated
  C++ source), demo method, and automated-reader protocol.

## Relationship to the production roadmap

The roadmap on `cursor/docs-production-roadmap-6d81`
(`docs/architecture/production-roadmap.md`) already reserves a slot for this
work: *"optional in-process interpreter backend"* that consumes the same
verified IR, emits *"compact, versioned instruction and metadata tables into the
normal native library,"* and is selected explicitly per build/method with the
selection recorded in the manifest. It also fixes several stances this document
adopts:

- **D6 — first backend.** Structured C++ first, interpreter second, over the
  same stable IR. This document agrees. Building the interpreter first would
  lock the IR to a one-opcode-at-a-time shape and would not prove the primary
  "real native code" path.
- **D16 — interpreter default.** Off by default; explicit per-build/per-method
  selection with a manifest entry and resource limits. This document agrees and
  makes the default-off requirement a hard integration constraint (existing
  tests stay on the direct path).
- **D17 — format stability.** The opcode/table format is versioned for
  rejection and diagnostics but kept internal and regenerated with every build.
  This document agrees and specifies the version word in the ISA.
- **JDK 17 baseline; typed CFG before global SSA.** Adopted as given.

### Where I disagree with, or sharpen, the roadmap

I am the ISA/backend designer; the following are deliberate positions, not
restatements.

1. **The interpreter must not be gated behind a *complete* IR.** The roadmap
   sequences the interpreter strictly after the IR and the direct-C++ vertical
   slice (D6), which I accept as the *default* order. But the interpreter needs
   far less of the IR than the optimizing C++ backend does: it needs typed
   values, basic blocks, ordered catch regions, and the explicit
   field/array/invoke/monitor operations — it does **not** need reducible
   control-flow recovery, structured loops, or SSA. I therefore recommend the
   IR expose a small **stable core subset** (§"IR → opcode emitter") that the
   interpreter can target as soon as it is frozen, so the interpreter can serve
   as a *differential oracle* for the C++ backend during that backend's
   development rather than only arriving afterward. This strengthens the
   roadmap's own differential-testing gate.

2. **The interpreter is the right home for the roadmap's `--backend=...`
   fallback, so its opcode coverage should be a strict superset of the C++
   backend's, not a subset.** The C++ backend may legitimately refuse
   irreducible control flow or exotic operations (roadmap: "irreducible graphs
   may use a documented block dispatcher"). The interpreter has no such
   structural constraints — it dispatches per instruction anyway — so it can and
   should cover *every* verifiable operation the front end accepts. That makes
   "compile with C++ where profitable, interpret the rest" a coherent policy
   instead of two partial backends.

3. **Do not describe the interpreter as an analysis-hardening feature.** The
   roadmap is careful here and so is this document: no performance or
   analysis-resistance claim is made. The interpreter's *measurable* properties
   are (a) it keeps method algorithms as **data** (an opcode array + constant
   indices) rather than as straight-line control-flow C++, and (b) it trades
   per-instruction dispatch cost for smaller, uniform generated C++. Whether (a)
   makes a method "harder to read" is an empirical question owned by
   [`interpreter-eval-protocol.md`](interpreter-eval-protocol.md), not a
   marketing claim.

4. **`invokedynamic` limitations are inherited, not solved, by interpreting.**
   On `master`, `IndyPreprocessor` rewrites indy and `InvokeDynamicHandler`
   throws if one reaches code generation; `LdcHandler` rejects
   `ConstantDynamic`. The interpreter changes none of this. Moving dispatch into
   a VM loop does not give us a call-site linker. See §"Correctness".

## How this fits the current pipeline

Today (`master`, `e7ca4c8`) a method is lowered like this:

- `MethodProcessor.processMethod` walks `method.instructions` linearly, keeps a
  numeric `stackPointer`, and appends one C++ statement per instruction.
- `GenericInstructionHandler` looks up the opcode name and substitutes tokens
  (`cstack$stackindex...`, `clocal$var`, `$trycatchhandler`, ...) from
  `cppsnippets.properties` through `Snippets`.
- Each generated JNI method declares one `jvalue` per stack slot (`cstackN`) and
  per local (`clocalN`), an `std::unordered_set<jobject> refs`, resolves
  classloader/class, lazily fills per-class pools (`cstrings[]`, `cclasses[]`,
  `cmethods[]`, `cfields[]`), and returns.
- Exceptions use `env->ExceptionCheck()` after risky ops and `goto` into catch
  dispatch blocks synthesized at the end of the method.
- `ClassSourceBuilder` registers the generated functions with
  `env->RegisterNatives`. Build is CMake (`CMAKE_CXX_STANDARD 17`) or
  `zig c++ -std=c++17`.

The interpreter backend reuses **all** of the runtime services above unchanged.
It only replaces the *body shape*: instead of N bespoke C++ statements, a
selected method's generated function becomes a fixed **trampoline** that calls
the interpreter with a pointer to that method's opcode stream and side tables.

```text
                          direct C++ backend (default, unchanged)
                        ┌───────────────────────────────────────────┐
verified IR (typed CFG) │  IR → per-instruction C++ (snippets today, │
      │                 │  structured C++ tomorrow) → RegisterNatives │
      │                 └───────────────────────────────────────────┘
      │
      │                  interpreter backend (optional, this doc)
      └───────────────► ┌───────────────────────────────────────────┐
                        │ IR → opcode emitter → { code[], consts[],   │
                        │   handlers[], exc_table[] } as static data  │
                        │ + trampoline function → RegisterNatives     │
                        │ + one shared interp_execute() in the lib    │
                        └───────────────────────────────────────────┘
```

### The generated trampoline

For a method selected for the interpreter, code generation emits a JNI function
with the *same* signature the direct backend would emit (so `RegisterNatives`,
hidden-method proxies, and the Java side are byte-for-byte compatible), whose
body marshals arguments into a slot array and calls the shared executor:

```cpp
// __ngen_<class>_<method>  (illustrative; generated)
jint JNICALL __ngen_pkg_Cls_algo(JNIEnv *env, jobject obj, jint arg0, jlong arg1) {
    native_jvm::interp::slot locals[/*maxLocals*/];
    locals[0].l = obj;                 // 'this'  (non-static)
    locals[1].i = arg0;
    locals[2].j = arg1;                // long occupies one 64-bit slot (see ISA)
    native_jvm::interp::frame fr;
    fr.method   = &M_pkg_Cls_algo;     // static description: code, consts, exc table
    fr.locals   = locals;
    fr.clazz    = clazz;               // resolved exactly as the direct backend does
    fr.classloader = classloader;
    return native_jvm::interp::execute_i(env, fr);  // typed entry per return kind
}
```

`M_pkg_Cls_algo` is a `const` aggregate initialized from static arrays emitted
into the same `.cpp` — see [`interpreter-isa.md`](interpreter-isa.md). The
per-class pools (`cstrings/cclasses/cmethods/cfields`) are shared with the
direct backend and referenced by index from the constant pool, so lazy
resolution, weak global refs, and the classloader lookup are identical.

### The shared executor lives in the runtime, not per method

`interp_execute` and the opcode handlers are added once to the runtime sources
(`native_jvm.hpp` / a new `native_jvm_interp.{hpp,cpp}` in
`src/main/resources/sources/`), compiled into every library regardless of
whether any method used the interpreter. When the interpreter is disabled, the
translation unit still compiles but is dead code the linker may drop; there is
**no** change to any currently generated method, which is what keeps the
existing test corpus on the direct path.

## Build and toolchain integration

The interpreter is a plain C++ translation unit and a set of `static const`
data arrays. It must compile under both existing flows with no new dependency:

- **CMake** (`CMAKE_CXX_STANDARD 17`, GCC ≥ 6, MSVC `/EHsc`, Clang/AppleClang).
  The new `native_jvm_interp.cpp` is added to `MAIN_FILES` in the generated
  `CMakeLists.txt` template. No new `find_package`, no new link libraries.
- **Zig** (`zig c++ -std=c++17 -O2 -shared`). `ZigBuilder.collectCppSources`
  already globs every `*.cpp` under the tree, so the new runtime file is picked
  up automatically; the per-target `jni_md.h` shim is unaffected.

Dispatch strategy is chosen to stay inside this portable envelope — see
§"Dispatch". The generated data arrays are `static const` (ideally `constexpr`)
so they land in `.rodata`, add no startup cost, and need no writable relocation.

### Portability constraints that shape the ISA

- **C++11 as the semantic floor, C++17 as the build reality.** Both build flows
  compile at C++17 today, but the interpreter core is written to a C++11 feature
  floor (fixed-width types, unions/`jvalue`, plain functions, no RTTI, no
  exceptions across the dispatch loop) so it can be built by the widest set of
  toolchains and so `-fno-exceptions`/`-fno-rtti` remain possible. C++17-only
  conveniences (`if constexpr`, inline variables) are allowed only where a
  C++11 fallback is trivial.
- **No compiler-specific dispatch is *required* for correctness.** Computed-goto
  ("labels-as-values") and tail-threading are optimizations selected behind
  feature macros; the portable `switch` dispatcher must always exist and must be
  the MSVC path. See §"Dispatch".
- **Endianness and alignment.** The opcode stream is byte-addressed with
  explicitly little-endian immediates read through `memcpy`, never through
  aliasing casts, so it is portable across the tool's targets and does not rely
  on unaligned access. (The stream is *not* a persisted artifact — it is
  regenerated each build per D17 — but keeping it endianness-defined avoids
  surprising a big-endian target and keeps golden tests stable.)

## Dispatch

The executor is a decode/execute loop over the opcode stream. Three
implementations are specified, selected at build time; all three are
behaviorally identical and are validated against each other in CI.

| Strategy | Portability | When used | Notes |
|---|---|---|---|
| `switch` over opcode byte | Every C++11 compiler incl. MSVC | **Default / required baseline** | One `while (true) { switch(code[pc++]) { ... } }`. Simple, debuggable, the MSVC path. |
| Computed goto (`&&label` + `goto *tab[op]`) | GCC/Clang extension | Opt-in `NJVM_INTERP_THREADED` when `__GNUC__` | Removes the switch range check and improves branch prediction. Purely an optimization. |
| Tail-threaded (one function per handler, `[[clang::musttail]]`/`[[gnu::musttail]]` continuation) | Clang (musttail), recent GCC | Opt-in `NJVM_INTERP_TAILCALL` | Each handler tail-calls the next; keeps `pc`/`sp` in registers. Requires guaranteed TCO, so it is compiler-gated and falls back to `switch`. |

Rationale for defaulting to `switch`:

- MSVC has no computed-goto and no guaranteed `musttail`; a portable baseline is
  mandatory because Windows is a first-class target (`x64-windows`,
  `arm64-windows`, `x86-windows`).
- The roadmap makes **no** performance claim for the interpreter, so we do not
  pay portability or correctness risk for dispatch speed by default. Threaded
  and tail-threaded modes exist so the *performance* evaluation lane can measure
  the dispatch overhead honestly, not because any speedup is promised.
- All three share the exact same handler bodies (the handler logic is written
  once as `static inline` functions or macros; the three modes differ only in
  how control reaches the next handler), so there is a single source of
  semantic truth.

The loop keeps `pc` (byte offset into `code[]`) and `sp` (operand-stack depth)
in locals; the operand stack and locals are `slot` arrays (`jvalue`-shaped) sized
from `maxStack`/`maxLocals`. Reference slots are tracked in the same `refs`
set the direct backend uses, so local-reference cleanup is identical.

## Selection policy

**Default: every method uses the direct C++ backend.** The interpreter is opt-in
only. This is the hard rule that keeps `ClassicTest` and the whole existing
corpus on the direct path with zero behavioral change (roadmap D16).

Selection is layered, most-specific-wins, and always recorded in the build
manifest:

1. **Global switch, default off:** `--backend=cpp` (default) or
   `--backend=interpreter` (route *all* eligible methods through the
   interpreter — used mainly for differential testing and code-size
   experiments).
2. **Per-method mapping file:** a checked `--backend-map <file>` using the same
   `class#name!desc` / wildcard grammar the existing black/white lists use
   (see `ClassMethodFilter`), e.g.

   ```text
   com/example/Crypto#mix!(JJ)J   interpreter
   com/example/Hot#loop!(I)I      cpp
   com/example/**                 cpp
   ```
3. **Annotation (optional, behind `-a`):** a future `@Interpret` /
   `@NoInterpret` pair analogous to the existing `@Native` / `@NotNative`, with
   the mapping file and CLI taking priority (matching today's "whitelist/
   blacklist has higher priority than annotations" rule).

### Automatic eligibility gate (advisory, never silent-on-by-default)

Even when a method is *requested* for the interpreter, the backend applies an
eligibility gate and records the decision. A method is routed to the
interpreter only if it is *requested* **and** *eligible*; otherwise the manifest
records why. Suggested signals, all cheap to compute from the IR/bytecode:

- **Size / compile-time pressure.** Very large methods (high bytecode count,
  large `maxStack`×`maxLocals`) are where the direct backend's one-`jvalue`-per-
  slot, one-statement-per-opcode expansion inflates generated C++ and native
  compile time the most. These are the natural interpreter candidates: the
  opcode stream grows ~linearly and uniformly and the C++ the compiler sees is
  the fixed executor, not N unique statements. This is a *code-size / build-time*
  argument, which the roadmap explicitly lists as an intended use ("compatibility
  fallback, code-size experiments, and differential testing").
- **C++-backend refusal.** If the (future) structured C++ backend refuses a
  method (irreducible control flow it declines to dispatch, or an operation it
  does not yet lower), and policy permits interpreter fallback, route it here
  instead of failing the build — recorded in the manifest per roadmap D8.
- **Source-pattern opacity (evaluation-gated, opt-in).** A method whose *point*
  is that its algorithm should read as data rather than as line-by-line C++
  control flow may be *requested* for the interpreter. The backend does **not**
  infer this automatically and makes **no** claim that it helps until
  [`interpreter-eval-protocol.md`](interpreter-eval-protocol.md) measures it on
  that corpus. Until then this signal is a user choice, not a heuristic.

### Ineligible (route to C++ or refuse, never silently miscompile)

- Methods containing `invokedynamic`/`ConstantDynamic` that survive
  preprocessing — same limitation as the direct backend; the front end must
  refuse with a class/method/offset diagnostic (see §"Correctness").
- Methods using `jsr`/`ret` or malformed/preview class files the verifier
  rejects — refused before any backend, exactly as the roadmap requires.

The default profile selects **no** methods for the interpreter, so a build with
no `--backend`/`--backend-map`/annotation input is bit-for-bit the current
direct-backend build for every currently supported input.

## Integration with the future IR

The interpreter and the direct C++ backend are two consumers of one **typed
CFG** IR (roadmap: block parameters carry incoming locals and operand-stack
values; ordered catch regions; explicit field/array/alloc/monitor/clinit/invoke/
type-check ops; Java integer/shift/division/float-NaN corner cases; JNI
reference lifetime facts; per-op class/method/offset/line provenance).

### IR → opcode emitter

The emitter is a straightforward, non-optimizing lowering — deliberately dumb,
because the interpreter wants *coverage and fidelity*, not cleverness:

1. **Linearize blocks.** Choose a deterministic block order (reverse-postorder)
   and concatenate their instruction lists into `code[]`. Block parameters
   become explicit slot moves at block boundaries (or are eliminated when the
   predecessor already leaves values in the right slots — an *optional* peephole,
   off by default so golden output is trivial to diff).
2. **Resolve intra-method control flow to byte offsets.** Every IR branch/switch
   target becomes a little-endian offset into `code[]`, patched in a second pass
   once block offsets are known. There is no `goto`; the loop just assigns `pc`.
3. **Lower each typed IR op to exactly one ISA opcode** (plus immediates). The
   ISA is close to JVM bytecode but *typed and pre-resolved*: constant-pool
   references, field/method/class references, and array-kind selectors are
   emitted as indices into the per-class pools the direct backend already builds
   (via `NodeCache`/`CachedFieldInfo`/`CachedMethodInfo`). No name resolution
   happens at run time beyond the existing lazy pool fill.
4. **Emit the exception table** from the IR's ordered catch regions:
   `[start_pc, end_pc, handler_pc, class_pool_index_or_ANY]` rows, in source
   order (see ISA). The interpreter's throw path scans this table exactly as the
   JVM specifies, replacing the direct backend's synthesized `goto` catch blocks.
5. **Carry provenance side tables** (debug builds only): `pc → (bytecode offset,
   line)`, so a debug build can map an interpreter PC back to the original
   method, satisfying the roadmap's "debug builds can map interpreter PCs back to
   bytecode offsets."

Because the emitter consumes the same IR as the C++ backend and touches the same
effect annotations (`pure`, `may_throw`, `may_allocate`, `jni_call`,
`synchronizes`), the two backends cannot disagree about *what* an operation does
— only about *how* it is executed. That is exactly the property that makes the
interpreter a valid differential oracle.

### Minimum IR surface the interpreter needs

To let the interpreter come online as early as possible (my disagreement #1
above), the IR should expose a documented **core subset** first: typed values
(`i32/i64/f32/f64/ref`), basic blocks with successors and *ordered catch
regions*, and the explicit runtime ops (field/array/alloc/monitor/clinit/invoke/
typecheck/throw). The interpreter needs none of: reducible-CFG recovery,
structured loops, SSA/phi placement, or scalar-region extraction. Freezing that
core subset is the only IR precondition for building the interpreter.

## Correctness

The interpreter must reproduce **the same observable behavior** as the direct
backend and as reference Java, judged by the roadmap's oracle (return value,
thrown type/message where contractual, state changes, stdout, exit). Concrete
obligations:

- **Operand stack & locals.** Category-2 values (`long`/`double`) follow the JVM
  model. The ISA stores each value in one 64-bit `slot` for interpreter
  simplicity, but the emitter preserves JVM stack *arithmetic* (a `long` still
  reserves the verifier's two words for frame-size and `dup2`/`pop2` purposes);
  the ISA documents the slot mapping precisely so `dup_x2`, `pop2`, and wide
  loads/stores are unambiguous. See [`interpreter-isa.md`](interpreter-isa.md).
- **Integer/float corner cases.** `idiv`/`irem`/`ldiv`/`lrem` `INT_MIN / -1` and
  divide-by-zero, shift masking (`& 0x1f` / `& 0x3f`), `iushr`/`lushr` unsigned
  semantics, `fcmpl`/`fcmpg`/`dcmpl`/`dcmpg` NaN direction, and narrowing casts
  are implemented by reusing the *exact expressions* the direct backend uses in
  `cppsnippets.properties` — the handlers are literally the same C++, so there
  is one implementation of these semantics, not two.
- **Monitors.** `monitorenter`/`monitorexit` call `env->MonitorEnter/Exit` with
  the same null check. Because the interpreter loop can unwind through multiple
  frames on exception, the executor must release monitors acquired within a
  frame during abrupt completion; the ISA reserves this as an explicit
  per-frame monitor list rather than relying on synchronized-method magic. This
  is a place the direct backend is *simpler* (structured C++ scope), so the
  interpreter must be tested specifically for monitor release on exception.
- **Exceptions.** After any `may_throw` op the loop checks
  `env->ExceptionCheck()`; on a pending exception it consults the frame's
  exception table (source order, `IsInstanceOf` per row, `ANY` catch-all), sets
  `pc` to the handler and pushes the exception object, or returns the zero value
  of the return kind if no handler matches — identical to the direct backend's
  `TRYCATCH_*` snippet logic, just table-driven instead of `goto`-driven.
- **JNI reference lifetime.** The `refs` set and `utils::clear_refs` are reused
  verbatim; every opcode that produces a local ref inserts it, exactly as the
  snippets do. The executor must bound frame-local reference growth the same way
  the direct backend does (this is also why the roadmap flags fixed local-ref
  limits as *not* universal — the interpreter inherits whatever policy the
  runtime uses).
- **`invokedynamic` / `ConstantDynamic`: unchanged limitation.** The interpreter
  does **not** add a call-site linker. Indy is still preprocessed away by
  `IndyPreprocessor` where possible; anything that would reach the emitter as a
  live indy/condy is refused at the front end with a class/method/offset
  diagnostic, the same fail-closed behavior the direct backend needs. Moving
  execution into a loop does not change what we can link. (`USE_HOTSPOT` builds
  keep the existing `link_call_site` path for the preprocessed cases; the
  interpreter reuses it, it does not reimplement it.)
- **Class initialization & invoke.** `invoke*`/`getstatic`/`putstatic`/`new`
  trigger the same JVM class-init semantics because they go through the same JNI
  calls the direct backend uses; the interpreter does not cache or reorder them
  across the effect barriers the IR declares.

Correctness is proven, not assumed: the interpreter is added to the roadmap's
differential harness as a third execution mode (Java / C++ / interpreter) over
the same corpus and oracle, and the three dispatch implementations are
cross-checked against each other.

## Non-goals

- No performance claim. Dispatch overhead is expected; it is measured, not sold.
- No analysis-resistance claim. See the eval protocol for what is actually
  measured and how.
- No packing, encryption, self-modification, executable-memory generation, or
  anti-analysis behavior of any kind.
- No new Java-visible surface: the trampoline's JNI signature and
  `RegisterNatives` wiring are identical to the direct backend's, so the output
  JAR and loader are unchanged.

## Human decisions this backend needs

These are the decisions a person must make before the interpreter ships; they
map onto the roadmap's decision matrix (`human-decision-matrix.md`).

1. **D16 default & scope.** Confirm interpreter stays off by default and agree
   the selection grammar (`--backend`, `--backend-map`, annotations). Owner:
   compiler lead.
2. **D17 format versioning.** Confirm the opcode/table format is internal and
   regenerated per build (no external stability contract) and approve the ISA
   version word. Owner: compiler lead.
3. **D8 unsupported-method policy for the interpreter path.** Decide whether a
   C++-backend refusal may *fall back* to the interpreter automatically or must
   be an explicit opt-in, and how it is surfaced in the manifest. Owner:
   compiler + release.
4. **IR core-subset freeze.** Approve exposing the minimal typed-CFG subset the
   interpreter needs (my disagreement #1) so it can be built as a differential
   oracle rather than strictly last. Owner: IR owner.
5. **Dispatch build matrix.** Approve which of `switch` / computed-goto /
   tail-threaded modes are built and tested on which tier-1 targets (ties into
   roadmap D18). Owner: build/release.
6. **Monitor-on-exception test bar.** Accept the specific correctness fixtures
   for monitor release during interpreter unwinding before enabling the backend
   on any synchronized-heavy corpus. Owner: correctness.
7. **Whether opacity is even a supported use.** Decide if "keep the algorithm as
   data" is an advertised use of the interpreter, which is gated on the eval
   protocol producing measured evidence first (D21/D22). Owner: product +
   security. Until then it is an experimental, unadvertised property.
