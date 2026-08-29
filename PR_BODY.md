## Summary

- admit proven `ConstantDynamic` `LDC` shapes on
  `--codegen=ir --ir-lower=direct`
- cache condy success (including `null`) or `BootstrapMethodError` once in a
  synchronized synthetic owner-class resolver
- recursively lower proven nested condy arguments
- admit raw `MethodHandle` and `MethodType` `LDC` through the shared LDC
  preprocessor on a private method copy
- move the reject-before-mutation sentinel to legacy `jsr`/`ret`
- document the admitted and still-rejected constant shapes

The generated condy resolver is called as an ordinary typed IR static invoke,
so it reuses JNI class/method caches and the existing exceptional CFG edge
handling. Unsupported shapes are rejected before the caller `MethodNode` is
changed.

## Admission boundary

Admitted condy bootstraps are `REF_invokeStatic`, begin with exact
`MethodHandles.Lookup`, `String`, and `Class` parameters, have one compatible
parameter per static argument, and return exactly the condy descriptor.
Loadable primitive/reference constants, method types, method handles, and
recursively proven condy arguments are supported.

Non-static, variable-arity, incompatible, malformed, and interface-owner condy
shapes remain rejected before mutation. This change does not implement
`jsr`/`ret` or additional constructor-split cases.

## Test plan

```text
CC=gcc CXX=g++ ./gradlew :obfuscator:test --rerun-tasks \
  --tests by.radioegor146.ir.IrCompilerTest \
  --tests by.radioegor146.CodegenModeTest
```

JUnit XML from the focused compile-and-run acceptance pass:

- `IrCompilerTest`: 121 tests, 0 skipped, 0 failures, 0 errors
- `CodegenModeTest`: 7 tests, 0 skipped, 0 failures, 0 errors

The passing suite includes executed `g++` harnesses for nested string condy, a
used raw `MethodType`, existing string-concat `invokedynamic`, and existing
monitor behavior. The condy resolver test also executes the generated JVM
resolver twice and verifies that both nested and outer bootstraps run once.

Ship-ready: **No**  
Review mode: **(c) executed tests, no stacked review**
