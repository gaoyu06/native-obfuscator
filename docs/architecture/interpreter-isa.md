# Interpreter ISA

## Status and scope

This document specifies the **instruction set architecture** of the optional
interpreter backend described in
[`interpreter-backend.md`](interpreter-backend.md): the opcode set, immediate
encoding, constant pool, exception table, and the invoke/JNI helper contracts.

It is a design sketch, not an implementation. The format is **internal and
regenerated with every build** (roadmap decision D17); it is versioned only so a
mismatched runtime can *reject* rather than misinterpret a stream. There is no
external stability contract and no on-disk persistence: the opcode stream is
`static const` data compiled directly into the generated `.cpp`, landing in
`.rodata`.

The design goal is **fidelity and coverage**, not density or speed. The ISA is
deliberately close to verified JVM bytecode — typed and pre-resolved — so the
IR→opcode lowering is a near-mechanical mapping and the handlers can reuse the
exact C++ expressions the direct backend already ships in
`cppsnippets.properties`.

## Design principles

1. **Typed, pre-resolved, register-of-record-free.** Like the JVM it is a
   stack machine, but every operation carries its value kind in the opcode (so
   no run-time type tags on slots) and every symbolic reference is already an
   index into a per-class pool the direct backend builds. The interpreter does
   **no** name resolution beyond the existing lazy pool fill.
2. **One IR op → one opcode.** Keeps the emitter dumb and the golden output
   diffable.
3. **Handlers are shared with the direct backend.** The body of each arithmetic/
   compare/convert/array/field/invoke handler is the same C++ expression used in
   the snippet templates, so JVM corner-case semantics have exactly one
   implementation.
4. **Portable decoding.** Byte-addressed code; immediates are little-endian and
   read via `memcpy`, never via aliasing casts or unaligned pointer loads.

## Value model: the `slot`

The operand stack and locals are arrays of `slot`, a 64-bit tagless union
shaped like JNI's `jvalue`:

```cpp
union slot {          // C++11; no ctor, trivially copyable
    jint    i;        // int / boolean / byte / char / short  (sign/zero handled by opcode)
    jlong   j;        // long
    jfloat  f;        // float   (stored in the low 32 bits; upper bits unused)
    jdouble d;        // double
    jobject l;        // reference / null
};
```

### Category-2 (long/double) mapping — the one place we diverge from the JVM

The JVM operand stack gives `long`/`double` **two** slots. The interpreter
stores each value in **one** 64-bit `slot`. This is a representation choice, not
a semantic one, and it must be handled carefully:

- **Frame sizing** still uses the verifier's `maxStack`/`maxLocals` (two words
  for category-2). The interpreter allocates `slot stack[maxStack]` and
  `slot locals[maxLocals]`; because a `slot` is 64-bit, a category-2 value that
  the verifier counted as two words simply occupies the first of its two
  reserved indices, and the second reserved index is left unused. This keeps
  local **indexing** identical to bytecode (a `lstore 3` writes `locals[3]`,
  and index 4 stays reserved), which is what makes the IR→opcode mapping
  mechanical.
- **Stack-manipulation opcodes** (`dup2`, `dup2_x1`, `dup2_x2`, `pop2`, `swap`)
  are emitted by the front end in a **form-resolved** way: the verifier already
  knows whether a `dup2` is "duplicate top two category-1 values" or "duplicate
  one category-2 value," so the emitter picks the correct concrete opcode
  (`DUP2_1` vs `DUP2_2`, etc.) instead of making the interpreter re-derive it.
  This removes all run-time ambiguity and matches how the direct backend's
  snippets already assume a known stack shape.

This mirrors the direct backend, which likewise stores each value in a single
`jvalue` (`cstackN`) and relies on the verifier-known shape.

## Method description record

Each selected method lowers to one `static const` aggregate plus its arrays:

```cpp
namespace native_jvm::interp {

struct exc_row {
    uint32_t start_pc;      // inclusive byte offset into code
    uint32_t end_pc;        // exclusive
    uint32_t handler_pc;
    int32_t  catch_class;   // index into cclasses[]; -1 == ANY (finally / catch-all)
};

struct method_desc {
    uint16_t       isa_version;   // must equal NJVM_ISA_VERSION
    uint16_t       flags;         // bit0: is_static, bit1: synchronized, bit2: has_monitors
    uint16_t       max_stack;     // in 64-bit slots (see category-2 note)
    uint16_t       max_locals;    // in 64-bit slots
    uint8_t        ret_kind;      // KIND_* of the return value (KIND_VOID for void)
    uint32_t       code_len;
    const uint8_t *code;          // the opcode stream
    uint32_t       exc_len;
    const exc_row *exc_table;
    // constant operands are read inline from `code` as pool indices; the pools
    // themselves are the per-class cstrings/cclasses/cmethods/cfields arrays.
    const line_row *lines;        // debug builds only; pc -> (bytecode offset, line)
    uint32_t       lines_len;
};

} // namespace
```

`NJVM_ISA_VERSION` is a compile-time constant baked into both the executor and
every `method_desc`. The executor asserts equality on entry; a mismatch is a
build/runtime bug (it cannot happen for a correctly regenerated library) and
fails loudly rather than interpreting stale data.

## Value kinds

A 3-bit kind selector, reused wherever an opcode is parameterized by type. These
line up with the direct backend's `TYPE_TO_STACK` / `CPP_TYPES` sort ordering so
the emitter can translate ASM `Type.getSort()` directly.

| KIND | name | slot field | notes |
|---|---|---|---|
| 0 | `KIND_VOID` | — | return-only |
| 1 | `KIND_INT` | `.i` | boolean/byte/char/short are int on the stack |
| 2 | `KIND_LONG` | `.j` | category-2 |
| 3 | `KIND_FLOAT` | `.f` | |
| 4 | `KIND_DOUBLE`| `.d` | category-2 |
| 5 | `KIND_REF` | `.l` | object/array/null |

Sub-int array/field element kinds (boolean/byte/char/short) are distinguished by
dedicated opcodes (`IALOAD` vs `BALOAD` vs `CALOAD` …), exactly as in JVM
bytecode and in the existing snippets, so the `slot` never needs a narrower tag.

## Immediate encoding

Opcodes are one byte. Operands follow inline, little-endian, `memcpy`-read:

| Operand form | Bytes | Used by |
|---|---|---|
| none | 0 | `NOP`, arithmetic, compares, `ARETURN`, stack ops, `MONITORENTER`… |
| `u8` | 1 | small local index fast-paths (`ILOAD_0`-style, optional) |
| `u16 local` | 2 | `ILOAD`/`ISTORE`/`IINC`(+`i16`)/`ALOAD`… (wide locals) |
| `i32 imm` | 4 | `SIPUSH`/`BIPUSH` folded to `IPUSH`, `LDC_INT` inline small ints |
| `i32 branch` | 4 | branch/goto target = absolute byte offset into `code[]` |
| `u16 pool` | 2 | constant/class/field/method pool index |
| switch payload | var | `TABLESWITCH`/`LOOKUPSWITCH` (see below) |

Design choices:

- **Branch targets are absolute `code[]` offsets**, resolved by the emitter's
  second pass. The loop does `pc = target;` — there is no `goto`, so computed-
  goto/tail-threaded modes need no label fixups beyond the dispatch table.
- **Local indices are `u16`** to cover `wide`-prefixed locals without a separate
  `WIDE` opcode; the JVM `wide` prefix is absorbed by the emitter.
- **`iinc` carries `u16 local` + `i16 const`**, absorbing `wide iinc`.
- **No operand is variable-length except the two switches**, so the decoder
  advances `pc` by a per-opcode constant in every other case (a static
  `operand_len[256]` table), which is what keeps all three dispatch modes
  branch-free in decoding.

### Switch payloads

`TABLESWITCH`: `u16 pool`? no — encoded inline as `i32 default_target`,
`i32 low`, `i32 high`, then `(high-low+1)` × `i32 target`. The emitter pads
nothing; the decoder computes the index arithmetically.

`LOOKUPSWITCH`: `i32 default_target`, `i32 npairs`, then `npairs` ×
(`i32 key`, `i32 target`), keys ascending (as the class file guarantees). The
handler binary-searches, matching JVM semantics; both mirror the existing
`TABLESWITCH_*` / `LOOKUPSWITCH_*` snippets.

## Opcode set

The set is organized to mirror the direct backend's snippet families so each
opcode's handler is the corresponding snippet expression. Below, `S0` is the top
of the operand stack, `S1` the next, etc.; `pc` advances past operands unless a
branch reassigns it.

### Constants & stack

`NOP`, `ACONST_NULL`, `ICONST` (via `IPUSH i32`), `LCONST`/`FCONST`/`DCONST`
(via typed `LDC_*`), `IPUSH i32`, `LDC_INT i32`, `LDC_LONG`(pool),
`LDC_FLOAT`(pool), `LDC_DOUBLE`(pool), `LDC_STRING u16pool`,
`LDC_CLASS u16pool`.

`POP`, `POP2_1`, `POP2_2`, `DUP`, `DUP_X1`, `DUP_X2`, `DUP2_1`, `DUP2_2`,
`DUP2_X1_1`, `DUP2_X1_2`, `DUP2_X2_*`, `SWAP`.

The `POP2`/`DUP2*` families are split into the category-1 and category-2 forms
the verifier already disambiguated (see the category-2 note above), so each
opcode is a fixed sequence of `slot` copies identical to the corresponding
`DUP2*` snippet.

### Loads / stores

`ILOAD/LLOAD/FLOAD/DLOAD/ALOAD u16`, `ISTORE/LSTORE/FSTORE/DSTORE/ASTORE u16`.
Fast-path single-byte variants (`*LOAD_0..3`) are optional density opcodes with
identical semantics.

### Arithmetic / logic / convert

Full JVM set: `IADD…DREM`, `INEG…DNEG`, `ISHL/ISHR/IUSHR/LSHL/LSHR/LUSHR`,
`IAND/IOR/IXOR/LAND/LOR/LXOR`, `IINC u16 i16`, and all conversions
`I2L…I2S`, `LCMP`, `FCMPL/FCMPG`, `DCMPL/DCMPG`.

**Corner-case semantics are the snippet expressions, verbatim:** `INT_MIN/-1`
guards for `IDIV`/`LDIV`, divide-by-zero → `ArithmeticException`, shift masking
`& 0x1f`/`& 0x3f`, `IUSHR`/`LUSHR` unsigned via `uint32_t`/`uint64_t` casts,
`FREM`/`DREM` via `std::fmod`, and the NaN direction of `FCMPL` vs `FCMPG`
(`-1` vs `+1`). These are copied from `cppsnippets.properties` so there is one
source of truth.

### Arrays

`NEWARRAY_<kind>`, `ANEWARRAY u16pool`, `MULTIANEWARRAY u16pool u8dims`,
`ARRAYLENGTH`, and typed element access `IALOAD/LALOAD/FALOAD/DALOAD/AALOAD/
BALOAD/CALOAD/SALOAD` + the matching `*ASTORE`. Each does the null check and
throws `NullPointerException`/`NegativeArraySizeException` exactly as the array
snippets do, and uses the same `env->Get*ArrayRegion`/`Set*ArrayRegion`/
`utils::baload`/`utils::bastore` calls. Element refs are inserted into `refs`.

### Fields

`GETSTATIC_<kind> u16field u16class`, `PUTSTATIC_<kind> …`,
`GETFIELD_<kind> u16field`, `PUTFIELD_<kind> u16field`. The field/class indices
address `cfields[]`/`cclasses[]`; handlers are the `GETSTATIC_*`/`PUTFIELD_*`
snippet bodies with the same null checks and `refs` inserts.

### Type checks

`CHECKCAST u16pool`, `INSTANCEOF u16pool` — the `CHECKCAST`/`INSTANCEOF` snippet
bodies (`env->IsInstanceOf`, `ClassCastException` message construction).

### Control flow

`GOTO i32`, `IF_<cond> i32` (`IFEQ…IFLE`, `IF_ICMP*`, `IF_ACMPEQ/NE`,
`IFNULL/IFNONNULL`), `TABLESWITCH`, `LOOKUPSWITCH`. Conditions are the `IF*`
snippet predicates; a taken branch sets `pc = target`.

### Returns

`IRETURN/LRETURN/FRETURN/DRETURN/ARETURN/RETURN`. Each casts `S0` to the
executor's `ret_kind` and returns from `execute_*`, after `utils::clear_refs`
and any monitor release (see §"Monitors").

### Object creation

`NEW u16pool` (via `env->AllocObject` like the `NEW` snippet; the following
`INVOKESPECIAL <init>` runs the constructor exactly as today).

### Invocation — the important family

Invocation is where pre-resolution matters. The emitter has already computed,
per call site, the return kind, the argument count and their kinds, and the
target's pool index. Opcodes:

| Opcode | Operands | Maps to snippet |
|---|---|---|
| `INVOKEVIRTUAL_<ret> u16method u8argwords` | method pool index, arg width | `INVOKEVIRTUAL_*` |
| `INVOKEINTERFACE_<ret> u16method u8argwords` | | `INVOKEINTERFACE_*` |
| `INVOKESTATIC_<ret> u16method u16class u8argwords` | | `INVOKESTATIC_*` |
| `INVOKESPECIAL_<ret> u16method u16class u8argwords` | | `INVOKESPECIAL_*` |

`<ret>` is the return `KIND_*` (0–5, plus the sub-int variants the snippets
distinguish for boolean/char/byte/short returns). At run time the handler:

1. Reads `argwords` values below `S0`'s region to build a `jvalue argv[]`
   (the executor keeps a small reusable buffer). Argument slots are copied
   straight from the operand stack — no boxing — because the `slot`/`jvalue`
   shapes match. This is the interpreter analogue of the direct backend's
   `INVOKE_ARG_*` expansion.
2. Null-checks the receiver for non-static calls (throwing `NullPointerException`
   as the snippet does).
3. Calls the matching JNI method: `env->Call<Ret>Method` /
   `CallNonvirtual<Ret>Method` / `CallStatic<Ret>Method` — the *same* JNI entry
   points the snippets use, so virtual dispatch, interface dispatch, and
   `super`/private/`<init>` semantics are the JVM's, not ours.
4. Pushes the result (if any) and inserts object results into `refs`.
5. Runs the standard post-call `ExceptionCheck` → exception-table path.

Because dispatch is delegated to JNI, the interpreter needs no vtable model,
no method resolution, and no bytecode for callees — a called method may itself
be interpreted, direct-C++, or ordinary Java; the boundary is invisible.

### JNI / runtime helpers

The interpreter calls the **same** `native_jvm::utils` services the direct
backend uses; it does not introduce a parallel runtime:

- `utils::throw_re(env, class, msg, line)` for synthesized exceptions
  (NPE/AE/NASE/CCE), with `line` taken from the debug line table when present,
  else `-1`.
- `utils::find_class_wo_static`, `get_class_from_object`,
  `get_classloader_from_class`, `get_lookup` for the same lazy class/lookup
  resolution.
- `utils::baload`/`utils::bastore` for boolean/byte array element access.
- `utils::clear_refs(env, refs)` at every return/abrupt exit.
- `utils::get_interned` and the `cstrings[]` global refs for `LDC_STRING`.
- On `USE_HOTSPOT` builds, `utils::link_call_site` remains the only indy path,
  used exactly as the preprocessed direct path uses it — the interpreter adds
  nothing here (see the invokedynamic limitation in the backend doc).

The `frame` passed to the executor carries `env`, `clazz`, `classloader`,
`locals`, the operand `stack`, and the `refs` set, so every handler has the same
context a generated direct-backend function has.

## Constant pool model

There is **no** new per-method constant pool. The ISA reuses the per-class pools
the direct backend already emits and lazily fills
(see `ClassSourceBuilder.addHeader`):

| Pool | Type | Filled by | Referenced by opcodes |
|---|---|---|---|
| `cstrings[]` | `jstring` (interned global ref) | `__ngen_register_methods` | `LDC_STRING` |
| `cclasses[]` | `jclass` (weak global ref, mutex-guarded lazy resolve) | on first use | `LDC_CLASS`, `NEW`, `ANEWARRAY`, `CHECKCAST`, `INSTANCEOF`, `INVOKESTATIC/SPECIAL`, `GETSTATIC/PUTSTATIC`, exception rows |
| `cmethods[]` | `jmethodID` | on first use | all `INVOKE*` |
| `cfields[]` | `jfieldID` | on first use | `GET/PUT FIELD/STATIC` |

Wide primitive/`long`/`double`/large `LDC` constants that are not inlineable as
`i32` are emitted into a small per-method `static const` array of `jvalue`
(`LDC_LONG/FLOAT/DOUBLE` take an index into it). Everything symbolic goes
through the shared class pools, so lazy resolution, weak-ref revalidation
(`IsSameObject(..., NULL)`), and classloader scoping are identical to the direct
backend and require no new code paths.

## Exception table

The exception table replaces the direct backend's synthesized `goto` catch
blocks with a data table scanned by the throw path:

- Rows are `{start_pc, end_pc, handler_pc, catch_class}` in **source order**
  (the order try/catch blocks appear, which is the order the JVM requires for
  matching).
- `catch_class == -1` is `ANY` (compiler-generated `finally` / catch-all),
  mirroring the `TRYCATCH_ANY_L` snippet.
- On a pending exception at `pc`, the executor:
  1. captures and clears it (`ExceptionOccurred` + `ExceptionClear`), inserting
     it into `refs`;
  2. scans rows where `start_pc <= pc < end_pc`, in order, testing
     `catch_class == -1 || env->IsInstanceOf(exc, cclasses[catch_class])`
     (matching `TRYCATCH_CHECK_STACK`);
  3. on match: clears the operand stack to depth 0, pushes `exc`, sets
     `pc = handler_pc`, resumes;
  4. on no match in this frame: releases monitors held by this frame, runs
     `clear_refs`, re-throws with `env->Throw(exc)`, and returns the zero value
     of `ret_kind` (matching `TRYCATCH_END_STACK` / `TRYCATCH_EMPTY`).

This is behaviorally identical to the direct backend; only the representation
(table vs generated blocks) differs.

## Monitors

`MONITORENTER`/`MONITOREXIT` call `env->MonitorEnter/MonitorExit` with the null
check from the monitor snippets. Because the interpreter can complete abruptly
(exception with no matching handler in the frame), it must release monitors the
frame still holds. The `method_desc.flags` `has_monitors` bit tells the executor
to maintain a small per-frame monitor stack (pushed on enter, popped on exit);
on abrupt completion it unwinds that stack with `MonitorExit`. `synchronized`
methods are modeled as an implicit outermost monitor on the receiver/class,
entered in the trampoline and released on every exit path. The direct backend
gets this "for free" from C++ scoping; the interpreter makes it explicit, and
the eval/correctness suite tests it specifically.

## Versioning & rejection (D17)

- `NJVM_ISA_VERSION` is bumped whenever any opcode number, operand layout, or
  table shape changes. It is not a compatibility promise — it is a tripwire.
- The executor checks `method_desc.isa_version == NJVM_ISA_VERSION` on entry and
  `env->FatalError`s on mismatch (this can only happen if a stale object file is
  mixed into a build, which the single-build regeneration model forbids).
- No forward/backward compatibility, no migration path, no persisted streams:
  the format may change freely between tool versions because it never leaves a
  single build.

## Worked micro-example

Java:

```java
static int f(int a, int b) { return a * b + 1; }
```

Direct backend (today) emits ~4 straight-line C++ statements assigning
`cstackN.i`. The interpreter emits (bytecode offset shown for provenance):

```text
code[]:                              ; sp after
  ILOAD  0x0000        ; local a       1
  ILOAD  0x0001        ; local b       2
  IMUL                 ;               1
  IPUSH  0x00000001    ; const 1       2
  IADD                 ;               1
  IRETURN              ;               -
method_desc{ ret_kind=KIND_INT, max_stack=2, max_locals=2, exc_len=0, ... }
```

The algorithm now lives in `code[]` (a byte array) plus the `IMUL`/`IADD`
handler expressions in the shared executor, rather than as method-specific
arithmetic in the generated C++. Whether that materially changes how a reader
recovers the algorithm is the subject of
[`interpreter-eval-protocol.md`](interpreter-eval-protocol.md); no claim is made
here.
