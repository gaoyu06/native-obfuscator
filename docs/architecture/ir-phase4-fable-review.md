# IR phase 4 — Fable design review of the Sol implementation

Reviewer: Claude Fable 5 (author of the IR design; reviewer of phases 1, 2 and 3).
Subject: `cursor/ir-compiler-phase4-6d81` (PR #36) — the phase-four extension of
the typed-CFG IR compiler (first-class exception edges + structured C++ catch
dispatch), stacked on `cursor/ir-phase3-fable-review-6d81`.
Design of record: `docs/architecture/ir-compiler.md` §5 (exception model),
`ir-migration-plan.md`, `ir-examples.md`. Status claims under review:
`docs/architecture/ir-phase4-status.md`. Prior reviews:
`ir-phase1-fable-review.md`, `ir-phase2-fable-review.md`,
`ir-phase3-fable-review.md`.

This is a compiler/transpiler review only. `native-obfuscator` re-expresses each
Java method's bytecode as a JNI C++ function; the review is scoped to code
generation correctness and fidelity — CFG, SSA, exception edges, typed IR,
structured C++ emit, and JNI pending-exception lifetime. Packing and
analysis-resistance are explicitly out of scope and are not discussed here.

---

## Verdict

**Accept with nits.**

Phase 4 turns JVM exception handling into first-class control-flow edges on the
typed CFG. Each protected block carries its exception-table entries in JVM order
(`IrExceptionEdge`, catch-all encoded as a null catch type); every may-throw
instruction ends its block; `ATHROW` is a typed `Throw` terminator with no normal
fallthrough; handler entries are seeded with a single `Ref` produced by a typed
`CaughtException`, and locals reach handlers through exceptional phi edges. The
emitter routes a pending exception from every protected JNI op — including the
receiver null checks and the phase-3 `GetIntArrayRegion`/`SetIntArrayRegion`
bounds path — to one shared `IR_CATCH_n` dispatch per distinct handler set, which
captures the throwable with `ExceptionOccurred`, clears it, runs an ordered
`IsInstanceOf` chain, and rethrows with `env->Throw` when nothing matches.

I read every changed file under `obfuscator/src/main/java/by/radioegor146/ir/**`
and the tests, re-ran the focused suite, confirmed the g++ compile-smoke actually
executed (not skipped) on this machine, independently recompiled the exact
translation unit the smoke test wrote, and read the emitted C++ for all four new
exception fixtures out of that g++-accepted file. Every checkpoint holds: the
protected `IALOAD` `ExceptionCheck` goes to a shared `IR_CATCH_0` and the matching
catch runs; a catchable exception is **not** swallowed by an early `return 0`;
an unmatched catch rethrows via `env->Throw`; the catch-all dispatch has no
`IsInstanceOf`; no `goto` jumps into an initialized automatic (g++ would have
rejected the TU otherwise, and did not); and only real JNI carriers appear
(`jobject`, `jclass`, `jthrowable`, `jarray`/`jintArray`/`jstring`, and the
existing primitive carriers) — no invented types such as `juint`.
Fallback-before-mutation is intact, the default is still `legacy`, and the legacy
snippet path is untouched. I found **no correctness blocker to fix**, so no
compiler code was changed on this review branch. The nits are all disclosed and
non-blocking (dead `return 0` after an unconditional `ATHROW` goto, the
`caught_exception` local ref not being proactively deleted, an unused local phi in
the normal-return block, plus the carried-forward phase-1..3 nits).

---

## What I verified, and how

Environment: `g++ (Ubuntu 13.3.0-6ubuntu2~24.04.1) 13.3.0`, OpenJDK `21.0.10`,
CMake `3.28.3`, JNI headers present at `${java.home}/include{,/linux}`
(`/usr/lib/jvm/java-21-openjdk-amd64`).

| Check | Result |
| --- | --- |
| `./gradlew :obfuscator:test --tests …ir.IrCompilerTest --tests …CodegenModeTest` | BUILD SUCCESSFUL |
| `IrCompilerTest` JUnit XML | `tests="17" skipped="0" failures="0" errors="0"` (time 0.522 s) |
| `CodegenModeTest` JUnit XML | `tests="2" skipped="0" failures="0" errors="0"` (time 0.113 s) |
| Total | **19 tests, 0 skipped, 0 failures, 0 errors** |
| `generatedCppPassesGppSyntaxCheckWhenToolchainAvailable` really ran? | **Yes** — the JUnit `<testcase>` has time 0.212 s and no `<skipped>` child; the g++/jni.h assumptions were satisfied, so `g++ -std=c++17 -fsyntax-only` compiled the concatenated TU (all 14 methods, incl. the 4 exception fixtures) and exited 0 |
| Independent recompile of the smoke TU | I re-ran `g++ -std=c++17 -fsyntax-only -I$JAVA_HOME/include -I$JAVA_HOME/include/linux ir-smoke.cpp` on the file the test wrote (`/tmp/ir-compile-smoke*/ir-smoke.cpp`, 21 686 bytes, written during this run) — exit 0 |
| Fallback-before-mutation | `UnsupportedIrConstructException` is thrown only inside `AsmToIr.build(...)`, which runs before `IrCppEmitter.emitBody` allocates any cache id and before `MethodShellEmitter.beginIr` touches `output`/`nativeMethods`/`ACC_NATIVE`; `rejectsIntStoreIntoInstanceReceiverLocal` proves caches and outputs stay empty on fallback |

Because I did not want to trust the status doc's quoted C++, every code excerpt
below is copied from the translation unit that g++ accepted in the same run.

---

## (a) Exceptional CFG and reachability — correct

`CfgBuilder.build(...)`:

- Try-region starts/ends and handler labels are added as basic-block leaders, so
  a protected region and its handler are always block-aligned
  (`addLeader(...)`).
- Every may-throw opcode (`ARRAYLENGTH`, `IALOAD`, `IASTORE`, `GETFIELD`,
  `PUTFIELD`, `INVOKESTATIC`, `INVOKEVIRTUAL`, `ATHROW`) forces a leader at the
  next instruction (`mayThrow(...)` in the leader pass), so **a throwing
  instruction is always the last instruction in its block**. This is the property
  that makes the exceptional phi wiring sound: because no local is written after
  the throwing op, the block's end-of-block local state equals the local state at
  the throw point, and it is exactly that state the emitter copies onto the
  handler edge.
- Exception edges are attached per block in JVM exception-table order, deduped by
  catch type via a `LinkedHashSet` (`seenTypes`), matching the legacy
  `CatchesBlock` "first table entry for a type wins" behavior. A null catch type
  is preserved as the catch-all entry.
- `Graph.reachableBlocks()` follows both normal successors and exception edges, so
  handler blocks are retained; unreachable blocks (including dead handlers) are
  dropped before SSA construction.

`AsmToIr.validateHandlerEntries(...)` rejects a handler entry that also has a
reachable *normal* predecessor. That is the documented phase-4 restriction and it
is enforced structurally, not assumed.

## (b) Typed SSA at handler entries — correct

In `AsmToIr.build(...)`, a block with exceptional predecessors is seeded with a
single `IrType.REFERENCE` stack slot materialized by an `IrNodes.CaughtException`
instruction (the frontend also asserts the handler's inferred stack is exactly
`[REFERENCE]`). Locals that are definite on every incoming edge become handler
phis; `computeDefiniteLocals(...)` intersects over both normal and exception
predecessors, so a local that is only assigned inside the try body (e.g. the
`ASTORE` target in a normal `catch (E e)` prologue) is correctly treated as *not*
definite at the handler and is supplied by the handler body itself rather than a
phi. `connectPhis(...)` connects exceptional local phis from the predecessor's
end-of-block local state and re-checks carrier identity across the edge
(`connectLocalPhis` throws `UnsupportedIrConstructException` on a carrier change),
so the SSA type discipline holds across exceptional edges exactly as it does
across normal ones.

## (c) Shared structured catch dispatch — correct

`catchBounds([I)I` (a `try { return arr[-1]; } catch (AIOOBE) { return -7; }`
shape) lowers to (verbatim from the compiled TU; SSA carriers declared once,
before any label):

```cpp
jthrowable caught_exception;
jint v5; jint v6; jobject v1; jint v2; jobject v3; jobject v4; jint v7;
B0:
v5 = -1;
if (arg0 == nullptr) {
    utils::throw_re(env, ((char *)(string_pool + 56LL)), ((char *)(string_pool + 234LL)), -1);
    jobject edge0 = arg0;
    v3 = edge0;
    goto IR_CATCH_0;
}
{
    jint iaload0 = 0;
    env->GetIntArrayRegion((jintArray) arg0, v5, 1, (&iaload0));
    if (env->ExceptionCheck() != 0) {
        jobject edge1 = arg0;
        v3 = edge1;
        goto IR_CATCH_0;
    }
    v6 = iaload0;
}
{
    jobject edge2 = arg0;
    jint edge3 = v6;
    v1 = edge2;
    v2 = edge3;
    goto B1;
}
B1:
return v2;
B2:
v4 = (jobject) caught_exception;
v7 = -7;
return v7;
IR_CATCH_0:
caught_exception = env->ExceptionOccurred();
env->ExceptionClear();
if (env->IsInstanceOf((jobject) caught_exception, cclasses[2])) {
    goto B2;
}
env->Throw(caught_exception);
return 0;
return (jint) 0;
```

- **The protected `IALOAD` bounds check goes to the shared dispatch, and the
  matching catch runs.** `GetIntArrayRegion` with index `-1` raises
  `ArrayIndexOutOfBoundsException`; the following `ExceptionCheck` routes to
  `IR_CATCH_0`, which matches `cclasses[2]` (AIOOBE) and jumps to the handler `B2`,
  returning `-7`. The bounds edge is real, not dead code.
- **It does not swallow a catchable exception with `return 0`.** On the protected
  path the exceptional exit copies the handler phi inputs and jumps to the
  dispatch; there is no `return 0` between the array call and the successful load.
  The only `return 0` reachable after the call is the *rethrow* tail inside
  `IR_CATCH_0` (after `env->Throw`), which is correct. The test asserts this
  ordering (`swallowedReturn < 0 || swallowedReturn > successfulLoad`), and the
  emitted file confirms it.
- **The receiver null check is also routed to the catch, not to `return 0`.** In a
  protected block, `nullCheck(...)` emits `utils::throw_re(... NPE ...)` followed
  by the same phi-copy-and-`goto IR_CATCH_0` exceptional exit (lines above). So a
  null array inside a `try` raises an NPE that the dispatch then offers to the
  handlers — the phase-3 behavior of silently `return 0`-ing past a live catch is
  gone for protected ops.
- **No `goto` jumps into an initialized automatic.** All SSA carriers and
  `caught_exception` are function-scope, uninitialized declarations emitted before
  the first label. The only initialized automatics (`iaload0`/`iastore0`,
  `edge0..3`, `resolved_class_n`) live inside nested `{ }` scopes, and every
  `goto` leaves those scopes toward a top-level label. g++ diagnoses
  "jump to label crosses initialization" as an error under `-std=c++17`; it
  accepted this TU, empirically confirming the scoping discipline.

`rethrowBounds([I)I` is the same body with an `NullPointerException` catch. The
AIOOBE thrown by the `-1` index does not match, so `IsInstanceOf` is false and
control reaches `env->Throw(caught_exception); return 0;` — **unmatched catch
rethrows via `env->Throw`**, then returns so the JVM re-raises the pending
throwable. Verified in the emitted file and by the `unmatchedCatchRethrows...`
test.

`catchAny([I)I` (null catch type) lowers its dispatch to:

```cpp
IR_CATCH_0:
caught_exception = env->ExceptionOccurred();
env->ExceptionClear();
goto B2;
```

— **the catch-all dispatch has no `IsInstanceOf`** and jumps straight to the
handler. Confirmed in the file and by `representsCatchAllWithoutATypeTest`.

`catchThrown(Ljava/lang/Throwable;)I` exercises the `ATHROW` terminator:

```cpp
if (arg0 == nullptr) {
    utils::throw_re(env, ..., ((char *)(string_pool + 354LL)), -1);
} else {
    env->Throw((jthrowable) arg0);
}
if (env->ExceptionCheck() != 0) {
    jobject edge0 = arg0;
    v1 = edge0;
    goto IR_CATCH_0;
}
return 0;
```

`ATHROW` of a null reference raises NPE; otherwise it rethrows the operand, and
the pending exception is routed to the shared dispatch, which matches
`java/lang/Throwable` and enters the handler. Correct.

The shared dispatch is keyed on the ordered `(catchTypes, handlerIds)` of the
block's exception set (`HandlerSet.equals/hashCode`), so exactly one `IR_CATCH_n`
is materialized per distinct handler set — matching the design's "materialize
dispatch once per distinct handler set" and the legacy `context.catches` dedup.

## (d) Fidelity to the legacy exception behavior — faithful

The IR dispatch reproduces the legacy `cppsnippets.properties` catch model
one-for-one:

| Legacy snippet | IR emission |
| --- | --- |
| `TRYCATCH_START` (`ExceptionOccurred` + `ExceptionClear` + save throwable) | `caught_exception = env->ExceptionOccurred(); env->ExceptionClear();` |
| `TRYCATCH_CHECK_STACK` (`if (IsInstanceOf(...)) goto handler;`) | `if (env->IsInstanceOf((jobject) caught_exception, cclasses[n])) { goto Bk; }` |
| `TRYCATCH_ANY_L` (`goto handler;`) | catch-all `goto Bk;` |
| `TRYCATCH_END_STACK` (`env->Throw(...); return 0;`) | `env->Throw(caught_exception); return 0;` |
| `TRYCATCH_EMPTY` (`if (ExceptionCheck()) return 0;`) | unprotected block exceptional exit = `return 0` (pending exception propagates) |

Handler class caching still flows through the shared `MethodShellEmitter` and the
existing `cclasses`/`cclasses_mtx` arrays and `__ngen_register_methods`, so the
JNI ABI and registration are byte-for-byte identical to today.

## (e) Fallback-before-mutation — preserved

`IrMethodCompiler.processMethod` runs `frontend.build(...)` first; all
whole-method validation (method shape, opcode admission, local/stack typing,
handler-entry shape, reachability, definite locals, and the exceptional/normal
phi carrier re-checks) lives inside `build(...)` and throws
`UnsupportedIrConstructException` there. Only after a clean build does
`emitBody(...)` allocate cache ids, and only after that does
`MethodShellEmitter.beginIr` mutate `output`/`nativeMethods`/`ACC_NATIVE`. So a
capability miss on any exception construct (a handler with a normal predecessor, a
non-reference-on-stack handler entry, an unsupported opcode inside a try, a
malformed/empty region) leaves the shared caches and the reused `MethodContext`
pristine, and `NativeObfuscator` catches the exception and runs per-method legacy
fallback on clean state. `rejectsIntStoreIntoInstanceReceiverLocal` still asserts
a rejected method stays non-native with empty `output`/`nativeMethods` and all
four caches at size 0. No default flip and no opcode-stream machine were
introduced; `CodegenMode` defaults to `LEGACY` (`Main` `--codegen` default
`legacy`; the two-arg `NativeObfuscator.process` overload passes
`CodegenMode.LEGACY`), and `MethodProcessor`/`Snippets`/`cppsnippets.properties`
are untouched.

---

## Deltas from design §5 (all honest, all acceptable)

1. **Where the throwable is captured.** The design describes the landing block as
   *beginning* with `CaughtException = env->ExceptionOccurred()`. The
   implementation captures `caught_exception = env->ExceptionOccurred()` in the
   shared `IR_CATCH_n` dispatch and has `CaughtException` in the handler read that
   function-scope variable (`v = (jobject) caught_exception`). Every path into a
   handler goes through the dispatch, so the variable is always assigned before it
   is read; this is semantically equivalent to the design and is the natural shape
   once dispatch is shared across a handler set.
2. **`ExceptionClear` before the `IsInstanceOf` chain.** The design's prose is an
   "ordered `InstanceOf` chain terminating in rethrow"; the implementation clears
   the pending exception before running the chain and re-raises with `env->Throw`
   on the no-match tail. This matches the legacy `TRYCATCH_START`/`END_STACK`
   pairing and is the correct JNI discipline (do not run general JNI calls with a
   pending exception). Faithful, and arguably more precise than the prose.
3. **Local-ref lifetime of the caught throwable.** Legacy inserts the caught
   throwable into `refs` for bulk `clear_refs`; the IR path holds it only in
   `caught_exception` and does not `DeleteLocalRef` it. This is a bounded,
   method-scoped local ref freed by the JVM when the native frame returns — no
   leak across calls — and it is consistent with the design's "phase-1 default
   lowering, precise deletion is a later `LocalRefLiveness` pass" posture. Noted as
   a nit, not a delta that changes behavior.
4. **Scope of the exception slice.** Phase 4 is deliberately a subset: handlers
   with a normal predecessor, `monitor`/`finally`/nested unsupported bodies,
   switches, wide carriers, and most opcodes still fall back per method. This is
   documented in `ir-phase4-status.md` and enforced in `build(...)`; it is a
   staging decision, not a contradiction of §5.

---

## Nits (all non-blocking)

1. **Dead `return 0;` after an unconditional `ATHROW`.** In `catchThrown`, the
   `ATHROW` always leaves a pending exception, so `if (env->ExceptionCheck())
   goto IR_CATCH_0;` always takes the goto and the trailing `return 0;` is
   unreachable. Harmless dead code (the emitter always appends `earlyReturn` after
   the throw terminator); g++ accepts it.
2. **`caught_exception` not proactively deleted.** See delta 3 — a
   `DeleteLocalRef` (or reusing the existing `refs` set) would match the legacy
   ref bookkeeping; it is safe as-is because JNI frees the local ref on return.
3. **Unused local phi in the normal-return block.** `catchBounds`/`catchAny` emit
   a `jobject v1;` phi for the array local in the fallthrough return block that is
   never used (the block only returns the loaded stack value). Correct, just
   verbose — the same "no DCE yet" tradeoff disclosed in prior phases.
4. **Carried-forward phase-1..3 nits (unchanged, out of scope for phase 4).** No
   receiver null-check dedup (each protected op re-emits `if (arg0 == nullptr)`);
   the dead `ExceptionCheck` after `GetIntField`/`SetIntField`; and `ISHR`'s
   reliance on the (universally arithmetic) implementation-defined signed
   right-shift under C++17.

None of these affect observable behavior. No compiler code was changed on this
review branch because there was nothing to fix.

## 中文（简要）

结论：**接受（有小瑕疵）/ accept-with-nits。**

本次审阅仅针对 IR 编译器（字节码 → JNI C++ 代码生成）的正确性与保真度——CFG、SSA、
异常边、typed IR、结构化 C++ 发射与 JNI pending-exception 生命周期，不涉及加壳与
反分析。我阅读了 `ir/**` 下全部改动文件与测试，重跑了聚焦测试套件，确认 g++ 冒烟测试
确实执行（未被跳过），并**独立重新编译**了测试写出的整个翻译单元，且直接从该被 g++
接受的文件中阅读了全部四个异常样例所生成的 C++。

- **异常 CFG 与可达性**：try 边界/handler 入口为基本块 leader；每个可能抛出的指令都
  结束其所在块（因此抛出点即块尾，异常边上的 local 状态即抛出时的状态，phi 连接因此
  健全）；异常边按 JVM 表顺序存储并按类型去重（catch-all 为 `null`）；可达性同时跟随
  正常与异常后继。正确。
- **handler 处的 typed SSA**：handler 入口由 typed `CaughtException` 提供唯一 `Ref`
  栈值；仅在所有入边均已定义的 local 才建立 phi；跨异常边重新校验 carrier。正确。
- **共享结构化 catch dispatch**：受保护的 `IALOAD` 越界检查跳转到共享 `IR_CATCH_0`
  并进入匹配的 catch（返回 `-7`）；**不会**用 `return 0` 吞掉可捕获异常；未匹配时经
  `env->Throw(caught_exception)` 重新抛出；catch-all 无 `IsInstanceOf`；所有 SSA
  载体与 `caught_exception` 为函数级未初始化声明，`goto` 不跨越已初始化的自动变量
  （g++ 接受整个 TU 即为佐证）；仅使用真实 JNI 类型，无 `juint` 之类臆造类型。
- **与旧路径保真**：dispatch 与旧 `TRYCATCH_*` 片段逐条对应（捕获/清除/有序类型判定/
  重抛/未保护块传播）。
- **变更前回退**：所有能力缺失都在 `AsmToIr.build(...)` 内抛出，早于缓存 ID 分配与
  `output`/`nativeMethods`/`ACC_NATIVE` 的改动；默认仍为 `legacy`，旧片段路径未删。

测试：`IrCompilerTest` 17/17、`CodegenModeTest` 2/2，0 跳过 / 0 失败 / 0 错误；
`generatedCppPassesGppSyntaxCheckWhenToolchainAvailable` 真实运行（0.212 s，未跳过），
`g++ -std=c++17 -fsyntax-only` 编译含全部四个异常方法的翻译单元并返回 0，我另行独立
重编亦返回 0。未发现需修复的正确性阻塞项，故本审阅分支未改动任何编译器代码。

小瑕疵（均不阻塞）：`ATHROW` 无条件 goto 之后的死 `return 0`；`caught_exception`
未主动 `DeleteLocalRef`（随帧返回自动释放）；正常返回块中未使用的 local phi；以及沿用
自 1–3 期的接收者空指针检查未去重、`GetIntField`/`SetIntField` 后的死 `ExceptionCheck`、
`ISHR` 依赖 C++17 下实现定义的算术右移。
