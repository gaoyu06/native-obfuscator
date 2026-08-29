# IR phase 13 — Fable review of the small-primitive field/invoke lowering

Reviewer: Claude Fable 5 (co-author of the IR compiler design of record).
Subject: `cursor/ir-compiler-phase13-6d81` at
`b5a403fd398961870eb6aadafb50b882bc17f273`
([draft PR #90](https://github.com/gaoyu06/native-obfuscator/pull/90)),
implemented on top of `cursor/ir-phase12-sol-review-6d81`
(`481b7b108388380bfbbdf94703ee56eb4b601b02`, draft PR #89).
Design of record: `docs/architecture/ir-compiler.md`, `ir-migration-plan.md`.
Status claims under review: `docs/architecture/ir-phase13-status.md`.

`native-obfuscator` transpiles Java bytecode into C++ that re-expresses each
method through JNI. This review is scoped to code-generation correctness and
fidelity for the phase-13 slice: admitting the `Z`/`B`/`C`/`S` (boolean, byte,
char, short) field and invoke descriptors into the typed-CFG IR path. Nothing
outside code generation is in scope.

---

## Verdict

**Accept.**

Phase 13 is a faithful, correctly-scoped extension of the phase-12 IR path. The
frontend admits exactly the four small integral sorts for field access and
invoke arguments/returns; the emitter selects the exact JNI
Boolean/Byte/Char/Short accessor and `Call*` families from the descriptor; the
JVM widen-on-read / narrow-on-write semantics on the shared `I32` stack carrier
are correct, including boolean low-bit masking and byte/short/char truncation;
invoke argument order and return widening are correct; `F`/`D` remain rejected;
the constructor special-void path is unchanged; rejection happens before any
bytecode mutation; and `legacy` remains the default. I found no correctness
defect, so this review is documentation-only — no compiler code was changed on
this branch.

---

## What I verified, and how

All evidence below was produced on the implementation branch with JDK 21 and
`g++` (gcc 13.3.0) present, using the exact focused command the task requires.

| Check | Result |
| --- | --- |
| `CC=gcc CXX=g++ ./gradlew :obfuscator:test --tests …ir.IrCompilerTest --tests …CodegenModeTest` | BUILD SUCCESSFUL |
| `IrCompilerTest` count (JUnit XML) | 62 tests, 0 skipped, 0 failures, 0 errors |
| `CodegenModeTest` count (JUnit XML) | 2 tests, 0 skipped, 0 failures, 0 errors |
| Total | 64 tests, all passing, none skipped |
| `generatedCppPassesGppSyntaxCheckWhenToolchainAvailable` | ran (not skipped) — the generated C++ passes a real `g++ -std=c++17 -fsyntax-only` against the JDK JNI headers |

The zero-skipped count confirms the real-toolchain smoke unit actually executed
rather than being assumed away, so the emitted small-primitive C++ compiles
against real `jni.h`, not just string-matched.

---

## Correctness assessment

### Exact JNI Boolean/Byte/Char/Short families — correct

Both selectors are descriptor-driven and produce the exact families, never
collapsing to `Int`:

- Fields: `IrCppEmitter.fieldCarrier` switches on `Type.getSort()` and returns
  `Boolean`/`Byte`/`Char`/`Short`/`Int`/`Long`/`Object`; `fieldAccessor`
  composes `Get`/`Set` + optional `Static` + carrier + `Field`. So instance and
  static reads/writes emit `Get/SetBooleanField`, `Get/SetByteField`,
  `Get/SetCharField`, `Get/SetShortField` and their `Static` variants.
- Invokes: `invokeCallMethod` switches on the *return descriptor* sort and
  builds `Call` / `CallStatic` / `CallNonvirtual` + carrier + `Method`, adding
  the four small carriers alongside the pre-existing Void/Int/Long/Object. The
  test `lowersSmallPrimitiveInvokeArgumentsAndReturnsWithExactJniFamilies`
  asserts the exact `CallStaticBooleanMethod` / `CallByteMethod` /
  `CallCharMethod` / `CallNonvirtualShortMethod` shapes across static, virtual,
  interface, and special kinds, and asserts the corresponding `…IntMethod` is
  absent.

### JVM widen / narrow on the I32 stack carrier — correct

The heap/ABI type is small, but the IR keeps the value in `I32`, so the emitter
bridges at each boundary:

- Read (`widenIntCarrier`): for sorts in `[BOOLEAN, INT)` it emits `(jint)`
  around the JNI result. This is correct sign behavior for free: `jbyte`/
  `jshort` are signed, so the cast sign-extends; `jchar` is unsigned 16-bit, so
  it zero-extends; `jboolean` is unsigned 8-bit, so `0`/`1` widen cleanly.
- Write / argument (`narrowIntCarrier`): `byte`→`(jbyte)`, `char`→`(jchar)`,
  `short`→`(jshort)` truncating casts; `boolean`→`(jboolean)((uint32_t)value & 1)`.
  The boolean low-bit mask matches how HotSpot narrows `int`→`boolean` on a
  boolean field store, and the `uint32_t` intermediate avoids signed-overflow
  UB. `int`/`long`/reference fall through unchanged.

I confirmed the emitted C++ empirically via
`lowersSmallPrimitiveFieldRoundTripsWithJvmNarrowing`, which checks the `(jint)
env->Get…Field` widening on reads, the `(jbyte|jchar|jshort)` and `& 1` narrowing
on writes, and that no `IntField` accessor leaks in for these descriptors —
across instance and static, with representative values (boolean 0/1, negative
byte, char 200, negative short).

### Invoke argument order and return widening — correct

In `emitInvoke` the receiver (or static class slot) and method-ID slot are added
first, then arguments are appended in descriptor order with
`narrowIntCarrier(argumentTypes[i], expr(args.get(i)))` — the index-matched loop
over `Type.getArgumentTypes(desc)` preserves order and narrows each small
argument to its JNI type before the varargs `Call*` call. The receiver is never
folded into the narrowed-argument loop, so instance calls keep the correct
`(obj, methodID, args…)` shape. Results are wrapped with
`widenIntCarrier(returnType, call)`, restoring the `I32` carrier. The method-body
`Return` terminator applies the symmetric `narrowIntCarrier(returnType, …)` when
the returned value is `I32`, so a method declared to return `Z`/`B`/`C`/`S`
truncates its `ireturn` value to the JNI return type.

### F/D still rejected; constructor void path unchanged — correct

`isIntLike` is `sort >= BOOLEAN && sort <= INT`, which spans boolean, char, byte,
short, int and deliberately excludes `FLOAT` (6) and `DOUBLE` (8). Frontend
validation (`validateMethodShape`, `isSupportedInvoke`,
`isSupportedInvokeReturn`, `fieldType`) all gate on `isIntLike`/`LONG`/reference,
so float/double field and invoke descriptors, arguments, and returns are still
rejected with `UnsupportedIrConstructException`. This is covered by
`rejectsFloatAndDoubleFieldSortsBeforeMutation` and
`rejectsFloatAndDoubleInvokeDescriptorsBeforeMutation` (the latter now sweeps
static, virtual, interface, and special opcodes). The constructor path is
untouched: `<init>` still requires `INVOKESPECIAL` with a `VOID` return, the
void invoke branch still emits a bare `ExpressionStatement` with the `Void`
carrier, and `CallNonvirtualVoidMethod` and the verifier-safe constructor bridge
are unchanged.

### Reject-before-mutation; legacy default — correct

`IrMethodCompiler.processMethod` runs the frontend `build` and the emitter
`emitBody` to completion before `MethodShellEmitter.beginIr` performs any
bytecode mutation; only `UnsupportedIrConstructException` is caught, and
`NativeObfuscator` falls back to the legacy generator per method (or, for a
constructor, leaves the bytecode unchanged). The new
`rejectsUnsupportedAfterPhaseThirteenOpsBeforeMutation` test exercises the case
where valid small-primitive field/invoke work precedes an unsupported `FCONST_0`
and asserts the method, output, native metadata, and all four caches are
unchanged. The parameterless `NativeObfuscator.process(...)` overload still
defaults to `CodegenMode.LEGACY`, and `CodegenModeTest` (2 tests) guards the CLI
default.

---

## Fidelity and scope

The slice stays inside its stated envelope. `IrType` is unchanged — small
primitives ride the existing `I32` carrier rather than adding new IR types, which
is the right call: the JVM already represents boolean/byte/char/short as int on
the operand stack, so the only real work is at the JNI ABI boundary, exactly
where this change puts it. No new opcodes, nodes, or passes were introduced; the
stacked phase-9 array returns, phase-10 `I`/`J`/reference fields, phase-11
interface/special invokes, and phase-12 constructor bridge and prefix-local
rejection are preserved.

One fidelity nit, not a defect: `narrowIntCarrier` masks boolean **invoke
arguments** with `& 1`, whereas the JVM does not canonicalize boolean method
arguments at the call site (it only narrows on boolean field/array stores).
Because the JNI carrier is `jboolean`, some narrowing is unavoidable, and for
javac-emitted bytecode booleans are always `0`/`1`, so this is never observable
in practice; it would only differ from a JVM for hand-crafted bytecode passing a
non-`0`/`1` int as a boolean argument. Masking is a defensible canonicalization
and is if anything safer than a plain truncation. Worth a sentence in the status
doc; not worth a code change.

---

## Tests: real, ran, adequate

Real: yes. The new units build actual ASM `MethodNode`s for the four small sorts
across instance/static fields and static/virtual/interface/special invokes,
assert the exact JNI family strings and the widen/narrow C++, assert the absence
of the `Int` family for these descriptors, cover null-receiver exceptional
exits, and cover fallback-before-mutation. The suite also compiles a large
aggregate of IR-generated C++ through real `g++`.

Ran: yes, under the required `CC=gcc CXX=g++` focused command — 62 + 2 = 64
tests, zero skipped/failed/errored, with the g++ syntax check unskipped.

Adequate for the slice. The remaining gaps are pre-existing and out of scope for
phase 13: string-match/syntax-only checks do not prove native runtime parity on
every platform, and IR-vs-legacy differential parity is still manual. These are
correctly disclosed in the status doc's non-goals.

---

## (a)(b)(c)(d)

**(a) What I did.** Reviewed the phase-13 small-primitive field/invoke lowering
for code-generation correctness against the IR design: exact JNI
Boolean/Byte/Char/Short family selection, JVM widen-on-read / narrow-on-write on
the `I32` carrier, invoke argument order and return widening, continued `F`/`D`
rejection and unchanged constructor void path, reject-before-mutation, and the
`legacy` default. Re-ran the focused `IrCompilerTest` + `CodegenModeTest` suite
with `CC=gcc CXX=g++`.

**(b) Blocking issues.** None. No compiler code was changed on this review
branch.

**(c) Findings.** All five review points hold. Real counts: `IrCompilerTest` 62,
`CodegenModeTest` 2, total 64; zero skipped, failed, or errored; the real-g++
syntax-check unit ran (not skipped). One non-blocking fidelity nit: boolean
invoke *arguments* are masked with `& 1`, which the JVM does not do at the call
site — unobservable for javac output, harmless, worth one status-doc sentence.

**(d) Comparison base.** Reviewed `cursor/ir-compiler-phase13-6d81` at
`b5a403fd398961870eb6aadafb50b882bc17f273`
([draft PR #90](https://github.com/gaoyu06/native-obfuscator/pull/90)), based on
`cursor/ir-phase12-sol-review-6d81`
(`481b7b108388380bfbbdf94703ee56eb4b601b02`, draft PR #89). This review branch is
`cursor/ir-phase13-fable-review-6d81`; no pull request is opened by the reviewer.

---

## (a)(b)(c)(d)（中文）

**（a）我做了什么。** 依据 IR 设计，对第十三阶段的 small-primitive 字段/调用
lowering 做代码生成正确性评审：精确的 JNI Boolean/Byte/Char/Short family 选择、
在 `I32` carrier 上的「读取扩展 / 写入窄化」JVM 语义、invoke 参数顺序与返回值
扩展、`F`/`D` 继续拒绝且构造函数 void 路径不变、mutation 前拒绝，以及 `legacy`
默认值。以 `CC=gcc CXX=g++` 重跑聚焦的 `IrCompilerTest` 与 `CodegenModeTest`。

**（b）阻断性问题。** 无。本评审分支未修改任何编译器代码。

**（c）结论。** 五个评审要点全部成立。真实计数：`IrCompilerTest` 62、
`CodegenModeTest` 2，共 64；跳过、失败、错误均为零；真实 g++ 语法检查单测已运行
（未跳过）。一个非阻断的 fidelity 小问题：boolean 的 **invoke 参数** 被
`& 1` 掩码，而 JVM 在调用点并不做此规范化——对 javac 产出不可观测、无害，值得在
状态文档中补一句说明。

**（d）比较基线。** 评审对象为 `cursor/ir-compiler-phase13-6d81` 的
`b5a403fd398961870eb6aadafb50b882bc17f273`
（[草稿 PR #90](https://github.com/gaoyu06/native-obfuscator/pull/90)），基于
`cursor/ir-phase12-sol-review-6d81`
（`481b7b108388380bfbbdf94703ee56eb4b601b02`，草稿 PR #89）。本评审分支为
`cursor/ir-phase13-fable-review-6d81`；评审方不开启任何 pull request。

---

## Verification commands (for reproduction)

```text
CC=gcc CXX=g++ ./gradlew :obfuscator:test \
  --tests by.radioegor146.ir.IrCompilerTest \
  --tests by.radioegor146.CodegenModeTest --rerun-tasks
# Read counts from:
#   obfuscator/build/test-results/test/TEST-by.radioegor146.ir.IrCompilerTest.xml
#   obfuscator/build/test-results/test/TEST-by.radioegor146.CodegenModeTest.xml
# Recorded: IrCompilerTest tests="62", CodegenModeTest tests="2";
#           skipped="0" failures="0" errors="0" for both.
```
