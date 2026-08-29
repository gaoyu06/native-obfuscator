# Independent review: IR JDK 17 runtime repair (Sol reject+fix)

Review target: draft [PR #115](https://github.com/gaoyu06/native-obfuscator/pull/115)
on branch `cursor/ir-jdk17-runtime-sol-review-6d81-d5c1` at
`d12406827d50434f128637674fc6f26dd38e3f06`. This is Sol's reject+fix of draft
[PR #113](https://github.com/gaoyu06/native-obfuscator/pull/113) at
`417fc70e2e1bdeb59e242c201bba36264370e2db`, itself stacked on draft
[PR #108](https://github.com/gaoyu06/native-obfuscator/pull/108) at
`5a6f6097524c1fe42cd82be2425f5e6736667688`.

This is a second, independent compiler-correctness review. It is limited to
Java-to-native codegen correctness. It does not discuss packing, anti-analysis,
or protection semantics.

## Verdict

**Accept as a documented review (docs-only).** Re-running the required focused
suite reproduces `BUILD SUCCESSFUL` with 89 tests passing, and the two fixes are
correct on independent code inspection. No new code defects were found, so this
branch adds only this review document and does not change compiler code.

## What #115 changes over #113

1. **Caller-local signature-polymorphic trampolines.**
   `MethodHandleUtils.getInvokeHelper` now takes the caller `ClassNode` plus the
   original invoke *name* and *descriptor*. It rejects anything other than
   `invoke`/`invokeExact`, keeps the exact call-site descriptor (no more
   Object-simplification), and emits `INVOKEVIRTUAL MethodHandle.<name>` with
   that exact descriptor — so `invokeExact` stays `invokeExact` and `invoke`
   stays `invoke`. The generated trampoline is injected as a
   `public static synthetic bridge` method into the *caller's own* class
   (`HiddenMethodsPool.getMethod(ClassNode owner, ...)`), which keeps the
   descriptor's referenced types resolvable in the caller's own loader and
   access context. Both call sites (legacy `MethodHandler` and `IrCppEmitter`)
   were updated to pass `context.clazz` and the invoke name.
2. **Method-iteration boundary.** `NativeObfuscator` captures
   `methodsToProcess = classNode.methods.size()` before the per-method loop, so
   trampolines appended to the same class during processing are written to the
   output class but are not themselves fed back into the compiler pass.
3. **Rejected-constructor restore.** When IR codegen rejects an `<init>`, the
   preprocessed body still contains `native/magic/...` indy marker calls. The
   fix replaces that method with the pristine method parsed from the original
   class bytes (`readOriginalMethod(src, ...)`), so the emitted constructor
   carries its original `invokedynamic` and no leftover markers.
4. **Debug-jar parity.** Hidden-class bytes are now written to the debug jar in
   the same loop that writes them to the output jar, on every platform, with no
   duplicate entry in the later HotSpot-only embedding loop.

## Independent defect hunt

- **`invokeExact` semantics.** The trampoline's `INVOKEVIRTUAL` carries the
  unmodified call-site descriptor, which is exactly what `invokeExact` requires
  for its strict `MethodType` check. The regression
  `generatedInvokeExactHelperPreservesExactMethodTypeChecks` defines the helper,
  loads it, and executes it: an exact target returns normally, a
  `CharSequence`-typed target through the `invoke` helper is adapted, the same
  adaptable target through the `invokeExact` helper throws
  `WrongMethodTypeException`, and a wrong-return target through `invoke` throws
  `ClassCastException`. This is real runtime proof, not a string assertion.
- **Trampoline placement / access.** Injecting into the caller class (not a
  shared `native0.hidden` class) removes the loader/access mismatch Sol observed
  as `IllegalAccessError`. The method is `static`, so no receiver is needed; the
  native body reaches it through an ordinary JNI `GetStaticMethodID` +
  `CallStatic*Method` on the caller class.
- **Method-iteration boundary.** Appends land past `methodsToProcess`, and the
  restore uses `classNode.methods.set(i, ...)` at an index below the boundary, so
  neither disturbs the other. Verified by inspection of the loop bounds.
- **Naming collisions.** `getMethod(ClassNode owner, ...)` keys its cache by
  `name + '\0' + desc` and loops on `hasMethodNamed(owner, ...)` using a
  run-global counter, so injected names are unique within each owner class and
  across classes. Distinct call-site descriptors get distinct trampolines, which
  is required for `invokeExact`.
- **Constructor restore.** `src` is the pristine pre-preprocess class image, and
  the restored `MethodNode` carries the original `InvokeDynamicInsnNode`; ASM
  regenerates a matching `BootstrapMethods` attribute on write. The regression
  `rejectedIrConstructorRestoresOriginalInvokedynamic` drives a real
  `NativeObfuscator.process` on a constructor with an unsupported IR shape and
  asserts the output constructor keeps `invokedynamic` and contains no
  `native/magic/` marker.
- **Default codegen unchanged.** The CLI still defaults to `legacy`
  (`cliDefaultsToLegacy` passes); the restore path only runs under
  `selectedCodegen == IR`; no phase-18 `NEWARRAY` support is added (the
  constructor regression relies on that shape still being rejected).

No defect required a code change.

## Verification (re-run on this branch)

```bash
CC=gcc CXX=g++ ./gradlew :obfuscator:test \
  --tests by.radioegor146.ir.IrCompilerTest \
  --tests by.radioegor146.CodegenModeTest --console=plain
```

Result on `d124068` with `CC=gcc CXX=g++` (JDK 21 build host, Gradle 9.3.1):
`BUILD SUCCESSFUL`. Per the JUnit XML reports:

| Suite | tests | failures | errors | skipped |
| --- | ---: | ---: | ---: | ---: |
| `by.radioegor146.ir.IrCompilerTest` | 85 | 0 | 0 | 0 |
| `by.radioegor146.CodegenModeTest` | 4 | 0 | 0 | 0 |
| **Total** | **89** | **0** | **0** | **0** |

This matches the "focused tests 89 pass" claim.

**Not re-verified here:** the "five JDK 17 fixtures 5/5 stdout parity" claim.
Those fixtures live in the separate `test-jdk17-e2e-harness` branch and require a
JDK 17 runtime plus a full native compile/run; they are outside the required
focused command and this host runs JDK 21. I did not reproduce, and do not
restate, those numbers.

## Release questions / 发布问题

(a) **Scope / 范围:** Independently re-review Sol's reject+fix of #113 —
caller-local `invoke`/`invokeExact` trampolines, the method-iteration boundary,
rejected-constructor restore, and the debug-jar/version handling — re-run the
focused suite, and either accept or fix on a stacked branch. / 独立复审 Sol 对
#113 的 reject+fix：调用方本地的 `invoke`/`invokeExact` trampoline、方法迭代
边界、被拒构造器恢复，以及 debug jar/版本处理；重跑聚焦测试，并据此接受或在
堆叠分支上修复。

(b) **Ship-ready? / 可直接上线？** Not as production or as a "JDK 17 supported"
claim. The corrected stack is ready for maintainer review only. / 不能作为生产
可用或“支持 JDK 17”的结论；修正后的堆叠分支仅可进入维护者复审。

(c) **Review required? / 是否需要 review？** Yes. A maintainer should still
review the caller-local trampoline injection and the original-constructor
restore before this stack advances. / 需要；堆叠推进前，维护者仍应复审调用方
本地 trampoline 注入与原始构造器恢复逻辑。

(d) **Preconditions / 前置条件:** Keep the base commits (#108/#113) fixed,
retain `legacy` as the CLI default, do not merge phase-18 `NEWARRAY`, re-run the
focused suite (and the JDK 17 fixture harness on a real JDK 17 runtime) on the
final stacked SHA, and do not label JDK 17 supported without broader platform and
bytecode coverage. / 固定 #108/#113 基线提交，保持 `legacy` 为 CLI 默认值，不合入
phase-18 `NEWARRAY`，在最终堆叠 SHA 上重跑聚焦测试（并在真实 JDK 17 运行时上跑
JDK 17 用例）；在更广泛的平台与字节码覆盖完成前，不得标记为“支持 JDK 17”。
