# IR dynamic/loadable constant admission status

> This page describes one typed-CFG IR compiler increment. It is not a
> ship-readiness or JDK-support statement. `--codegen` continues to default to
> `legacy`; this path is selected with `--codegen=ir --ir-lower=direct`.

## Lowering

`AsmToIr` validates special `LDC` constants before making a private
`MethodNode` copy:

- Raw `MethodType` and `MethodHandle` constants are expanded on the copy with
  the shared `LdcPreprocessor`. The resulting lookup, class-loader,
  `MethodType.fromMethodDescriptorString`, and `MethodHandles.Lookup` calls use
  the existing typed IR invoke and JNI cached-member lowering.
- An admitted `ConstantDynamic` in a class becomes an ordinary static invoke
  of a synthetic resolver in the constant's owning class.
- For a public, non-annotation interface at class-file version 55 or newer, the
  IR body instead invokes a resolver in a deterministic synthetic companion
  under the existing `HiddenMethodsPool` namespace (`nativeN/hidden/`). The
  mutable state, typed value, and cached error fields all live on that
  companion, never on the interface. The companion is loaded from the output
  jar by the transformed application's class loader because its resolver
  depends on the application interface.
- The interface receives only a public synthetic, uncached bootstrap bridge.
  Executing `MethodHandles.lookup()` in that bridge preserves the interface as
  the lookup class and lets the original interface context invoke a private
  bootstrap. The synchronized companion resolver calls the bridge and owns the
  one-time cache.
- Resolver and bridge members are installed only after frontend and
  direct-backend validation succeed. Companion naming and all member collisions
  are checked without mutating the caller or hidden pool first.
- Each resolver is synchronized and stores a state, the typed result, and a
  cached `BootstrapMethodError`. It invokes a static bootstrap directly for a
  class owner or through the interface bridge, with `MethodHandles.lookup()`,
  name, constant `Class`, and the validated static arguments. A successful
  value, including `null`, is reused. A bootstrap failure is wrapped when
  needed, stored, and rethrown on later uses.
- Structurally identical occurrences use the same resolver. Nested admitted
  dynamic arguments call their own resolver, so nested values retain the same
  one-time cache behavior.

The resolver call is an ordinary IR invoke. Existing pending-exception checks
therefore route bootstrap failures through the method's existing exceptional
CFG edges when the `LDC` is in a protected region.

## Admitted shapes

| `LDC` value | Direct IR status |
| --- | --- |
| `MethodType` with a valid method descriptor | Admitted through the shared LDC expansion |
| `MethodHandle` using a field or invoke reference kind supported by `MethodHandleUtils` | Admitted through the shared LDC expansion |
| `ConstantDynamic` with a `REF_invokeStatic` bootstrap whose first parameters are exactly `Lookup`, `String`, and `Class`, whose remaining parameters match the static arguments, and whose return descriptor exactly matches the constant descriptor | Admitted through a cached synthetic resolver |
| The same proven `ConstantDynamic` in a public, non-annotation interface at class-file version 55 or newer | Admitted through a cached hidden companion and uncached interface bootstrap bridge |
| Nested `ConstantDynamic` arguments satisfying the same checks | Admitted recursively |
| Reference, array, boolean/byte/char/short/int, long, float, and double condy results | Admitted |

Static bootstrap arguments are limited to loadable scalar constants, strings,
reference/array class constants, method types, method handles, and recursively
admitted dynamic constants. The direct helper invocation admits exact primitive
carriers and reference arguments whose assignability can be established without
loading application classes.

## Still rejected before mutation

- non-static condy bootstraps;
- bootstrap signatures that do not have the required leading arguments, use a
  variable-arity packing shape, have an incompatible static argument, or return
  a different descriptor;
- unsupported or malformed nested constants and method handles;
- interface condy when a companion cannot be placed safely, including a
  non-public interface, annotation interface, class-file version below 55, or
  resolver/bridge name collision;
- legacy `jsr`/`ret` subroutines.

These checks happen before the caller `MethodNode` is changed. Unsupported
methods remain available for per-method fallback. Unsafe interface condy and
`jsr`/`ret` provide reject-before-mutation coverage; this increment does not
implement legacy subroutines or additional constructor-split shapes.

## Acceptance

The focused command compiles and executes the IR compiler and codegen tests:

```text
CC=gcc CXX=g++ ./gradlew :obfuscator:test --rerun-tasks \
  --tests by.radioegor146.ir.IrCompilerTest \
  --tests by.radioegor146.CodegenModeTest
```

The runtime coverage requires CMake, `g++`, and JNI headers. It compiles and
runs a Java parity jar for string and int interface condy under
`-Xverify:all -Xcheck:jni`; two structurally identical LDCs share one companion
resolver and each bootstrap counter reaches exactly one. It also retains the
C++ harnesses for a nested class-hosted string condy and a used raw
`MethodType`; the existing string-concat `invokedynamic` and monitor harnesses
remain in the same focused suite.

JUnit XML from the focused run records:

- `IrCompilerTest`: 145 tests, 0 skipped, 0 failures, 0 errors
- `CodegenModeTest`: 7 tests, 0 skipped, 0 failures, 0 errors
