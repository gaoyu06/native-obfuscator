# JDK 17 E2E status

All five fixtures are compiled with `javac --release 17`, packaged, run as an
unmodified oracle, transpiled, compiled as native code, and run again. The
harness requires transformed stdout to equal oracle stdout exactly.

| Case | Observable coverage | Result on JDK 21 |
|---|---|---|
| `InvokeDynamicLambdaE2E` | Capturing and noncapturing lambdas, method and constructor references, primitive capture, repeated invocation, and exception propagation | **PASS** on `HOTSPOT`, `STD_JAVA`, and `ANDROID` |
| `MethodHandlesE2E` | `findStatic`, `findVirtual`, constructor, getter/setter, `findSpecial`, `invokeExact`, `asType`, primitive adaptation, and exception propagation | **PASS** on `HOTSPOT`, `STD_JAVA`, and `ANDROID` |
| `NestPrivateAccessE2E` | Direct private nestmate field/method access plus `getNestHost`, `getNestMembers`, and `isNestmateOf` | **FAIL** on `HOTSPOT`; native access produced the right values, but nest reflection metadata changed |
| `SealedHierarchyE2E` | Sealed interface dispatch, `isSealed`, and exact permitted subclass names | **FAIL** on `HOTSPOT`; dispatch worked, but `isSealed()` became false and `getPermittedSubclasses()` returned null |
| `RecordSemanticsE2E` | Accessors, equality, hash code, `toString`, `isRecord`, and exact record component names/types | **FAIL** on `HOTSPOT`; ordinary generated behavior worked, but `isRecord()` became false and `getRecordComponents()` returned null |

The failing tests stop at their first platform by design, so no result is
claimed for their `STD_JAVA` or `ANDROID` variants.

## Failure evidence

`NestPrivateAccessE2E` oracle:

```text
secret=25
25
Main
true
Main,Main$Worker
```

Transformed output:

```text
secret=25
25
Main$Worker
false
Main
```

`SealedHierarchyE2E` transformed output reached normal dispatch, then reported
`false` for `Shape.class.isSealed()` and threw a `NullPointerException` when
the fixture attempted to stream the null permitted-subclasses array.

`RecordSemanticsE2E` transformed output preserved accessors, equality, hash
code, and `Point[x=7, label=alpha]`, then reported `false` for
`Point.class.isRecord()` and threw a `NullPointerException` when the fixture
attempted to stream the null record-components array.

## Verified cause

The audit claim about class-file version was confirmed:
`NativeObfuscator` unconditionally assigns `classNode.version = 52` before
writing every processed input class. `javap -verbose` on retained failed-test
artifacts showed:

```text
Nest input/output major:   61 -> 52
Sealed input/output major: 61 -> 52
Record input/output major: 61 -> 52
```

ASM still serialized `NestHost`/`NestMembers`, `PermittedSubclasses`, and
`Record` attributes into the version-52 outputs; they were not physically
removed. The JDK does not recognize their modern semantics at that class-file
version, which explains the reflection results. This PR intentionally records
that behavior in failing compatibility oracles and does not change the
compiler, IR, or class-version policy.
