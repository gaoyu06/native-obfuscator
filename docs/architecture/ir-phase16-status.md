# IR phase 16 status

Phase 16 extends the optional direct Java bytecode → typed CFG IR → C++/JNI
compiler with the category-one stack reorder and reference-array operations
that dominate the remaining measured fallback: `SWAP`, `AALOAD`, and
`AASTORE`. The CLI and API default remains `legacy`, unsupported methods
retain per-method legacy fallback, and `sources/cppsnippets.properties`
remains present. This phase is direct IR only and does not change the
evaluator or reader.

Required base:
`cursor/ir-compiler-phase15-6d81-00a8` at
`f46c3eae27f03071a8b3a9e161533b8f24c23735`
([draft PR #99](https://github.com/gaoyu06/native-obfuscator/pull/99)). Sol
review [#102](https://github.com/gaoyu06/native-obfuscator/pull/102) accepted
that exact tip without a compiler change. The base is PR #99, not `master`.

## Admitted operations

| Bytecode | Input stack | Output stack / effect | Direct lowering |
| --- | --- | --- | --- |
| `SWAP` (95) | `..., value1, value2` | `..., value2, value1` | Reorder the existing SSA values |
| `AALOAD` (50) | `..., arrayref, index:I32` | `..., value:REFERENCE` | `JNIEnv::GetObjectArrayElement` |
| `AASTORE` (83) | `..., arrayref, index:I32, value:REFERENCE` | `...` | `JNIEnv::SetObjectArrayElement` |

`IrNodes.ArrayLoad` and `IrNodes.ArrayStore` now accept either the retained
`I32` element carrier for `IALOAD`/`IASTORE` or the `REFERENCE` carrier for
`AALOAD`/`AASTORE`. The node carrier selects the JNI operation, so primitive
and reference array operations cannot be confused during emission.

## SWAP validation and SSA behavior

The complete stack-type pass checks both operands without removing or
reordering either one. Both must have one JVM slot; therefore `I32`, `F32`,
and `REFERENCE` are admitted in every pairing, while either operand being
`I64` or `F64` rejects the method at opcode 95.

After validation, lowering only exchanges the two existing `IrValue`
references in the block state. It creates no IR instruction, temporary,
native cache entry, or JNI call. Successor stack phis consequently receive
the already reordered SSA values.

## Reference-array exceptions

Both reference-array operations first use the existing direct-IR null guard.
A null `arrayref` creates a pending `NullPointerException` and follows the
block's ordinary exceptional exit.

For a non-null array, `GetObjectArrayElement` and `SetObjectArrayElement`
perform the JNI bounds check. A negative index or an index greater than or
equal to the array length leaves a pending
`ArrayIndexOutOfBoundsException`; the emitter checks `ExceptionCheck`
immediately after the JNI call and transfers to the same exceptional exit.
The loaded reference is assigned to its SSA result only after that check.

`SetObjectArrayElement` also performs the JVM reference-component
compatibility check. An incompatible value for (for example) a `String[]`
leaves the JNI-raised `ArrayStoreException` pending. The direct emitter does
not clear or replace it: an in-method handler uses the shared exception
dispatcher, and an unmatched exception is rethrown or returns across JNI
while still pending.

## Fallback before mutation

Whole-method validation still completes before C++ emission, cache
allocation, native metadata emission, `ACC_NATIVE`, or constructor bridge
creation. The phase-16 regression executes admitted `AASTORE`, `AALOAD`, and
`SWAP` in bytecode order and then reaches unsupported `POP2`. Rejection is
reported at opcode 88 while generated output, native metadata, method access,
all caches, and hidden method state remain unchanged.

`NEWARRAY` forms outside the retained `int[]` slice, `MULTIANEWARRAY`,
primitive array loads/stores beyond that slice, `POP2`, `DUP2*`,
`invokedynamic`, and the evaluator/reader remain outside this phase.

## Verification

The focused suite is run with the required GNU C/C++ toolchain:

```text
CC=gcc CXX=g++ ./gradlew :obfuscator:test \
  --tests by.radioegor146.ir.IrCompilerTest \
  --tests by.radioegor146.CodegenModeTest \
  --rerun-tasks
```

Result on 2026-08-29: `BUILD SUCCESSFUL`.

Counts read directly from Gradle's JUnit XML:

```text
IrCompilerTest: tests=78, skipped=0, failures=0, errors=0 (time=0.725 s)
CodegenModeTest: tests=2, skipped=0, failures=0, errors=0 (time=0.098 s)
Total: 80 tests, 0 skipped, 0 failures, 0 errors
```

The five new focused tests cover all nine ordered pairings of `I32`, `F32`, and
`REFERENCE` for `SWAP`; rejection with `I64`/`F64` in either position;
`Object[]` and `String[]` store/load round trips; null, negative-index, and
`index == length` paths; JNI-raised `ArrayStoreException`; and
fallback-before-mutation after all three newly admitted operations.
Phase-9 through phase-15 regressions remain in the same focused suite,
including `jarray` `ARETURN`, constructor/prefix-local checks, exact Z/B/C/S
and F/D carriers, and String/Class/Long LDC.

### Real g++ smoke evidence

Environment:

```text
g++ (Ubuntu 13.3.0-6ubuntu2~24.04.1) 13.3.0
openjdk version "21.0.10" 2026-01-20
JNI headers: /usr/lib/jvm/java-21-openjdk-amd64/include
```

`generatedCppPassesGppSyntaxCheckWhenToolchainAvailable` is a required test,
not an assumption-based skip. It ran in 0.255 s and has no `<skipped>` child
in the JUnit XML. The retained translation unit contains exactly 128
`JNICALL` functions (the phase-15 unit's 119 plus nine phase-16 smoke
methods).

The retained unit
`/tmp/ir-compile-smoke7492160452615799211/ir-smoke.cpp` was independently
checked with:

```text
g++ -std=c++17 -fsyntax-only \
  -I/usr/lib/jvm/java-21-openjdk-amd64/include \
  -I/usr/lib/jvm/java-21-openjdk-amd64/include/linux \
  /tmp/ir-compile-smoke7492160452615799211/ir-smoke.cpp
```

`g++` exited zero with empty diagnostics.

### Default and retained assets

`CodegenModeTest.cliDefaultsToLegacy` remains the default-mode gate. `Main`
still declares `defaultValue = "legacy"`, and the public API overload without
a `CodegenMode` still delegates with `CodegenMode.LEGACY`.
`sources/cppsnippets.properties` remains present. No evaluator ISA, opcode
encoding, reader, primitive-array expansion, `MULTIANEWARRAY`, or
`POP2`/`DUP2*` work is included.

## Ship-readiness / 交付准备度

- **(a) Scope / 范围:** Direct typed-CFG IR support for category-one `SWAP`
  and reference-array `AALOAD`/`AASTORE`, including JNI pending-exception
  routing and pre-mutation fallback. /
  直接 typed-CFG IR 支持 category-one `SWAP` 与引用数组
  `AALOAD`/`AASTORE`，包括 JNI pending exception 路由和 mutation 前
  fallback。
- **(b) Ship-ready? / 可直接发布？:** **No — not ship-ready.** /
  **否——尚未达到可发布状态。**
- **(c) Review focus / 审查重点:** Review category-two `SWAP` rejection
  before stack mutation, reference carriers on the shared array nodes, and
  preservation of JNI-raised bounds and array-store exceptions. /
  请重点审查 category-two `SWAP` 在栈 mutation 前的拒绝、共享数组节点的
  reference carrier，以及 JNI 产生的越界与数组存储异常是否被完整保留。
- **(d) Integration / 集成:** Base is PR #99 at `f46c3eae`, not `master`;
  re-run the focused suite with `CC=gcc CXX=g++`; preserve phase-9 through
  phase-15 regressions, the `legacy` default, and snippet resources. /
  基线是 `f46c3eae` 的 PR #99 而非 `master`；请使用
  `CC=gcc CXX=g++` 重新运行聚焦测试；保留 phase-9 至 phase-15
  回归、`legacy` 默认值和 snippet 资源。
