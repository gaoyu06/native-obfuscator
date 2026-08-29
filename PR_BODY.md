## Summary

Admit prefix `ASTORE` writes to reference/array constructor-parameter slots in
the IR constructor split. The retained prefix computes the replacement value;
only each affected hidden-bridge and suffix-entry argument descriptor is widened
to `java/lang/Object`, allowing the rewritten bridge call to pass JVM
verification while preserving the runtime reference.

Prefix `ASTORE 0`, prefix-to-suffix and suffix-to-prefix control flow,
cross-split try/catch regions, and multiple this/super candidates remain
rejected. `--codegen` still defaults to `legacy`. ConstantDynamic and `jsr/ret`
are unchanged.

Ship-ready: **No**.

## Scope

- `ConstructorSpecialMethodProcessor`: targeted descriptor widening for
  prefix-written reference/array arguments
- `IrCompilerTest`: bridge/verifier coverage, retained local-0 rejection, and a
  plain-Java versus generated IR/JNI compile-and-run parity test
- `docs/architecture/ir-flex-ctor-status.md`: admitted and rejected split rules

## Safety

The rewrite does not erase untouched argument descriptors. Local 0 remains
rejected because it may stop naming the initialized receiver while a
prefix-only alias carries `uninitializedThis`; the suffix bridge cannot
generally reconstruct that alias. Cross-split control flow and handlers still
need a different, path-sensitive bridge design.

## Verification

Executed:

```text
CC=gcc CXX=g++ ./gradlew :obfuscator:test --rerun-tasks \
  --tests by.radioegor146.ir.IrCompilerTest \
  --tests by.radioegor146.CodegenModeTest
```

Result: `BUILD SUCCESSFUL`.

Exact JUnit XML counts:

```text
IrCompilerTest: tests=116, skipped=0, failures=0, errors=0 (time=4.36 s)
CodegenModeTest: tests=7, skipped=0, failures=0, errors=0 (time=0.878 s)
Total: tests=123, skipped=0, failures=0, errors=0
```

`prefixReferenceParameterAstoreCompilesAndRunsWithJavaParity` executed (not
skipped): the plain and transformed JVM runs both printed
`forwarded-result`; the transformed run used the generated CMake/JNI library
under `-Xverify:all -Xcheck:jni`. The existing
`rewrittenConstructorPassesJvmVerification` also passed.

## 中文摘要

允许构造器前缀把引用/数组参数写回参数槽。仅把被改写参数在隐藏 bridge 和
suffix 入口的描述符放宽为 `Object`，保留 JVM verifier 与运行时语义。local 0、
跨切分控制流、跨切分 try/catch、多个 this/super 仍拒绝。默认 codegen 仍为
`legacy`，不能直接上线。
