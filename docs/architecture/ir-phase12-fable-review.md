# IR phase 12 — Fable design review of the Sol implementation

Reviewer: Claude Fable 5 (author of the IR design; reviewer of the prior
phases). Subject: `cursor/ir-compiler-phase12-6d81` (draft PR #84) — the
phase-twelve extension of the typed-CFG IR compiler that admits supported
constructor (`<init>`) method bodies, stacked on
`cursor/ir-compiler-phase11-6d81`.

Preferred merge base:
`cursor/ir-compiler-phase11-6d81` at
`6fc64927a53c777a36c38e54aaed01b1bd696ed3` (draft PR #78).
Review branch head under review:
`28147db4d8f0379a16105526279f825eec0c8bb4`.
Status claims under review: `docs/architecture/ir-phase12-status.md`.
Design of record: `docs/architecture/ir-compiler.md`.

This is a compiler/transpiler review only. `native-obfuscator` re-expresses a
Java method's bytecode as a JNI C++ function; the review is scoped to code
generation correctness and fidelity — Java bytecode → typed IR → C++/JNI
lowering, the verifier-safe constructor split, JNI receiver/argument mapping,
exception lifetime, and fallback-before-mutation. Analysis-resistance and
packing are out of scope and are not discussed.

---

## Verdict

**Accept.**

Phase 12 lowers supported constructor bodies with a verifier-safe split, and
the split is correct on every point the review calls for:

- The JVM class-file rules forbid `ACC_NATIVE` on `<init>`. The constructor is
  never marked native — only the hidden static helper is.
- Exactly one linear direct `this(...)`/`super(...)` call stays in the Java
  constructor. The native suffix begins after that call, so the
  constructor-chain call is not executed twice.
- The hidden bridge is a static method whose first declared parameter is the
  initialized receiver; it maps to IR local 0 (`REFERENCE`) and to `jobject
  obj` in the emitted C++.
- The complete constructor is admitted to typed IR before any output, cache,
  method flag, bridge, or bytecode is mutated. A capability miss leaves the
  constructor as untouched Java bytecode.
- A rejected constructor is left as Java bytecode and is not sent to the
  legacy method shell (which deliberately has no constructor path).
- The CLI/API default is still `legacy`; constructors are excluded in legacy
  mode.

I read every changed file, re-ran the focused suite with `CC=gcc CXX=g++
--rerun-tasks` and inspected the JUnit XML, confirmed the g++ compile-smoke
actually executed (not skipped), independently recompiled the exact
translation unit the smoke test wrote, and read the emitted constructor-bridge
C++ out of that g++-accepted file. Every checkpoint holds. I found **no
correctness blocker to fix**, so no compiler code was changed on this review
branch. The nits are disclosed and non-blocking.

---

## What I verified, and how

Environment: `g++ (Ubuntu 13.3.0-6ubuntu2~24.04.1) 13.3.0`, OpenJDK
`21.0.10`, JNI headers present at `${JAVA_HOME}/include{,/linux}`
(`/usr/lib/jvm/java-21-openjdk-amd64`).

| Check | Result |
| --- | --- |
| `CC=gcc CXX=g++ ./gradlew :obfuscator:test --tests …ir.IrCompilerTest --tests …CodegenModeTest --rerun-tasks` | BUILD SUCCESSFUL |
| `IrCompilerTest` JUnit XML | `tests="57" skipped="0" failures="0" errors="0"` (time 0.705 s) |
| `CodegenModeTest` JUnit XML | `tests="2" skipped="0" failures="0" errors="0"` (time 0.118 s) |
| Total | **59 tests, 0 skipped, 0 failures, 0 errors** |
| `generatedCppPassesGppSyntaxCheckWhenToolchainAvailable` really ran? | **Yes** — the JUnit `<testcase>` is `time="0.268"` with no `<skipped>` child, and the file has no `<skipped>` elements |
| Independent recompile of the smoke TU | `g++ -std=c++17 -fsyntax-only -I$JAVA_HOME/include -I$JAVA_HOME/include/linux ir-smoke.cpp` on the retained unit (`/tmp/ir-compile-smoke*/ir-smoke.cpp`, 61 `JNICALL` functions, 2 `special_init` bridges) — exit 0, empty diagnostics |

The C++ excerpts below are copied from the translation unit that g++ accepted
in the same run, not from the status doc.

---

## (a) JVM never sees a native `<init>` — correct

`MethodShellEmitter.getSpecialMethodProcessor("<init>")` now returns a
`ConstructorSpecialMethodProcessor` instead of `null`. Its `preProcess`
allocates the hidden helper and stores it as `context.proxyMethod`. In
`MethodShellEmitter.begin(...)`, the `context.proxyMethod != null` branch marks
**that** node native:

```77:77:obfuscator/src/main/java/by/radioegor146/ir/emit/MethodShellEmitter.java
            context.nativeMethod.access |= Opcodes.ACC_NATIVE;
```

`context.method` (the constructor) is never touched by that branch. In
`postProcess` the constructor's instruction list is replaced, but its access
flags are left alone, so `ACC_NATIVE` is never set on `<init>`. The helper is
created native and static:

```47:49:obfuscator/src/main/java/by/radioegor146/special/ConstructorSpecialMethodProcessor.java
                    methodNode.access = Opcodes.ACC_NATIVE | Opcodes.ACC_PUBLIC
                            | Opcodes.ACC_STATIC | Opcodes.ACC_SYNTHETIC
                            | Opcodes.ACC_BRIDGE;
```

`admitsSimpleConstructorOnlyForIrAndKeepsGetterOnIr` asserts
`constructor.access & ACC_NATIVE == 0` and that the proxy node is native.
`rewrittenConstructorPassesJvmVerification` goes further: it writes the
rewritten owner class with `COMPUTE_FRAMES`, loads it, and constructs an
instance. The constructor loads and verifies; instantiation fails only with
`UnsatisfiedLinkError` (the native bridge is unimplemented in the test), which
proves the classfile is verifier-legal and `<init>` is not native.

## (b) One linear this/super call stays in Java, not re-run in native — correct

`split(...)` locates exactly one direct constructor-chain call whose owner is
the current class or its direct superclass, rejecting a second candidate and
rejecting any `<init>` with none. It then rejects any branch or switch in the
prefix `[0, callIndex]`, so the retained prefix is linear:

```141:150:obfuscator/src/main/java/by/radioegor146/special/ConstructorSpecialMethodProcessor.java
        for (int i = 0; i <= callIndex; i++) {
            AbstractInsnNode instruction = constructor.instructions.get(i);
            if (instruction instanceof JumpInsnNode
                    || instruction instanceof TableSwitchInsnNode
                    || instruction instanceof LookupSwitchInsnNode) {
                throw unsupported(
                        "Control flow before the this/super call cannot be split safely",
                        i, instruction);
            }
        }
```

`postProcess` rebuilds the Java constructor as: the cloned prefix **including**
the this/super call (`cloneRange(method, 0, split.callIndex + 1)`), then
`ALOAD 0`, the descriptor arguments, `INVOKESTATIC` of the bridge, and
`RETURN`. `createNativeBody` builds the native suffix from `callIndex + 1`
onward, i.e. strictly after the this/super call:

```92:97:obfuscator/src/main/java/by/radioegor146/special/ConstructorSpecialMethodProcessor.java
        for (int i = split.callIndex + 1; i < constructor.instructions.size(); i++) {
            AbstractInsnNode instruction = constructor.instructions.get(i);
            if (!(instruction instanceof FrameNode)) {
                body.instructions.add(instruction.clone(labels));
            }
        }
```

So the constructor-chain call executes exactly once, in bytecode. The emitted
bridge body for `example/Math.<init>(I)V` confirms the suffix contains no
`<init>` invoke — it goes straight to the field store:

```cpp
void JNICALL __ngen_special_init_0_59(JNIEnv *env, jobject ignored_hidden, jobject obj, jint arg0) {
    ...
    // IR codegen: example/Math.<init>(I)V
    ...
    env->SetIntField(obj, cfields[0], arg0);
    if (env->ExceptionCheck() != 0) {
        return;
    }
    ...
    B1:
    return;
```

There is no `CallNonvirtualVoidMethod` for `Object.<init>` in the suffix. The
status doc's note that the *complete-body* IR (built only for admission and
discarded) still models the `<init>` invoke is accurate and is not what runs:
`IrMethodCompiler.processMethod` discards the complete build's result and emits
from `createNativeBody`. `lowersSubclassAndReferenceFieldConstructorBodies`
independently asserts the retained INVOKESPECIAL (`example/Base.<init>(I)V`,
`example/Math.<init>(I)V` for the delegated `this(...)` case) survives in the
constructor.

## (c) Hidden bridge receives initialized this as local 0 / jobject — correct

The bridge descriptor prepends `java/lang/Object` before the constructor
arguments:

```39:43:obfuscator/src/main/java/by/radioegor146/special/ConstructorSpecialMethodProcessor.java
        Type[] bridgeArguments = new Type[constructorArguments.length + 1];
        bridgeArguments[0] = Type.getType(Object.class);
        System.arraycopy(constructorArguments, 0, bridgeArguments, 1,
                constructorArguments.length);
        String bridgeDescriptor = Type.getMethodDescriptor(Type.VOID_TYPE, bridgeArguments);
```

`postProcess` pushes `ALOAD 0` (the receiver, initialized because it is loaded
after the this/super call) then the descriptor arguments, and invokes the
static bridge. The native suffix is built with the *constructor's* own
descriptor as an instance method, so local 0 is the receiver `REFERENCE` and
descriptor arguments start at local 1 — matching how the original suffix read
them. The emitted C++ maps that receiver to `jobject obj` and each field store
targets it: `env->SetIntField(obj, ...)`. The two-reference-field constructor
maps to `jobject obj, jobject arg0, jarray arg1`, verbatim from the compiled
unit:

```cpp
void JNICALL __ngen_special_init_0_60(JNIEnv *env, jobject ignored_hidden, jobject obj, jobject arg0, jarray arg1) {
```

The `ignored_hidden` slot is the static method's `jclass`-equivalent argument
(the pre-existing hidden-proxy convention, immediately `DeleteLocalRef`'d), so
`obj` is genuinely the first real bridge argument = the initialized receiver.
`admitsSimpleConstructorOnlyForIrAndKeepsGetterOnIr` asserts the exact
`jobject ignored_hidden, jobject obj, jint arg0` signature, and the complete
constructor IR exposes `IrType.REFERENCE` as parameter 0.

## (d) Full-body admission before mutation — preserved

`IrMethodCompiler.processMethod` validates the complete constructor, builds the
suffix, and emits the C++ body string *before* `MethodShellEmitter.beginIr`
creates the bridge or `finishIr` rewrites the constructor:

```32:46:obfuscator/src/main/java/by/radioegor146/ir/IrMethodCompiler.java
        MethodNode bytecodeBody = context.method;
        if ("<init>".equals(context.method.name)) {
            frontend.build(context.clazz.name, context.method);
            bytecodeBody = ConstructorSpecialMethodProcessor.createNativeBody(
                    context.clazz, context.method);
        }
        IrMethod method = frontend.build(context.clazz.name, bytecodeBody);
        String body = emitter.emitBody(method, context);

        MethodShellEmitter.Shell shell = shellEmitter.beginIr(context);
        context.output.append(body);
        shellEmitter.finishIr(context, shell);
```

All admission failures — an unsupported opcode anywhere in the complete body,
zero or multiple constructor-chain calls, a non-linear prefix, a suffix
edge/switch into the prefix, or an exception region crossing the split — are
thrown as `UnsupportedIrConstructException` from `frontend.build` or `split`,
i.e. before `emitBody` touches any string/class/field/method cache and before
`beginIr` allocates the bridge or `finishIr` rewrites bytecode.
`rejectsUnsupportedConstructorBeforeAnyMutation` proves it end-to-end: the
`FCONST_0` after the chain call is rejected, `proxyMethod` stays null, the
hidden pool has no classes, the instruction list and opcode sequence are
unchanged, and `ACC_NATIVE` stays unset.

## (e) Rejected constructors unchanged, and legacy default preserved — correct

`NativeObfuscator` catches the constructor rejection and leaves the method as
Java bytecode rather than routing it to the constructor-less legacy shell:

```288:293:obfuscator/src/main/java/by/radioegor146/NativeObfuscator.java
                                    if ("<init>".equals(method.name)) {
                                        logger.info("IR codegen unsupported for {}#{}{}: {}; "
                                                        + "leaving constructor bytecode unchanged",
                                                classNode.name, method.name, method.desc,
                                                ex.getMessage());
                                        continue;
                                    }
```

`MethodProcessor.shouldProcess(method)` still excludes `<init>` because the
one-argument overload delegates with `CodegenMode.LEGACY`; the two-argument
overload only admits `<init>` when `codegenMode == IR`. `Main`'s `--codegen`
option still `defaultValue = "legacy"`, and the public
`NativeObfuscator.process(...)` overload without a mode delegates with
`CodegenMode.LEGACY`. `CodegenModeTest.cliDefaultsToLegacy` passes.
`sources/cppsnippets.properties` is present (574 lines) and unchanged. The
phase-9 `jarray` return carrier, phase-10 field families, and phase-11 invoke
families remain in the retained regressions of the g++-accepted unit.

---

## Deltas from the design (all honest, all acceptable)

1. **The constructor split is a lowering strategy the base design did not
   spell out.** The design keeps `<init>` as a special method; the
   implementation adds the verifier-safe prefix/suffix split because the JVM
   forbids native `<init>` and forbids passing uninitialized `this` to a
   helper. The split changes no observable JVM behavior: the chain call runs
   once, then the suffix runs on the initialized receiver.
2. **Complete-body admission is a validate-then-discard build.** The frontend
   builds the whole constructor purely to gate admission, then the emitted body
   is built from the suffix. This is a deliberate correctness gate, not double
   work that reaches output.
3. **Scope of the slice.** Only constructors with one linear direct chain call,
   a wholly-suffix exception region, no suffix dependency on prefix-only
   locals, and operations inside the existing phase-1..11 subset are admitted;
   everything else (float/double, invokedynamic, `POP2`, `MULTIANEWARRAY`,
   other primitive field/invoke carriers, `java.lang.Object.<init>` itself)
   falls back per the documented policy.

## Nits (all non-blocking, carried forward)

1. **Dead trailing `return (void) 0;`** after a returning block — the method
   shell's default tail, harmless dead code, disclosed since earlier phases.
2. **Unused local phis on split edges.** The suffix copies `obj`/`arg`
   carriers (`v2`/`v3`) onto an edge a returning block never reads — the "no
   DCE yet" tradeoff.
3. **Redundant receiver null-check / `ExceptionCheck` after the field store**
   — the same no-nullability-elimination and dead-check posture disclosed in
   the field phases.

None of these affect observable behavior. No compiler code was changed on this
review branch because there was nothing to fix.

## 中文（简要）

结论：**接受 / accept。**

本次审阅仅针对 IR 编译器（Java 字节码 → typed IR → C++/JNI 代码生成）的正确性
与保真度——verifier-safe 构造函数 split、JNI receiver/参数映射、异常生命周期、
mutation 前 admission，不涉及加壳与反分析。我阅读了全部改动文件与
`IrCompilerTest`，以 `CC=gcc CXX=g++ --rerun-tasks` 重跑聚焦测试并核对 JUnit
XML，确认 g++ 冒烟确实执行（未跳过），并**独立重新编译**了测试写出的整个翻译
单元（61 个 `JNICALL` 函数、2 个 `special_init` bridge，退出码 0），且直接从该被
g++ 接受的文件中阅读了构造函数 bridge 的 C++。

- **绝不出现 native `<init>`**：native 标记只加在 hidden static helper 上；构造
  函数的 access flag 未被改动；`rewrittenConstructorPassesJvmVerification` 通过
  实际类加载与 `COMPUTE_FRAMES` 证明改写后的构造函数可通过验证，仅因未实现
  native bridge 抛 `UnsatisfiedLinkError`。
- **唯一的线性 this/super 调用保留在 Java 且不在 native 重复执行**：`split`
  仅接受一个直接构造链调用并要求前缀线性；`postProcess` 保留含该调用的前缀，
  native 后缀从调用之后开始；发射的 bridge 后缀中无 `<init>` invoke。
- **hidden bridge 以 local 0 / jobject 接收已初始化的 this**：bridge 描述符将
  `Object` 前置为参数 0，经 `ALOAD 0` 传入，映射为 `jobject obj`，字段写入
  `env->SetIntField(obj, ...)`。
- **mutation 前完成完整方法体 admission**：所有 admission 失败在
  `frontend.build`/`split` 抛出，早于 `emitBody` 触碰缓存及 `beginIr`/`finishIr`
  改写；`rejectsUnsupportedConstructorBeforeAnyMutation` 证明 `proxyMethod` 为
  null、hidden pool 为空、指令与 `ACC_NATIVE` 不变。
- **被拒构造函数保持原样**：`NativeObfuscator` 捕获异常后 `continue`，不送入
  legacy shell。默认仍为 `legacy`；`cppsnippets.properties`（574 行）与
  phase-9/10/11 回归均保留。

测试：`IrCompilerTest` 57、`CodegenModeTest` 2，共 59；跳过、失败、错误均为零。
`generatedCppPassesGppSyntaxCheckWhenToolchainAvailable` 真实运行（0.268 s，
未跳过），独立 `g++ -std=c++17 -fsyntax-only` 重编返回 0。未发现需修复的正确性
阻塞项，故本审阅分支未改动任何编译器代码。

小瑕疵（均不阻塞，沿用自既往阶段）：块尾死 `return`；split 边上未使用的
local phi；字段写入后冗余的接收者空检查 / `ExceptionCheck`。
