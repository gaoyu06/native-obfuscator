# Independent review: IR JDK 17 runtime repair

Review target: draft [PR #113](https://github.com/gaoyu06/native-obfuscator/pull/113)
at `417fc70e2e1bdeb59e242c201bba36264370e2db`, stacked on draft
[PR #108](https://github.com/gaoyu06/native-obfuscator/pull/108) at
`5a6f6097524c1fe42cd82be2425f5e6736667688`.

## Verdict

**Reject as submitted; fixed on the stacked review branch (`reject+fix`).**

The classfile-version preservation, JDK 17 metadata retention, `TypeDescriptor`
bootstrap acceptance, IR marker lowering, HotSpot hidden-class JAR packaging,
and `legacy` CLI default were correct in the reviewed diff. Two defects required
code changes:

1. Direct `MethodHandle.invokeExact` and `invoke` calls shared an
   Object-simplified trampoline that always called `MethodHandle.invoke`.
   This removed `invokeExact`'s exact `MethodType` check and could also remove
   `invoke` call-site reference conversions. The fix injects a synthetic
   trampoline into the original caller class, preserving its class loader,
   access rights, exact descriptor, and invocation name. Generated helpers are
   excluded from the current compiler pass by fixing the method-iteration
   boundary.
2. An IR-rejected constructor was not actually left unchanged: invokedynamic
   preprocessing had already inserted random `native/magic/...` marker calls.
   The fix restores that constructor from the original class bytes before
   writing the output.

An intermediate attempt to put exact trampolines in an application-loaded
`native0.hidden` class was rejected by the runtime with
`IllegalAccessError: failed to access class Target`. The final caller-local
design removes that access and loader mismatch.

## Verification

Focused command:

```bash
CC=gcc CXX=g++ ./gradlew :obfuscator:test \
  --tests by.radioegor146.ir.IrCompilerTest \
  --tests by.radioegor146.CodegenModeTest --console=plain
```

Final result: `BUILD SUCCESSFUL`; 89 tests passed (85 `IrCompilerTest`, 4
`CodegenModeTest`). The added regressions execute exact/adaptable trampolines
and verify that a rejected constructor retains its original invokedynamic
without any marker call.

The five fixtures were checked out from
`origin/cursor/test-jdk17-e2e-harness-6d81` at
`7389820175f72eac2a062e9ae9a2917ec8815aed`, compiled with `--release 17`,
transformed with `--codegen=ir`, configured and built with `CC=gcc CXX=g++`,
and packaged with `native0/x64-linux.so`:

| Fixture | Input IR methods | Configure/build | Native exit | Stdout | Hidden0 |
| --- | ---: | --- | ---: | --- | --- |
| `InvokeDynamicLambdaE2E` | 6/6 | 0/0 | 0 | PASS | present |
| `MethodHandlesE2E` | 8/8 | 0/0 | 0 | PASS | present |
| `NestPrivateAccessE2E` | 5/5 | 0/0 | 0 | PASS | present |
| `RecordSemanticsE2E` | 9/9 | 0/0 | 0 | PASS | present |
| `SealedHierarchyE2E` | 8/8 | 0/0 | 0 | PASS | present |
| **Total** | **36/36** | **5/5** | **5/5 exit 0** | **5/5 PASS** | **5/5** |

The generated C++ contained 48 IR method markers, including 12 generated
`<clinit>` methods, leaving 36 code-bearing input methods. No transformation
log reported unsupported IR, fallback, or a retained constructor. No generated
C++ or JAR inventory contained `native/magic`; all oracle/native stderr and
stdout diff files were empty.

`javap -v` confirmed major version 61 and `NestHost`/`NestMembers`, `Record`
components, and `PermittedSubclasses`. `Main` in `MethodHandlesE2E` contains
caller-local `mhinvokeexact*` methods whose bytecode invokes
`MethodHandle.invokeExact` with the original descriptors. The packaged
`native0.hidden.Hidden0` retains reverse-invoke helpers.

A separate default-CLI (no `--codegen`) `MethodHandlesE2E` run also configured,
built, exited zero, and matched stdout, confirming the `legacy` shell path and
default remain intact.

These are five Linux x86-64 fixtures, not evidence of general JDK 17 support.

## Release questions / 发布问题

(a) **Scope / 范围:** Review and fix classfile metadata preservation, indy
markers, MethodHandle linkage, constructor fallback, hidden-class packaging,
and the five stated fixtures. / 审查并修复 classfile 元数据、indy 标记、
MethodHandle 链接、构造器回退、隐藏类打包，以及指定的五个用例。

(b) **Ship-ready? / 可直接上线？** No as a production or JDK 17 support claim;
the corrected stack is ready for maintainer review only. / 不能作为生产可用或
“支持 JDK 17”的结论；修正后的堆叠分支仅可进入维护者复审。

(c) **Review required? / 是否需要 review？** Yes. Review the caller-local
trampoline and original-constructor restoration before stacking it. / 需要；
堆叠前必须复审调用方本地 trampoline 与原始构造器恢复逻辑。

(d) **Preconditions / 前置条件:** Keep the base commits fixed, retain
`legacy` as the default, run the focused suite and packaged fixtures on the
final stacked SHA, and do not label JDK 17 supported without broader platform
and bytecode coverage. / 固定上述基线提交，保持 `legacy` 默认值，在最终堆叠
SHA 上运行聚焦测试与打包用例；在更广泛的平台和字节码覆盖完成前，不得标记
为“支持 JDK 17”。
