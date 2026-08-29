# Primitive Class LDC on direct IR

The optional typed CFG IR frontend now admits primitive `Type` constants for
`Z`, `B`, `C`, `S`, `I`, `F`, `J`, `D`, and `V`. The compiler copies the
method, replaces each primitive Class `LDC` with the corresponding wrapper
`TYPE` field read, and leaves the caller's `MethodNode` unchanged:

| Descriptor | Field |
| --- | --- |
| `I`, `J`, `F`, `D` | `Integer.TYPE`, `Long.TYPE`, `Float.TYPE`, `Double.TYPE` |
| `Z`, `B`, `S`, `C` | `Boolean.TYPE`, `Byte.TYPE`, `Short.TYPE`, `Character.TYPE` |
| `V` | `Void.TYPE` |

Classfile serialization stores a `CONSTANT_Class` name as text, so ASM reads a
synthetic one-character primitive descriptor back as an object-looking
`Type`. The frontend recognizes only the exact nine primitive descriptor names
in that normalized form.

The rewritten `GETSTATIC ... TYPE:Ljava/lang/Class;` instructions use the
existing typed field IR and JNI cache path. Direct C++ lowering therefore uses
`GetStaticFieldID` and `GetStaticObjectField`; no new `ClassConst` cache
semantics or marker calls were added. Object and array Class constants continue
to use `ClassConst`.

Unsupported methods still reject before shell or cache mutation. The existing
`jsr` reject-before-mutation sentinels remain in the focused suite. Constructor
leftovers and interpreter/evaluator behavior are unchanged. CLI/API defaults
remain `codegen=legacy`, direct IR lowering, and the C++ backend.

## Validation

Command run on 2026-08-29:

```text
CC=gcc CXX=g++ ./gradlew :obfuscator:test --rerun-tasks \
  --tests by.radioegor146.ir.IrCompilerTest \
  --tests by.radioegor146.CodegenModeTest
```

Result: `BUILD SUCCESSFUL`.

Counts read from Gradle's JUnit XML:

```text
IrCompilerTest: tests=142, skipped=0, failures=0, errors=0 (time=17.517 s)
CodegenModeTest: tests=7, skipped=0, failures=0, errors=0 (time=0.665 s)
Total: tests=149, skipped=0, failures=0, errors=0
```

`lowersEveryPrimitiveClassLdcThroughWrapperTypeFieldOnPrivateCopy` covers all
nine sorts, verifies the original LDC instructions remain intact, and checks
the existing JNI static-object-field path without `native.magic`.

`primitiveClassLdcCompilesAndRunsWithHotSpotParity` transforms primitive LDC
accessors, configures and builds the generated C++ with CMake/g++, then runs the
result with `-Xverify:all -Xcheck:jni`. Its output matches a HotSpot reference
using wrapper `TYPE` reads for `int`, `long`, `boolean`, and `void`; every
identity check is `true`, including `int.class == Integer.TYPE`, and every
`Class.getName()` value matches.
