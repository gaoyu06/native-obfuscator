# IR phase 14 status

Phase 14 extends the optional direct Java bytecode → typed CFG IR → C++/JNI
compiler with real JVM `float` and `double` values. `F` uses IR `F32` and C++
`jfloat`; `D` uses IR `F64` and C++ `jdouble`. They are not represented as
`I32`, and `D` remains one category-two value rather than two category-one
values. The CLI and API default remains `legacy`, unsupported methods retain
per-method legacy fallback, and `sources/cppsnippets.properties` remains
present. This phase does not include the evaluator lowering stack.

Required base:
`cursor/ir-compiler-phase13-6d81` at
`b5a403fd398961870eb6aadafb50b882bc17f273`
([draft PR #90](https://github.com/gaoyu06/native-obfuscator/pull/90)). The base
is PR #90, not `master`.

## Typed carriers and scalar operations

| JVM descriptor | IR carrier | C++/JNI carrier | JVM slots |
| --- | --- | --- | --- |
| `F` | `F32` | `jfloat` | 1 |
| `D` | `F64` | `jdouble` | 2 |

The carriers flow through method parameters and returns, locals, operand-stack
entries, CFG stack/local phis, field nodes, and invoke nodes. `DLOAD`/`DSTORE`
and double phis observe category-two slot accounting; the continuation slot is
not independently live.

The admitted scalar surface is:

- `FLOAD*`, `FSTORE*`, `DLOAD*`, `DSTORE*`, `FRETURN`, and `DRETURN`;
- `FCONST_0/1/2`, `DCONST_0/1`, and ASM `LDC` nodes containing `Float` or
  `Double` (including class-file `LDC2_W` double constants);
- `FADD`, `FSUB`, `FMUL`, `FDIV`, `FREM`, `FNEG`, and the corresponding
  `D*` operations;
- `FCMPL`, `FCMPG`, `DCMPL`, and `DCMPG`, producing `I32`; and
- `I2F`, `F2I`, `L2F`, `F2L`, `I2D`, `D2I`, `L2D`, `D2L`, `F2D`, and `D2F`.

Float and double constants are materialized from their raw 32-bit or 64-bit
patterns with `std::memcpy`. This preserves signed zero, infinities, and NaN
payload bits at constant materialization without adding a runtime helper.

## Field descriptors

Instance and static field operations use the exact JNI families:

| Descriptor | Instance accessors | Static accessors | IR carrier |
| --- | --- | --- | --- |
| `F` | `GetFloatField` / `SetFloatField` | `GetStaticFloatField` / `SetStaticFloatField` | `F32` |
| `D` | `GetDoubleField` / `SetDoubleField` | `GetStaticDoubleField` / `SetStaticDoubleField` | `F64` |

No float/double heap value is masked, narrowed through `jint`, or routed
through the Int/Long field family. Instance null receivers use the existing
pending-`NullPointerException` exceptional exit.

## Invoke descriptors

`invokestatic`, `invokevirtual`, `invokeinterface`, and non-constructor
`invokespecial` accept `F`/`D` arguments and returns:

| Return | Virtual/interface | Static | Special |
| --- | --- | --- | --- |
| `F` | `CallFloatMethod` | `CallStaticFloatMethod` | `CallNonvirtualFloatMethod` |
| `D` | `CallDoubleMethod` | `CallStaticDoubleMethod` | `CallNonvirtualDoubleMethod` |

Arguments pass as `jfloat`/`jdouble`, and results remain `F32`/`F64`. IR
methods whose own descriptor returns `F` or `D` expose `jfloat` or `jdouble`
at the JNI function boundary. Null virtual/interface/special receivers use the
existing pending-NPE exit. Constructor calls remain void
`CallNonvirtualVoidMethod` calls.

The constructor representation is unchanged: `<init>` itself is not native,
the verifier-required `this(...)`/`super(...)` prefix stays in bytecode, and a
hidden static native bridge receives the initialized receiver and original
arguments. A tested `(FD)V` constructor compiles when F/D operations are in
the safe suffix. Prefix writes to local 0 or forwarded reference parameters
remain rejected before bridge creation.

## JVM floating semantics

- `FCMPL`/`DCMPL` produce `-1` if either operand is NaN;
  `FCMPG`/`DCMPG` produce `+1`.
- `FREM`/`DREM` emit `std::fmod`, not IEEE remainder.
- Floating division by zero uses ordinary C++ floating division and yields
  infinity or NaN; it does not enter the integer
  `ArithmeticException` path.
- Floating-to-int/long conversion first maps NaN to zero, clamps positive and
  negative overflow to the JVM maximum/minimum value, and only then uses the
  C++ toward-zero cast in the in-range branch. This avoids out-of-range C++
  conversion undefined behavior.

## Fallback before mutation

The frontend validates the complete method before C++ emission, cache
allocation, native metadata emission, method flag changes, or constructor
bridge creation. The phase-14 regression performs admitted F/D constants,
arithmetic, remainder, stores, fields, and invokes, then reaches unsupported
`FALOAD`. Rejection is reported at `FALOAD` while method access, generated
output, native metadata, caches, constructor instructions, and hidden bridge
state remain unchanged.

## Tests and recorded results

Command run on 2026-08-29:

```text
CC=gcc CXX=g++ ./gradlew :obfuscator:test \
  --tests by.radioegor146.ir.IrCompilerTest \
  --tests by.radioegor146.CodegenModeTest \
  --rerun-tasks
```

Result: `BUILD SUCCESSFUL`.

Counts read directly from Gradle's JUnit XML:

```text
IrCompilerTest: tests=68, skipped=0, failures=0, errors=0 (time=0.660 s)
CodegenModeTest: tests=2, skipped=0, failures=0, errors=0 (time=0.090 s)
Total: 70 tests, 0 skipped, 0 failures, 0 errors
```

Coverage includes F/D instance/static field round trips for negative zero,
positive zero, payload NaN, infinity, and finite values; exact static,
virtual, interface, and special invoke families; null-receiver exits;
arithmetic, `fmod` remainder, negation, both NaN compare flavors, every
phase-14 conversion and its NaN/overflow mapping; category-two double locals
and phis; the safe constructor bridge; and fallback-before-mutation after
admitted F/D operations. Phase-9 through phase-13 regressions remain in the
same focused suite.

### Real g++ smoke evidence

Environment:

```text
g++ (Ubuntu 13.3.0-6ubuntu2~24.04.1) 13.3.0
JNI headers: /usr/lib/jvm/java-21-openjdk-amd64/include
```

`generatedCppPassesGppSyntaxCheckWhenToolchainAvailable` is now a required
test rather than an assumption-based skip. It ran in 0.249 s and has no
`<skipped>` child in the JUnit XML. Its retained translation unit contains
116 `JNICALL` functions and includes the Float/Double instance/static field
families, virtual/interface/static/special invoke families, scalar operations,
conversions, phis, and retained phase-9 through phase-13 coverage.

The retained unit
`/tmp/ir-compile-smoke10818166713855814343/ir-smoke.cpp` was independently
checked with:

```text
g++ -std=c++17 -fsyntax-only \
  -I/usr/lib/jvm/java-21-openjdk-amd64/include \
  -I/usr/lib/jvm/java-21-openjdk-amd64/include/linux \
  /tmp/ir-compile-smoke10818166713855814343/ir-smoke.cpp
```

g++ exited zero with empty diagnostics.

### Default and retained assets

`CodegenModeTest.cliDefaultsToLegacy` passed. `Main` still declares
`defaultValue = "legacy"`, and the public API overload without a
`CodegenMode` still delegates with `CodegenMode.LEGACY`.
`sources/cppsnippets.properties` remains present. The phase-9 `jarray` return
cast, phase-10 I/J/reference fields, phase-11 interface/special invokes,
phase-12 constructor bridge and prefix-local checks, and phase-13 exact
Z/B/C/S JNI families remain covered.

## Constructs that still fall back

Primitive array loads/stores other than the existing `int[]` slice remain
unsupported, including `FALOAD`/`FASTORE`, `DALOAD`/`DASTORE`, and the
corresponding Z/B/C/S/J operations. `MULTIANEWARRAY`, `POP2`/`DUP2*`,
`invokedynamic`, unsupported long scalar operations, and all evaluator ISA
work also remain outside this phase. The default was not changed from
`legacy`, and no snippet resource was removed.

## Ship-readiness / 交付准备度

- **(a) Scope / 范围:** Direct typed-CFG IR support for scalar JVM F/D types,
  constants, locals/phis, fields, invokes, arithmetic, comparisons,
  conversions, JNI boundaries, and safe constructor suffixes. /
  直接 typed-CFG IR 已覆盖 JVM 标量 F/D 类型、常量、局部变量/phi、字段、调用、
  算术、比较、转换、JNI 边界以及安全的构造器后缀。
- **(b) Ship-ready? / 可直接发布？:** **No — not ship-ready.** /
  **否——尚未达到可发布状态。**
- **(c) Review focus / 审查重点:** Yes — review exact JNI families,
  category-two D slots, NaN compare rules, saturating conversions, and
  fallback-before-mutation. / 是——请重点审查精确 JNI family、D 的双槽规则、
  NaN 比较规则、饱和转换以及 mutation 前 fallback。
- **(d) Integration / 集成:** Base is PR #90, not `master`; re-run the
  focused suite with `CC=gcc CXX=g++`; preserve the constructor bridge,
  phase-9 `jarray` cast, and `legacy` default. /
  基线是 PR #90 而非 `master`；请使用 `CC=gcc CXX=g++` 重新运行聚焦测试；
  保留构造器 bridge、phase-9 `jarray` 转换以及 `legacy` 默认值。
