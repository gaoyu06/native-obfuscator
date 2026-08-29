# IR phase 13 status

Phase 13 extends the optional Java bytecode → typed CFG IR → C++/JNI compiler
with the JVM small integral field and invoke descriptors `Z`, `B`, `C`, and
`S`. Their operand-stack and local carriers remain IR `I32`, while heap access
and JNI calls use the descriptor-specific JNI types and function families. The
CLI and API default remains `legacy`, unsupported methods retain per-method
legacy fallback, and all existing snippet resources remain present.

Required base:
`cursor/ir-phase12-sol-review-6d81` at
`481b7b108388380bfbbdf94703ee56eb4b601b02`
([draft PR #89](https://github.com/gaoyu06/native-obfuscator/pull/89)). This is
the preferred phase-12 tip containing the constructor prefix-local correction.
The unfixed phase-12 branch and the alternate review branch are not part of
this phase.

## Field descriptors

Instance and static field operations now use the exact JNI family:

| Descriptor | Instance accessors | Static accessors | IR read carrier |
| --- | --- | --- | --- |
| `Z` | `GetBooleanField` / `SetBooleanField` | `GetStaticBooleanField` / `SetStaticBooleanField` | `I32`, zero-extended |
| `B` | `GetByteField` / `SetByteField` | `GetStaticByteField` / `SetStaticByteField` | `I32`, sign-extended |
| `C` | `GetCharField` / `SetCharField` | `GetStaticCharField` / `SetStaticCharField` | `I32`, zero-extended |
| `S` | `GetShortField` / `SetShortField` | `GetStaticShortField` / `SetStaticShortField` | `I32`, sign-extended |

Field reads explicitly widen the JNI result to `jint`. Field writes explicitly
narrow the `I32` value before the JNI call:

- `Z`: `(jboolean) ((uint32_t) value & 1)`, matching JVM `putfield` /
  `putstatic` boolean narrowing;
- `B`: `(jbyte) value`;
- `C`: `(jchar) value`; and
- `S`: `(jshort) value`.

The existing exact `I`, `J`, object, and array families are unchanged.

## Invoke descriptors

`invokestatic`, `invokevirtual`, `invokeinterface`, and non-constructor
`invokespecial` now accept `Z`, `B`, `C`, and `S` argument and return sorts.
Arguments are narrowed to the matching JNI type before the variadic JNI call,
and returned values are widened to the IR `I32` carrier.

The return descriptor selects the exact call family:

- virtual and interface:
  `CallBooleanMethod`, `CallByteMethod`, `CallCharMethod`, and
  `CallShortMethod`;
- static:
  `CallStaticBooleanMethod`, `CallStaticByteMethod`,
  `CallStaticCharMethod`, and `CallStaticShortMethod`; and
- special:
  `CallNonvirtualBooleanMethod`, `CallNonvirtualByteMethod`,
  `CallNonvirtualCharMethod`, and `CallNonvirtualShortMethod`.

The existing Void, Int, Long, and Object families are unchanged. The supported
constructor representation remains a special void invoke using
`CallNonvirtualVoidMethod`; its verifier-safe bridge and retained
`this(...)`/`super(...)` prefix are unchanged.

IR methods whose own return descriptor is `Z`, `B`, `C`, or `S` narrow the
`I32` return at the JNI function boundary. Boolean return narrowing uses the
same low-bit rule as JVM `ireturn`.

## Fallback before mutation

`F` and `D` field and invoke descriptors remain unsupported. The frontend
validates all instructions and descriptors before C++ emission, cache
allocation, native metadata emission, method flag changes, or constructor
bridge creation.

The phase-13 regression constructs valid `Z`/`B`/`C`/`S` instance/static field
operations and static/virtual/interface/special invokes, followed by an
unsupported float instruction. Rejection occurs at that instruction with the
method access, output, native metadata, and string/class/field/method caches
unchanged.

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
IrCompilerTest: tests=62, skipped=0, failures=0, errors=0 (time=0.669 s)
CodegenModeTest: tests=2, skipped=0, failures=0, errors=0 (time=0.099 s)
Total: 64 tests, 0 skipped, 0 failures, 0 errors
```

Phase-13 coverage includes:

- instance and static `Z`/`B`/`C`/`S` field get/put round trips;
- boolean false/true, negative byte/short, and char value 200 cases;
- exact narrowing on writes and sign/zero extension on reads;
- argument and return lowering for all four sorts across static, virtual,
  interface, and special invoke forms;
- null-receiver exceptional exits for newly admitted field and invoke forms;
- `F`/`D` field and invoke rejection for all invoke forms;
- fallback before mutation after newly admitted operations; and
- retained phase-9 array return, phase-10 `I`/`J`/reference fields, phase-11
  interface/special invokes, and phase-12 constructor bridge and prefix-local
  rejection regressions.

### Real g++ smoke evidence

Environment:

```text
g++ (Ubuntu 13.3.0-6ubuntu2~24.04.1) 13.3.0
openjdk version "21.0.10" 2026-01-20
JNI headers: present
```

`generatedCppPassesGppSyntaxCheckWhenToolchainAvailable` ran in 0.281 s and has
no `<skipped>` child in the JUnit XML. Its retained translation unit contains
87 `JNICALL` functions, including every phase-13 field and invoke family and
the phase-9 through phase-12 regressions.

The exact retained unit
`/tmp/ir-compile-smoke7831278959029876564/ir-smoke.cpp` was also checked
independently with:

```text
g++ -std=c++17 -fsyntax-only \
  -I/usr/lib/jvm/java-21-openjdk-amd64/include \
  -I/usr/lib/jvm/java-21-openjdk-amd64/include/linux \
  /tmp/ir-compile-smoke7831278959029876564/ir-smoke.cpp
```

g++ exited zero with empty diagnostics.

### Default and retained assets

`CodegenModeTest.cliDefaultsToLegacy` passed. `Main` still declares
`defaultValue = "legacy"`, and the public API overload without a `CodegenMode`
still delegates with `CodegenMode.LEGACY`. The phase-9 `jarray` return cast,
phase-10 fields, phase-11 invokes, phase-12 constructor bridge and prefix-local
checks, and `sources/cppsnippets.properties` remain present.

## Constructs that still fall back

This phase does not add float/double operations or descriptors,
`MULTIANEWARRAY`, non-`int` primitive arrays, `POP2`, or `invokedynamic`.
Methods containing those constructs continue to use the existing fallback
policy. The default was not changed from `legacy`, and no snippet resource was
removed.
