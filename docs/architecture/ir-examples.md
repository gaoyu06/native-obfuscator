# IR Examples: bytecode → IR → C++

Status: **Illustrative (docs only).** The IR text and C++ below are proposals to
make [`ir-compiler.md`](./ir-compiler.md) concrete. No compiler emits this yet.

Each example shows four things:

1. **Java** source.
2. **Bytecode** (javac-style disassembly).
3. **IR** in the textual notation defined below.
4. **Expected C++** from the IR emitter. For the first examples the current
   snippet-path output is also sketched, to show the IR reaches parity while
   emitting cleaner code.

For readability the shared **prologue** (JNI signature, `classloader`/`lookup`
resolution, `refs` declaration, argument load) is abbreviated as
`// <shell prologue>` except in Example 1, where it is shown in full. The prologue
is emitted identically by both paths via `MethodShellEmitter`
(see design §8.2).

---

## Textual IR notation

```
method <name><desc> -> <ret>  [static|instance, owner <C>]
  params: %name:<type>, ...
block <id>(%param:<type>, ...):     ; block params = SSA phi / merge inputs
  %v:<type> = <op> <operands>       ; each instruction defines 0..1 SSA value
  <terminator>                      ; branch / switch / return / throw / goto
  exc: <block>                      ; exceptional successor (may-throw nodes)
```

Types (design §4): `i32` (boolean/byte/char/short/int), `i64` (long), `f32`,
`f64`, `ref<Descriptor, Nullability>`, `void`. `i1` denotes a boolean predicate
produced by a compare (materialized as `jint` 0/1 when needed).

Nullability: `NonNull`, `MaybeNull`, `Null`.

---

## Example 1 — pure arithmetic (no JNI)

Demonstrates: stack-to-SSA collapsing the operand stack, direct-C++ backend,
zero JNI calls.

### Java
```java
static int add(int a, int b) {
    return a + b;
}
```

### Bytecode
```text
static int add(int, int);
  0: iload_0
  1: iload_1
  2: iadd
  3: ireturn
  // maxStack=2, maxLocals=2
```

### IR
```text
method add(II) -> i32  [static]
  params: %a0:i32, %a1:i32
block b0:
  %0:i32 = iadd %a0, %a1
  return %0
```

The two `iload`s and the `iadd` no longer touch stack slots; stack-to-SSA turned
the pushes/pops into value references, so the whole body is one `iadd`.

### Expected C++ (IR emitter)
```cpp
jint JNICALL __ngen_add(JNIEnv *env, jclass clazz, jint arg0, jint arg1) {
    // <shell prologue>, shown in full once:
    jobject classloader = utils::get_classloader_from_class(env, clazz);
    if (env->ExceptionCheck()) { return (jint) 0; }
    if (classloader == nullptr) { env->FatalError(/*"classloader == null"*/); return (jint) 0; }
    jobject lookup = nullptr;
    std::unordered_set<jobject> refs;
    // end prologue

    jint v0 = arg0 + arg1;
    return v0;
}
```

### For contrast — current snippet path
```cpp
jint JNICALL __ngen_add(JNIEnv *env, jclass clazz, jint arg0, jint arg1) {
    // ... same prologue ...
    jvalue cstack0 = {}, cstack1 = {};
    jvalue clocal0 = {}, clocal1 = {};
    std::unordered_set<jobject> refs;

    clocal0.i = (jint) arg0;         // LOCAL_LOAD_ARG_5
    clocal1.i = (jint) arg1;

    cstack0.i = clocal0.i;           // ILOAD 0
    cstack1.i = clocal1.i;           // ILOAD 1
    cstack0.i = cstack0.i + cstack1.i;   // IADD
    return (jint) cstack0.i;         // IRETURN
    return (jint) 0;
}
```

Same result; the IR version has no `jvalue` unions, no dead trailing `return`, and
carries a real `i32` type on every value. A later pass may also drop the
`classloader` resolution here, since this body never touches the class loader
(*(opt)*, not required for parity).

---

## Example 2 — a loop (CFG, phi via block params, goto emission)

Demonstrates: basic blocks, block-argument SSA at a loop header, and how phi
nodes lower to slot assignments on control-flow edges with `goto`+labels.

### Java
```java
static int sumTo(int n) {
    int s = 0;
    for (int i = 0; i < n; i++) {
        s += i;
    }
    return s;
}
```

### Bytecode
```text
static int sumTo(int);
  0: iconst_0
  1: istore_1            // s = 0
  2: iconst_0
  3: istore_2            // i = 0
  4: iload_2
  5: iload_0
  6: if_icmpge 19
  9: iload_1
 10: iload_2
 11: iadd
 12: istore_1            // s = s + i
 13: iinc  2, 1          // i++
 16: goto  4
 19: iload_1
 20: ireturn
  // locals: 0=n, 1=s, 2=i
```

### IR
```text
method sumTo(I) -> i32  [static]
  params: %n:i32
block b0:                          ; entry
  goto b1(0, 0)                    ; (s=0, i=0)
block b1(%s:i32, %i:i32):          ; loop header — phi inputs are block params
  %c:i1 = icmp_ge %i, %n
  branch %c -> b3, b2
block b2:                          ; loop body
  %s1:i32 = iadd %s, %i
  %i1:i32 = iadd %i, 1
  goto b1(%s1, %i1)
block b3:                          ; exit
  return %s
```

`b1`'s two parameters `%s`/`%i` are the SSA merge of the entry values `(0,0)` and
the back-edge values `(%s1,%i1)`. This is the "basic-block-argument" form the
design chose because it lowers trivially to `goto`.

### Expected C++ (IR emitter)
```cpp
jint JNICALL __ngen_sumTo(JNIEnv *env, jclass clazz, jint arg0) {
    // <shell prologue>
    jint s, i;

    // b0: goto b1(0, 0)
    s = 0; i = 0;
    goto B1;

B1:                                  // loop header
    if (i >= arg0) goto B3;
    // fallthrough to b2

    // b2:
    {
        jint s1 = s + i;
        jint i1 = i + 1;
        s = s1; i = i1;              // assign block params on the back-edge
        goto B1;
    }

B3:
    return s;
}
```

Phi lowering = "write the incoming values into the header's slots on each edge",
which is exactly the assignment before `goto B1`. No JNI, no `jvalue`.

---

## Example 3 — instance field increment (cache dedup + NPE elimination)

Demonstrates: `GetField`/`PutField` as high-level nodes, class/field cache
materialized **once** (today it is re-emitted before every access), and the
null-check on the receiver removed because `this` is `NonNull`.

### Java
```java
class C {
    int x;
    void inc() { x++; }
}
```

### Bytecode
```text
void inc();
  0: aload_0
  1: dup
  2: getfield  #2   // Field x:I
  5: iconst_1
  6: iadd
  7: putfield  #2   // Field x:I
 10: return
```

### IR
```text
method inc()V  [instance, owner C]
  params: %this:ref<C, NonNull>
block b0:
  %x:i32  = getfield C.x:I  %this        ; %this NonNull  => no NPE check
  %x1:i32 = iadd %x, 1
  putfield C.x:I  %this, %x1             ; %this NonNull  => no NPE check
  return
```

`aload_0; dup; ...; putfield` all reference the same SSA value `%this`; the `dup`
disappears into value reuse.

### Expected C++ (IR emitter)
```cpp
void JNICALL __ngen_inc(JNIEnv *env, jobject obj) {
    // <shell prologue> (resolves clazz = find_class_wo_static(...C...))

    // ClassRef(C) + FieldRef(C.x:I) materialized once (CacheMaterialization pass)
    if (!cclasses[0] || env->IsSameObject(cclasses[0], NULL)) {
        cclasses_mtx[0].lock();
        if (!cclasses[0] || env->IsSameObject(cclasses[0], NULL)) {
            if (jclass c = utils::find_class_wo_static(env, classloader, cstrings[0] /* "C" */)) {
                cclasses[0] = (jclass) env->NewWeakGlobalRef(c);
                env->DeleteLocalRef(c);
            }
        }
        cclasses_mtx[0].unlock();
        if (env->ExceptionCheck()) return;
    }
    if (!cfields[0]) {
        cfields[0] = env->GetFieldID(cclasses[0], "x", "I");
        if (env->ExceptionCheck()) return;
    }

    jint v0 = env->GetIntField(obj, cfields[0]);   // no null check: obj is receiver
    if (env->ExceptionCheck()) return;
    env->SetIntField(obj, cfields[0], v0 + 1);
    if (env->ExceptionCheck()) return;
    return;
}
```

### What changed vs the snippet path
- The current path emits the **class-cache init block twice** (before `getfield`
  and again before `putfield`) plus a field-id init before each. The IR emits each
  once, hoisted ahead of first use.
- The current path emits `if (cstackN.l == nullptr) utils::throw_re(... NPE ...)`
  before both `getfield` and `putfield`. Both are gone: `%this` is `NonNull`
  (receiver of an instance method).
- No `refs` bookkeeping is generated because the body produces no object
  references (the field is an `int`).

---

## Example 4 — virtual call (method cache, NPE kept, JNI transition)

Demonstrates: `Invoke` lowering to the right `Call*Method` family by return type,
`ClassRef`/`MethodRef` resolution, and a null-check that is **kept** because the
argument is `MaybeNull`.

### Java
```java
static int len(String s) {
    return s.length();
}
```

### Bytecode
```text
static int len(java.lang.String);
  0: aload_0
  1: invokevirtual #2   // Method java/lang/String.length:()I
  4: ireturn
```

### IR
```text
method len(Ljava/lang/String;) -> i32  [static]
  params: %s:ref<java/lang/String, MaybeNull>
block b0:
  %len:i32 = invokevirtual java/lang/String.length:()I  %s
             ; may throw (NPE if %s == null, or callee exception) -> exc: caller
  return %len
```

There is no try-catch in this method, so the exceptional successor is the implicit
"return to caller" edge (today's `TRYCATCH_EMPTY`).

### Expected C++ (IR emitter)
```cpp
jint JNICALL __ngen_len(JNIEnv *env, jclass clazz, jobject arg0) {
    // <shell prologue>

    // ClassRef(java/lang/String) + MethodRef(String.length:()I) — materialized once
    if (!cclasses[0] || env->IsSameObject(cclasses[0], NULL)) { /* lazy init "java.lang.String" */ }
    if (!cmethods[0]) {
        cmethods[0] = env->GetMethodID(cclasses[0], "length", "()I");
        if (env->ExceptionCheck()) return (jint) 0;
    }

    if (arg0 == nullptr) {                          // %s is MaybeNull -> check kept
        utils::throw_re(env, "java/lang/NullPointerException", "INVOKEVIRTUAL Int npe", 1);
        return (jint) 0;
    }
    jint v0 = env->CallIntMethod(arg0, cmethods[0]);  // return sort=int -> CallIntMethod
    if (env->ExceptionCheck()) return (jint) 0;
    return v0;
}
```

The call family (`CallIntMethod`) is selected from the IR return type, replacing
the snippet key `INVOKEVIRTUAL_5`. Because the result is a primitive `int`, no
local reference is produced and no `refs` cleanup is emitted. If `length()`
returned an object, the IR would mark the result an owned local ref and the
`LocalRefLiveness` pass (§7.9) would place a precise `DeleteLocalRef` at its last
use instead of the bulk `refs`/`clear_refs`.

---

## Example 5 — try/catch (exception edges as first-class control flow)

Demonstrates: a may-throw node with an exceptional successor, a landing block with
a `CaughtException` value, an ordered handler `instanceof` chain, and rethrow —
the structured replacement for the appended `$trycatchhandler` text plus the
`L_CATCH_*` epilogue.

### Java
```java
static int safeDiv(int a, int b) {
    try {
        return a / b;
    } catch (ArithmeticException e) {
        return -1;
    }
}
```

### Bytecode
```text
static int safeDiv(int, int);
  0: iload_0
  1: iload_1
  2: idiv
  3: ireturn
  4: astore_2            // catch: e
  5: iconst_m1
  6: ireturn
Exception table:
  from  to  target  type
     0   4       4  java/lang/ArithmeticException
```

### IR
```text
method safeDiv(II) -> i32  [static]
  params: %a:i32, %b:i32
block b0:
  %q:i32 = idiv %a, %b               ; may throw ArithmeticException (div-by-zero)
  return %q
  exc: bdispatch
block bdispatch(%e:ref<java/lang/Throwable, NonNull>):   ; CaughtException
  %is:i1 = instanceof %e, java/lang/ArithmeticException
  branch %is -> bhandler, brethrow
block bhandler:                       ; catch (ArithmeticException e) { return -1; }
  return -1
block brethrow:
  throw %e
```

`idiv`'s exceptional successor is `bdispatch`; `bdispatch` is generated once per
distinct covering handler set (matching how `MethodContext.catches` dedups
`CatchesBlock`). The `astore_2`/`iconst_m1` of the handler collapse to
`return -1` after copy-propagation/DCE.

### Expected C++ (IR emitter)
```cpp
jint JNICALL __ngen_safeDiv(JNIEnv *env, jclass clazz, jint arg0, jint arg1) {
    // <shell prologue>; try-catch class cache: ClassRef(java/lang/ArithmeticException) at cclasses[0]
    if (!cclasses[0] || env->IsSameObject(cclasses[0], NULL)) { /* lazy init ArithmeticException */ }

    jint q;
    // idiv with JVM-accurate div-by-zero and INT_MIN/-1 handling
    if (arg1 == 0) {
        utils::throw_re(env, "java/lang/ArithmeticException", "IDIV / by 0", 2);
        goto B_DISPATCH;
    } else if (arg1 == -1 && arg0 == (jint) 2147483648U) {
        q = arg0;                        // INT_MIN / -1 overflow, preserved as-is
    } else {
        q = arg0 / arg1;
    }
    return q;

B_DISPATCH:                              // bdispatch
    {
        jthrowable e = env->ExceptionOccurred();
        env->ExceptionClear();
        if (env->IsInstanceOf(e, cclasses[0])) {   // instanceof ArithmeticException
            env->DeleteLocalRef(e);                // e dead after handler entry
            return (jint) -1;                      // bhandler
        }
        env->Throw(e);                             // brethrow
        return (jint) 0;
    }
}
```

This mirrors the semantics of the snippet path's `TRYCATCH_START` /
`TRYCATCH_CHECK_STACK` / `TRYCATCH_END_STACK`, but every arrow (`idiv → dispatch`,
`dispatch → handler`, `dispatch → rethrow`) is an explicit IR edge a pass can
inspect — enabling, for instance, dropping the exceptional edge entirely when a
node is proven not to throw, or coalescing `ExceptionCheck`s across adjacent calls.

---

## Cross-cutting notes

- **Parity is the contract.** Every example's C++ is behavior-identical to the
  snippet path; the acceptance gate is the existing `ClassicTest` diffing stdout
  across all `Platform`s (design §10, migration plan §1.5).
- **Where the wins are visible.** Examples 3–5 show the three levers the snippet
  path cannot pull: cache dedup/hoisting (§7.5), null/redundant-check elimination
  (§7.8), and precise local-reference lifetime / JNI-transition minimization
  (§7.9).
- **The runtime is untouched.** Every `utils::*`, `env->*`, `cclasses`/`cmethods`/
  `cfields`, and `cstrings` reference above is the *existing* ABI from
  `native_jvm.{cpp,hpp}` and `ClassSourceBuilder`; the IR changes only how method
  bodies are produced.
