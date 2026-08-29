# IR phase 2 — Fable design review of the Sol implementation

Reviewer: Claude Fable 5 (author of the IR design; reviewer of phase 1).
Subject: `cursor/ir-compiler-phase2-6d81` — the phase-two extension of the
typed-CFG IR compiler (typed CFG → direct C++ emitter), stacked on
`cursor/ir-phase1-fable-review-6d81`.
Design of record: `docs/architecture/ir-compiler.md`, `ir-migration-plan.md`,
`ir-examples.md`. Status claims under review: `docs/architecture/ir-phase2-status.md`.
Prior review: `docs/architecture/ir-phase1-fable-review.md`.

This is a compiler/transpiler review only. `native-obfuscator` re-expresses each
Java method's bytecode as a JNI C++ function; the review is scoped to code
generation correctness and fidelity. Packing and analysis-resistance are out of
scope.

---

## Verdict

**Accept with nits.**

Phase 2 adds instance `int` fields (`GETFIELD`/`PUTFIELD`), `int`-returning
`INVOKESTATIC`/`INVOKEVIRTUAL` with `int`/reference arguments, reference
parameters and reference-typed phis, `ALOAD`, and `DUP`, all on top of the
phase-1 arithmetic/branch subset. I read every changed file, ran the suite,
verified the g++ compile-smoke actually executes (not skips) on this machine,
and generated the emitted C++ for the field and invoke paths by hand to inspect
it. Field/invoke lowering is correct, the JNI exception-check placement is
correct, the receiver null checks are correct and instance-only, and the
safe-fallback-before-mutation property from phase 1 is preserved. I found no
compile or correctness blocker to fix. The nits are disclosed verbosity (no
cache hoisting), a set of harmless dead `ExceptionCheck`s after primitive field
accessors, and a parity-inherited null-class edge shared with the legacy path.

---

## What I verified, and how

Environment: `g++ (Ubuntu 13.3.0-6ubuntu2~24.04.1) 13.3.0`, OpenJDK
`21.0.10`, JNI headers present at `${java.home}/include{,/linux}`.

| Check | Result |
| --- | --- |
| `./gradlew :obfuscator:test --tests …CodegenModeTest --tests …ir.IrCompilerTest` | BUILD SUCCESSFUL — 8/8 `IrCompilerTest`, 2/2 `CodegenModeTest`, 0 skipped |
| `generatedCppPassesGppSyntaxCheckWhenToolchainAvailable` really ran? | **Yes** — 212 ms, `SUCCESS` (not skipped); the g++ assumptions were all satisfied, so `g++ -std=c++17 -fsyntax-only` actually compiled the concatenated TU and exited 0 |
| Emitted C++ for the `GETFIELD`/`PUTFIELD` increment method (generated out-of-band and read) | class cache + field-ID cache + receiver null check + `GetIntField`/`SetIntField`, each throwing call followed by an `ExceptionCheck` early-return |
| Emitted C++ for `String.length()` via `INVOKEVIRTUAL` (generated and read) | class cache + `GetMethodID` cache + **receiver null check** + `CallIntMethod` + `ExceptionCheck`; `INVOKESTATIC` correctly omits the null check and calls `CallStaticIntMethod` on `cclasses[…]` |
| Fallback-before-mutation | `UnsupportedIrConstructException` is thrown only inside `AsmToIr.build(...)`, before `emitBody` allocates any cache id and before `beginIr` touches `output`/`nativeMethods`/`ACC_NATIVE`; the reject test confirms caches and outputs stay empty on fallback |

### Field lowering (`GETFIELD`/`PUTFIELD`) — correct

For the classic `this.value++` shape (`ALOAD 0; DUP; GETFIELD; ICONST_1; IADD;
PUTFIELD`) the emitter produces, for each access:

1. the weak-global class cache (`!cclasses[i] || IsSameObject(...)` guard, mutex,
   `find_class_wo_static`, `NewWeakGlobalRef`, `DeleteLocalRef`) with a trailing
   `ExceptionCheck`;
2. a lazy `GetFieldID` into `cfields[i]` guarded by `if (!cfields[i])`, with an
   `ExceptionCheck` **inside** the guard (so no check runs when the id is already
   cached and no JNI call was made);
3. a receiver null check (`if (obj == nullptr) { throw_re(...); return …; }`)
   using distinct pooled diagnostics for GET vs PUT;
4. `GetIntField` / `SetIntField`, then an `ExceptionCheck`.

The field-resolution-before-null-check ordering matches the JVM's
link-then-null-check semantics for `GETFIELD`/`PUTFIELD`. The arithmetic wraps
through the `uint32_t` carrier fixed in phase 1.

### Invoke lowering (`INVOKESTATIC`/`INVOKEVIRTUAL`) — correct

Arguments are popped right-to-left (correct stack order), the receiver is popped
last for virtual calls, and the emitter caches the class of the *invoked owner*
plus the method id (`GetMethodID`/`GetStaticMethodID`). For virtual calls the
receiver null check is emitted after id resolution and before `CallIntMethod`;
for static calls no null check is emitted and the class handle is passed to
`CallStaticIntMethod`. Every `Call*IntMethod` and every id lookup is followed by
an `ExceptionCheck` early-return. The node constructors enforce the invariants
(static invoke has no receiver; args are `i32`/reference; return carrier is
`i32`), and the frontend only admits invokes whose descriptor return is exactly
`I`.

### JNI exception checks — correct (with harmless dead checks)

Every JNI call that can raise — `find_class_wo_static`, `GetFieldID`,
`GetMethodID`/`GetStaticMethodID`, `CallIntMethod`/`CallStaticIntMethod` — is
immediately followed by `if (env->ExceptionCheck() != 0) return <zero>;`. The
early-return yields `return;` for void methods and `return 0;`/`return (jint)0;`
for `int` methods, so no partially-computed value is used after a pending
exception. The condition is correctly parenthesized (`… != 0`), which was the
exact defect the status doc says the first g++ run caught and the author fixed
(`if env->ExceptionCheck()` → a proper boolean condition). The only imprecision
is that `GetIntField`/`SetIntField` are also followed by an `ExceptionCheck`
even though primitive field accessors never raise — dead but harmless (see nits).

### Fallback before mutation — preserved

`IrMethodCompiler.processMethod` runs `frontend.build` (whole-method validation),
then `emitBody` (which allocates the class/field/method/string caches), then
`beginIr`/`append`/`finishIr` (which mutate `output`, `nativeMethods`, and
`ACC_NATIVE`). `UnsupportedIrConstructException` — the only exception the
per-method fallback in `NativeObfuscator` catches — is thrown exclusively from
`build`, before any cache allocation or output mutation. So a capability miss
leaves the shared caches and the reused `MethodContext` pristine, and the legacy
generator runs on clean state. `emitBody`/`connectPhis` throw only
`IllegalStateException` for true invariant violations, which correctly remain
hard failures rather than silent fallbacks. The phase-1 nit about storing an
`int` into a reference slot is now closed: `validateLocalTypes` rejects a local
used under conflicting carriers, and `ISTORE` into a reference-typed slot throws
`UnsupportedIrConstructException` (`rejectsIntStoreIntoInstanceReceiverLocal`
asserts caches/outputs stay empty).

### Typed reference stack, phis, and merges — correct for the subset

`computeStackTypes` runs a forward operand-stack **type** analysis over reachable
blocks and rejects any merge whose carrier types disagree, so an ill-typed phi
is never constructed. Reference values flow as parameters, receivers, invoke
arguments, and typed phis; `connectPhis` re-checks carrier identity across every
edge as a backstop. `ALOAD`/`DUP`/definite-assignment all reject reads of
maybe-undefined locals. The whole-method opcode/descriptor/local/stack validation
runs over every block (reachable and dead) before lowering, as in phase 1.

---

## Status-doc claims: what held

Accurate and, as in phase 1, unusually candid. Verified true: the capability
list; the whole-method validation before mutation; the fixed instance-receiver
carrier and the `ISTORE 0` rejection; the compile-smoke set and that the test
actually executes rather than skipping; the `BUILD SUCCESSFUL` with 8/8 + 2/2
and zero skips; the honest disclosure that cache materialization is re-emitted at
each use (no dominance hoisting) and that null checks are conservatively kept for
lack of a nullability lattice; and the enumerated remaining per-method
fallbacks. The "first g++ run was a real failure, then fixed" narrative matches
the committed condition-emission fix. I did not find an overstatement in this
status doc.

---

## Nits (non-blocking)

1. **Dead exception checks after primitive field accessors.** `GetIntField` and
   `SetIntField` cannot raise, yet each is followed by an `ExceptionCheck`
   early-return. Harmless and uniform, but it is unreachable code that slightly
   bloats every field access. Could be dropped (keep the checks on the id
   lookups and the calls).
2. **No cache hoisting → duplicated setup per use (disclosed).** The `this.value++`
   method materializes the *same* class cache and field-id lookup, and re-emits
   the receiver null check, once for the `GETFIELD` and again for the `PUTFIELD`,
   even though it is the same field on the same (DUP-shared) receiver. Correct but
   verbose; the status doc owns this as deferred dominance-based hoisting.
3. **Reference carrier coarsened to `jobject`.** `IrType.REFERENCE` maps to
   `jobject`, and the emitter passes real `jstring`/`jarray` parameters into
   `CallIntMethod`/`GetIntField` relying on C++ implicit pointer conversion.
   Fine in practice (JNI reference types are related pointer types in C++ mode),
   but the IR discards the precise reference type it validated.
4. **Null-class edge inherited from the legacy path (parity).** If
   `find_class_wo_static` returns null *without* a pending exception, the
   `ExceptionCheck` passes and a null `jclass` is handed to
   `GetFieldID`/`GetMethodID`. The IR path reproduces the legacy behavior here,
   so this is a shared, out-of-scope concern rather than a phase-2 regression.

## Recommended follow-ups (not blocking)

- Drop the dead `ExceptionCheck` after `GetIntField`/`SetIntField`.
- When hoisting lands, deduplicate the class/field/method cache setup and the
  redundant null checks by dominance, as the design specifies.
- Consider narrowing the emitted reference carrier (or documenting the reliance
  on C++ pointer conversion) once reference-returning ops arrive.

---

## (a)(b)(c)(d)

**(a) Verdict.** Accept with nits. Field and invoke lowering, JNI exception
checks, receiver null checks, typed reference phis, and
fallback-before-mutation are all correct for the declared subset; the g++
compile-smoke is real and passes. No compile/correctness blocker found.

**(b) Top issues.**
1. [Nit] Dead `ExceptionCheck` emitted after `GetIntField`/`SetIntField`
   (primitive accessors never raise) — harmless, unreachable.
2. [Nit, disclosed] No dominance-based cache hoisting: class/field/method cache
   setup and receiver null checks are re-emitted at every use (twice for the
   `this.value++` field example).
3. [Nit] `IrType.REFERENCE` is coarsened to `jobject`; real `jstring`/`jarray`
   params reach `CallIntMethod`/`GetIntField` via C++ implicit conversion.
4. [Nit, parity] A null class from `find_class_wo_static` with no pending
   exception would feed a null `jclass` to `GetFieldID`/`GetMethodID`; shared
   with legacy, out of scope.

**(c) What I patched.** Nothing in the compiler. I reviewed every changed file,
ran the suite, confirmed the g++ compile-smoke executes and passes, and
generated the field/invoke emitted C++ to verify it by hand. This review
document is the only added file; no code, opcode, node, or scope change.

**(d) PR / compare URL.** DRAFT PR from `cursor/ir-phase2-fable-review-6d81`
into `cursor/ir-compiler-phase2-6d81`; compare link recorded in the PR
description.

---

## (a)(b)(c)(d)（中文）

**（a）结论。** 基本通过，尚有小问题（accept with nits）。字段与方法调用的下译、JNI 异常检查、
接收者空指针检查、带类型的引用 phi，以及「变异前先回退」的性质，在其声明的指令子集内均正确；
g++ 编译冒烟测试是真实执行并通过的。未发现编译或正确性阻断问题。

**（b）主要问题。**
1. 【小问题】在 `GetIntField`/`SetIntField` 之后仍发出 `ExceptionCheck`（基本类型字段访问不会抛异常）——无害但为不可达代码。
2. 【小问题，已披露】没有基于支配关系的缓存提升：类/字段/方法的缓存初始化与接收者空检查在每次使用处重复发出（`this.value++` 字段示例发出两次）。
3. 【小问题】`IrType.REFERENCE` 一律映射为 `jobject`；真实的 `jstring`/`jarray` 形参通过 C++ 隐式指针转换传入 `CallIntMethod`/`GetIntField`。
4. 【小问题，与 legacy 一致】若 `find_class_wo_static` 返回空且无挂起异常，空的 `jclass` 会被传给 `GetFieldID`/`GetMethodID`；此为与 legacy 共有、超出本期范围的问题。

**（c）我修改了什么。** 未改动编译器。我审阅了全部变更文件、运行了测试套件、确认 g++ 编译冒烟测试真实执行并通过，
并额外生成了字段/方法调用的发射 C++ 以逐行核对。仅新增本评审文档，未改动任何代码、指令、节点或功能范围。

**（d）PR / 对比链接。** 从 `cursor/ir-phase2-fable-review-6d81` 向
`cursor/ir-compiler-phase2-6d81` 发起的 DRAFT PR；对比链接记录在 PR 描述中。

---

## Verification commands (for reproduction)

```text
# suite + real g++ compile-smoke (executes when g++ + JNI headers are present)
./gradlew :obfuscator:test \
  --tests by.radioegor146.CodegenModeTest \
  --tests by.radioegor146.ir.IrCompilerTest
#   -> BUILD SUCCESSFUL; generatedCppPassesGppSyntaxCheckWhenToolchainAvailable
#      runs (not skipped) and the emitted TU passes g++ -std=c++17 -fsyntax-only
```
