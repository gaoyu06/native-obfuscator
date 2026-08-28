# JDK 17 metadata fix — real test run

Run date: 2026-08-28
Branch: `cursor/jdk17-classfile-metadata-6d81` (based on
`cursor/test-jdk17-e2e-harness-6d81`, commits `1a7cb68` and `7de3400`)

## Environment

```text
openjdk version "21.0.10" 2026-01-20
OpenJDK Runtime Environment (build 21.0.10+7-Ubuntu-124.04)
javac 21.0.10
cmake version 3.28.3
gcc/g++ (Ubuntu 13.3.0-6ubuntu2~24.04.1) 13.3.0
krak2: not installed
```

## Changes under test

1. `NativeObfuscator.process` no longer stamps every processed class with
   `classNode.version = 52`. It now only raises classes whose major version is
   below 52 (injected interface method bodies require 52; class-literal LDC
   requires 49) and never downgrades. A Java 17 (major 61) input class keeps
   major 61, so the JVM honors its `NestHost`/`NestMembers`, `Record`, and
   `PermittedSubclasses` attributes, which ASM had been writing all along.
   Synthetic hidden classes remain at version 52 — they contain only native
   stubs and trivial `MethodHandle.invoke` trampolines.
2. `IndyPreprocessor` (STD_JAVA/ANDROID path) now accepts a bootstrap method
   whose third parameter is declared `java.lang.invoke.TypeDescriptor`, the
   supertype of `MethodType`. `java.lang.runtime.ObjectMethods.bootstrap`,
   used by records' generated `equals`/`hashCode`/`toString`, declares exactly
   that; the old literal descriptor check replaced the call with a thrown
   `BootstrapMethodError("Wrong 3 first arguments in bsm")`. The rewrite
   always passes an actual `MethodType`, and `ObjectMethods.bootstrap`
   returns a `CallSite` when given one, so the relaxation is sound.

## Command and result

```sh
CC=gcc CXX=g++ ./gradlew :obfuscator:cleanTest :obfuscator:test --console=plain
```

Result: **BUILD SUCCESSFUL in 2m 23s** — 17 tests, 16 passed, 1 skipped,
0 failed.

```text
Running test "testStaticList()" -> SUCCESS in 20ms
Running test "testPatternList()" -> SUCCESS in 1ms
Running test "TestClInitStacktrace" -> SUCCESS in 5546ms
Running test "JavaObfuscatorTest" -> SUCCESS in 55047ms
Running test "IndyTest1" -> SUCCESS in 5966ms
Running test "PullRequest72" -> SKIPPED in 7ms
Running test "Issue52" -> SUCCESS in 5868ms
Running test "NestPrivateAccessE2E" -> SUCCESS in 8321ms
Running test "InvokeDynamicLambdaE2E" -> SUCCESS in 11123ms
Running test "SealedHierarchyE2E" -> SUCCESS in 9554ms
Running test "MethodHandlesE2E" -> SUCCESS in 9547ms
Running test "RecordSemanticsE2E" -> SUCCESS in 11549ms
Running test "InterfaceDefaultStacktrace" -> SUCCESS in 5949ms
Running test "InterfaceDefault" -> SUCCESS in 5812ms
Running test "EmptyTest1" -> SUCCESS in 3439ms
Running test "testGet()" -> SUCCESS in 3ms
Running test "testBuild()" -> SUCCESS in 1ms
BUILD SUCCESSFUL in 2m 23s
```

`PullRequest72` was skipped for the same pre-existing environment reason as
before: `krak2` is not installed. Each passing E2E case runs its transformed
jar on all three platforms (`HOTSPOT`, `STD_JAVA`, `ANDROID`) and requires
exact stdout equality with the untransformed oracle.

## Before / after

Baseline on the harness branch (`docs/audit/test-run-after-harness.md`, same
command, same environment): 17 tests, **3 failed**
(`NestPrivateAccessE2E`, `SealedHierarchyE2E`, `RecordSemanticsE2E`),
1 skipped.

| Case | Before | After |
|---|---|---|
| `NestPrivateAccessE2E` | FAIL on HOTSPOT (nest reflection wrong) | PASS on all 3 platforms |
| `SealedHierarchyE2E` | FAIL on HOTSPOT (`isSealed()` false, null permitted subclasses) | PASS on all 3 platforms |
| `RecordSemanticsE2E` | FAIL on HOTSPOT (`isRecord()` false, null record components) | PASS on all 3 platforms |
| `InvokeDynamicLambdaE2E` | PASS | PASS |
| `MethodHandlesE2E` | PASS | PASS |
| 7 pre-existing runnable E2E fixtures + 4 unit tests | PASS | PASS |
| `PullRequest72` (Krakatau) | SKIP (`krak2` missing) | SKIP (`krak2` missing) |

An intermediate run with only the version fix (commit `1a7cb68`) had
16 passed / 1 failed: nest and sealed passed everywhere and records passed on
`HOTSPOT`, but `RecordSemanticsE2E` failed on `STD_JAVA` with
`java.lang.BootstrapMethodError: Wrong 3 first arguments in bsm` raised from
`Point.equals`. That exposed the second, independent defect fixed by
commit `7de3400`.

## Known remaining gaps

- `PullRequest72` remains unverified here (`krak2` unavailable in this
  environment); it was equally skipped in the baseline.
- No claim is made for hidden classes, `ConstantDynamic` bootstrap entries, or
  preview-flagged class files beyond what these tests cover; preview minor
  bits are passed through untouched by the new version logic.
- The tool itself still builds with Java 8 source/target compatibility;
  nothing in the tool's own sources uses newer language features.
