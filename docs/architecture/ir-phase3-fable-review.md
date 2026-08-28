# IR phase 3 — Fable design review of the Sol implementation

Reviewer: Claude Fable 5 (author of the IR design; reviewer of phases 1 and 2).
Subject: `cursor/ir-compiler-phase3-6d81` (PR #29) — the phase-three extension of
the typed-CFG IR compiler (typed CFG → direct C++ emitter), stacked on
`cursor/ir-phase2-fable-review-6d81`.
Design of record: `docs/architecture/ir-compiler.md`, `ir-migration-plan.md`,
`ir-examples.md`. Status claims under review: `docs/architecture/ir-phase3-status.md`.
Prior reviews: `docs/architecture/ir-phase1-fable-review.md`,
`ir-phase2-fable-review.md`.

This is a compiler/transpiler review only. `native-obfuscator` re-expresses each
Java method's bytecode as a JNI C++ function; the review is scoped to code
generation correctness and fidelity. Packing and analysis-resistance are out of
scope.

---

## Verdict

**Accept with nits.**

Phase 3 adds bitwise `IAND`/`IOR`/`IXOR`, shifts `ISHL`/`ISHR`/`IUSHR`, unary
`INEG` and the narrowing conversions `I2B`/`I2S`/`I2C`, `int[]` access
(`ARRAYLENGTH`/`IALOAD`/`IASTORE`) as dedicated IR nodes with real bounds
checking, and a dedicated `String.length()` intrinsic — all on top of the
phase-1/2 arithmetic, branch, field, and invoke subset. I read every changed
file, ran the suite, verified the g++ compile-smoke actually executes (not
skips) on this machine, and independently generated the emitted C++ for the new
opcodes and read it. Every new lowering is carrier-correct, the array-region
bounds `ExceptionCheck`s are real (not dead), the `String.length` intrinsic
emits `GetStringLength` and no `GetMethodID`/`CallIntMethod`, and the
safe-fallback-before-mutation property from phase 1/2 is preserved. I found no
compile or correctness blocker to fix. The nits are all disclosed and
non-blocking: `ISHR`'s reliance on implementation-defined signed right shift,
redundant per-access receiver null checks (no null-check dedup), and the
carried-forward dead `ExceptionCheck` after `GetIntField`/`SetIntField`.

---

## What I verified, and how

Environment: `g++ (Ubuntu 13.3.0-6ubuntu2~24.04.1) 13.3.0`, OpenJDK `21.0.10`,
JNI headers present at `${java.home}/include{,/linux}`.

| Check | Result |
| --- | --- |
| `./gradlew :obfuscator:test --tests …ir.IrCompilerTest --tests …CodegenModeTest` | BUILD SUCCESSFUL — 12/12 `IrCompilerTest`, 2/2 `CodegenModeTest`, 0 skipped |
| `generatedCppPassesGppSyntaxCheckWhenToolchainAvailable` really ran? | **Yes** — 196 ms, `SUCCESS` (not skipped); the g++ assumptions were satisfied, so `g++ -std=c++17 -fsyntax-only` actually compiled the concatenated translation unit (now including `mix`/`narrow`/`bump`/`stringLength`) and exited 0 |
| Emitted C++ for `mix`/`narrow`/`bump`/`stringLength` (generated out-of-band and read from the smoke TU) | matches the status doc verbatim; see the per-item sections below |
| Fallback-before-mutation | `UnsupportedIrConstructException` is thrown only inside `AsmToIr.build(...)`, before `emitBody` allocates any cache id and before `beginIr` touches `output`/`nativeMethods`/`ACC_NATIVE`; the reject test confirms caches and outputs stay empty on fallback |

Because I did not want to trust the status doc's quoted C++, I re-read the actual
translation unit the smoke test wrote to `/tmp/ir-compile-smoke*/ir-smoke.cpp`
after the run. Every snippet below is copied from that file, i.e. from code that
`g++` accepted in the same test.

---

## (a) Bitwise / shift / narrow — correct

`mix(II)I` (`IAND`/`IOR`/`IXOR`/`ISHL`/`ISHR`/`IUSHR`) lowers to:

```cpp
v2 = (jint) ((uint32_t) arg0 & (uint32_t) arg1);
v3 = (jint) ((uint32_t) v2 | (uint32_t) arg1);
v4 = (jint) ((uint32_t) v3 ^ (uint32_t) arg1);
v5 = (jint) ((uint32_t) v4 << ((uint32_t) arg1 & 31));
v6 = (jint) ((int32_t) v5 >> ((uint32_t) arg1 & 31));
v7 = (jint) ((uint32_t) v6 >> ((uint32_t) arg1 & 31));
```

- Bitwise `&`/`|`/`^` compute on the `uint32_t` carrier and cast back to `jint`;
  the operation is bit-identical to the JVM's, and the unsigned carrier keeps the
  arithmetic well-defined.
- The JVM shift-amount mask (`& 31`) is applied explicitly on every shift, so no
  C++ shift ever has an out-of-range count (which would be undefined behaviour).
- Signedness is carried by the operand cast, matching JVM semantics: `ISHL` and
  `IUSHR` shift a `uint32_t` (logical), `ISHR` shifts an `int32_t` (arithmetic).

`narrow(I)I` (`INEG`/`I2B`/`I2S`/`I2C`) lowers to:

```cpp
v1 = (jint) (-(uint32_t) arg0);
v2 = (jint) (jbyte) v1;
v3 = (jint) (jshort) v2;
v4 = (jint) (jchar) v3;
```

- `INEG` negates through `uint32_t` so `-Integer.MIN_VALUE` wraps instead of
  overflowing.
- `I2B`/`I2S` narrow through the signed `jbyte`/`jshort` (sign-extended on
  widening back to `jint`); `I2C` narrows through the unsigned `jchar`
  (zero-extended). All three match JVM narrowing semantics exactly.

The frontend admits these opcodes (`isIntBinaryOp`/`isIntUnaryOp`) and types them
as `i32` producers/consumers, and the `CppAst` allow-lists were widened to permit
exactly `& | ^ << >>` and unary `- ~ &`, so no operator escapes the structured
AST. Correct.

Nit: `ISHR` emits `(int32_t) v >> n`. Right-shift of a negative signed integer is
implementation-defined under `-std=c++17` (it becomes well-defined arithmetic
only in C++20). Every target compiler (g++, clang, MSVC) already performs an
arithmetic shift, which is what JVM `ISHR` requires, so this is correct in
practice — but a one-line comment noting the reliance would be worth adding.

## (b) `int[]` region ops with real bounds checking — correct

`bump([II)I` (`ARRAYLENGTH` + `IALOAD` + `ICONST_1`/`IADD` + `IASTORE`) lowers to
(verbatim from the compiled TU):

```cpp
if (arg0 == nullptr) {
    utils::throw_re(env, ((char *)(string_pool + 56LL)), ((char *)(string_pool + 218LL)), -1);
    return 0;
}
v2 = env->GetArrayLength((jarray) arg0);
if (arg0 == nullptr) {
    utils::throw_re(env, ((char *)(string_pool + 56LL)), ((char *)(string_pool + 234LL)), -1);
    return 0;
}
{
    jint iaload0 = 0;
    env->GetIntArrayRegion((jintArray) arg0, arg1, 1, (&iaload0));
    if (env->ExceptionCheck() != 0) {
        return 0;
    }
    v3 = iaload0;
}
v4 = 1;
v5 = (jint) ((uint32_t) v3 + (uint32_t) v4);
if (arg0 == nullptr) {
    utils::throw_re(env, ((char *)(string_pool + 56LL)), ((char *)(string_pool + 245LL)), -1);
    return 0;
}
{
    jint iastore1 = v5;
    env->SetIntArrayRegion((jintArray) arg0, arg1, 1, (&iastore1));
    if (env->ExceptionCheck() != 0) {
        return 0;
    }
}
return v2;
```

- Each access is preceded by a receiver null check (NPE on a null array,
  matching JVM order: null check before index check).
- `GetIntArrayRegion`/`SetIntArrayRegion` themselves raise
  `ArrayIndexOutOfBoundsException` for an out-of-range index, so the following
  `ExceptionCheck` early-return is a **real** bounds edge, not dead code — this
  is the key distinction from the phase-2 dead-`ExceptionCheck` nit, and the
  status doc calls it out correctly.
- The region temporaries live in their own C++ block scope, so the
  function-level `goto` edges (labels are only at block starts) never jump into
  an initialized automatic. g++ accepted the TU, empirically confirming the
  scoping discipline holds.
- `ARRAYLENGTH` casts to `jarray` for `GetArrayLength` (which cannot throw, hence
  no `ExceptionCheck`); `IALOAD`/`IASTORE` cast to `jintArray`. The JVM verifier
  guarantees the operand is really an `int[]`, so the casts are sound.

The frontend's stack-type transfer models these precisely (`ARRAYLENGTH`:
ref→i32; `IALOAD`: ref+i32→i32; `IASTORE`: ref+i32+i32→∅), and only
`IALOAD`/`IASTORE`/`ARRAYLENGTH` are admitted — the other typed array
loads/stores (`AALOAD`/`BALOAD`/…) still fall back. Correct.

Nit: `bump` emits three identical `if (arg0 == nullptr)` guards against the same
never-reassigned reference. This is correct but redundant; like the disclosed
cache-hoisting gap, there is no null-check dedup yet. Non-blocking.

## (c) `String.length()` intrinsic — correct

`stringLength(Ljava/lang/String;)I` (`INVOKEVIRTUAL java/lang/String.length()I`)
lowers to:

```cpp
if (arg0 == nullptr) {
    utils::throw_re(env, ((char *)(string_pool + 56LL)), ((char *)(string_pool + 269LL)), -1);
    return 0;
}
v1 = env->GetStringLength((jstring) arg0);
return v1;
```

- One `GetStringLength((jstring) recv)` after a receiver null check — no
  `GetMethodID` cache lookup, no `CallIntMethod`, no class cache. The dedicated
  test asserts the emitted C++ contains `GetStringLength` and contains neither
  `GetMethodID` nor `CallIntMethod`, and I confirmed the same in the smoke TU.
- The match is exact: `isStringLength` keys on owner `java/lang/String`, name
  `length`, descriptor `()I`. Any other `int`-returning virtual (the
  `virtualInvokeMethod` test was retargeted to `Object.hashCode()`) still takes
  the generic `GetMethodID`/`CallIntMethod` path — verified in the same TU.
- The frontend's stack typing routes it through the generic `MethodInsnNode`
  transfer (pop ref, push i32), which matches the `StringLength` node's shape, so
  the type analysis and the lowering agree. Correct.

## (d) Safe fallback / fallback-before-mutation — preserved

`IrMethodCompiler.processMethod` runs `frontend.build(...)` first, and all
whole-method validation (opcode admission, descriptor/carrier checks, stack-type
inference, definite-locals, and the `connectPhis` cross-edge carrier re-check)
lives inside `build(...)`, which throws `UnsupportedIrConstructException`. Only
after a clean build does `emitBody` allocate cache ids, and only after that does
`MethodShellEmitter.beginIr` mutate `output`/`nativeMethods`/`ACC_NATIVE`. So a
capability miss on any phase-3 opcode leaves the shared caches and reused
`MethodContext` pristine, and `NativeObfuscator` catches the exception and runs
the per-method legacy fallback on clean state.

The new opcodes construct their IR nodes (`Unary`, `ArrayLength`, `ArrayLoad`,
`ArrayStore`, `StringLength`, and the new `Binary` operations) during `build(...)`
inside `lowerBlock`, so the node constructors' `requireI32`/`requireReference`
invariants are enforced on the pre-mutation side too. The
`rejectsIntStoreIntoInstanceReceiverLocal` regression still asserts a rejected
method stays non-native with empty `output`/`nativeMethods` and all four caches
at size 0. `try`/`catch` is still rejected wholesale (item 4 skipped, with a
sound structural reason), and no default flip and no opcode machine were
introduced. Correct.

## (e) g++ compile-smoke — real

The smoke test uses `Assumptions.assumeTrue(...)` on `g++`, `jni.h`, and the
platform include dir, so it *would* skip if the toolchain were missing. On this
machine it ran (196 ms, `SUCCESS`, not skipped): the assumptions were satisfied,
the phase-3 methods were emitted through the shared shell into one translation
unit with the real cache-carrier declarations, and `g++ -std=c++17
-fsyntax-only` compiled it and exited 0. I reproduced this by re-reading the
exact TU the test wrote and confirming the snippets above came from it. Real, not
skipped.

---

## Nits (all non-blocking)

1. **`ISHR` implementation-defined shift.** `(int32_t) v >> n` relies on the
   arithmetic-right-shift behaviour that is implementation-defined under C++17
   and standardized only in C++20. Correct on every real target; a comment would
   document the assumption.
2. **No receiver null-check dedup.** Repeated array accesses on the same
   unreassigned reference emit repeated identical null checks (three in `bump`).
   Correct, just verbose — the same family of tradeoff as the disclosed
   cache-materialization-per-use gap.
3. **Carried-forward dead `ExceptionCheck`.** The phase-2 dead
   `ExceptionCheck` after `GetIntField`/`SetIntField` is still present (correctly
   left out of scope to keep the phase-3 diff focused); the array-region checks
   added this phase are *not* dead.
4. **Bitwise reuse of `wrappingArithmetic`.** `IAND`/`IOR`/`IXOR` route through
   the same `uint32_t`-cast helper as `IADD`; the casts are harmless and the
   result is bit-identical. Cosmetic only.

## 中文（简要）

结论：**接受（有小瑕疵）/ accept-with-nits。**

本次审阅仅针对 IR 编译器（字节码 → JNI C++ 代码生成）的正确性与保真度，
不涉及加壳与反分析。我阅读了全部改动文件，运行了测试套件，确认 g++ 冒烟测试
确实执行（未被跳过），并独立生成并阅读了新增操作码所产出的 C++。

- **(a) 位运算 / 移位 / 窄化转换**：`IAND`/`IOR`/`IXOR` 以 `uint32_t` 计算后转回
  `jint`；移位显式施加 `& 31` 掩码，`ISHL`/`IUSHR` 走无符号、`ISHR` 走有符号
  （算术右移）；`INEG` 经 `uint32_t` 取负以正确回绕；`I2B`/`I2S` 经有符号窄化、
  `I2C` 经无符号 `jchar` 窄化。语义与 JVM 完全一致，正确。
- **(b) `int[]` 区域访问与真实边界检查**：`ARRAYLENGTH`/`IALOAD`/`IASTORE`
  下降为独立 IR 节点；先做接收者空指针检查，再由
  `GetIntArrayRegion`/`SetIntArrayRegion` 抛出 `ArrayIndexOutOfBoundsException`，
  其后的 `ExceptionCheck` 提前返回是**真实**的边界边（非死代码）；区域临时变量位于
  独立块作用域，`goto` 不会跳入已初始化的自动变量。正确。
- **(c) `String.length()` 内建**：只发出一次 `GetStringLength`，不含
  `GetMethodID`/`CallIntMethod`；测试与生成的 C++ 均已确认。其它 `int` 返回的
  虚调用仍走通用路径。正确。
- **(d) 变更前回退（fallback-before-mutation）**：全部整方法校验都在
  `AsmToIr.build(...)` 内完成并抛出 `UnsupportedIrConstructException`，早于任何缓存
  分配与 `output`/`nativeMethods`/`ACC_NATIVE` 的改动；能力不支持时共享状态保持洁净，
  按方法回退到旧后端。`try`/`catch` 仍整体拒绝（第 4 项本期跳过，理由充分）。正确。
- **(e) g++ 冒烟测试为真**：在本机（g++ 13.3.0 + OpenJDK 21）确实运行（196 ms，
  未跳过），`g++ -std=c++17 -fsyntax-only` 编译含新方法的整个翻译单元并返回 0。

小瑕疵（均不阻塞）：`ISHR` 依赖 C++17 下实现定义的有符号右移（各主流编译器均为算术
右移，实际正确）；对同一未重新赋值的引用重复发出空指针检查（无去重）；沿用自第 2 期的
`GetIntField`/`SetIntField` 之后的死 `ExceptionCheck`；位运算复用 `uint32_t`
包装助手（无害）。

未发现需修复的编译或正确性阻塞项；构建成功，`IrCompilerTest` 12/12、
`CodegenModeTest` 2/2 通过，0 跳过。
