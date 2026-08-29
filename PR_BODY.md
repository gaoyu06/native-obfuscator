## Summary

- admit `LAND`, `LOR`, and `LXOR` as typed `I64` binary operations
- add a dedicated long-shift IR node with an `I64` value and `I32` count for
  `LSHL`, `LSHR`, and `LUSHR`
- emit `count & 0x3f`, unsigned wrapping carriers, and distinct arithmetic
  versus logical right shifts
- retain the `legacy` default and per-method fallback

## Tests

```text
CC=gcc CXX=g++ ./gradlew :obfuscator:test \
  --tests by.radioegor146.ir.IrCompilerTest \
  --tests by.radioegor146.CodegenModeTest
```

Result: `BUILD SUCCESSFUL` (98 tests, 0 skipped, 0 failures, 0 errors).

Focused phase-19 coverage includes all six operations, typed shift operands,
shift-count masking, logical-right-shift emission, a wrapping left-shift case,
and the generated C++ syntax smoke test.

## Benchmark-kernel admission

The current-master `IntegerLoopKernel.run(I)J` and
`RecursionKernel.recurse(IJ)J` methods are compiled with `--codegen=ir`.
The transpile command exits zero, the log has no per-method fallback entry,
and both generated functions carry direct-IR markers. Admission evidence is
recorded in `docs/architecture/ir-phase19-status.md`; no timing benchmark was
run.
