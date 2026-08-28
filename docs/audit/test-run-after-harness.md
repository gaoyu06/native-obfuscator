# Test run after the JDK 17 E2E harness

Run date: 2026-08-28

## Environment

```text
openjdk version "21.0.10" 2026-01-20
OpenJDK Runtime Environment (build 21.0.10+7-Ubuntu-124.04)
OpenJDK 64-Bit Server VM (build 21.0.10+7-Ubuntu-124.04, mixed mode, sharing)
javac 21.0.10
cmake version 3.28.3
g++ (Ubuntu 13.3.0-6ubuntu2~24.04.1) 13.3.0
```

`krak2` and Zig were not installed. CMake and g++ were installed. The image's
default `/usr/bin/c++` was Clang 18.1.3, which selected an incomplete GCC 14
installation and failed to link a trivial program with
`/usr/bin/ld: cannot find -lstdc++`. Calling `g++` directly linked successfully.

The shipped tool and the Gradle test source set still compile with Java 8
source/target compatibility. JDK 17 fixture sources are test resources, not
Gradle test sources. The E2E harness compiles only fixtures under
`test_data/tests/jdk17/` with `javac --release 17`; it aborts those tests with a
clear JUnit assumption if that release is unavailable. No `.class` files are
committed because the existing fixture convention is source-only and JDK 21
was available here.

## Required command, unmodified environment

Command:

```sh
./gradlew :obfuscator:test --console=plain
```

Result: **FAILED**, exit 1.

```text
17 tests completed, 12 failed, 1 skipped
BUILD FAILED in 16s
```

This proves JUnit discovery and execution are active: 4 unit tests passed, the
12 source-based E2E cases reached transpilation and then failed during CMake's
compiler check, and the Krakatau fixture was skipped. All 12 E2E failures had
the same environment cause:

```text
The C++ compiler "/usr/bin/c++" is not able to compile a simple test program.
/usr/bin/ld: cannot find -lstdc++: No such file or directory
```

`PullRequest72` was the one skipped test:

```text
Fixture requires krak2, but krak2 -V was not successful
```

## Native-capable command

Because `g++` itself works in this image, the suite was also run with CMake's
compiler choice corrected through its standard environment variables:

```sh
CC=gcc CXX=g++ ./gradlew :obfuscator:cleanTest :obfuscator:test --console=plain
```

Result: **FAILED**, exit 1.

```text
17 tests completed, 3 failed, 1 skipped
BUILD FAILED in 2m 7s
```

The 13 passing tests were the 4 unit tests, the 7 runnable pre-existing E2E
fixtures, `InvokeDynamicLambdaE2E`, and `MethodHandlesE2E`.
`PullRequest72` was skipped because `krak2` was absent.
`NestPrivateAccessE2E`, `SealedHierarchyE2E`, and `RecordSemanticsE2E` failed
for reproduced class-metadata semantics defects described in
`jdk17-e2e-status.md`.

For both passing new E2E cases, logs show every input class was preprocessed
and processed. Each then completed CMake configure/build and transformed-JAR
execution with exact stdout equality on `HOTSPOT`, `STD_JAVA`, and `ANDROID`.
The three metadata cases completed transpilation, native compilation, and a
transformed run on `HOTSPOT`; each failed there, so the harness correctly did
not continue those cases to the other platforms.

## Harness correction discovered during verification

An intermediate run appeared green but logged `Skipping Main` for every new
fixture. It was rejected as evidence. The old harness had always enabled
annotation-only processing even though nearly all fixtures have no `@Native`
annotation. The final harness processes classes by default and uses the
`.use-annotations` marker only for `EmptyTest1`, whose purpose is to verify
`@NotNative`. The counts above are from the corrected harness.
