# IR Compiler Architecture

> **Current tree:** the typed CFG IR and structured C++/JNI emitter described
> here are on `master` as `--codegen=ir` (default remains `legacy`). Read
> [project-status.md](project-status.md) and [ir-phase18-status.md](ir-phase18-status.md)
> for what actually landed. This file began as a design proposal; later
> “not implemented yet” sentences below are historical.

Scope: replace the string-template snippet concatenation that currently turns JVM
bytecode into C++ with a real, well-typed intermediate representation (IR) and a
structured C/C++ emitter. CMake and Zig remain the native backends unchanged.

Audience: contributors to `native-obfuscator` who know the current transpiler but
not the proposed IR.

---

## 1. What the tool does today (the thing we are replacing)

`native-obfuscator` is a **Java-bytecode-to-C++ transpiler**. It reads a `.jar`,
and for each selected method it emits a JNI function whose body reproduces the
method's bytecode semantics by calling back into the JVM through `JNIEnv`. The
original method is turned into a `native` method (or a proxy), so at runtime the
JVM dispatches into the generated shared library instead of interpreting bytecode.

The pipeline, as it exists in `obfuscator/src/main/java/by/radioegor146`:

1. **Load + filter** — `NativeObfuscator.process(...)` streams jar entries, parses
   each class with ASM into a `ClassNode`, and applies `ClassMethodFilter`
   (black/white list + `@Native`/`@NotNative` annotations).
2. **Bytecode preprocessing** — `bytecode/PreprocessorRunner` runs
   `IndyPreprocessor` (lowers `invokedynamic` into ordinary bytecode plus "magic
   marker" static calls such as lookup / classloader / `link_call_site` /
   `invoke_reverse`) and `LdcPreprocessor` (lowers `LDC` of `Handle`/method `Type`
   constants). The class is then re-serialized with
   `ClassWriter.COMPUTE_MAXS | COMPUTE_FRAMES` and re-read, so stack map frames and
   `maxStack`/`maxLocals` are authoritative before codegen.
3. **Per-method codegen** — `MethodProcessor.processMethod(MethodContext)` is the
   single choke point. It:
   - picks a `SpecialMethodProcessor` (`<init>` skipped, `<clinit>` proxied via
     `ClInitSpecialMethodProcessor`, everything else via
     `DefaultSpecialMethodProcessor`);
   - emits the JNI function signature and a fixed **prologue** (resolve `clazz`,
     `classloader`, `lookup`, cache try-catch classes, declare
     `jvalue cstack0..N` and `jvalue clocal0..N`, declare
     `std::unordered_set<jobject> refs`, load arguments into locals);
   - walks `method.instructions` and, for each node, dispatches to an
     `InstructionTypeHandler`. Most handlers extend `GenericInstructionHandler`,
     which fills a `props` map and calls `Snippets.getSnippet(name, props)` to load
     a template from `sources/cppsnippets.properties` and substitute tokens into
     `MethodContext.output` (a `StringBuilder`);
   - emits a **catch-dispatch epilogue** (`L_CATCH_*` labels) from
     `context.catches`.
4. **Class assembly** — `source/ClassSourceBuilder` writes the per-class `.cpp/.hpp`
   (cache arrays `cstrings/cclasses/cmethods/cfields`, `__ngen_register_methods`),
   `CMakeFilesBuilder` builds `CMakeLists.txt`, `MainSourceBuilder` writes
   `native_jvm_output.cpp`, and `source/StringPool` emits the modified-UTF-8 blob.
5. **Runtime** — `resources/sources/native_jvm.{cpp,hpp}` provides `utils::*`
   helpers (`throw_re`, `find_class_wo_static`, `get_lookup`, `clear_refs`,
   multidim array creation, etc.). `string_pool.{cpp,hpp}` backs the pooled
   strings.
6. **Build** — CMake, or `zig/ZigBuilder` when `--use-zig` is set.

### 1.1 The operand-stack model in use today

The current codegen is **not** an SSA compiler and **not** a general
stack-machine interpreter. It is a *statically stack-numbered* transpiler:

- `MethodContext.stackPointer` is a compile-time integer tracking the operand
  stack depth. Each handler reports how the depth changes via
  `getNewStackPointer(node, currentStackPointer)`
  (`instructions/InsnHandler.java`, `VarHandler.java`, etc.).
- Snippets refer to slots relative to the current depth: `GenericInstructionHandler`
  precomputes `stackindex0`, `stackindexm1` … `stackindexm5` and `stackindex1..5`
  (`context.stackPointer + i`), and the templates index fixed
  `jvalue cstack<N>` C++ variables. Example: `IADD` is
  `cstack$stackindexm2.i = cstack$stackindexm2.i + cstack$stackindexm1.i;`.
- Longs/doubles occupy two slots, mirrored in the `getNewStackPointer` arithmetic
  and in snippets like `LSTORE`/`DADD`.
- Locals are `jvalue clocal<N>`; a `jvalue` is a union accessed as `.i/.j/.f/.d/.l`.

So there is already a crude "stack elimination": operand positions are mapped to
named C++ variables at compile time. What is missing is everything a real
compiler gives you — types on values, a control-flow graph, dataflow, and the
ability to reason across instructions.

### 1.2 Concrete pain points that motivate an IR

These are not hypothetical; they are direct consequences of the snippet design.

- **No value types.** Everything is a `jvalue` union; correctness depends entirely
  on javac/ASM having produced valid frames. A wrong `.i` vs `.j` in a template is
  a silent miscompile. `props`-map substitution has no type checking at all.
- **JNI on every operation.** Every field/method/array/object op is a `JNIEnv`
  call, and every produced reference is inserted into `refs` and bulk-freed by
  `utils::clear_refs`. The README itself warns the output is much slower. There is
  no place to *prove* a check is redundant, because there is no dataflow.
- **Exception handling is textual.** After each possibly-throwing op the handler
  appends `$trycatchhandler` — an `if (env->ExceptionCheck()) goto L_CATCH_n;`
  string. Try-catch class caching is a hand-written mutex/weak-ref snippet copied
  into `MethodHandler`, `FieldHandler`, `LdcHandler`, and `MethodProcessor`.
  Exception *edges* are implicit in emitted text, not a structure any pass can see.
- **Redundant work.** The same class-cache init block is emitted before every
  `INVOKE*`, `GET/PUTFIELD`, `GET/PUTSTATIC`, and `LDC class`, even when the class
  was resolved two instructions earlier. Nothing can hoist or dedup it because
  there is no IR to run a pass over.
- **Templates are hard to extend and unsafe.** `cppsnippets.properties` is ~575
  lines of C++ fragments keyed by opcode-and-sort (`INVOKEVIRTUAL_9`,
  `GETFIELD_5`, …). Adding an optimization, a new target, or a new type rule means
  editing string fragments by hand. `Util.dynamicRawFormat` is a regex replace.
- **No unit-testable middle.** The only tests are end-to-end
  (`ClassicTest` compiles → transpiles → compiles C++ → diffs stdout). There is no
  way to assert "this bytecode lowers to this IR" without a full native toolchain.

An IR fixes all of the above by inserting a typed, inspectable data structure
between "ASM tree" and "emitted C++", and by moving every codegen decision from
string templates into passes and a structured emitter.

---

## 2. Design goals and non-goals

**Goals**

1. A typed IR that makes illegal states unrepresentable where practical (a `jint`
   add cannot silently consume a reference).
2. Preserve exact observable behavior. The acceptance bar is the existing
   `ClassicTest`: for every test and every `Platform`, IR-generated output must
   produce byte-identical stdout to the ideal run (already how the suite works).
3. Coexistence: the legacy snippet path and the new IR path live behind a flag and
   both stay green in CI throughout the migration.
4. A clean seam for later backends (direct C++ for hot ops; a compact opcode
   stream + tiny interpreter for selected methods — hook only, not implemented).
5. Passes that can *remove* JNI transitions and redundant checks — the main
   performance lever the current design cannot pull.

**Non-goals (for the compiler itself)**

- Not a general-purpose optimizing compiler. We optimize what matters for JNI
  transpilation (checks, caches, ref lifetime), not e.g. auto-vectorization.
- Not a protector/packer. This document describes a transpiler only; no
  anti-analysis, no encryption, no obfuscation of the emitted C++.
- No change to the `.jar` rewriting, loader injection, hidden-method proxying, or
  the runtime `utils::*` ABI in the first phases. The IR reuses them verbatim.

---

## 3. Chosen IR flavor: typed CFG in block-local SSA (a hybrid), justified

Three candidates were considered against *this* codebase:

| Option | Fit | Verdict |
| --- | --- | --- |
| Keep a stack-machine IR (just formalize `cstackN` slots) | Trivial from today's code | **Rejected.** It preserves the core defect: no dataflow, so no check/cache/ref elimination, and types stay implicit. |
| Full global pruned SSA (dominance frontiers, global value numbering) | Powerful | **Deferred.** More machinery than needed for phase 1, and object-reference lifetime (the `refs` set) needs liveness we can compute more simply block-locally first. Global SSA is a later, optional pass. |
| **Typed CFG + block-local SSA with explicit phi at merges (hybrid)** | Matches the frames/stack model already present | **Chosen.** |

### 3.1 Why the hybrid fits this codebase specifically

- **The frontend is nearly free.** ASM already computes stack map frames and
  `maxStack/maxLocals` (`NativeObfuscator` re-reads with `COMPUTE_FRAMES`). We can
  drive an abstract interpretation over the operand stack per basic block and turn
  every push into an SSA value definition and every consume into a value use. The
  existing `getNewStackPointer` logic is exactly the stack-effect table this
  simulation needs, so we are formalizing knowledge the code already encodes.
- **Hybrid node granularity matches the two worlds we straddle.** Arithmetic,
  comparisons, constants, conversions, local moves — these are already pure C++ in
  the snippets (`IADD`, `I2L`, `LCMP`) and become *low-level* IR nodes emitted
  without JNI. Field/method/array/object/monitor/throw ops are *high-level* IR
  nodes that lower to `JNIEnv` calls plus a resolved cache slot. One IR with two
  node tiers keeps both readable and lets the emitter pick "direct C++" vs "JNI".
- **References need lifetimes, and SSA gives us liveness cheaply.** The `refs` set
  + `clear_refs` is a conservative "free everything at the end" scheme. With SSA
  values carrying a `Ref` type, a liveness pass computes the *last use* of each
  reference and inserts `DeleteLocalRef` precisely — the JNI-ref-minimization win.
- **Phi at merges, not global SSA, because control flow is already goto-based.**
  The emitter targets C++ with `goto` and labels (today's `LabelPool`), so we do
  not need a relooper/structurizer. Block-local SSA with phi nodes maps directly:
  a phi becomes "assign the merged slot on each incoming edge." This is the
  minimum SSA that still enables the passes we want.

### 3.2 Shape of the IR

```
IrMethod
 ├─ signature (owner, name, desc, isStatic, returnType, paramTypes)
 ├─ params: List<IrValue>            // includes receiver for instance methods
 ├─ entryBlock: IrBlock
 └─ blocks: List<IrBlock>            // reverse-postorder

IrBlock
 ├─ label: BlockId
 ├─ params: List<IrPhi>             // block-local SSA merge points
 ├─ body: List<IrInstruction>      // straight-line, each defines 0..1 IrValue
 ├─ terminator: IrTerminator       // branch / switch / return / throw / goto
 └─ exceptionEdges: List<ExceptionEdge>   // ordered handlers covering this block

IrValue
 ├─ id
 ├─ type: IrType
 └─ (defining instruction or block-param)
```

- **SSA discipline:** every `IrValue` has exactly one definition. Merges use
  `IrPhi` block parameters. This is "basic-block-argument" SSA (à la MLIR/Swift
  SIL), which is easier to emit to `goto` C++ than classic phi placement.
- **Reverse-postorder** block ordering makes emission and most passes single-pass.

---

## 4. Type system

The JVM verifier collapses `boolean/byte/char/short/int` to `int` on the operand
stack; the current code mirrors that with `TYPE_TO_STACK`/`CPP_TYPES` in
`MethodProcessor`. The IR keeps a small primitive lattice plus a richer reference
type.

### 4.1 Value types (`by.radioegor146.ir.type.IrType`)

| IrType | JVM sorts covered | C++ carrier |
| --- | --- | --- |
| `I32` | boolean, byte, char, short, int | `jint` |
| `I64` | long | `jlong` |
| `F32` | float | `jfloat` |
| `F64` | double | `jdouble` |
| `Ref` | object, array, null | `jobject` (+ subtype tags below) |
| `Void` | void return | — |
| `ReturnAddress` | `jsr`/`ret` | (unsupported; see 4.4) |

`I32` deliberately merges the sub-int types (matching the verifier and the
existing `.i` union access). Narrowing to byte/char/short is represented by
explicit `Truncate` nodes (today's `I2B`/`I2C`/`I2S`), so the width information is
never lost even though the carrier is `jint`.

### 4.2 Reference subtyping and nullability

`Ref` carries two pieces of refinement metadata, both optional and monotone:

- **Descriptor tag**: internal name (`java/lang/String`) or array descriptor
  (`[I`, `[Ljava/lang/Object;`), when statically known. Drives JNI call/field
  variant selection, `CHECKCAST`/`INSTANCEOF`, and array-element typing. Unknown =
  `java/lang/Object` (matching `MethodHandler.simplifyType`).
- **Nullability**: a 3-point lattice `Null ⊑ MaybeNull ⊑ NonNull` (top =
  `MaybeNull`). Sources: `ACONST_NULL` ⇒ `Null`; `NEW`/`NEWARRAY`/`LDC string`/
  the receiver of an instance method ⇒ `NonNull`; a value that survived a
  successful `IFNONNULL`/`CHECKCAST`/null-check ⇒ `NonNull` on the guarded edge.
  This lattice is what the null-check-elimination pass consumes.

Nullability is an *optimization hint only*; dropping it must never change
behavior, only remove provably-dead checks.

### 4.3 Typing rules and verification

An `IrVerifier` (debug/CI only) checks: every use's type matches its def; phi
inputs are type-compatible with the phi; terminator targets exist; every
`may-throw` node has an exception-edge set; SSA single-definition holds. This is
the type safety the `props` map never had. It runs under an assertion flag and in
tests, not in production runs.

### 4.4 Edge cases called out

- **`jsr`/`ret`**: not in the current `Util` opcode map and not emitted by modern
  javac. The frontend runs ASM's `JSRInlinerAdapter` during preprocessing so the
  IR never sees subroutines. Documented as "inlined away", not supported natively.
- **Uninitialized `this` / `NEW`+`<init>`**: `<init>` methods are skipped entirely
  today (`MethodProcessor.shouldProcess`). The `NEW` snippet uses `AllocObject` and
  the constructor is invoked separately. The IR preserves this exactly: `New` is a
  node producing a `NonNull Ref`; `INVOKESPECIAL <init>` is a normal invoke node.

---

## 5. Exception model

Today an exception path is a string (`$trycatchhandler`) appended after each
throwing op, plus `L_CATCH_*` blocks generated from `MethodContext.catches`
(`CatchesBlock`). In the IR, exceptions are **first-class control-flow edges.**

- Each basic block records the ordered list of try-catch handlers covering it
  (from `method.tryCatchBlocks`, exactly as `GenericInstructionHandler` computes
  `CatchesBlock` today, but stored on the block, not recomputed per instruction).
- Every node that can throw (`may-throw`: array access, field/method access, `new`,
  `checkcast`, `athrow`, division, monitor ops, and every JNI call) has an implicit
  exceptional successor: the block's handler dispatch. This models the JNI
  contract "check `env->ExceptionCheck()` after the call" as an edge, not text.
- A **catch/landing block** begins with a special `CaughtException` value (the
  current `cstack0.l = env->ExceptionOccurred()` step). Handler selection is an
  ordered `InstanceOf` chain terminating in "rethrow to caller" — the same logic
  as `TRYCATCH_CHECK_STACK` / `TRYCATCH_END_STACK`, but as IR.
- The **exception dispatch** for a block is materialized once per distinct handler
  set (matching how `context.catches` dedups `CatchesBlock` keys), so we do not
  duplicate dispatch code per throwing instruction.

Benefit: a pass can now see that, e.g., an `IADD` cannot throw and needs no edge,
that a call to a callee proven not to throw needs no `ExceptionCheck`, or that two
adjacent throwing ops can share one check. None of that is expressible today.

---

## 6. JNI object model

This is the part most specific to this project. The IR must faithfully model the
JVM-via-JNI execution the runtime performs.

### 6.1 Environment values

Well-known values available to every method body, mirroring the prologue in
`MethodProcessor.processMethod`:

- `env` (`JNIEnv*`) — implicit, threaded to every JNI op by the emitter.
- `clazz` (`jclass`) — resolved via `find_class_wo_static` for instance methods,
  the `jclass` parameter for static ones.
- `classloader` (`jobject`) — from `get_classloader_from_class`.
- `lookup` (`jobject`) — lazily created (`utils::get_lookup`), matching
  `MethodHandler`'s `isLookupLocal` handling.

These correspond to the "magic marker" intrinsics
(`PreprocessorUtils.LOOKUP_LOCAL`, `CLASSLOADER_LOCAL`, `CLASS_LOCAL`). In the IR
they are `EnvIntrinsic` nodes rather than fake method calls, so the frontend
recognizes them structurally instead of by owner-string matching.

### 6.2 Local reference lifetime

- Any `Ref`-producing op that corresponds to a JNI call returning a local ref
  (`GetObjectField`, `CallObjectMethod`, `NewObjectArray`, `AALOAD`, …) is marked
  as producing an **owned local reference**.
- Today these are all inserted into `std::unordered_set<jobject> refs` and freed in
  bulk by `utils::clear_refs`. The IR keeps that as the *default lowering* for
  phase-1 parity, then a `LocalRefLiveness` pass replaces bulk cleanup with
  precise `DeleteLocalRef` at each reference's last use (§7.8). Values that escape
  (returned, stored to a field/array, passed to a call) are excluded from early
  deletion — the pass uses the def-use graph SSA gives us.

### 6.3 Constant caches as IR entities

The four `NodeCache` pools (`cstrings`, `cclasses`, `cmethods`, `cfields`) become
typed IR references:

- `StringConst(value)` → interned global-ref slot (today's `cstrings[i]`).
- `ClassRef(internalName)` → lazily-initialized weak-global-ref slot with the
  mutex-guarded init block (today's `cclasses[i]` + `cclasses_mtx[i]`).
- `MethodRef(owner,name,desc,static)` → `jmethodID` slot (today's `cmethods[i]`).
- `FieldRef(owner,name,desc,static)` → `jfieldID` slot (today's `cfields[i]`).

Dedup within a method already happens via `NodeCache.getId`. Modeling them as
nodes lets a `CacheMaterialization` pass (§7.5) decide *where* to emit the lazy
init (hoist to first dominating use instead of before every access) and drop
repeated `ClassRef` inits — the single biggest code-size and speed win available.

The per-class cache *arrays* and `__ngen_register_methods` (in
`ClassSourceBuilder`) are unchanged; the IR only changes how/where slot accesses
are emitted inside method bodies.

---

## 7. Pass pipeline

Passes run in this order under a `PassManager`. Each pass is pure over the
`IrMethod` (mutates in place with a clear pre/postcondition). Passes marked
*(phase 1)* are required for behavioral parity; *(opt)* are performance/quality
and can land later without changing output correctness.

1. **CFG construction** *(phase 1)* — compute basic-block leaders (jump targets,
   handler starts, instructions after a branch/return/athrow) and build
   `IrBlock`s. Source of truth: the preprocessed `MethodNode` after
   `COMPUTE_FRAMES`.
2. **Stack-to-register / SSA build** *(phase 1)* — abstract-interpret the operand
   stack per block (seeded from ASM frames), converting pushes to SSA defs and
   consumes to uses; place `IrPhi` block params at merges. Replaces the
   `stackPointer`/`cstackN` numbering with typed values. `getNewStackPointer`
   tables move here as the stack-effect model.
3. **Type assignment + verify** *(phase 1)* — attach `IrType` to every value from
   descriptors/frames; run `IrVerifier` (assertions/tests).
4. **Invoke & indy lowering** *(phase 1)* — select the JNI call family
   (`CallNonvirtual*`/`Call*`/`CallStatic*`, per the `INVOKESPECIAL_*` /
   `INVOKEVIRTUAL_*` / `INVOKESTATIC_*` snippet families) from opcode + return
   sort; resolve `MethodRef`/`ClassRef`. `invokedynamic`/condy stay lowered at the
   bytecode level by the existing preprocessors for now (§9), so the IR sees only
   ordinary invokes plus the `link_call_site`/`invoke_reverse` intrinsics.
5. **Cache materialization** *(phase 1 for correctness, opt for hoisting)* —
   turn `StringConst`/`ClassRef`/`MethodRef`/`FieldRef` uses into slot accesses
   with lazy-init guards. Phase-1 emits init at each use (parity with today);
   the hoisting/dedup refinement is *(opt)*.
6. **Exception-edge construction** *(phase 1)* — attach exceptional successors to
   may-throw nodes and build per-block handler dispatch blocks with
   `CaughtException` + ordered `InstanceOf` chain.
7. **Constant folding + copy propagation + DCE** *(opt)* — fold `ICONST`+`IADD`,
   propagate trivial copies (`DUP`, local round-trips), delete unused pure values.
   Must never remove a may-throw node or a value that escapes.
8. **Null-check & bounds-check elimination** *(opt)* — use the nullability lattice
   (§4.2) to drop `if (x == nullptr) throw NPE` when `x` is `NonNull`; drop
   redundant `CHECKCAST` when the descriptor already matches.
9. **JNI transition minimization / local-ref liveness** *(opt)* — replace bulk
   `refs`/`clear_refs` with liveness-driven `DeleteLocalRef`; coalesce adjacent
   `ExceptionCheck`s; elide checks after calls proven non-throwing. This is the
   pass that addresses the README's "slows down code significantly" warning.
10. **Slot allocation** *(phase 1)* — map SSA values to C++ locals. Phase-1
    strategy: keep `jvalue`-union slots to stay ABI-identical to today. Later
    *(opt)*: typed scalars (`jint v3;`) for non-escaping primitives to help the C++
    compiler.
11. **Emit** *(phase 1)* — hand the finalized `IrMethod` to the emitter (§8).

The pipeline is a list, so adding/removing an *(opt)* pass is a one-line change
and is independently testable via IR snapshot tests (§10).

---

## 8. The emitter (structured, not string concat)

The emitter is a small C++ AST plus a pretty-printer, replacing
`Snippets`/`cppsnippets.properties`/`StringBuilder`.

### 8.1 C++ model (`by.radioegor146.ir.emit`)

```
CType         // void, jint, jlong, jfloat, jdouble, jobject, jclass, jmethodID, ...
CExpr         // literal, var, field-access (.i/.j/.l), call, cast, binop, unary, cond
CStmt         // decl, assign, expr-stmt, if, goto, label, switch, block, return
```

- `CEmitter` walks the `IrMethod` and produces `CStmt`s. It never concatenates raw
  C++ fragments; it builds typed expression nodes (e.g. `CBinOp(ADD, a.i, b.i)`).
- `CPrinter` renders `CStmt`/`CExpr` to text with correct indentation. The current
  `context.output.toString().replace("\n", "\n    ")` reindent hack in
  `NativeObfuscator` goes away; indentation is the printer's job.
- `RuntimeCalls` is a typed façade over the runtime ABI — `env->CallIntMethod(...)`,
  `utils::throw_re(...)`, `utils::find_class_wo_static(...)`, `utils::get_lookup(...)`
  — so the exact `native_jvm.hpp` contract is expressed once, in Java, with the
  right argument types, instead of scattered across ~575 lines of properties.

### 8.2 Prologue/epilogue sharing

The method prologue (JNI signature, `clazz`/`classloader`/`lookup` setup,
try-catch class caching, `jvalue` slot decls, `refs` decl, argument load) and the
catch-dispatch epilogue are today inline in `MethodProcessor`. They are extracted
into a shared `MethodShellEmitter` used by *both* the legacy and IR paths, so the
two paths differ only in the *body* between prologue and epilogue. This keeps the
JNI ABI and registration (`__ngen_register_methods`, native-method table) bit-for-
bit identical and shrinks the surface where IR could regress.

### 8.3 Two node tiers → two emission styles

- **Low-level nodes** (arith, compare, convert, const, local move, stack copy)
  emit direct C++ on the value's carrier — no `env` call. This is exactly what the
  arithmetic snippets already do (`IADD`, `LCMP`, `I2D`), now driven by types.
- **High-level nodes** (field/method/array/object/monitor/throw) emit
  `RuntimeCalls` + the exception-edge check. Same JNI calls as today's snippets,
  chosen by IR type rather than by opcode-sort string suffix.

---

## 9. Backends and later hooks

### 9.1 Native backends unchanged

CMake (`CMakeFilesBuilder`) and Zig (`zig/ZigBuilder`, `--use-zig`) consume the
emitted `.cpp/.hpp` tree exactly as now. The IR changes what goes *inside* method
bodies, not the file layout, the cache arrays, the loader, or the runtime. No
backend changes are required for phases 1–3.

### 9.2 (i) Direct-C++ backend for hot / platform-independent ops

This is the low-level node tier of §8.3, framed as a per-node backend choice.
Arithmetic, comparisons, conversions, constant loads, and local/stack moves are
platform-independent and need no JVM round-trip, so they are emitted as straight
C++ on typed temporaries. The IR makes this a property of the node, so as passes
prove more values are pure primitives (constant folding, copy prop), more of a
method body becomes JNI-free automatically.

### 9.3 (ii) Compact opcode stream + tiny interpreter — **hook only**

For selected methods it can be preferable to emit a compact bytecode-like stream
plus a shared interpreter loop instead of straight-line C++ (smaller code, uniform
shape). This document specifies only the **extension point**, not an
implementation, and explicitly not a protector:

```
interface MethodLoweringStrategy {
    boolean supports(IrMethod m);          // e.g. size/opcode-set heuristics
    LoweredMethod lower(IrMethod m, LoweringContext ctx);
}
```

- `DirectCppStrategy` (the default, = §9.2 + high-level nodes) is the only strategy
  implemented in the migration.
- An `InterpreterStreamStrategy` would implement the same interface, serializing the
  IR to a compact stream and emitting a call into a shared interpreter. The
  interface, the selection point in the pipeline (after pass 10, before emit), and
  the `LoweredMethod` contract are the deliverable; the strategy body is left for a
  future PR and is out of scope here.

No anti-analysis, encryption, or tamper-resistance is designed or endorsed here.

---

## 10. Testing strategy

The current suite is end-to-end only (`ClassicTest` via `TestsGenerator`:
compile → transpile → CMake build → run → diff stdout, across every `Platform`).
The IR adds testable layers *below* that:

1. **IR snapshot / golden tests** *(new, fast, no toolchain)* — feed a small
   `MethodNode` (built with ASM or compiled from a `.java`/`.j` fixture) through the
   frontend + passes and assert the textual IR dump equals a checked-in golden
   file. Catches frontend/pass regressions in milliseconds. Lives in
   `src/test/java/by/radioegor146/ir/`.
2. **Pass unit tests** *(new)* — each *(opt)* pass gets targeted tests: e.g.
   null-check elimination removes the guard for a `NEW` result; local-ref liveness
   inserts `DeleteLocalRef` at the right point; cache hoisting dedups two
   `ClassRef`s.
3. **Emitter tests** *(new)* — assert `CPrinter` output for representative nodes
   (compile-only: the emitted `.cpp` compiles with the toolchain, checked in a
   lightweight CMake smoke build).
4. **Differential parity tests** *(reuse existing harness)* — run the full
   `ClassicTest` suite twice, once `--codegen=legacy` and once `--codegen=ir`, and
   require identical stdout to the ideal run for both. This is the release gate.
5. **Cross-path diff** *(new, optional)* — for a corpus of methods, assert that the
   *behavior* (stdout) of IR output matches the legacy output, not just the ideal,
   to catch cases where both happen to diverge from ideal identically.

CI (`.github/workflows/main.yml`) already fans out over JDK 8/11/17/21/25 and
Ubuntu/macOS/Windows. The IR path is added as an extra axis (or an extra invocation
inside the job); until parity is reached it runs as a **separate, non-blocking**
matrix entry so it never breaks the legacy signal (details in the migration plan).

---

## 11. Proposed package and type names

All new code lives under `by.radioegor146.ir` so it is clearly separated from the
legacy `instructions`/`source` packages. These names are a proposal; the human
reviewer may rename before implementation lands (see the migration plan's
human-decision list).

```
by.radioegor146.ir
  ├─ IrMethod, IrBlock, IrValue, IrInstruction, IrTerminator, IrPhi, BlockId
  ├─ type/       IrType, RefType (descriptor + Nullability)
  ├─ node/       Const, BinOp, UnOp, Convert, Compare,           // low-level tier
  │              LocalLoad, LocalStore, StackCopy,
  │              ArrayLoad, ArrayStore, ArrayLength, NewArray, MultiNewArray,
  │              GetField, PutField, GetStatic, PutStatic,        // high-level tier
  │              Invoke (Virtual/Special/Static/Interface), New,
  │              CheckCast, InstanceOf, MonitorEnter, MonitorExit,
  │              Throw, Return, Branch, Switch, Goto,
  │              CaughtException, EnvIntrinsic,                    // JNI model
  │              StringConst, ClassRef, MethodRef, FieldRef       // cache entities
  ├─ frontend/   CfgBuilder, StackToSsa, AsmToIr
  ├─ pass/       Pass, PassManager, and one class per §7 pass
  ├─ emit/       CType, CExpr, CStmt, CEmitter, CPrinter, RuntimeCalls,
  │              MethodShellEmitter (shared prologue/epilogue)
  ├─ backend/    MethodLoweringStrategy, DirectCppStrategy          // §9.3 hook
  └─ IrMethodCompiler   // orchestrates frontend → passes → emit; the IR entry point
```

`IrMethodCompiler` is the IR counterpart of `MethodProcessor.processMethod`: given
a `MethodContext`, it produces the same `context.output` / `context.nativeMethods`
/ `context.proxyMethod` results, so `NativeObfuscator`'s per-class loop can call
either one behind the flag.

---

## 12. How this maps back to today's files (summary)

| Today | Under the IR |
| --- | --- |
| `MethodProcessor.processMethod` body walk | `IrMethodCompiler` (frontend → passes → emit) |
| `stackPointer` + `getNewStackPointer` tables | `StackToSsa` stack-effect model |
| `GenericInstructionHandler` + `instructions/*` | frontend node builders + `CEmitter` |
| `Snippets` + `cppsnippets.properties` | `CEmitter` + `RuntimeCalls` (typed) |
| `$trycatchhandler` text + `L_CATCH_*` epilogue | exception edges + dispatch blocks |
| `NodeCache` pools referenced inline | `StringConst`/`ClassRef`/`MethodRef`/`FieldRef` nodes + `CacheMaterialization` |
| `std::unordered_set<jobject> refs` + `clear_refs` | owned-ref nodes + `LocalRefLiveness` |
| method prologue/epilogue inline in `MethodProcessor` | shared `MethodShellEmitter` |
| `IndyPreprocessor` / `LdcPreprocessor` | reused unchanged as frontend normalization |
| `ClassSourceBuilder` / `CMakeFilesBuilder` / `MainSourceBuilder` / `StringPool` | unchanged |
| `native_jvm.{cpp,hpp}` / `string_pool.*` runtime | unchanged |

The precise file-by-file change list, PR slicing, risk, and rollback are in
[`ir-migration-plan.md`](./ir-migration-plan.md). Worked
bytecode → IR → C++ examples are in [`ir-examples.md`](./ir-examples.md).
