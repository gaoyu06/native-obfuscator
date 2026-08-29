# IR phase 9 — Fable design review of the Sol implementation

Reviewer: Claude Fable 5 (author of the IR design; reviewer of phases 1–8).
Subject: `cursor/ir-compiler-phase9-6d81` (PR #66) — the phase-nine extension of
the typed-CFG IR compiler, which admits reference-returning method bodies
(`ARETURN`), a typed null reference (`ACONST_NULL`), reference-null control flow
(`IFNULL` / `IFNONNULL`), and category-one `POP`. Reviewed at
`cursor/ir-compiler-phase9-6d81` tip
`32ac47d` (`Refresh final Phase 9 test evidence`). Preferred merge base:
`cursor/ir-compiler-phase8-6d81` at
`95eb5ffd2fc5a9515af65c1d15403e7c983c64a5`.
Status claims under review: `docs/architecture/ir-phase9-status.md`. Prior
reviews: `ir-phase1-fable-review.md` … `ir-phase7-review.md`,
`ir-phase8-*-review` context.

This is a compiler/transpiler review only. `native-obfuscator` re-expresses each
Java method's bytecode as a JNI C++ function; the review is scoped to code
generation correctness and fidelity — the typed IR, the CFG and its exception
edges, structured C++ emission, JNI carrier selection, and pending-exception
lifetime. Nothing about packaging or analysis resistance is in scope and none of
that is discussed here.

---

## Verdict

**Accept with nits.**

Phase 9 adds four small, well-isolated lowering families to the opt-in typed CFG
compiler, and each one is correct and faithful to the JVM semantics:

- **`ARETURN`** flows through the existing `IrType.REFERENCE` / `jobject`
  carrier, is admitted only when the method descriptor's return sort is a
  reference, and emits `return <ref>` in a `jobject`-returning JNI function.
- **Unprotected exceptional exits** in a reference-returning method return the
  JNI default `nullptr` and leave the JVM exception pending (no
  `ExceptionClear` on those paths).
- **`ACONST_NULL`** lowers to a typed `NullReference` IR value of
  `IrType.REFERENCE` that emits `nullptr`; it is a reference, not an integer
  zero.
- **`IFNULL` / `IFNONNULL`** lower to a dedicated `ReferenceBranch` terminator
  that compares the reference against `nullptr` with `==` / `!=`; they are never
  represented as integer compares.
- **`POP`** is admitted only for a single category-one operand; category-two
  operands and `POP2` are still rejected and fall back per method.

I read every changed file under
`obfuscator/src/main/java/by/radioegor146/ir/**` and the phase-9 additions to
`IrCompilerTest`, re-ran the focused suite with `CC=gcc CXX=g++` and inspected
the JUnit XML, confirmed the g++ compile-smoke actually executed (not skipped),
and **independently re-ran** `g++ -std=c++17 -fsyntax-only` on the exact
translation unit the smoke test wrote (69 270 bytes, exit 0). Every C++ excerpt
below is copied out of that g++-accepted file, not from the status doc.

Fallback-before-mutation is intact: `IrMethodCompiler.processMethod` runs
`frontend.build(...)` and `emitter.emitBody(...)` before the shell touches
`output`, `nativeMethods`, `ACC_NATIVE`, or any cache; an unsupported construct
after the newly admitted operations (`POP2`) is rejected inside `build(...)`
before any of that state changes. The default remains `legacy` and constructor
method bodies stay out of scope (`MethodProcessor.shouldProcess` still excludes
`<init>`). I found **no correctness blocker to fix**, so no compiler code was
changed on this review branch. The nits are all disclosed and non-blocking.

---

## What I verified, and how

Environment: `gcc`/`g++ (Ubuntu 13.3.0-6ubuntu2~24.04.1) 13.3.0`, OpenJDK
`21.0.10`, JNI headers present at
`/usr/lib/jvm/java-21-openjdk-amd64/include{,/linux}`.

| Check | Result |
| --- | --- |
| `CC=gcc CXX=g++ ./gradlew :obfuscator:test --tests …ir.IrCompilerTest --tests …CodegenModeTest` | BUILD SUCCESSFUL |
| `IrCompilerTest` JUnit XML | `tests="42" skipped="0" failures="0" errors="0"` (time 0.604 s) |
| `CodegenModeTest` JUnit XML | `tests="2" skipped="0" failures="0" errors="0"` (time 0.097 s) |
| Total | **44 tests, 0 skipped, 0 failures, 0 errors** |
| `generatedCppPassesGppSyntaxCheckWhenToolchainAvailable` really ran? | **Yes** — the suite XML has zero `<skipped>` elements and this case ran in 0.249 s, so the g++/jni.h assumptions were satisfied and `g++ -std=c++17 -fsyntax-only` compiled the concatenated 39-method TU to exit 0 |
| Independent recompile of the smoke TU | I re-ran `g++ -std=c++17 -fsyntax-only -I$JAVA_HOME/include -I$JAVA_HOME/include/linux ir-smoke.cpp` on the file the test wrote (`/tmp/ir-compile-smoke*/ir-smoke.cpp`, 69 270 bytes, `gpp-output.txt` empty) — exit 0 |
| Fallback-before-mutation | `rejectsUnsupportedAfterPhaseNineOpsBeforeMutation` builds a method whose `POP2` follows admitted `ACONST_NULL`/`POP`/`IFNONNULL`/`ARETURN`, and asserts the rejection carries `Opcodes.POP2`, `ACC_NATIVE` stays unset, `output`/`nativeMethods` stay empty, and the class/string/field/method caches are all size 0 |
| Default `legacy` | `CodegenModeTest.cliDefaultsToLegacy` passes; the two-arg `NativeObfuscator.process` overload still passes `CodegenMode.LEGACY` |

---

## (a) `ARETURN` — reference/jobject carrier matching the descriptor — correct

Admission derives the method's IR return type from the descriptor:
`isReference(returnType)` yields `IrType.REFERENCE`, and any other unsupported
sort still throws. `ARETURN` is accepted in `validateInstructions`, its stack
transfer pops exactly one `REFERENCE` and requires an empty residual stack, and
`lowerBlock` rejects it unless `shape.returnType == IrType.REFERENCE`
("`ARETURN does not match the method descriptor`"). `IrNodes.Return` now accepts
a `REFERENCE` value alongside `I32`/`I64`. The emitted `returnAllocatedObject`
(a `new java/lang/Object` / `dup` / `<init>` / `ARETURN`) is, verbatim from the
compiled TU:

```cpp
jobject JNICALL __ngen_native_returnAllocatedObject34(JNIEnv *env, jclass clazz) {
    …
    v2 = (jobject) env->AllocObject(cclasses[1]);
    if ((v2 == nullptr) || (env->ExceptionCheck() != 0)) {
        return nullptr;
    }
    …
    env->CallNonvirtualVoidMethod(v0, cclasses[1], cmethods[2]);
    if (env->ExceptionCheck() != 0) {
        return nullptr;
    }
    …
    B2:
    return v1;
    return (jobject) 0;
}
```

The JNI function is declared `jobject` (the same carrier the shared shell derives
from the `()Ljava/lang/Object;` descriptor), and the terminator returns the
`jobject` SSA value `v1`. The carrier and the descriptor agree, and the trailing
`return (jobject) 0;` is the shell's default tail (harmless dead code — a nit
carried since phase 1).

## (b) Unprotected exceptional exits — JNI default with the exception pending — correct

In `returnAllocatedObject` above, both the `AllocObject` failure and the
`CallNonvirtualVoidMethod` failure sit in a block with no exception edges, so
`exceptionalExit` emits `earlyReturn(method)`, which for a reference method is
`return nullptr;`. Neither path clears the pending JVM exception (`ExceptionCheck`
only *tests*; there is no `ExceptionClear` on these unprotected exits), so the
caller sees the JNI default value (`nullptr` for `jobject`) with the exception
still pending — exactly the JVM contract for an escaping exception. This is the
same machinery phases 5–8 use for `int`/`long` returns, now extended to the
`REFERENCE` default.

## (c) `ACONST_NULL` — a typed null reference — correct

`ACONST_NULL` is admitted as an `INSN`, its stack transfer pushes
`IrType.REFERENCE`, and `lowerBlock` emits `IrNodes.NullReference` whose result
is validated by `requireReference(...)`. The IR text is `%v0:ref = aconst_null`,
and the emitter lowers it to a `nullptr` assignment. `returnNull` is, verbatim:

```cpp
jobject JNICALL __ngen_native_returnNull35(JNIEnv *env, jclass clazz) {
    …
    jobject v0;
    B0:
    v0 = nullptr;
    return v0;
    return (jobject) 0;
}
```

`v0` is a `jobject`, not a `jint 0` — the null is carried as a reference, so it
composes correctly with reference sinks (`ARETURN`, `ASTORE`, reference phis,
`ATHROW`, invoke arguments).

## (d) `IFNULL` / `IFNONNULL` — reference conditions, not integer compares — correct

`isReferenceNullJump` gates admission, the stack transfer pops one `REFERENCE`,
and `lowerJump` builds `IrNodes.ReferenceBranch` with `IS_NULL` / `IS_NON_NULL`
*before* it reaches the integer-compare path — so these never fall into the
`IF_ICMP*` / `IFEQ..IFLE` integer machinery. The successor-count guard now runs
before any operand is popped, so a malformed branch is rejected before mutating
the SSA stack. `ReferenceBranch` renders its condition against `nullptr`:

```cpp
// ifNull
B0:
if (arg0 == nullptr) {
    …
    goto B2;
} else {
    …
    goto B1;
}
```

```cpp
// ifNonNull
B0:
if (arg0 != nullptr) {
    …
    goto B2;
} else {
    …
    goto B1;
}
```

The operator is `==` / `!=` against `nullptr`, i.e. a JNI reference identity test,
not an `arg0 != 0` integer comparison, and the reference operand keeps its
`jobject` carrier across both edges.

## (e) `POP` — one category-one operand only; `POP2`/category-two rejected — correct

`POP` is admitted as an `INSN`; both the type-inference transfer and `lowerBlock`
re-check `getJvmSlots() != 1` and throw "`POP requires a category-one operand`"
for a category-two top of stack. `POP2` is absent from the admitted `INSN` set,
so it falls back. `popsUnusedCategoryOneInvokeResult` discards a
`CallStaticIntMethod` result and returns void; `rejectsPopOfCategoryTwoValue`
builds `LCONST_0; POP` and asserts the rejection carries `Opcodes.POP` with a
"category-one" message. The discarded invoke still emits its side-effecting call:

```cpp
void JNICALL __ngen_native_discardInvokeResult38(JNIEnv *env, jclass clazz) {
    …
    v2 = env->CallStaticIntMethod(cclasses[0], cmethods[7], v1);
    if (env->ExceptionCheck() != 0) {
        return;
    }
    …
    B1:
    return;
}
```

`POP` discards the SSA value but the producing JNI call (and its exception check)
remains, which is exactly JVM `POP` semantics — the stack slot is dropped, the
side effect is not.

## (f) Fallback-before-mutation after newly admitted operations — preserved

`IrMethodCompiler.processMethod` runs `frontend.build(...)` then
`emitter.emitBody(...)`, and only afterward does `MethodShellEmitter.beginIr`
append to `output` / register natives / clear `ACC_NATIVE`. All phase-9 admission
lives in `AsmToIr` (`validateInstructions`, the stack transfer, and `lowerBlock`
descriptor checks), so an unsupported construct throws
`UnsupportedIrConstructException` before any cache id is allocated or any shell
state is touched. `rejectsUnsupportedAfterPhaseNineOpsBeforeMutation` proves this
for a `POP2` that follows an admitted `ACONST_NULL`/`POP`/`IFNONNULL`/`ARETURN`
prefix: `ACC_NATIVE` unset, both output buffers empty, and all four caches at
size 0, so `NativeObfuscator` runs per-method legacy fallback on pristine state.

## (g) Default pipeline and constructor scope — unchanged

`Main`'s `--codegen` still defaults to `legacy`, and `CodegenModeTest`
(`cliDefaultsToLegacy`, `cliAcceptsIr`) passes. `MethodProcessor.shouldProcess`
still excludes `<init>`, so constructor method *bodies* remain out of scope; the
phase-9 `returnAllocatedObject` test exercises an `INVOKESPECIAL <init>` *call*
(supported since phase 8) on a non-constructor method, which is a different thing.
The legacy snippet path (`MethodProcessor`, `Snippets`, `cppsnippets.properties`)
is untouched.

---

## Deltas from the design (all honest, all acceptable)

1. **Scope of the slice.** Phase 9 deliberately admits only `ARETURN` for
   reference descriptors, a typed `ACONST_NULL`, reference-null branches, and
   category-one `POP`. Float/double, `MULTIANEWARRAY`, non-`int` primitive
   arrays, reference fields, `INVOKEINTERFACE`/invokedynamic, non-constructor
   `INVOKESPECIAL`, constructor bodies, and category-two stack manipulation still
   fall back per method. This is a staging decision enforced in `build(...)`, not
   a contradiction of the design.
2. **`ReferenceBranch` is a distinct terminator, not a reuse of `Branch`.** The
   design keeps reference-null tests conceptually separate from integer compares;
   the implementation realizes that with its own node and `==`/`!=`-against-null
   emission rather than smuggling a reference through the integer condition
   lattice. This is faithful and avoids a carrier-confusion class of bug.

## Nits (all non-blocking)

1. **Cosmetic default-return inconsistency.** The block exceptional exit and the
   `ACONST_NULL`/`ARETURN` paths emit `return nullptr;`, while the shell's
   dead trailing tail emits `return (jobject) 0;`. Both are a null `jobject`;
   only the spelling differs.
2. **Dead trailing `return` after a returning block** — the shell's default tail
   (`return (jobject) 0;` / `return;`) is unreachable here, the same disclosed
   nit as every prior phase.
3. **Carried-forward phase-1..8 nits (unchanged, out of scope here).**
   `caught_exception` is not proactively `DeleteLocalRef`'d; redundant class-cache
   init is re-emitted per access; unused local phis are copied onto exceptional
   edges; and `ISHR` relies on C++'s (universally arithmetic)
   implementation-defined signed right shift.

None of these affect observable behavior. No compiler code was changed on this
review branch because there was nothing to fix.

---

## 中文（简要）

结论：**接受（有小瑕疵）/ accept-with-nits。**

本次审阅仅针对 IR 编译器（Java 字节码 → typed CFG IR → C++/JNI 代码生成）的正确性
与保真度——typed IR、CFG 与异常边、结构化 C++ 发射、JNI carrier 选择与
pending-exception 生命周期，不涉及打包或反分析。我阅读了 `ir/**` 下全部改动文件与
`IrCompilerTest` 的第九阶段新增，使用 `CC=gcc CXX=g++` 重跑聚焦测试并核对 JUnit
XML，确认 g++ 冒烟确实执行（未跳过），并**独立重新编译**了测试写出的整个翻译单元
（69 270 字节，退出码 0），且直接从该被 g++ 接受的文件中阅读了
`returnAllocatedObject`、`returnNull`、`ifNull`、`ifNonNull`、`discardInvokeResult`
所生成的 C++。

- **`ARETURN`**：经现有 `IrType.REFERENCE` / `jobject` carrier；仅当方法描述符返回
  引用时接纳；JNI 函数声明为 `jobject` 并 `return <引用值>`，carrier 与描述符一致。
- **未受保护的异常出口**：引用返回方法返回 JNI 默认值 `nullptr`，且异常保持 pending
  （这些路径不做 `ExceptionClear`）。
- **`ACONST_NULL`**：lowering 为 typed `NullReference`（`ref`），发射 `nullptr`，是
  引用而非整数零。
- **`IFNULL` / `IFNONNULL`**：lowering 为专用 `ReferenceBranch`，对 `nullptr` 做
  `==` / `!=`，绝不表示为整数比较；后继数量校验在弹栈之前完成。
- **`POP`**：仅接纳单 slot / category-one 值；category-two 与 `POP2` 仍被拒绝并
  fallback；被丢弃的 invoke 仍发射其副作用调用。
- **变更前回退**：第九阶段全部准入位于 `AsmToIr`，紧随其后的不支持指令（`POP2`）在
  `build(...)` 内即被拒，早于任何缓存 ID 分配与 `output`/`nativeMethods`/`ACC_NATIVE`
  改动；`rejectsUnsupportedAfterPhaseNineOpsBeforeMutation` 证明四类缓存与两处输出
  均为空。
- **默认与构造器范围**：默认仍为 `legacy`；`shouldProcess` 仍排除 `<init>`，构造器
  方法体不在范围内。

测试：`IrCompilerTest` 42/42、`CodegenModeTest` 2/2，共 44 个、0 跳过 / 0 失败 /
0 错误；`generatedCppPassesGppSyntaxCheckWhenToolchainAvailable` 真实运行（0.249 s，
未跳过），`g++ -std=c++17 -fsyntax-only` 编译 39-method 翻译单元并返回 0，我另行独立
重编亦返回 0。未发现需修复的正确性阻塞项，故本审阅分支未改动任何编译器代码。

小瑕疵（均不阻塞）：`nullptr` 与 `(jobject) 0` 的拼写不一致；返回块之后的死
`return` 尾；以及沿用自 1–8 期的 `caught_exception` 未主动 `DeleteLocalRef`、重复
发射类缓存初始化、异常边上未使用的 local phi、`ISHR` 依赖 C++ 下算术右移。
