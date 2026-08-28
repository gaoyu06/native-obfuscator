# JDK 17+ compatibility gaps

## Compatibility boundary

The parser is ASM 9.8 (`obfuscator/build.gradle:32-34`) and uses `ClassNode(Opcodes.ASM9)` (`NativeObfuscator.java:196-198,233-235`), so reading many modern class files is not the primary limitation. The critical write path is:

```java
classNode.version = 52;
classNode.accept(classWriter);
```

(`NativeObfuscator.java:283-287`)

There is no code that removes or translates record components, `PermittedSubclasses`, `NestHost`/`NestMembers`, module metadata, or other version-gated attributes. A processed modern class is therefore serialized as Java 8 while retaining modern structures. Depending on the structure, the JVM rejects the class format or loses required semantics. Constructors are never transpiled (`MethodProcessor.java:92-96`), but the containing class is still rewritten when another method is selected.

Separately, direct codegen accepts only primitive/String/class LDC constants. Method-type and method-handle constants are lowered, but `ConstantDynamic` is not (`LdcPreprocessor.java:10-39`; `LdcHandler.java:40-76`). Every `invokedynamic` is expanded before codegen; a residual node is a hard failure (`IndyPreprocessor.java:374-381`; `InvokeDynamicHandler.java:6-20`).

## Feature status today

| Feature | Current assessment | Source-grounded reason |
|---|---|---|
| Records (JDK 16+) | **Expected to fail when record class is processed** | Record metadata requires a modern class version, but `recordComponents` are not handled and output is forced to 52. Generated `equals/hashCode/toString` also exercise `ObjectMethods.bootstrap` indy, a complex untested bootstrap. |
| Sealed classes (JDK 17+) | **Expected to fail when class carrying `PermittedSubclasses` is processed** | The attribute requires class-file 61 semantics; it is neither removed nor translated before version 52 output. |
| Nestmates (JDK 11+) | **Expected to fail for processed nest host/member; private access is not safely preservable** | `NestHost`/`NestMembers` metadata is version-gated and untouched. If rejected, loading fails; if lost, direct cross-nest private access yields `IllegalAccessError`. |
| Text blocks (JDK 15+) | **Likely works, untested** | Text blocks are compile-time syntax and normally become ordinary String LDCs, which are supported (`LdcHandler.java:43-45`). Exact whitespace/escapes need E2E comparison. |
| Switch expressions (JDK 14+) | **Likely works, untested** | Ordinary integral/String/enum switch expressions lower to supported branches, `tableswitch`/`lookupswitch`, fields, and calls. Pattern switch is different (below). |
| `instanceof` pattern variables (JDK 16+) | **Likely works, untested** | Java 17 lowers this to supported `INSTANCEOF`, branch, `CHECKCAST`, and locals (`TypeHandler.java:35-45`; `JumpHandler.java:21-44`). |
| Pattern switch / record patterns (final in JDK 21) | **Unknown/high risk, untested** | Compilation can use `invokedynamic` bootstraps such as type switching plus modern record metadata. Generic Indy rewriting exists, but no matching fixture or bootstrap-specific assertion exists. |
| Lambdas / method references | **Implemented and one Java-8-style E2E fixture exists, but this audit run determines actual local result separately** | `IndyTest1` uses a stream lambda (`test_data/tests/indy/IndyTest1/Test1.java:3-11`). Indy is expanded platform-specifically (`IndyPreprocessor.java:27-279`). |
| Java 9+ string concatenation indy | **Implemented and fixture exists, conditional on `krak2`** | PullRequest72 supplies a version-55 Krakatau class using `StringConcatFactory` (`TestStringConcatFactory.j:1-19`). |
| Method-type / method-handle LDC | **Implemented, weakly tested** | LDC preprocessing reconstructs them through `MethodType.fromMethodDescriptorString` and `MethodHandles.Lookup.find*` (`MethodHandleUtils.java:36-120`). There is no focused assertion covering all handle tags. |
| `ConstantDynamic` (JDK 11 class files) | **Fails** | It survives `LdcPreprocessor`, then reaches the unsupported branch in `LdcHandler.java:74-76`. |
| `Lookup.defineHiddenClass` (JDK 15+) used by input code | **Potentially callable, untested** | It is an ordinary method call at the caller, but no fixture verifies class bytes, lookup privileges, returned lookup, or hidden invocation. |
| Tool-generated “hidden” helpers | **Not JDK hidden classes** | Helpers are ordinary version-52 classes (`HiddenMethodsPool.java:44-63`), embedded and loaded with JNI `DefineClass` on non-Android (`NativeObfuscator.java:312-355`; `MainSourceBuilder.java:25-31`), or added to the jar on Android (`NativeObfuscator.java:306-311`). |
| Nest-based private access | **Expected to fail when modern nest attributes survive the downgrade; untested** | Method-handle lookup is synthesized by invoking the `MethodHandles.Lookup(Class)` constructor (`sources/native_jvm.cpp:97-105,320-325`), which is not equivalent to proving original nestmate access. |

“Likely works” means the language feature lowers to covered bytecode; it is not a pass claim.

## Build/runtime JDK claims

- Java sources target 1.8 (`obfuscator/build.gradle:10-14`), while generated native code requests JNI 1.8 (`sources/native_jvm_output.cpp:38-42`).
- README correctly limits full support to Java 8 and calls Java 9+ experimental (`README.md:4`).
- CI declares host JDKs 8/11/17/21/25 (`.github/workflows/main.yml:5-8,38-45`), but current wrapper is Gradle 9.3.1 (`gradle/wrapper/gradle-wrapper.properties:1-5`). A declared matrix is not evidence of successful execution, and modern Gradle itself requires a newer runtime than the README's JDK-8 prerequisite. Actual local runtime/tool results are recorded in `local-test-run.md`.
- HotSpot indy relies on internal `java/lang/invoke/MethodHandleNatives.linkCallSite` signatures with only two probed variants (`sources/native_jvm.cpp:107-124`). Standard-Java/Android avoid that internal API but box arguments and use `invokeWithArguments` (`IndyPreprocessor.java:27-183,264-276`).

## Concrete E2E tests to add

These should use the existing ideal-vs-transformed harness, compile under an explicit required JDK/release, and run all supported platforms. Metadata-sensitive tests should also inspect transformed class major version/attributes before launch.

1. **`RecordSemanticsE2E`** — define `record Point(int x, String label)`; assert accessors, constructor, `equals`, `hashCode`, `toString`, reflection `isRecord()`, and exact record-component names/types.
2. **`SealedHierarchyE2E`** — sealed `Shape permits Circle, Rectangle`; assert dispatch and `Shape.class.isSealed()` plus exact `getPermittedSubclasses()`.
3. **`NestmateMetadataE2E`** — host with nested classes; assert `getNestHost`, full `getNestMembers`, and `isNestmateOf` after transformation.
4. **`NestPrivateAccessE2E`** — nested class directly reads/writes a private host field and invokes a private method; assert final value and no `IllegalAccessError`.
5. **`TextBlockLiteralE2E`** — return a text block containing indentation, quotes, a backslash, CR/LF-sensitive lines, NUL, and non-ASCII text; assert exact code points/length, not only printed text.
6. **`SwitchExpressionE2E`** — assert dense integer, sparse integer, String, and enum switch expressions, including `yield`, default, and a throwing arm.
7. **`InstanceofPatternE2E`** (JDK 17) — assert matching/nonmatching/null paths, scoped pattern variable use, and guarded boolean expressions.
8. **`PatternSwitchE2E`** (JDK 21, separate gated suite) — assert null/default, guarded type cases, and record deconstruction; this isolates modern type-switch indy from baseline Java 17.
9. **`HiddenClassE2E`** — obtain bytes for an excluded-from-transpilation payload, call `MethodHandles.lookup().defineHiddenClass`, assert `Class.isHidden()`, invoke a static method from the returned lookup, and assert the expected string.
10. **`InvokeDynamicLambdaE2E`** — capturing/noncapturing lambdas, method/constructor references, primitive/reference captures, serialization only if intended, and repeated invocation; assert output and exception propagation.
11. **`MethodHandlesE2E`** — exercise `findStatic`, `findVirtual`, getter/setter, constructor, `findSpecial`, `invokeExact`, and `invoke`; assert primitive/reference adaptation and thrown exceptions.
12. **`ConstantDynamicE2E`** — ASM/Krakatau fixture with primitive, String, and object condy values; initially assert the current explicit unsupported diagnostic, then become a success test if support is implemented.

The first implementation order should be `NestPrivateAccessE2E`, `RecordSemanticsE2E`, `SealedHierarchyE2E`, then `InvokeDynamicLambdaE2E`/`MethodHandlesE2E`: the first three expose the forced-version correctness issue, while the latter two protect the most complex existing rewrite.
