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
- An admitted `ConstantDynamic` becomes an ordinary static invoke of a
  synthetic resolver in the constant's owning class. The resolver is installed
  only after frontend and direct-backend validation succeed.
- Each resolver is synchronized and stores a state, the typed result, and a
  cached `BootstrapMethodError`. It invokes a static bootstrap with
  `MethodHandles.lookup()`, name, constant `Class`, and the validated static
  arguments. A successful value, including `null`, is reused. A bootstrap
  failure is wrapped when needed, stored, and rethrown on later uses.
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
- condy in an interface, because an interface cannot host the mutable resolver
  fields used by this increment;
- legacy `jsr`/`ret` subroutines.

These checks happen before the caller `MethodNode` is changed. Unsupported
methods remain available for per-method fallback. `jsr`/`ret` is the current
reject-before-mutation test sentinel; this increment does not implement legacy
subroutines or additional constructor-split shapes.

## Acceptance

The focused command compiles and executes the IR compiler and codegen tests:

```text
CC=gcc CXX=g++ ./gradlew :obfuscator:test --rerun-tasks \
  --tests by.radioegor146.ir.IrCompilerTest \
  --tests by.radioegor146.CodegenModeTest
```

The runtime coverage requires `g++` and JNI headers. It compiles and runs C++
harnesses for a nested string condy and a used raw `MethodType`; the existing
string-concat `invokedynamic` and monitor harnesses remain in the same focused
suite.

JUnit XML from the focused run records:

- `IrCompilerTest`: 121 tests, 0 skipped, 0 failures, 0 errors
- `CodegenModeTest`: 7 tests, 0 skipped, 0 failures, 0 errors
