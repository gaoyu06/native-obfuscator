# IR phase 14 — Fable review of the float/double slice

Reviewer: Claude Fable 5 (author of the IR design; reviewer of the prior
phases). Subject: `cursor/ir-compiler-phase14-6d81`
([draft PR #95](https://github.com/gaoyu06/native-obfuscator/pull/95)) at
`ece69f5810bbefe7cdc144e09980d5ad9e5fb22d`, the phase-fourteen extension that
adds scalar JVM `float`/`double` to the opt-in direct Java bytecode → typed CFG
IR → C++/JNI compiler. Preferred merge base: the reviewed head above. Status
claims under review: `docs/architecture/ir-phase14-status.md`. Prior reviews:
`ir-phase1-fable-review.md` … `ir-phase5-fable-review.md`,
`ir-phase6-review.md` … `ir-phase13` status.

This is a compiler/transpiler correctness review only: typed IR, CFG and
exception edges, structured C++ emission, the JNI object model, and fidelity to
JVM floating-point semantics. It concerns the correctness and completeness of a
code-generation backend (Java bytecode → typed CFG → C++/JNI). Packing,
anti-analysis, and recovery resistance are out of scope and not discussed.

---

## Verdict

**Accept with nits.**

Phase 14 introduces IR carriers `F32`/`F64` (C++ `jfloat`/`jdouble`) and the
scalar `float`/`double` surface: load/store/const/return, the exact Float/Double
JNI field and invoke families, `+ - * /` and `std::fmod` remainder and negation,
`FCMPL`/`FCMPG`/`DCMPL`/`DCMPG`, and the ten I/F/L/D conversions. I read every
changed file (`ir/IrType.java`, `ir/IrMethod.java`, `ir/IrNodes.java`,
`ir/emit/CppAst.java`, `ir/emit/IrCppEmitter.java`, `ir/frontend/AsmToIr.java`,
and `IrCompilerTest`), re-ran the focused suite and read the JUnit XML,
confirmed the g++ compile-smoke actually executed (not skipped), independently
recompiled the exact translation unit the smoke test wrote, and read the emitted
C++ for the float/double scalar ops and every saturating conversion out of that
g++-accepted file. Every checkpoint holds and the six requested checks all pass.
I found **no correctness blocker to fix**, so no compiler code was changed on
this review branch. The nits are cosmetic (verbose re-referencing of pure SSA
operands, the per-constant IIFE lambda) plus the carried-forward phase-1..13
nits; none affects observable behavior.

---

## What I verified, and how

Environment: `g++ (Ubuntu 13.3.0-6ubuntu2~24.04.1) 13.3.0`, OpenJDK
`21.0.10`, JNI headers at `/usr/lib/jvm/java-21-openjdk-amd64/include{,/linux}`.
Command (`CC=gcc CXX=g++`):

```text
CC=gcc CXX=g++ ./gradlew :obfuscator:test \
  --tests by.radioegor146.ir.IrCompilerTest \
  --tests by.radioegor146.CodegenModeTest --rerun-tasks
```

| Check | Result |
| --- | --- |
| Gradle build | BUILD SUCCESSFUL in 18s |
| `IrCompilerTest` JUnit XML | `tests="68" skipped="0" failures="0" errors="0"` (time 0.914 s) |
| `CodegenModeTest` JUnit XML | `tests="2" skipped="0" failures="0" errors="0"` (time 0.122 s) |
| Total | **70 tests, 0 skipped, 0 failures, 0 errors** |
| `generatedCppPassesGppSyntaxCheckWhenToolchainAvailable` really ran? | **Yes** — the `<testcase>` is `time="0.399"` self-closing with no `<skipped>` child; the whole XML has zero `<skipped>` elements |
| Retained smoke TU | `/tmp/ir-compile-smoke577298874112137438/ir-smoke.cpp`, 230 016 bytes, `gpp-output.txt` empty; **116** `JNICALL` functions |
| Independent recompile | `g++ -std=c++17 -fsyntax-only -I$JH/include -I$JH/include/linux ir-smoke.cpp` → exit 0, empty diagnostics |

The status doc's counts (70 focused tests; 116 JNICALL in the retained unit) are
accurate. Because I did not want to trust the status doc's quoted C++, every
excerpt below is copied from the translation unit that g++ accepted in this run.

---

## (1) Focused tests and JUnit XML — pass

68 `IrCompilerTest` + 2 `CodegenModeTest` = 70 tests, 0 skipped, 0 failures, 0
errors, matching the status claim. New/relevant cases include
`emitsFloatAndDoubleJniBoundaryTypes`,
`lowersFloatAndDoubleFieldRoundTripsWithExactBitsAndJniFamilies`,
`lowersFloatAndDoubleInvokeArgumentsAndReturnsWithExactJniFamilies`,
`lowersFloatAndDoubleArithmeticRemainderNegationAndNanCompares`,
`lowersAllFloatAndDoubleConversionsWithJvmSaturation`,
`carriesFloatAndCategoryTwoDoubleThroughStackPhis`,
`admitsFloatAndDoubleConstructorSuffixWithHiddenBridge`, and
`rejectsUnsupportedAfterPhaseFourteenOpsBeforeMutation`.

## (2) Exact JNI families — never Int/Long for F/D — correct

`IrCppEmitter.fieldCarrier` maps `Type.FLOAT → "Float"` and `Type.DOUBLE →
"Double"`, and `fieldAccessor` composes `Get`/`Set` + optional `Static` +
carrier + `Field`. `invokeCallMethod` composes `Call`/`CallStatic`/
`CallNonvirtual` + carrier + `Method`, with the same Float/Double carriers.
The retained, g++-accepted unit contains all eight field families and all six
invoke families:

```text
env->GetFloatField / env->SetFloatField
env->GetDoubleField / env->SetDoubleField
env->GetStaticFloatField / env->SetStaticFloatField
env->GetStaticDoubleField / env->SetStaticDoubleField
env->CallFloatMethod / env->CallStaticFloatMethod / env->CallNonvirtualFloatMethod
env->CallDoubleMethod / env->CallStaticDoubleMethod / env->CallNonvirtualDoubleMethod
```

No float/double value is routed through the Int/Long family. `widenIntCarrier`
only widens sub-`int` sorts (`sort >= BOOLEAN && sort < INT`), so `F`/`D` field
reads and invoke returns are not touched; `narrowIntCarrier` masks only
boolean/byte/char/short, so `jfloat`/`jdouble` arguments pass unmodified. There
is no `& 1` mask, no `jint` narrowing, on any float/double path.

## (3) Category-two `D` slot accounting — correct

`IrType.F64` reports `jvmSlots == 2` and `isWide` treats it like `I64`.
`DLOAD`/`DSTORE` go through `checkedWideLocal` (requires `local + 1 <
maxLocals`), `validateLocalTypes` sets `wideContinuations[local + 1]` and
rejects reads of the continuation slot, and `computeDefiniteLocals`/
`transferDefined` mark both `local` and `local + 1` defined on a `DSTORE` and
require both on a `DLOAD`. In `lowerBlock`, `DSTORE` writes `locals[local]` and
clears `locals[local + 1]`. Local phis are created only for the head slot
(`localTypes[local + 1]` stays null with `wideContinuations[local + 1]` set), so
the continuation slot is never independently live. `numbersWideStackPhiSlotsBy
JvmSlotWidth` and `carriesFloatAndCategoryTwoDoubleThroughStackPhis` assert the
double stack phi occupies one slot (`slotIndex == 0`, carrier `F64`), matching
the two-slot `LLOAD`/`LSTORE` treatment from phase 8.

## (4) JVM floating semantics — NaN polarity, fmod, no-AE division, saturating conversions — correct

From the g++-accepted `floatScalarOps(FF)I` (SSA carriers declared before the
label; excerpt):

```cpp
v2 = (arg0 + arg1);
v4 = (v2 - v3);
v6 = (v4 * v5);
v8 = (v6 / v7);
v9 = std::fmod(v8, arg1);
v10 = (-v9);
v12 = ((std::isnan(v10) || std::isnan(v11)) ? -1 : ((v10 > v11) ? 1 : ((v10 < v11) ? -1 : 0)));
v14 = ((std::isnan(v10) || std::isnan(v13)) ?  1 : ((v10 > v13) ? 1 : ((v10 < v13) ? -1 : 0)));
```

- **NaN compare polarity.** `FCMPL`/`DCMPL` lower to `NanResult.LESS` and emit
  `? -1`; `FCMPG`/`DCMPG` lower to `NanResult.GREATER` and emit `? 1`. The
  ordered arm is `left>right ? 1 : (left<right ? -1 : 0)`, so equal values
  (including `+0.0`/`-0.0`) give `0`. This is exactly the JVM rule.
- **`FREM`/`DREM` → `std::fmod`**, not IEEE remainder. `std::fmod` computes
  `a - b*trunc(a/b)` (dividend sign, `NaN` for `inf`/zero divisor), matching
  JVM `frem`/`drem`.
- **Floating division does not enter the integer `ArithmeticException` path.**
  `FDIV`/`DDIV` emit a plain `/`; the test asserts the body has no
  `ArithmeticException`. Division by zero yields `±inf`/`NaN` per IEEE, as the
  JVM requires.
- **Signed zero / NaN payload preserved.** Float/double constants are
  materialized from raw bits via an `std::memcpy` IIFE — e.g. `FCONST_0` →
  `0x00000000U`, `FCONST_2` → `0x40000000U`, the payload NaN `0x7fc01234U`, and
  the double NaN `0x7ff8000000001234ULL` — so no host NaN canonicalization or
  literal spelling can perturb the value.

Saturating conversions, copied verbatim from the accepted unit:

```cpp
// F2I
v1 = (std::isnan(arg0) ? 0 : ((arg0 >= (jfloat) 2147483647) ? 2147483647 : ((arg0 <= (jfloat) ((jint) 0x80000000U)) ? ((jint) 0x80000000U) : (jint) arg0)));
// D2I
v1 = (std::isnan(arg0) ? 0 : ((arg0 >= (jdouble) 2147483647) ? 2147483647 : ((arg0 <= (jdouble) ((jint) 0x80000000U)) ? ((jint) 0x80000000U) : (jint) arg0)));
// F2L
v1 = (std::isnan(arg0) ? 0 : ((arg0 >= (jfloat) 9223372036854775807LL) ? 9223372036854775807LL : ((arg0 <= (jfloat) ((jlong) 0x8000000000000000ULL)) ? ((jlong) 0x8000000000000000ULL) : (jlong) arg0)));
// D2L
v1 = (std::isnan(arg0) ? 0 : ((arg0 >= (jdouble) 9223372036854775807LL) ? 9223372036854775807LL : ((arg0 <= (jdouble) ((jlong) 0x8000000000000000ULL)) ? ((jlong) 0x8000000000000000ULL) : (jlong) arg0)));
```

The order is `NaN → 0`, then clamp `≥ (src) MAX → MAX`, then clamp
`≤ (src) MIN → MIN`, and only the in-range fall-through uses the C++
toward-zero cast. Because the ordinary cast runs solely when
`MIN < operand < MAX`, there is no out-of-range float→integer C++ undefined
behavior. `MIN` is rendered as `((jint) 0x80000000U)` / `((jlong)
0x8000000000000000ULL)`, so the constants are well-formed. The float thresholds
round the way that keeps the boundary exact (e.g. `(jfloat) INT_MAX` rounds to
`2^31`, which is precisely the smallest float that must saturate). The widening
and integer-source conversions (`I2F`, `L2F`, `I2D`, `L2D`, `F2D`, `D2F`) are
direct casts with the host's default round-to-nearest, matching the JLS.

## (5) Fallback before mutation; constructor prefix local-0 still rejected — preserved

`IrMethodCompiler.processMethod` runs `frontend.build(...)` (full opcode/local
validation and complete lowering, which throws `UnsupportedIrConstructException`
at the first unsupported op) *before* `emitBody(...)`, and only then does
`MethodShellEmitter.beginIr` mutate `output`/`nativeMethods`/`ACC_NATIVE`. The
phase-14 fixture performs admitted `Z`/`B` fields, small-primitive invokes, an
`FADD` with `FSTORE`, an `F` field round trip, an `identityFloat` invoke, a
`DREM` with `DSTORE`, a `D` static field round trip, and an `identityDouble`
invoke, then reaches unsupported `FALOAD`;
`rejectsUnsupportedAfterPhaseFourteenOpsBeforeMutation` asserts the rejection
carries `Opcodes.FALOAD` and that `assertUnchangedAfterRejectedIr` holds
(`ACC_NATIVE` unset; `output` and `nativeMethods` empty; class, string, field,
and method caches all size 0).

For constructors, `processMethod` calls `frontend.build(...)` on the *original*
`<init>` before `ConstructorSpecialMethodProcessor.createNativeBody` builds the
bridge, so an unsupported constructor is rejected before any bridge or hidden
class exists (`rejectsUnsupportedConstructorBeforeAnyMutation`). The phase-12
prefix guard is intact and untouched: `forwardedReferenceLocals` includes local
`0` plus every reference parameter, and any `ASTORE` to those before the
`this`/`super` call throws "Constructor prefix changes a reference local
forwarded to the bridge" (`rejectsPrefixWritesToForwardedReferenceLocals
BeforeMutation`, exercising both a reassigned reference parameter and a
reassigned receiver). Commit `1ce12a4` only relaxed one test assertion
(`SetDoubleField(obj` → `SetDoubleField(`) so the double field's receiver may be
an SSA phi value rather than literally `obj`; it changes no production code and
does not weaken the local-0 rejection. `admitsFloatAndDoubleConstructorSuffix
WithHiddenBridge` confirms an `(FD)V` constructor compiles when the F/D work is
in the safe suffix, with the hidden static native bridge signature
`jobject ignored_hidden, jobject obj, jfloat arg0, jdouble arg1` and `<init>`
itself staying non-native.

## (6) Legacy default and retained phase-9..13 behavior — preserved

`CodegenModeTest` (2/2) passed; `Main`'s `--codegen` still declares
`defaultValue = "legacy"`, and the two-arg `NativeObfuscator.process` overload
still delegates with `CodegenMode.LEGACY`.
`obfuscator/src/main/resources/sources/cppsnippets.properties` remains present
(574 lines). The retained smoke unit still contains the phase-9 `jarray` return
cast (`return (jarray) v…`), phase-10 I/J/reference/array field families,
phase-11 interface/special invokes, phase-12 constructor bridge and prefix
checks, and phase-13 exact Z/B/C/S JNI families — all inside the same
translation unit g++ accepted. The generated method bodies are appended into an
output unit that includes `native_jvm.hpp`, which already provides `<cmath>` and
`<cstring>`, so `std::fmod`, `std::isnan`, and the `std::memcpy` constant
materialization resolve in production output, not only in the test harness.

---

## Deltas from the design (all honest, all acceptable)

1. **Saturating float→integer clamp is an implementation detail the design did
   not spell out.** Out-of-range float→int casts are undefined in C++, so the
   emitter maps `NaN → 0` and clamps to `MIN`/`MAX` before the toward-zero cast.
   This is required for correctness on the C++ backend and reproduces exactly the
   JVM `d2i`/`f2i`/`d2l`/`f2l` results.
2. **Constants are materialized bit-exactly via an `std::memcpy` IIFE** rather
   than a C++ float literal, to avoid host NaN canonicalization and decimal
   round-trip spelling issues. Correct and self-contained; no runtime helper was
   added.
3. **Scope of the slice.** Phase 14 is deliberately scalar-only: `FALOAD`/
   `FASTORE`, `DALOAD`/`DASTORE`, the other primitive array ops, `POP2`/`DUP2*`,
   `MULTIANEWARRAY`, `invokedynamic`, and the evaluator ISA remain per-method
   fallback, enforced in `AsmToIr` admission. This is a staging decision, not a
   contradiction of the design.

## Nits (all non-blocking)

1. **Verbose operand re-referencing.** `emitFloatingCompare` re-references each
   operand up to three times, and `floatingToIntegral` re-references the operand
   up to four times. The operands are pure SSA carriers (`v…`/`arg…`), so there
   is no double-evaluation hazard — just longer expressions. A `CacheMaterial
   ization`/CSE pass (design §7.5, opt) would shrink these.
2. **Per-constant IIFE lambda.** Each float/double constant emits its own
   `([]() { … std::memcpy … }())`. Correct and exact, but heavier than a shared
   `bit_cast`-style helper; harmless under `-fsyntax-only` and at any reasonable
   optimization level.
3. **Carried-forward phase-1..13 nits (unchanged, out of scope here).** The dead
   trailing `return (jint) 0;`; no DCE (unused local phis on exceptional edges);
   redundant per-access class-cache init; `caught_exception` not proactively
   `DeleteLocalRef`'d; no receiver null-check dedup; and `ISHR`'s reliance on the
   (universally arithmetic) implementation-defined signed right shift under
   C++17.

None of these affect observable behavior. No compiler code was changed on this
review branch because there was nothing to fix. The default was not changed from
`legacy`, and no array lowering was implemented.

## 中文（简要）

结论：**接受（有小瑕疵）/ accept-with-nits。**

本次审阅仅针对 IR 编译器（Java 字节码 → typed CFG → C++/JNI 代码生成）的正确性与
保真度，不涉及加壳与反分析。我阅读了 `ir/**` 下全部 phase 14 改动文件与
`IrCompilerTest`，用 `CC=gcc CXX=g++` 重跑聚焦测试并核对 JUnit XML，确认 g++ 冒烟
确实执行（未跳过），并**独立重新编译**了测试写出的整个翻译单元（230 016 字节，
116 个 `JNICALL`，退出码 0），且直接从被 g++ 接受的文件中阅读了浮点标量运算与全部
饱和转换所生成的 C++。

- **精确 JNI family**：字段用 `Get/Set[Static]Float/DoubleField`，调用用
  `Call[Static|Nonvirtual]Float/DoubleMethod`；F/D 绝不经过 Int/Long family，也无
  掩码或 `jint` 收窄。
- **D 双槽规则**：`F64` 占两个 JVM 槽，延续槽不独立存活；double 栈 phi 仅占一个
  slot（slotIndex 0）。
- **NaN 比较极性**：`FCMPL/DCMPL` 出 `-1`，`FCMPG/DCMPG` 出 `+1`，有序分支
  `>→1, <→-1, 相等→0`。
- **`frem/drem` 用 `std::fmod`**；浮点除法用普通 `/`，不进入整数
  `ArithmeticException` 路径。
- **饱和转换**：`NaN→0`，越界钳到 `MIN/MAX`，仅在范围内才做向零取整 cast，避免
  C++ 越界未定义行为；`MIN` 以 `0x80000000U`/`0x8000000000000000ULL` 形式发射。
- **常量按原始位** 通过 `std::memcpy` 精确物化，保留有符号零、无穷与 NaN 载荷。
- **变更前回退**：完整校验先于任何 mutation；phase-14 用例在 `FALOAD` 处被拒，
  三类缓存与两处输出均为空；构造器前缀写 local 0 / 转发引用参数仍被拒。
- **默认仍为 `legacy`**，`cppsnippets.properties` 保留，phase-9..13 行为保留。

测试：`IrCompilerTest` 68/68、`CodegenModeTest` 2/2，共 70 个、0 跳过 / 0 失败 /
0 错误；g++ 冒烟真实运行（0.399 s，未跳过），独立重编返回 0。未发现需修复的正确性
阻塞项，本审阅分支未改动任何编译器代码，未改默认值，也未实现数组。
