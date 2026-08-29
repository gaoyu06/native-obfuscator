# IR phase 15 status

Phase 15 extends the optional direct Java bytecode → typed CFG IR → C++/JNI
compiler with the common `LDC` forms that remained a dominant real-fixture
fallback: String literals, object/array Class literals, and Long constants.
The CLI and API default remains `legacy`, unsupported methods retain
per-method legacy fallback, and `sources/cppsnippets.properties` remains
present. This phase is direct IR only and does not include the evaluator
lowering stack.

Required base:
`cursor/ir-compiler-phase14-6d81` at
`ece69f5810bbefe7cdc144e09980d5ad9e5fb22d`
([draft PR #95](https://github.com/gaoyu06/native-obfuscator/pull/95)). Sol
review [#98](https://github.com/gaoyu06/native-obfuscator/pull/98) accepted
that exact tip without a compiler change. The base is PR #95, not `master`.

## Admitted LDC forms

The frontend now admits these ASM `LdcInsnNode.cst` shapes:

| ASM constant | IR carrier/node | Materialization |
| --- | --- | --- |
| `Integer` | `I32` / `Const` | Existing integer literal path |
| `Long` | `I64` / `LongConst` | Existing signed `jlong` literal path |
| `Float` | `F32` / `FloatConst` | Existing exact raw-bit path |
| `Double` | `F64` / `DoubleConst` | Existing exact raw-bit path |
| `String` | `REFERENCE` / `StringConst` | Existing interned `cstrings` cache |
| object `Type` | `REFERENCE` / `ClassConst` | Existing `cclasses` lookup/cache |
| array `Type` | `REFERENCE` / `ClassConst` | Existing `cclasses` lookup/cache |

Class-file `LDC2_W` Long constants arrive from ASM as `Long`; no separate
encoding-specific IR node is needed. The tested values are
`0x1_0000_0000L` and `-1L`, rather than the existing `LCONST_0/1` cases.

Still rejected at whole-method validation are `Handle`, `ConstantDynamic`,
method `Type` (MethodType constants), and primitive `Type` constants. Primitive
Class literals such as `int.class` are intentionally left on legacy fallback:
phase 15 does not pretend an `I` descriptor is a class name, and does not add
the wrapper `Integer.TYPE` field-lookup machinery.

## String materialization

`StringConst` stores the Java value in IR and the direct emitter obtains its
ID from the existing per-class `cachedStrings`. The emitted value is the
corresponding `cstrings[id]`; no second string table was introduced.

`ClassSourceBuilder` already initializes every `cstrings` entry with a pointer
into the shared `StringPool`, calls `JNIEnv::NewStringUTF`, interns the result
through `utils::get_interned`, and publishes a global reference. `StringPool`
uses JNI modified UTF-8. The phase-15 regression covers:

- empty String;
- ASCII (`ascii`);
- non-ASCII (`héllo世界`); and
- embedded NUL (`nul\u0000inside`), verified as modified-UTF-8 bytes `C0 80`.

The constants are exercised both as an `invokestatic` reference argument and
as real `java.lang.String` receivers of the existing `String.length()`
intrinsic (`JNIEnv::GetStringLength`).

## Class materialization

Object `Type` constants are normalized to internal names such as
`java/lang/String` and `example/Fixture`. Array constants retain their JVM
descriptors, including tested `[I` and `[Ljava/lang/String;`.

`ClassConst` reuses the same `cclasses` weak-global cache and
`IrCppEmitter.emitClassCache` path as `NEW`, `ANEWARRAY`, `CHECKCAST`, and
`INSTANCEOF`. Object classes use `utils::find_class_wo_static` with the
defining class loader and an existing cached String. Array classes use
`JNIEnv::FindClass` with a shared `StringPool` pointer. After lookup, the
existing `ExceptionCheck` exceptional exit runs before the Class reference is
read from `cclasses`; a failed `FindClass` therefore keeps its pending
exception and cannot silently materialize null. The emitter does not clear
that exception.

## Fallback before mutation

The frontend validates every LDC shape with every other instruction before
C++ emission, cache allocation, native metadata emission, `ACC_NATIVE`, or
constructor bridge creation. The phase-15 regression first executes admitted
String, object Class, and wide Long constants in bytecode order, then reaches
an unsupported `Handle` LDC. Rejection is reported at opcode 18 (`LDC`) while
method access, generated output, native metadata, all caches, and hidden
method state remain unchanged. A separate primitive `Type` regression proves
the same pre-mutation fallback.

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
IrCompilerTest: tests=73, skipped=0, failures=0, errors=0 (time=0.842 s)
CodegenModeTest: tests=2, skipped=0, failures=0, errors=0 (time=0.102 s)
Total: 75 tests, 0 skipped, 0 failures, 0 errors
```

The five new focused tests cover modified-UTF-8 String materialization,
object/array Class materialization and pending-exception ordering, nontrivial
Long constants, primitive Class fallback, and `Handle` fallback after admitted
phase-15 constants. The phase-9 through phase-14 regressions remain in the
same focused suite, including `jarray` `ARETURN`, prefix-local and constructor
checks, exact Z/B/C/S carriers, and F/D carriers and operations.

The generated per-class source check observed exactly five
`NewStringUTF` calls: four phase-15 String literals plus the existing
`example.Math` cached class-name String needed by the static invoke. In the
retained smoke translation unit, the four new String literals produce four
`(jobject) cstrings[...]` uses and the four Class literals produce four
`(jobject) cclasses[...]` uses.

### Real g++ smoke evidence

Environment:

```text
g++ (Ubuntu 13.3.0-6ubuntu2~24.04.1) 13.3.0
openjdk version "21.0.10" 2026-01-20
JNI headers: /usr/lib/jvm/java-21-openjdk-amd64/include
```

`generatedCppPassesGppSyntaxCheckWhenToolchainAvailable` is a required test,
not an assumption-based skip. It ran in 0.370 s and has no `<skipped>` child
in the JUnit XML. Its retained translation unit contains exactly 119
`JNICALL` functions (the phase-14 unit's 116 plus String/Class/Long LDC smoke
methods), including retained phase-9 through phase-14 coverage.

The retained unit
`/tmp/ir-compile-smoke4069473773269394627/ir-smoke.cpp` was independently
checked with:

```text
g++ -std=c++17 -fsyntax-only \
  -I/usr/lib/jvm/java-21-openjdk-amd64/include \
  -I/usr/lib/jvm/java-21-openjdk-amd64/include/linux \
  /tmp/ir-compile-smoke4069473773269394627/ir-smoke.cpp
```

g++ exited zero with empty diagnostics.

### Default and retained assets

`CodegenModeTest.cliDefaultsToLegacy` passed. `Main` still declares
`defaultValue = "legacy"`, and the public API overload without a
`CodegenMode` still delegates with `CodegenMode.LEGACY`.
`sources/cppsnippets.properties` remains present. No evaluator ISA, opcode
encoding, reader, primitive-array, `MULTIANEWARRAY`, or `POP2`/`DUP2*` work
was added.

## Constructs that still fall back

`Handle`, `ConstantDynamic`, MethodType, and primitive Class LDC constants
remain unsupported. Primitive array loads/stores other than the existing
`int[]` slice, `MULTIANEWARRAY`, `POP2`/`DUP2*`, invokedynamic, unsupported
long scalar operations, and all evaluator ISA work also remain outside this
phase. The default was not changed from `legacy`, and no snippet resource was
removed.

## Ship-readiness / 交付准备度

- **(a) Scope / 范围:** Direct typed-CFG IR support for String, object/array
  Class, and Long LDC constants, including existing pool/cache reuse and
  pre-mutation fallback. /
  直接 typed-CFG IR 已支持 String、对象/数组 Class 与 Long 的 LDC 常量，
  并复用现有字符串池/类缓存且保持 mutation 前 fallback。
- **(b) Ship-ready? / 可直接发布？:** **No — not ship-ready.** /
  **否——尚未达到可发布状态。**
- **(c) Review focus / 审查重点:** Review modified-UTF-8 String reuse,
  Class-name normalization, pending-exception routing, wide Long constants,
  and rejection of primitive/MethodType/Handle constants before mutation. /
  请重点审查 modified UTF-8 字符串复用、Class 名称规范化、pending exception
  路由、宽 Long 常量，以及 primitive/MethodType/Handle 常量在 mutation 前拒绝。
- **(d) Integration / 集成:** Base is PR #95 at `ece69f5`, not `master`;
  re-run the focused suite with `CC=gcc CXX=g++`; preserve the phase-9
  `jarray` cast, constructor/prefix-local checks, and `legacy` default. /
  基线是 `ece69f5` 的 PR #95 而非 `master`；请使用 `CC=gcc CXX=g++`
  重新运行聚焦测试；保留 phase-9 `jarray` 转换、构造器/prefix-local 检查
  以及 `legacy` 默认值。
