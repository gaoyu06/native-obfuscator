# IR phase 18 status

> **On master.** This increment landed with the preferred-tip integration
> ([#118](https://github.com/gaoyu06/native-obfuscator/pull/118)). Independent
> accepts: Sol [#116](https://github.com/gaoyu06/native-obfuscator/pull/116),
> Fable [#119](https://github.com/gaoyu06/native-obfuscator/pull/119).
> Current public status: [project-status.md](project-status.md).

Phase 18 extends the optional direct Java bytecode → typed CFG IR → C++/JNI
compiler with every primitive `NEWARRAY`, the corresponding primitive
array loads and stores, and rectangular primitive/reference
`MULTIANEWARRAY`. The CLI and API default remains `legacy`, unsupported
methods retain per-method legacy fallback, and
`sources/cppsnippets.properties` remains present. This phase is direct IR
only and does not change the evaluator or reader.

Required base at the time this note was written:
`cursor/ir-compiler-phase17-6d81-2b77` at
`5a6f6097524c1fe42cd82be2425f5e6736667688`
([draft PR #108](https://github.com/gaoyu06/native-obfuscator/pull/108)).
Sol review [#109](https://github.com/gaoyu06/native-obfuscator/pull/109)
accepted that exact tip without a compiler change. That base is now on
`master` via #118.

## Primitive arrays

`IrNodes.ArrayType` records the JVM element kind, typed-IR carrier, bytecode
mnemonics, array descriptor, and exact JNI carrier:

| `NEWARRAY` atype | Descriptor | IR element | Load/store | JNI allocation and region family |
| --- | --- | --- | --- | --- |
| `T_BOOLEAN` | `[Z` | `I32` | `BALOAD` / `BASTORE` | `NewBooleanArray`, `Get/SetBooleanArrayRegion` |
| `T_BYTE` | `[B` | `I32` | `BALOAD` / `BASTORE` | `NewByteArray`, `Get/SetByteArrayRegion` |
| `T_CHAR` | `[C` | `I32` | `CALOAD` / `CASTORE` | `NewCharArray`, `Get/SetCharArrayRegion` |
| `T_SHORT` | `[S` | `I32` | `SALOAD` / `SASTORE` | `NewShortArray`, `Get/SetShortArrayRegion` |
| `T_INT` | `[I` | `I32` | `IALOAD` / `IASTORE` | `NewIntArray`, `Get/SetIntArrayRegion` |
| `T_FLOAT` | `[F` | `F32` | `FALOAD` / `FASTORE` | `NewFloatArray`, `Get/SetFloatArrayRegion` |
| `T_LONG` | `[J` | `I64` | `LALOAD` / `LASTORE` | `NewLongArray`, `Get/SetLongArrayRegion` |
| `T_DOUBLE` | `[D` | `F64` | `DALOAD` / `DASTORE` | `NewDoubleArray`, `Get/SetDoubleArrayRegion` |

Every allocation checks `length < 0` before calling JNI, creates a pending
`NegativeArraySizeException`, and follows the block's shared exceptional
exit. A null array reference is rejected by the existing NPE guard. The JNI
region call performs the bounds check; `ExceptionCheck` immediately routes
the pending `ArrayIndexOutOfBoundsException` through the same exceptional
exit.

Stores narrow `I32` to the required JNI carrier. In particular, boolean
stores retain the JVM low-bit normalization, while byte, char, and short
stores use `jbyte`, `jchar`, and `jshort` respectively.

### Boolean and byte precision

`BALOAD` and `BASTORE` encode both `[Z` and `[B`, but JNI exposes separate
Boolean and Byte APIs. Reference values therefore retain an optional JVM
descriptor. Descriptors originate from method parameters, allocations,
casts, field reads, invoke returns, constants, and reference-array loads,
and propagate through SSA phis.

Known `[Z` and `[B` values select the exact direct JNI family shown above.
If bytecode provides only an imprecise reference carrier, the retained
`utils::baload` / `utils::bastore` runtime discriminator remains available;
it distinguishes the two runtime array classes and calls the corresponding
JNI API rather than treating every opcode 51/84 as byte or boolean.

## Rectangular `MULTIANEWARRAY`

Preflight accepts a non-empty array descriptor when:

- the dimension operand count is at least one and no greater than the
  descriptor's total dimensions; and
- the base element is boolean, byte, char, short, int, float, long, double,
  or an object type.

Malformed descriptors, unsupported component types, and unsupported
dimension counts reject the whole method during validation. Dimension
operands are popped as `I32` values in JVM order and retained in outermost
to innermost order on `IrNodes.MultiNewArray`.

The emitter checks every supplied dimension for negativity before allocation.
Primitive bases call the retained
`utils::create_multidim_array_value<sort>` helper; reference bases call the
retained classloader-aware `utils::create_multidim_array` helper. Both
helpers recursively create object-array levels and populate each parent with
the next level. When the instruction supplies fewer counts than the
descriptor has dimensions, the remaining inner references stay null, as
required by JVM `MULTIANEWARRAY`. Full two-dimensional allocations are
rectangular arrays of arrays.

Allocation, class lookup, or element-store failure leaves the JNI exception
pending. The emitted null/`ExceptionCheck` guard then follows the ordinary
IR exceptional edge.

## Fallback before mutation

Whole-method validation still completes before C++ emission, cache
allocation, native metadata emission, `ACC_NATIVE`, or constructor bridge
creation. The phase-18 fallback regression executes admitted boolean
`NEWARRAY`, primitive `MULTIANEWARRAY`, and reference `MULTIANEWARRAY`, then
reaches unsupported `INVOKEDYNAMIC`. Rejection is reported at opcode 186
while generated output, native metadata, method access, hidden method state,
and class/string/method/field caches remain unchanged.

`INVOKEDYNAMIC` and `ConstantDynamic` LDC remain unsupported. No
default-mode, evaluator, reader, or snippet-resource change is included.
Phase-9 through phase-17 fallback and lowering regressions remain in the
focused suite; older fallback fixtures whose former trigger is now admitted
use `INVOKEDYNAMIC` as the still-unsupported suffix.

## Verification

The focused suite was run with the required GNU C/C++ toolchain:

```text
CC=gcc CXX=g++ ./gradlew :obfuscator:test \
  --tests by.radioegor146.ir.IrCompilerTest \
  --tests by.radioegor146.CodegenModeTest \
  --rerun-tasks
```

Result on 2026-08-29: `BUILD SUCCESSFUL`.

Counts read directly from Gradle's JUnit XML:

```text
IrCompilerTest: tests=88, skipped=0, failures=0, errors=0 (time=0.705 s)
CodegenModeTest: tests=4, skipped=0, failures=0, errors=0 (time=0.154 s)
Total: 92 tests, 0 skipped, 0 failures, 0 errors
```

The six phase-18 compiler tests cover every primitive atype with a
load/store round trip and exact JNI family; every primitive negative-length
path; separate boolean and byte families; two-dimensional `int` and
`String` arrays; every supplied negative dimension; and
fallback-before-mutation after the admitted operations. The mode suite also
retains the default gates and explicitly verifies `legacy` after phase 18.

### Real g++ smoke evidence

`generatedCppPassesGppSyntaxCheckWhenToolchainAvailable` is an assertion-based
g++ gate, not an assumption-based skip. It ran in 0.249 s and has no
`<skipped>` child in the JUnit XML. Its retained translation unit contains
exactly 151 `JNICALL` functions, including eight primitive array round-trip
methods, two rectangular multi-array methods, and one negative-dimension
method added for phase 18.

Environment:

```text
gcc (Ubuntu 13.3.0-6ubuntu2~24.04.1) 13.3.0
g++ (Ubuntu 13.3.0-6ubuntu2~24.04.1) 13.3.0
openjdk version "21.0.10" 2026-01-20
JNI headers: /usr/lib/jvm/java-21-openjdk-amd64/include
```

The retained unit
`/tmp/ir-compile-smoke17317211744783142853/ir-smoke.cpp` was independently
checked with:

```text
g++ -std=c++17 -fsyntax-only \
  -I/usr/lib/jvm/java-21-openjdk-amd64/include \
  -I/usr/lib/jvm/java-21-openjdk-amd64/include/linux \
  /tmp/ir-compile-smoke17317211744783142853/ir-smoke.cpp
```

`g++` exited zero with empty diagnostics.

### Default and retained assets

`CodegenModeTest.cliDefaultsToLegacy` remains the CLI default gate, and the
no-mode `MethodProcessor.shouldProcess` overload remains a legacy-selection
gate. `Main` still declares `defaultValue = "legacy"`, and the public API
overload without a `CodegenMode` still delegates with `CodegenMode.LEGACY`.
`sources/cppsnippets.properties` remains present.

The 36/36 JDK 17 figure from
[#110](https://github.com/gaoyu06/native-obfuscator/pull/110) is admission
measurement only. Phase 18 does not claim JDK 17 runtime, semantic, or
production support from that result.

## Ship-readiness / 交付准备度

- **(a) Scope / 范围:** Direct typed-CFG IR support for all primitive
  `NEWARRAY` atypes and matching primitive loads/stores, plus rectangular
  primitive/reference `MULTIANEWARRAY`, pending-exception routing, and
  pre-mutation fallback. /
  直接 typed-CFG IR 支持全部 primitive `NEWARRAY` atype 及对应的 primitive
  load/store，并支持矩形 primitive/reference `MULTIANEWARRAY`、pending
  exception 路由与 mutation 前 fallback。
- **(b) Ship-ready? / 可直接发布？:** **No — not ship-ready.** /
  **否——尚未达到可发布状态。**
- **(c) Review focus / 审查重点:** Review `[Z` versus `[B` descriptor flow
  and exact JNI selection, every primitive narrowing rule, dimension order
  and partial/full recursive allocation, and preservation of pending
  exceptions. /
  请重点审查 `[Z` 与 `[B` 的 descriptor 流转和精确 JNI 选择、各 primitive
  的窄化规则、dimension 顺序与部分/完整递归分配，以及 pending exception
  是否被完整保留。
- **(d) Integration / 集成:** Base is PR #108 at `5a6f609`, not `master`;
  re-run the focused suite with `CC=gcc CXX=g++`; preserve phase-9 through
  phase-17 regressions, the `legacy` default, and snippet resources. The
  #110 JDK 17 result is admission-only evidence. /
  基线是 `5a6f609` 的 PR #108 而非 `master`；请使用
  `CC=gcc CXX=g++` 重新运行聚焦测试；保留 phase-9 至 phase-17 回归、
  `legacy` 默认值和 snippet 资源。#110 的 JDK 17 结果仅是 admission
  证据。
