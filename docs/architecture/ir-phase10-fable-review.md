# IR phase 10 — Fable design review of the Sol implementation

Reviewer: Claude Fable 5 (author of the IR design; reviewer of the prior phases).
Subject: `cursor/ir-compiler-phase10-6d81` (PR #73) — the phase-ten extension of
the typed-CFG IR compiler, which adds typed instance and static field access for
exact `I`, exact `J`, and object/array reference descriptors. Stacked on
`cursor/ir-phase9-sol-review-6d81`. Preferred merge base:
`cursor/ir-phase9-sol-review-6d81` at
`0e323da959d34f29b3c3cede206e48aa96a4559e` (the reviewed phase-9 tip carrying the
array-return `jarray` carrier fix). Phase-10 tip under review:
`b8cdb8efb09c135e7d119249f48feba22cf7e8f4`.
Status claims under review: `docs/architecture/ir-phase10-status.md`.

This is a compiler/transpiler review only. `native-obfuscator` re-expresses each
Java method's bytecode as a JNI C++ function; the review is scoped to code
generation correctness and fidelity — Java bytecode → typed CFG IR → C++/JNI
lowering for instance and static field access. Nothing outside code-generation
correctness is discussed here.

---

## Verdict

**Accept with nits.**

Phase 10 replaces the phase-9 "only descriptor `I`" admission for the four field
opcodes with a descriptor-driven type map that admits exact `I`, exact `J`, and
object/array reference fields, and selects the matching JNI carrier and accessor
for each. I read every changed file under
`obfuscator/src/main/java/by/radioegor146/ir/**` and the added tests in
`IrCompilerTest`, re-ran the focused suite and inspected the JUnit XML, confirmed
the g++ compile-smoke actually executed (not skipped), independently recompiled
the exact translation unit the smoke test wrote, and read the emitted C++ for the
new instance/static Int/Long/Object accessors and the null-receiver exits out of
that g++-accepted file. Every checkpoint holds:

- **Descriptor-exact IR typing.** `AsmToIr.fieldType(...)` maps `Type.INT →
  IrType.I32`, `Type.LONG → IrType.I64`, and object/array (`isReference`) →
  `IrType.REFERENCE`, and throws for anything else. The frontend admission gate
  now calls `isSupportedFieldDescriptor(...)`, the abstract-interpretation stack
  effect (`GETFIELD`/`PUTFIELD`/`GETSTATIC`/`PUTSTATIC`) pushes/pops that same
  type, and the lowering constructs the result value / pops the stored value with
  it. `IrNodes` enforces the same map a second time at node construction via
  `requireType(result/value, fieldType(descriptor), …)`, so a mistyped SSA value
  is rejected at build time, not silently emitted.
- **Matching JNI accessor selection.** `IrCppEmitter.fieldAccessor(get,
  staticField, descriptor)` composes `Get`/`Set` + (`Static` | "") +
  `fieldCarrier(descriptor)` + `Field`, and `fieldCarrier` returns `Int`, `Long`,
  or `Object`. Array fields correctly resolve to the `Object` accessor family.
- **Null receiver → exceptional exit with NPE pending.** Instance `GETFIELD`/
  `PUTFIELD` emit the receiver null check *before* the accessor; the null branch
  raises `NullPointerException` via `utils::throw_re` and takes the block exit
  (`return <default>`) with no `env->ExceptionClear()`, so the pending exception
  survives.
- **Other primitive field sorts still rejected.** `Z`, `B`, `C`, `S`, `F`, `D`
  are not in the type map, so `isSupportedFieldDescriptor` returns false and the
  method falls back per-method during admission, before any mutation.
- **Reject-before-mutation.** The unsupported-descriptor rejection lives in
  `AsmToIr.validateInstructions(...)` inside `build(...)`, before `emitBody`
  allocates any cache id and before the shell touches `output`/`nativeMethods`/
  `ACC_NATIVE`.
- **Phase-9 `jarray` return cast retained.** The array-return boundary cast in
  `IrCppEmitter` is unchanged and interoperates with a phase-10 array field read:
  an `[I` instance field is read via `GetObjectField` into a `REFERENCE` value and
  returned through `return (jarray) v…`.
- **Default stays legacy.** `Main`'s `--codegen` still defaults to `legacy`.

I found **no correctness blocker to fix**, so no compiler code was changed on this
review branch. The nits are all disclosed and non-blocking (a descriptor→carrier
map that is now duplicated across three sites, and the carried-forward nits from
prior phases).

---

## What I verified, and how

Environment: `g++ (Ubuntu 13.3.0-6ubuntu2~24.04.1) 13.3.0`, OpenJDK `21.0.10`,
JNI headers present at `/usr/lib/jvm/java-21-openjdk-amd64/include{,/linux}`. The
focused suite was run with `CC=gcc CXX=g++`.

| Check | Result |
| --- | --- |
| `./gradlew :obfuscator:test --tests …ir.IrCompilerTest --tests …CodegenModeTest` (`CC=gcc CXX=g++`) | BUILD SUCCESSFUL |
| `IrCompilerTest` JUnit XML | `tests="47" skipped="0" failures="0" errors="0"` (time 0.658 s) |
| `CodegenModeTest` JUnit XML | `tests="2" skipped="0" failures="0" errors="0"` (time 0.105 s) |
| Total | **49 tests, 0 skipped, 0 failures, 0 errors** |
| `generatedCppPassesGppSyntaxCheckWhenToolchainAvailable` really ran? | **Yes** — the JUnit `<testcase>` is `time="0.282"` with no `<skipped>` child, and the file has zero `<skipped>` elements. The concatenated translation unit compiled and `gpp-output.txt` is empty (0 bytes) |
| Independent recompile of the smoke TU | `g++ -std=c++17 -fsyntax-only -I$JH/include -I$JH/include/linux ir-smoke.cpp` on the exact file the test wrote (`/tmp/ir-compile-smoke*/ir-smoke.cpp`, 98 090 bytes) — exit 0 |
| Accessor families present in the g++-accepted TU | `GetIntField`, `SetIntField`, `GetLongField`, `SetLongField`, `GetObjectField`, `SetObjectField`, and each `Static` counterpart all appear |

Because I did not want to trust the status doc's quoted C++, every code excerpt
below is copied from the translation unit that g++ accepted in the same run.

---

## (a) Descriptor-exact IR typing — correct

The single source of the map in the frontend is `AsmToIr.fieldType(String)`:

```java
private static IrType fieldType(String descriptor) {
    Type type = Type.getType(descriptor);
    if (type.getSort() == Type.INT) {
        return IrType.I32;
    }
    if (type.getSort() == Type.LONG) {
        return IrType.I64;
    }
    if (isReference(type)) {
        return IrType.REFERENCE;
    }
    throw new IllegalArgumentException("Unsupported field descriptor " + descriptor);
}
```

`isReference` is `type.getSort() == Type.OBJECT || type.getSort() == Type.ARRAY`,
so both plain objects (`Ljava/lang/Object;`) and arrays (`[I`) map to
`REFERENCE`. This one function is consulted in four places, keeping the frontend
self-consistent:

1. **Admission** — `isSupportedFieldDescriptor(field.desc)` (a try/catch wrapper
   around `fieldType`) replaces the phase-9 `"I".equals(field.desc)` in
   `validateInstructions`.
2. **Stack effect** — the abstract interpreter pushes `fieldType(desc)` for
   `GETFIELD`/`GETSTATIC` and pops `fieldType(desc)` for `PUTFIELD`/`PUTSTATIC`
   (the receiver is still popped as `REFERENCE` for the instance forms).
3. **Lowering** — `GetField`/`GetStaticField` allocate
   `irMethod.newInstructionValue(fieldType)`, and `PutField`/`PutStaticField`
   `pop(state, fieldType, instruction)`.
4. **Node invariants** — `IrNodes.GetField`/`PutField`/`GetStaticField`/
   `PutStaticField` now call `requireType(result/value, fieldType(descriptor),
   …)` (their own copy of the same INT/LONG/OBJECT/ARRAY map) instead of the old
   `requireI32`. A wrong SSA type throws at construction.

So the IR carrier for a field is derived from its descriptor consistently at
admission, at type-checking, at lowering, and at node construction — there is no
implicit widening and no path where an `I`-typed value could stand in for a `J`
or reference field.

## (b) Matching JNI accessor selection — correct

`IrCppEmitter` selects the accessor purely from the descriptor:

```java
private String fieldAccessor(boolean get, boolean staticField, String descriptor) {
    return (get ? "Get" : "Set") + (staticField ? "Static" : "")
            + fieldCarrier(descriptor) + "Field";
}

private String fieldCarrier(String descriptor) {
    Type type = Type.getType(descriptor);
    if (type.getSort() == Type.INT) {
        return "Int";
    }
    if (type.getSort() == Type.LONG) {
        return "Long";
    }
    if (type.getSort() == Type.OBJECT || type.getSort() == Type.ARRAY) {
        return "Object";
    }
    throw new IllegalArgumentException("Unsupported field descriptor " + descriptor);
}
```

In the g++-accepted TU the instance long getter and the object/array setters read
and write through exactly these families. The `J` instance read:

```cpp
v7 = env->GetLongField(v2, cfields[3]);
if (env->ExceptionCheck() != 0) {
    return nullptr;
}
```

and the array (`[I`) instance field — read through the **Object** accessor, then
returned through the retained phase-9 `jarray` boundary cast:

```cpp
v7 = env->GetObjectField(v2, cfields[5]);
if (env->ExceptionCheck() != 0) {
    return nullptr;
}
…
B2:
return (jarray) v6;
```

This is the correct JNI shape: arrays are `jobject` subtypes, so
`GetObjectField`/`SetObjectField` are the right accessors, and the array-return
descriptor carrier is restored by the phase-9 cast. The static forms use
`GetStaticIntField`/`SetStaticIntField`, `GetStaticLongField`/
`SetStaticLongField`, and `GetStaticObjectField`/`SetStaticObjectField` against
`cclasses[…]`/`cfields[…]`, reusing the existing class-and-field cache shape with
no new array or ABI. The object instance/static tests additionally assert the
emitted C++ contains **no** `Int` accessor, so a reference field never falls
through to the integer family.

## (c) Null receiver → exceptional exit, NPE pending — correct

Instance `GETFIELD`/`PUTFIELD` emit the receiver null check before the accessor.
From the g++-accepted TU, the null-receiver `J` getter:

```cpp
if (!cfields[10]) {
    cfields[10] = env->GetFieldID(cclasses[0], …);
    if (env->ExceptionCheck() != 0) { return 0; }
}
if (arg0 == nullptr) {
    utils::throw_re(env, ((char *)(string_pool + 56LL)), ((char *)(string_pool + 1325LL)), -1);
    return 0;
}
v3 = env->GetLongField(arg0, cfields[10]);
```

and the null-receiver object setter:

```cpp
if (arg0 == nullptr) {
    utils::throw_re(env, ((char *)(string_pool + 56LL)), ((char *)(string_pool + 1401LL)), -1);
    return;
}
env->SetObjectField(arg0, cfields[11], arg1);
```

In both, the null branch raises `NullPointerException` and returns the method's
default (`return 0;` / `return;`) with **no** `env->ExceptionClear()`, so the
exception is left pending exactly as the JVM requires, and the accessor is only
reached on a non-null receiver. `nullReceiverInstanceFieldAccessUsesExceptionalExit`
asserts the `if (arg0 == nullptr)` → `utils::throw_re` → default-return ordering
and the absence of `ExceptionClear`, and both compiled forms confirm it. Note the
field-id lookup is emitted before the null check; because it only allocates a JNI
`fieldID` (no side effect on the receiver and no observable Java state change),
this does not alter JVM-visible ordering.

## (d) Other primitive field sorts still rejected — preserved

`Z`, `B`, `C`, `S`, `F`, `D` are absent from `fieldType`'s map, so
`isSupportedFieldDescriptor` returns false and the method is rejected during
admission. `rejectsOtherPrimitiveFieldSortsBeforeMutation` iterates all six
descriptors, builds a `GETSTATIC` of each, and asserts the rejection carries
`Opcodes.GETSTATIC`, `ACC_NATIVE` stays unset, `output` and `nativeMethods` stay
empty, and the class, string, field, and method caches are all size 0. So the six
remaining primitive field sorts continue to fall back per-method on clean state —
this is a deliberate slice boundary, not a regression.

## (e) Reject-before-mutation — preserved

`IrMethodCompiler.processMethod` runs `frontend.build(...)` first; the
unsupported-descriptor rejection is in `AsmToIr.validateInstructions(...)` inside
`build(...)`, which throws `UnsupportedIrConstructException` before `emitBody`
allocates any cache id and before `MethodShellEmitter.beginIr` mutates `output`,
`nativeMethods`, or `ACC_NATIVE`. Both
`rejectsOtherPrimitiveFieldSortsBeforeMutation` and
`rejectsUnsupportedAfterPhaseTenFieldsBeforeMutation` prove all four caches and
both output buffers stay empty on rejection.

## (f) Phase-9 `jarray` return cast retained; default stays legacy

The array-return boundary cast in `IrCppEmitter` is unchanged:

```88:88:obfuscator/src/main/java/by/radioegor146/ir/emit/IrCppEmitter.java
                returned = new CppAst.Cast("jarray", returned);
```

It fires when a `REFERENCE` value is returned from a method whose return
descriptor is an array, and the `instanceArrayField(…)[I` and `staticArrayField(…)[I`
methods exercise exactly this in combination with a phase-10 array field read
(shown in (b)). `returnsAllocatedArrayWithJniDescriptorCarrier` and the round-trip
tests both assert `return (jarray) v`. The default codegen mode is still
`legacy` — `Main`'s `--codegen` option `defaultValue = "legacy"` — and
`CodegenModeTest` (2 tests) passes; the legacy snippet path
(`MethodProcessor`, `Snippets`, `cppsnippets.properties`) is untouched by this
phase.

---

## Deltas from the design (all honest, all acceptable)

1. **Reference fields carry a single `REFERENCE`/`jobject` type; the element
   kind is not modeled in the IR.** Objects, strings, and arrays all lower to the
   `Object` accessor family, which is the correct JNI contract for every
   reference field. Where a specific carrier is required at a method boundary
   (an array return), the phase-9 descriptor cast supplies it. No finer field
   typing is needed for correctness at this slice.
2. **Scope of the slice.** Phase 10 admits only exact `I`, exact `J`, and
   reference field descriptors; the six other primitive sorts and everything
   else documented in `ir-phase10-status.md` still fall back per method. This is
   a staging decision enforced in `build(...)`, not a contradiction of the design.

## Nits (all non-blocking)

1. **The descriptor→carrier map is now duplicated across three sites.**
   `AsmToIr.fieldType`, `IrNodes.fieldType`, and `IrCppEmitter.fieldCarrier` each
   encode the same INT/LONG/OBJECT/ARRAY decision. They agree today, and the
   double enforcement (frontend + node) is a genuine safety property, but a
   future carrier addition must touch all three or they will drift. A shared
   helper (or having `fieldCarrier` derive from the already-computed `IrType`)
   would remove the risk. Non-blocking.
2. **`IrCppEmitter.fieldCarrier` calls `Type.getType` without the malformed-
   descriptor guard that `IrNodes.fieldType` added.** Harmless because the
   descriptor is validated at admission long before codegen, but the two copies
   are asymmetric.
3. **Field-id lookup is emitted before the receiver null check.** JVM-invisible
   (a `GetFieldID` has no receiver side effect), but it does mean a class/field
   resolution error can surface before the NPE on a genuinely broken class; both
   are correct JNI outcomes.
4. **Carried-forward prior-phase nits (unchanged, out of scope here).** The dead
   trailing `return`; `caught_exception` not proactively `DeleteLocalRef`'d; the
   dead `ExceptionCheck` after accessors that cannot throw for a valid id; no
   receiver null-check or class-cache-init dedup across repeated accesses; and
   `ISHR`'s reliance on the (universally arithmetic) implementation-defined
   signed right shift under C++17.

None of these affect observable behavior. No compiler code was changed on this
review branch because there was nothing to fix.

## 中文（简要）

结论：**接受（有小瑕疵）/ accept-with-nits。**

本次审阅仅针对 IR 编译器（Java 字节码 → typed CFG IR → C++/JNI 代码生成）中实例
与静态字段访问的正确性与保真度，不涉及任何代码生成正确性以外的话题。我阅读了
`ir/**` 下全部改动文件与 `IrCompilerTest` 新增用例，以 `CC=gcc CXX=g++` 重跑聚焦
测试并核对 JUnit XML，确认 g++ 冒烟确实执行（未跳过），并**独立重新编译**了测试
写出的整个翻译单元（98 090 字节，退出码 0），且直接从该被 g++ 接受的文件中阅读了
新增实例/静态 Int/Long/Object accessor 与 null-receiver 出口所生成的 C++。

- **描述符精确 typing**：`AsmToIr.fieldType` 将 `I → I32`、`J → I64`、对象/数组
  → `REFERENCE`，其余抛异常；该映射在准入、抽象栈效果、lowering 与
  `IrNodes.requireType` 节点不变量四处一致使用，字段访问无隐式整数拓宽。
- **JNI accessor 选择**：`fieldAccessor` + `fieldCarrier` 依描述符产生
  `Get/Set[Static]{Int,Long,Object}Field`；数组字段正确走 `Object` accessor，并
  经保留的 phase-9 `(jarray)` 边界转换在数组返回处恢复 carrier。
- **null 接收者 → 异常出口，NPE pending**：实例 `GETFIELD`/`PUTFIELD` 在 accessor
  之前发射接收者空检查，空分支 `utils::throw_re` 抛 `NullPointerException` 后返回
  默认值，且**无** `env->ExceptionClear()`，异常保持 pending。
- **其余 primitive 字段 sort 仍被拒**：`Z`/`B`/`C`/`S`/`F`/`D` 不在映射内，准入
  阶段回退；`rejectsOtherPrimitiveFieldSortsBeforeMutation` 证明四类缓存与两处
  输出均为空。
- **变更前回退**：拒绝发生在 `AsmToIr.validateInstructions`（`build(...)` 内），
  早于任何缓存 ID 分配与 `output`/`nativeMethods`/`ACC_NATIVE` 改动。
- **phase-9 `jarray` 返回转换保留；默认仍为 `legacy`**。

测试（`CC=gcc CXX=g++`）：`IrCompilerTest` 47/47、`CodegenModeTest` 2/2，共 49
个、0 跳过 / 0 失败 / 0 错误；`generatedCppPassesGppSyntaxCheckWhenToolchainAvailable`
真实运行（0.282 s，未跳过），我另行独立重编生成翻译单元亦返回 0。未发现需修复的
正确性阻塞项，故本审阅分支未改动任何编译器代码。

小瑕疵（均不阻塞）：描述符→carrier 映射现重复于三处（`AsmToIr.fieldType`、
`IrNodes.fieldType`、`IrCppEmitter.fieldCarrier`），今日一致但新增 carrier 时须
同步三处；`fieldCarrier` 未带 `IrNodes` 那样的畸形描述符保护；字段 id 查找发射
于接收者空检查之前（JVM 不可见）；以及沿用自既往阶段的死 `return`、
`caught_exception` 未主动 `DeleteLocalRef`、accessor 后的死 `ExceptionCheck`、
接收者空检查与类缓存初始化未去重、`ISHR` 依赖 C++17 下算术右移。
