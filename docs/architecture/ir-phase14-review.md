# IR phase 14 Sol review

Review branch: `cursor/ir-phase14-sol-review-6d81-8efb`

Reviewed subject: `cursor/ir-compiler-phase14-6d81` at
`ece69f5810bbefe7cdc144e09980d5ad9e5fb22d`,
[draft PR #95](https://github.com/gaoyu06/native-obfuscator/pull/95), compared
with `cursor/ir-compiler-phase13-6d81` at
`b5a403fd398961870eb6aadafb50b882bc17f273`,
[draft PR #90](https://github.com/gaoyu06/native-obfuscator/pull/90).

## Verdict

**Accept.**

No correctness bug was found in the admitted phase-14 F/D compiler surface, so
this review is docs-only. In particular, no wrong JNI family, category-two
slot error, NaN-polarity error, out-of-range floating conversion, mutation on
fallback, or constructor receiver corruption remains in the reviewed code.

This is not a ship-readiness approval. The IR path remains an opt-in partial
compiler, and the focused unit/code-generation checks do not replace native
runtime parity and supported-platform CI.

## Findings

### Typed carriers and category-two `D`

- `IrType.F32` is `jfloat`/one slot and `IrType.F64` is `jdouble`/two slots
  (`obfuscator/src/main/java/by/radioegor146/ir/IrType.java:9-10`).
- Method descriptors, parameters, returns, fields, invokes, constants, and
  conversions map `F` and `D` to those distinct IR types
  (`AsmToIr.java:156-207`, `1365-1411`; `IrNodes.java:96-146`,
  `566-634`, `1581-1623`). No F/D carrier is routed through `I32`.
- Parameter-local numbering advances by ASM `Type.getSize()`, and a wide
  parameter records only a continuation marker in its second slot
  (`AsmToIr.java:198-204`, `748-759`). Local validation rejects a load/store
  from a continuation slot, a truncated wide local, and overlap with another
  local (`AsmToIr.java:317-370`).
- `DLOAD`/`DSTORE` require `F64`; `DSTORE` writes the first logical value and
  leaves the continuation slot non-live (`AsmToIr.java:824-873`). Local phis
  are created only at the first slot and require both slots to be definitely
  live (`AsmToIr.java:101-111`); stack phis remain one typed value while their
  physical slot index advances by `getJvmSlots()` (`AsmToIr.java:130-139`).
  Invoke arguments are popped once per descriptor argument, so a `D` is never
  split into two floats (`AsmToIr.java:1053-1066`).

### Exact JNI families and constant bits

- Field accessor names are derived from the exact descriptor. `F` selects
  `Get/Set(Static)FloatField`; `D` selects
  `Get/Set(Static)DoubleField` (`IrCppEmitter.java:668-765`).
- Invoke return families select `Float` or `Double`, with `CallStatic`,
  `Call`, or `CallNonvirtual` determined independently by invoke kind.
  Arguments retain `jfloat`/`jdouble` because integer narrowing applies only
  to Z/B/C/S (`IrCppEmitter.java:767-884`).
- LDC float/double values enter dedicated raw-bit IR nodes via
  `floatToRawIntBits`/`doubleToRawLongBits`
  (`AsmToIr.java:810-823`, `1279-1298`). The C++ AST materializes those exact
  bits with `std::memcpy` (`CppAst.java:69-102`). The focused regression covers
  instance and static field store/load generation for `-0.0`, finite values,
  infinities, and payload NaNs (`IrCompilerTest.java:976-999`,
  `2049-2079`).
- The retained smoke unit contains the expected `0x80000000U` float negative
  zero and `0x7ff8000000001234ULL` double payload-NaN materializations, together
  with all eight Float/Double field accessors and all six Float/Double invoke
  return families.

### Floating semantics

- The frontend maps `FCMPL`/`DCMPL` to NaN result `-1` and `FCMPG`/`DCMPG` to
  `+1` (`AsmToIr.java:944-955`). The emitter tests either operand with
  `std::isnan` before the ordered `1/-1/0` comparison
  (`IrCppEmitter.java:308-324`).
- `FREM`/`DREM` emit `std::fmod`; floating division is an ordinary `/` and
  never enters the integer divide-by-zero exception path
  (`IrCppEmitter.java:272-300`).
- F/D-to-I/J conversion first selects zero for NaN, then maximum/minimum at
  the source-typed boundaries, and evaluates the C++ cast only in the
  in-range conditional branch (`IrCppEmitter.java:356-425`). Thus NaN,
  infinities, and finite overflow never reach an out-of-range integral cast;
  the ordinary cast supplies JVM toward-zero behavior. A separate
  `-fsanitize=undefined,float-cast-overflow` harness exercised NaN, both
  infinities, and positive/negative finite values for the emitted I/J shape
  and exited zero.

### Fallback, constructors, and retained invariants

- `IrMethodCompiler.processMethod` completes frontend validation and body
  emission before `MethodShellEmitter.beginIr` can append output/native
  metadata, set `ACC_NATIVE`, or create a constructor bridge
  (`obfuscator/src/main/java/by/radioegor146/ir/IrMethodCompiler.java:31-46`).
  Complete opcode admission occurs at the start of `AsmToIr.build`
  (`AsmToIr.java:46-64`, `223-315`), before the emitter can allocate cache IDs.
- The mixed phase-14 regression reaches unsupported `FALOAD` only after
  admitted F/D constants, arithmetic, stores, fields, and invokes. It verifies
  unchanged access, output, native metadata, and all four caches
  (`IrCompilerTest.java:652-664`, `2139-2148`, `2824-2892`). The constructor
  `FALOAD` regression separately verifies the unchanged instruction list,
  proxy state, and hidden-method state (`IrCompilerTest.java:371-390`).
- Constructor validation builds the complete body, validates the split, and
  builds/emits the suffix before shell mutation (`IrMethodCompiler.java:31-46`).
  `(FD)V` reload uses `Type.getSize()`, so local 2 is one `D` value spanning
  slots 2/3 (`ConstructorSpecialMethodProcessor.java:35-76`;
  `IrCompilerTest.java:350-368`, `2525-2542`). Prefix `ASTORE` to local 0 or a
  forwarded reference parameter is still rejected before bridge creation
  (`ConstructorSpecialMethodProcessor.java:141-157`;
  `IrCompilerTest.java:393-406`, `2103-2125`).
- The phase-9 array-return boundary still casts a coarsened reference to
  `jarray` (`IrCppEmitter.java:1075-1087`). Z/B/C/S fields and invokes still use
  their exact JNI families (`IrCppEmitter.java:739-765`, `811-884`). The CLI
  default remains `legacy` (`Main.java:57-59`), the API default remains
  `CodegenMode.LEGACY` (`NativeObfuscator.java:103-108`), and
  `obfuscator/src/main/resources/sources/cppsnippets.properties` remains
  present.

## Verification evidence

Command re-run with the required compilers:

```text
CC=gcc CXX=g++ ./gradlew :obfuscator:test \
  --tests by.radioegor146.ir.IrCompilerTest \
  --tests by.radioegor146.CodegenModeTest \
  --rerun-tasks
```

Result: `BUILD SUCCESSFUL`.

Counts read directly from this run's JUnit XML:

```text
IrCompilerTest: tests=68, skipped=0, failures=0, errors=0 (time=0.861 s)
CodegenModeTest: tests=2, skipped=0, failures=0, errors=0 (time=0.102 s)
Total: 70 tests, 0 skipped, 0 failures, 0 errors
```

`generatedCppPassesGppSyntaxCheckWhenToolchainAvailable` ran in 0.403 s and has
no `<skipped>` child. The host had g++ 13.3.0, OpenJDK 21.0.10, and the JDK 21
JNI headers. The retained unit contains 116 matching `JNICALL` function lines
and was independently checked with:

```text
g++ -std=c++17 -fsyntax-only \
  -I/usr/lib/jvm/java-21-openjdk-amd64/include \
  -I/usr/lib/jvm/java-21-openjdk-amd64/include/linux \
  /tmp/ir-compile-smoke13229649017625115597/ir-smoke.cpp
```

The independent command exited zero with empty diagnostics.

The claims and recorded counts in `docs/architecture/ir-phase14-status.md`
match the current implementation and this re-run. The elapsed times differ, as
expected for separate runs; no substantive discrepancy was found.
