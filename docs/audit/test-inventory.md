# Test inventory and what it proves

## Gradle/JUnit wiring

`obfuscator/build.gradle` targets Java 8 source/bytecode, adds `test_data` to test resources, enables JUnit 5, and sets at least 32 parallel forks (`obfuscator/build.gradle:10-22,47-49,61-113`). There are four ordinary unit-test methods:

- `StringPoolTest.testBuild` checks exact generated `string_pool.cpp` text; `testGet` checks modified-UTF-8 offsets (`obfuscator/src/test/java/by/radioegor146/source/StringPoolTest.java:7-48`).
- `ClassMethodListTest.testStaticList` and `testPatternList` check exact/wildcard filter matching (`obfuscator/src/test/java/by/radioegor146/source/ClassMethodListTest.java:11-39`).

`TestsGenerator.generateTests` recursively makes one dynamic test for each first directory containing immediate `.java` files (`TestsGenerator.java:42-52,55-81`). In the current tree that produces eight E2E cases:

| Dynamic case | Main behavior / coverage |
|---|---|
| `clinit/TestClInitStacktrace` | Static-initializer proxying and stack trace output. |
| `empty/EmptyTest1` | Annotation excludes `main`; effectively checks a jar with no selected method. |
| `indy/IndyTest1` | Java stream/lambda `invokedynamic`. |
| `interface/InterfaceDefault` | Interface default-method proxy. |
| `interface/InterfaceDefaultStacktrace` | Interface proxy plus stack trace. |
| `issues/Issue52` | Primitive/reference multidimensional arrays. |
| `java-obfuscator-test/JavaObfuscatorTest` | Imported broad Java 8 corpus: inheritance, exceptions, arithmetic, inner classes, reflection, resources, loaders, annotations/security, and a calculation loop. |
| `pull-requests/PullRequest72` | Krakatau-authored Java 11 `StringConcatFactory.makeConcatWithConstants` `invokedynamic`, including static arguments and varargs (`TestStringConcatFactory.j:1-19`). |

## What each dynamic test really executes

`ClassicTest.execute` performs the full path, not a transpiler-only check:

1. copy Java/Krakatau inputs and resources to a temporary tree (`ClassicTest.java:45-95`);
2. invoke external `javac` (`ClassicTest.java:97-103`);
3. when detected, invoke `krak2 asm` for every `.j` file (`ClassicTest.java:105-114`);
4. invoke external `jar`, then run the unmodified “ideal” jar (`ClassicTest.java:116-135`);
5. for **each** `Platform` (`HOTSPOT`, `STD_JAVA`, `ANDROID`), call `NativeObfuscator.process`, invoke `cmake .`, invoke `cmake --build`, copy the produced native library, and run the transformed jar with `java.library.path` set (`ClassicTest.java:137-184`; `Platform.java:3-7`);
6. require exit success and exact transformed-vs-ideal **stdout** equality (`ClassicTest.java:184-190`).

This is transpile + C++ configure + C++ compile/link + transformed-JVM execution. It does not compare stderr; stack-trace cases only prove process success unless their relevant output is on stdout. It does not inspect generated C++, transformed class metadata, JNI leaks, performance, or Zig output. The broad imported corpus catches many failures internally and prints `ERROR`/`FAIL`; because both ideal and transformed output are compared, an identical baseline failure can still pass (`pack/Main.java:27-116`).

## Krakatau / `krak2`

The factory probes `krak2 -V`. Failure only sets `useKrakatau=false` and prints a warning (`TestsGenerator.java:60-72`). `ClassicTest` still discovers `.j` files but skips assembly when that flag is false (`ClassicTest.java:61-69,105-114`). Consequently, `pull-requests/PullRequest72/Main.java` still reflectively loads `TestStringConcatFactory` (`Main.java:1-10`) and will fail when `krak2` was absent and no class was produced. The README correctly says some tests require Krakatau (`README.md:205-210`); the behavior is failure, not skip.

## CI

`.github/workflows/main.yml` runs on every push. Its matrix uses Temurin JDK 8, 11, 17, 21, and 25 across Ubuntu, macOS, and Windows, with a macOS-13 substitution for JDK 8 (`main.yml:1-38`). It builds Krakatau from source, installs `krak2`, installs CMake/compiler dependencies, then runs `./gradlew build` (`main.yml:39-65`). This is broad host-JDK/toolchain coverage of the Java-8-authored corpus; it is **not** language-feature coverage because test sources are compiled without `--release` or feature-specific sources and contain no record/sealed/text-block/pattern/hidden-class cases.

## Missing test dimensions

- No direct opcode completeness test; unsupported `JSR`/`RET`, `ConstantDynamic`, and malformed residual `invokedynamic` are not asserted.
- No class-file metadata assertions after the unconditional version-52 rewrite.
- No JDK 17+ source-feature corpus (records, sealed classes, nestmates, switch expressions, text blocks, pattern matching).
- No hidden-class API or comprehensive method-handle test.
- No Zig installer/compiler/unit/E2E test under `obfuscator/src/test`.
- No sanitizer, JNI local-reference stress, concurrency/cache race, or performance benchmark gate.
- CI matrix entries are configuration evidence only; this audit does not treat the YAML as proof that any historical/current run passed.
