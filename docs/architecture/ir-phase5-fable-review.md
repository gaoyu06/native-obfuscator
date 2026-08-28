# IR phase 5 — Fable design review of the Sol implementation

Reviewer: Claude Fable 5 (author of the IR design; reviewer of phases 1–4).
Subject: `cursor/ir-compiler-phase5-6d81` (PR #40) — the phase-five extension of
the typed-CFG IR compiler (integer divide/remainder, `int` array allocation, and
static `int` field access), stacked on `cursor/ir-phase4-fable-review-6d81`
(PR #39). Preferred merge base:
`cursor/ir-phase4-fable-review-6d81` at
`564589c687af50f72982190af96c1461b2f43a48`.
Design of record: `docs/architecture/ir-compiler.md` (§4 type system, §5
exception model, §6 JNI object model). Status claims under review:
`docs/architecture/ir-phase5-status.md`. Prior reviews:
`ir-phase1-fable-review.md` … `ir-phase4-fable-review.md`.

This is a compiler/transpiler review only. `native-obfuscator` re-expresses each
Java method's bytecode as a JNI C++ function; the review is scoped to code
generation correctness and fidelity — typed IR, CFG and exception edges,
structured C++ emit, and JNI pending-exception lifetime. Packing and
analysis-resistance are explicitly out of scope and are not discussed here.

---

## Verdict

**Accept with nits.**

Phase 5 adds three lowering families to the opt-in typed CFG compiler, and each
one is correct and faithful to the JVM semantics:

- **`IDIV` / `IREM`** lower to a typed `IntDivRem`. The C++ emitter checks the
  divisor *before* evaluating `/` or `%`, raises `ArithmeticException`, and
  guards `Integer.MIN_VALUE / -1` and `Integer.MIN_VALUE % -1` explicitly so the
  overflowing signed operations — which are undefined in C++ but defined by the
  JVM — are never evaluated. Both bytecodes are now block-terminating may-throw
  ops, so a zero divisor inside a `try` reaches the phase-4 shared `IR_CATCH_n`
  dispatch.
- **`NEWARRAY T_INT`** lowers to a typed `NewArray`. Emission checks the length
  for `< 0` (raising `NegativeArraySizeException` with Java-first semantics,
  before allocation), calls `env->NewIntArray`, and checks both the returned
  reference and `env->ExceptionCheck()`. Every failure path uses the block's
  exceptional exit. Every other `NEWARRAY` kind is rejected during admission.
- **Static `I` fields** lower `GETSTATIC` / `PUTSTATIC` to typed
  `GetStaticField` / `PutStaticField`, reusing `CachedFieldInfo(..., true)`, the
  existing `cclasses`/`cfields` arrays and mutex, `GetStaticFieldID`, and
  `GetStaticIntField` / `SetStaticIntField`.

I read every changed file under `obfuscator/src/main/java/by/radioegor146/ir/**`
and `IrCompilerTest`, re-ran the focused suite and inspected the JUnit XML,
confirmed the g++ compile-smoke actually executed (not skipped), independently
recompiled the exact translation unit the smoke test wrote, and read the emitted
C++ for `divRem`, `catchDivide`, `allocate`, and `setAndGetCounter` out of that
g++-accepted file. Every checkpoint holds. Fallback-before-mutation is intact:
a non-`I` static field is rejected inside `AsmToIr.build(...)` before any cache
id is allocated and before the shell touches `output`/`nativeMethods`/
`ACC_NATIVE`. The default is still `legacy`, and the legacy snippet path
(`MethodProcessor`, `Snippets`, `cppsnippets.properties`) is untouched. I found
**no correctness blocker to fix**, so no compiler code was changed on this review
branch. The nits are all disclosed and non-blocking (a redundant null check on a
freshly-allocated array, redundant class-cache init emitted before both static
accesses of the same class, unused local phis on exceptional edges, and the
carried-forward phase-1..4 nits).

---

## What I verified, and how

Environment: `g++ (Ubuntu 13.3.0-6ubuntu2~24.04.1) 13.3.0`, OpenJDK `21.0.10`,
JNI headers present at `${java.home}/include{,/linux}`
(`/usr/lib/jvm/java-21-openjdk-amd64`).

| Check | Result |
| --- | --- |
| `./gradlew :obfuscator:test --tests …ir.IrCompilerTest --tests …CodegenModeTest` | BUILD SUCCESSFUL |
| `IrCompilerTest` JUnit XML | `tests="22" skipped="0" failures="0" errors="0"` (time 0.552 s) |
| `CodegenModeTest` JUnit XML | `tests="2" skipped="0" failures="0" errors="0"` (time 0.099 s) |
| Total | **24 tests, 0 skipped, 0 failures, 0 errors** |
| `generatedCppPassesGppSyntaxCheckWhenToolchainAvailable` really ran? | **Yes** — the JUnit `<testcase>` is `time="0.235"` with no `<skipped>` child, and the file has no `<skipped>` elements at all. The g++/jni.h assumptions were satisfied, so `g++ -std=c++17 -fsyntax-only` compiled the concatenated 18-method TU and exited 0 |
| Independent recompile of the smoke TU | I re-ran `g++ -std=c++17 -fsyntax-only -I$JAVA_HOME/include -I$JAVA_HOME/include/linux ir-smoke.cpp` on the file the test wrote (`/tmp/ir-compile-smoke*/ir-smoke.cpp`, 30 329 bytes, `gpp-output.txt` empty) — exit 0 |
| Fallback-before-mutation | `IrMethodCompiler.processMethod` runs `frontend.build(...)` first; the non-`I` static-field rejection is in `validateInstructions` inside `build(...)`, before `emitBody` allocates any cache id and before `MethodShellEmitter.beginIr` touches `output`/`nativeMethods`/`ACC_NATIVE`; `rejectsNonIntStaticFieldBeforeMutation` proves all three caches and both outputs stay empty |

Because I did not want to trust the status doc's quoted C++, every code excerpt
below is copied from the translation unit that g++ accepted in the same run.

---

## (a) `IDIV` / `IREM` — zero-check first, no C++ UB on `MIN/-1` — correct

`CfgBuilder.mayThrow(...)` now returns true for `IDIV` and `IREM`, so each ends
its block; the frontend lowers them to `IrNodes.IntDivRem` with a
`DIVIDE`/`REMAINDER` operation. The unprotected `divRem(II)I` emits (verbatim
from the compiled TU; SSA carriers declared once, before any label):

```cpp
B0:
if (arg1 == 0) {
    utils::throw_re(env, ((char *)(string_pool + 411LL)), ((char *)(string_pool + 441LL)), -1);
    return 0;
}
if ((arg0 == ((jint) 0x80000000U)) && (arg1 == -1)) {
    v9 = ((jint) 0x80000000U);
} else {
    v9 = (jint) ((int32_t) arg0 / (int32_t) arg1);
}
```

and, in the next block, the remainder:

```cpp
B1:
if (v3 == 0) {
    utils::throw_re(env, ((char *)(string_pool + 411LL)), ((char *)(string_pool + 453LL)), -1);
    return 0;
}
if ((v2 == ((jint) 0x80000000U)) && (v3 == -1)) {
    v10 = 0;
} else {
    v10 = (jint) ((int32_t) v2 % (int32_t) v3);
}
```

- **The divisor is checked before `/` or `%` is evaluated.** The `if (… == 0)`
  guard raises `ArithmeticException` and takes the exceptional exit; the division
  only appears in the `else` branch of the overflow test, which is reachable only
  when the divisor is non-zero. So there is no divide-by-zero in the emitted C++.
- **No C++ undefined behavior on `MIN/-1`.** `Integer.MIN_VALUE / -1` and
  `Integer.MIN_VALUE % -1` overflow signed `int` (UB in C++) but are defined by
  the JVM as `Integer.MIN_VALUE` and `0` respectively. The emitter special-cases
  both before the ordinary `(int32_t) a / (int32_t) b` / `% b`, producing exactly
  the JVM results. `Integer.MIN_VALUE` is rendered as `((jint) 0x80000000U)`, not
  a bare `-2147483648` (which C++ would parse as `-(2147483648)` and widen), so
  the constant itself is well-formed.
- **Only JNI/`int32_t` carriers appear** — `jint` results, `int32_t` operands,
  no invented unsigned carrier type; the test asserts `!cpp.contains("juint")`
  and the whole TU has none.
- The trailing `return (jint) 0;` after a returning block is the method shell's
  default tail, harmless dead code (same disclosed nit as prior phases).

For the protected form, `catchDivide(II)I` (a `try { return a / b; }
catch (ArithmeticException) { return -11; }` shape) routes the zero divisor to
the shared dispatch rather than swallowing it:

```cpp
B0:
if (arg1 == 0) {
    utils::throw_re(env, ((char *)(string_pool + 411LL)), ((char *)(string_pool + 441LL)), -1);
    jint edge0 = arg0;
    jint edge1 = arg1;
    v5 = edge0;
    v6 = edge1;
    goto IR_CATCH_0;
}
…
IR_CATCH_0:
caught_exception = env->ExceptionOccurred();
env->ExceptionClear();
if (env->IsInstanceOf((jobject) caught_exception, cclasses[5])) {
    goto B2;   // handler: return -11
}
env->Throw(caught_exception);
return 0;
```

A zero divisor inside the `try` raises `ArithmeticException`, copies the handler
phi inputs, and jumps to the shared `IR_CATCH_0`, which matches
`java/lang/ArithmeticException` (`cclasses[5]`) and enters the handler. The only
`return 0` reachable after the zero guard is the rethrow tail inside the
dispatch — the exception is **not** swallowed by an early `return 0`. The test
`divideByZeroInsideTryUsesSharedCatchDispatch` asserts exactly this ordering, and
the emitted file confirms it.

## (b) `NEWARRAY T_INT` — negative-length, null, and pending-exception checks — correct

`allocate(I)I` (a `try { return new int[n].length; }
catch (NegativeArraySizeException) { return -12; }` shape) lowers to:

```cpp
B0:
if (arg0 < 0) {
    utils::throw_re(env, ((char *)(string_pool + 484LL)), ((char *)(string_pool + 521LL)), -1);
    jint edge0 = arg0;
    v6 = edge0;
    goto IR_CATCH_0;
}
v8 = (jobject) env->NewIntArray(arg0);
if ((v8 == nullptr) || (env->ExceptionCheck() != 0)) {
    jint edge1 = arg0;
    v6 = edge1;
    goto IR_CATCH_0;
}
```

- **Negative length is rejected with Java-first semantics** — the `if (arg0 < 0)`
  raises `NegativeArraySizeException` (`string_pool + 484`) before any allocation,
  matching the JVM, which throws before `newarray` allocates. Inside the `try`,
  this routes to `IR_CATCH_0`, which matches `NegativeArraySizeException`
  (`cclasses[6]`) and enters the handler returning `-12`.
- **Both failure signals are checked** — the emitter tests the returned reference
  for `nullptr` and `env->ExceptionCheck()`, so an allocation failure with or
  without a pending exception still reaches the exceptional exit.
- Every exit uses the block's phase-4 exceptional path: protected here (shared
  dispatch); for an unprotected allocation it would be `return <default>` with the
  JNI exception left pending. `lowersIntNewArrayAndRoutesFailuresToCatchDispatch`
  asserts the `allocation → nullCheck/ExceptionCheck → goto IR_CATCH_0` ordering.
- The frontend admits only `NEWARRAY` with operand `T_INT`
  (`((IntInsnNode) node).operand == Opcodes.T_INT`); every other primitive kind
  falls back per method.

## (c) Static `I` fields — existing cache shape and static JNI calls — correct

`setAndGetCounter(I)I` (a `PUTSTATIC` then `GETSTATIC` of `example/Math.counter:I`)
lowers each access to the existing lazy class-cache init followed by a lazy
field-id init and the static JNI accessor:

```cpp
if (!cfields[1]) {
    cfields[1] = env->GetStaticFieldID(cclasses[0], ((char *)(string_pool + 558LL)), ((char *)(string_pool + 54LL)));
    if (env->ExceptionCheck() != 0) { return 0; }
}
env->SetStaticIntField(cclasses[0], cfields[1], arg0);
if (env->ExceptionCheck() != 0) { return 0; }
…
v4 = env->GetStaticIntField(cclasses[0], cfields[1]);
if (env->ExceptionCheck() != 0) { return 0; }
```

- **The static path uses `GetStaticFieldID` and `GetStaticIntField` /
  `SetStaticIntField`** — the correct static JNI family, with the field id keyed
  by the descriptor `"I"` (`string_pool + 54`).
- **It reuses the existing caches** — the same `cclasses[i]` weak-global-ref slot
  with the `cclasses_mtx[i]` mutex-guarded lazy init, and the same `cfields[i]`
  slot as the instance path; `CachedFieldInfo(..., true)` distinguishes the
  static field, and `lowersStaticIntFieldsThroughExistingCacheShape` asserts a
  single cached-field entry. No new cache array or ABI was introduced.
- **Field-access exceptions use the normal exceptional exit** — the
  `ExceptionCheck` after both id lookup and the accessor routes to the block's
  exit (here `return 0`, since the method is unprotected).
- A static field whose descriptor is not exactly `I` is rejected by admission
  (`"I".equals(field.desc)` in `validateInstructions`) before any cache slot is
  allocated — see (d).

## (d) Fallback-before-mutation for non-`I` static fields — preserved

`IrMethodCompiler.processMethod` runs `frontend.build(...)` first. Opcode and
descriptor admission for `GETSTATIC`/`PUTSTATIC` lives in
`AsmToIr.validateInstructions(...)`, which requires `"I".equals(field.desc)` and
throws `UnsupportedIrConstructException` there — before `emitBody(...)` allocates
any cache id and before `MethodShellEmitter.beginIr` mutates `output`,
`nativeMethods`, or `ACC_NATIVE`. `rejectsNonIntStaticFieldBeforeMutation` builds
a `GETSTATIC …:Ljava/lang/Object;` method and asserts the rejection carries
`Opcodes.GETSTATIC`, `ACC_NATIVE` stays unset, `output` and `nativeMethods` stay
empty, and the class, field, and method caches are all size 0. So a non-`I`
static field leaves the shared caches and the reused `MethodContext` pristine and
`NativeObfuscator` runs per-method legacy fallback on clean state. The
carried-forward `rejectsIntStoreIntoInstanceReceiverLocal` still holds too.

The default was not changed — `Main`'s `--codegen` still defaults to `legacy` and
the two-arg `NativeObfuscator.process` overload passes `CodegenMode.LEGACY`;
`CodegenModeTest` (`cliDefaultsToLegacy`, `cliAcceptsIr`) passes. The legacy
snippet path is intact: no diff to `instructions/**`, `MethodProcessor.java`,
`Snippets.java`, or `cppsnippets.properties` (574 lines, present) since the
phase-4 base.

## (e) Fidelity to the legacy behavior — faithful

The static-field cache init reuses the exact `cclasses`/`cclasses_mtx`/`cfields`
arrays and `__ngen_register_methods` registration, so the JNI ABI is byte-for-bit
identical to the legacy path. The div/rem `ArithmeticException`, the array
`NegativeArraySizeException`, and the static-field id/accessor exception routing
all flow through the same `utils::throw_re` + string-pool + `ExceptionCheck`
machinery as the legacy snippets, and all new message and class-name strings are
registered in the pool via `StringPool.getOffset(...)`, so they are emitted into
the modified-UTF-8 blob.

---

## Deltas from the design (all honest, all acceptable)

1. **Explicit `MIN/-1` guards are an implementation detail the design did not
   spell out.** Design §4 keeps `IDIV`/`IREM` as low-level `int` ops; the
   implementation adds the two overflow special-cases because the C++ target
   makes the overflowing signed division/remainder undefined. This is required
   for correctness on the C++ backend and changes no observable JVM result.
2. **`NegativeArraySizeException` is raised by an explicit length guard, not by
   `NewIntArray`.** JNI's `NewIntArray` with a negative length is not guaranteed
   to raise the Java exception, so the emitter checks `length < 0` first. This is
   more faithful to the JVM (which throws before allocation) than deferring to the
   JNI call.
3. **Scope of the slice.** Phase 5 is deliberately a subset: only `T_INT`
   `NEWARRAY`, only descriptor-`I` static fields, and the same per-method fallback
   for everything else documented in `ir-phase5-status.md`. This is a staging
   decision, enforced in `build(...)`, not a contradiction of the design.

## Nits (all non-blocking)

1. **Redundant null check on a freshly-allocated array.** In `allocate`, the
   `ARRAYLENGTH` on the just-`NewIntArray`'d reference re-emits
   `if (v2 == nullptr) { … NPE … }` even though the allocation was already
   null-checked. Correct, just redundant — the "no null-check / nullability
   elimination yet" posture disclosed since phase 1.
2. **Redundant class-cache init.** `setAndGetCounter` emits the full
   `cclasses[0]` lazy-init block before both the `PUTSTATIC` and the `GETSTATIC`
   of the same class. Correct, but the `CacheMaterialization` hoist/dedup (design
   §6.3 / §7.5, marked *opt*) is not implemented yet, so the block repeats.
3. **Unused local phis on exceptional edges.** `catchDivide`/`allocate` copy
   local phis (`v5`/`v6`, etc.) onto the handler edge that the handler never
   reads. Correct, just verbose — the "no DCE yet" tradeoff from prior phases.
4. **Carried-forward phase-1..4 nits (unchanged, out of scope here).** The dead
   trailing `return`; `caught_exception` not proactively `DeleteLocalRef`'d; the
   dead `ExceptionCheck` after `GetIntField`/`SetIntField`; no receiver
   null-check dedup; and `ISHR`'s reliance on the (universally arithmetic)
   implementation-defined signed right shift under C++17.

None of these affect observable behavior. No compiler code was changed on this
review branch because there was nothing to fix.

## 中文（简要）

结论：**接受（有小瑕疵）/ accept-with-nits。**

本次审阅仅针对 IR 编译器（字节码 → JNI C++ 代码生成）的正确性与保真度——typed IR、
CFG 与异常边、结构化 C++ 发射、JNI pending-exception 生命周期，不涉及加壳与反分析。
我阅读了 `ir/**` 下全部改动文件与 `IrCompilerTest`，重跑聚焦测试并核对 JUnit XML，确认
g++ 冒烟确实执行（未跳过），并**独立重新编译**了测试写出的整个翻译单元（30 329 字节，
退出码 0），且直接从该被 g++ 接受的文件中阅读了 `divRem`、`catchDivide`、`allocate`、
`setAndGetCounter` 所生成的 C++。

- **`IDIV` / `IREM`**：先检查除数再求值 `/`/`%`；除数为零抛
  `ArithmeticException`，位于 try 内时进入共享 `IR_CATCH_n`（`catchDivide` 返回 −11，
  不被 `return 0` 吞掉）；`Integer.MIN_VALUE / -1` 与 `Integer.MIN_VALUE % -1` 被显式
  特判为 `MIN` 与 `0`，避免 C++ 有符号溢出未定义行为；`MIN` 以 `((jint) 0x80000000U)`
  形式发射；只用 `jint`/`int32_t`，无臆造无符号类型（无 `juint`）。
- **`NEWARRAY T_INT`**：先检查负长度（Java 语义：分配前抛
  `NegativeArraySizeException`），再 `env->NewIntArray`，随后同时检查空返回值与
  `ExceptionCheck`；所有失败路径走块的异常出口；其余 `NEWARRAY` 种类一律回退。
- **静态 `I` 字段**：`GETSTATIC`/`PUTSTATIC` 复用现有 `cclasses`/`cclasses_mtx`/
  `cfields` 缓存与互斥，使用 `GetStaticFieldID` 与 `GetStaticIntField` /
  `SetStaticIntField`；异常经正常异常出口。
- **变更前回退**：非 `I` 静态字段在 `AsmToIr.build(...)` 的指令准入处即被拒，早于任何
  缓存 ID 分配与 `output`/`nativeMethods`/`ACC_NATIVE` 改动；`rejectsNonIntStaticField
  BeforeMutation` 证明三类缓存与两处输出均为空。默认仍为 `legacy`；`MethodProcessor`、
  `Snippets`、`cppsnippets.properties` 未改动。

测试：`IrCompilerTest` 22/22、`CodegenModeTest` 2/2，共 24 个、0 跳过 / 0 失败 /
0 错误；`generatedCppPassesGppSyntaxCheckWhenToolchainAvailable` 真实运行（0.235 s，
未跳过），`g++ -std=c++17 -fsyntax-only` 编译含四个新增方法的 18-method 翻译单元并返回
0，我另行独立重编亦返回 0。未发现需修复的正确性阻塞项，故本审阅分支未改动任何编译器
代码。

小瑕疵（均不阻塞）：对刚分配数组的冗余空检查；同一类的静态访问前重复发射类缓存初始化；
异常边上未使用的 local phi；以及沿用自 1–4 期的死 `return`、`caught_exception` 未主动
`DeleteLocalRef`、`GetIntField`/`SetIntField` 后的死 `ExceptionCheck`、接收者空检查未
去重、`ISHR` 依赖 C++17 下算术右移。
