# IR phase 15 compiler review

Review branch: `cursor/ir-phase15-sol-review-6d81-13be`

Reviewed subject: [draft PR #99](https://github.com/gaoyu06/native-obfuscator/pull/99),
`cursor/ir-compiler-phase15-6d81-00a8` at
`f46c3eae27f03071a8b3a9e161533b8f24c23735`, stacked on
[draft PR #95](https://github.com/gaoyu06/native-obfuscator/pull/95).

## Verdict

**Accept.**

No correctness defect was found in the phase-15 Java-bytecode-to-C++ lowering.
This review therefore makes no compiler change. String, object/array Class, and
Long LDC constants are admitted with the intended carriers and existing
runtime caches, while primitive Class, MethodType, Handle, and ConstantDynamic
constants continue to fall back before mutation.

This is an acceptance of the reviewed opt-in compiler slice, not a
production-readiness approval. The default remains `legacy`, and the
unsupported Java bytecode families still require per-method fallback.

## String LDC

- `AsmToIr` carries the Java String value in a reference-typed `StringConst`.
  `IrCppEmitter` allocates its ID in the existing `cachedStrings` cache and
  reads the corresponding `cstrings` entry; it does not create another String
  table.
- `StringPool` encodes UTF-16 code units as JNI modified UTF-8. In particular,
  U+0000 is emitted as `C0 80`, not as an interior zero byte. The empty,
  ASCII, non-ASCII, and embedded-NUL values remain separately NUL-terminated
  for `NewStringUTF`.
- The retained generated class source contains five `NewStringUTF` calls:
  four phase-15 literals and the existing `example.Math` class-name String.
  Each path calls `utils::get_interned` and stores `NewGlobalRef(int_str)` in
  `cstrings`.
- The generated method reads four phase-15 literals from `cstrings`; the
  values are exercised as both a static reference argument and real
  `java.lang.String` receivers.

## Class LDC

- Object `Type` constants are normalized with `Type.getInternalName`, then
  converted from internal names such as `java/lang/String` to binary names
  such as `java.lang.String` for the existing defining-classloader
  `utils::find_class_wo_static` path.
- Array `Type` constants retain descriptors. The reviewed `[I` and
  `[Ljava/lang/String;` values use `JNIEnv::FindClass`.
- Class values reuse `cclasses` and its existing weak-global cache. In the
  retained output, lookup and cache publication are followed by
  `ExceptionCheck` before the `cclasses` value is assigned to the IR result.
  The emitter does not clear the pending exception, so a failed `FindClass`
  cannot become a silent null Class result.
- Primitive `Type` constants fail `isSupportedClassConstant` because only
  object and array sorts are accepted. Rejection therefore occurs during
  complete instruction validation, before a class name or cache slot can be
  emitted. The compiler does not substitute `java.lang.Integer` for
  `Integer.TYPE`.

## Long LDC and category-two accounting

- The reviewed values are `0x1_0000_0000L` and `-1L`, not `LCONST_0/1`.
  They lower through `LongConst` with the `I64` carrier and emit as
  `4294967296LL` and `-1LL`.
- The same `I64` value type reports two JVM slots. The retained wide-stack-phi
  regression numbers the following category-one slot at index 2, and the
  generated Long LDC method uses `jlong` values and the existing wrapping
  `uint64_t` arithmetic path.

## Fallback before mutation

`AsmToIr.build` validates every instruction before lowering. `IrMethodCompiler`
then completes frontend construction and C++ body emission before
`MethodShellEmitter.beginIr` changes native metadata or bytecode.

The mixed regression places admitted String, object Class, and nontrivial Long
constants before an unsupported Handle LDC. Both that case and primitive Class
LDC reject at opcode 18. The assertions confirm unchanged `ACC_NATIVE`, empty
C++ and native-method output, and zero class, String, field, and method cache
entries. No constructor bridge path is entered. Direct inspection also
confirms that MethodType, Handle, and ConstantDynamic do not satisfy the
phase-15 admission predicate.

## Retained phases and defaults

- Phase 9: array returns retain the explicit `jarray` boundary cast.
- Phase 12: prefix writes to forwarded reference locals remain rejected before
  constructor mutation.
- Phase 13: Z/B/C/S field and invoke families retain their exact JNI accessors
  and narrowing/widening casts.
- Phase 14: F/D fields, invokes, constants, arithmetic, comparisons,
  conversions, and stack phis remain in the focused suite and generated
  smoke.
- `CodegenModeTest.cliDefaultsToLegacy` passed. `Main` still declares
  `defaultValue = "legacy"`, the API overload still delegates with
  `CodegenMode.LEGACY`, and
  `obfuscator/src/main/resources/sources/cppsnippets.properties` remains
  present.

## Verification evidence

Command:

```text
CC=gcc CXX=g++ ./gradlew :obfuscator:test \
  --tests by.radioegor146.ir.IrCompilerTest \
  --tests by.radioegor146.CodegenModeTest \
  --rerun-tasks
```

Result: `BUILD SUCCESSFUL`.

Counts read from the review run's JUnit XML:

```text
IrCompilerTest: tests=73, skipped=0, failures=0, errors=0 (time=0.866 s)
CodegenModeTest: tests=2, skipped=0, failures=0, errors=0 (time=0.107 s)
Total: 75 tests, 0 skipped, 0 failures, 0 errors
```

The required
`generatedCppPassesGppSyntaxCheckWhenToolchainAvailable` testcase ran
unskipped in 0.398 s. The environment used g++ 13.3.0, OpenJDK 21.0.10, and
the JDK 21 JNI headers. Its retained translation unit contains 119 `JNICALL`
functions and independently passed:

```text
g++ -std=c++17 -fsyntax-only \
  -I/usr/lib/jvm/java-21-openjdk-amd64/include \
  -I/usr/lib/jvm/java-21-openjdk-amd64/include/linux \
  /tmp/ir-compile-smoke12938103134007102709/ir-smoke.cpp
```

The independent command exited zero with empty diagnostics.

## 交付结论 / Delivery summary

- **(a) Verdict / 结论:** **Accept / 接受。** No phase-15 correctness
  miscompile was found, so this review is documentation-only. /
  未发现 phase 15 正确性误编译，因此本次 review 仅新增文档。
- **(b) Ship-ready? / 可直接发布？:** **No / 否。** This remains a partial,
  opt-in IR slice with required fallback. /
  当前仍是部分、显式启用且依赖 fallback 的 IR 切片。
- **(c) Evidence / 证据:** 73 IrCompilerTest plus 2 CodegenModeTest cases
  passed with no skips or failures; the retained 119-function C++ unit passed
  both the test's and the independent g++ syntax checks. /
  73 项 IrCompilerTest 与 2 项 CodegenModeTest 均无跳过、无失败；保留的
  119-function C++ 单元通过测试内及独立 g++ 语法检查。
- **(d) Integration / 集成:** Preserve the stack on PR #95, the phase-9
  `jarray` cast, phase-12 prefix-local rejection, phase-13 Z/B/C/S and
  phase-14 F/D families, fallback-before-mutation, and the `legacy` default. /
  集成时应保持基于 PR #95 的堆叠顺序，并保留 phase-9 `jarray` 转换、
  phase-12 prefix-local 拒绝、phase-13 Z/B/C/S、phase-14 F/D、
  mutation 前 fallback 以及 `legacy` 默认值。
