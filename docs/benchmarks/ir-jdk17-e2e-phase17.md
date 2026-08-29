# Phase-17 IR-mode behavioral E2E on the fetched JDK 17 fixtures

## Scope and result

- Compiler under measurement: draft [PR #108](https://github.com/gaoyu06/native-obfuscator/pull/108)
  at `5a6f6097524c1fe42cd82be2425f5e6736667688` (`5a6f609`).
- Fixture source: `origin/cursor/test-jdk17-e2e-harness-6d81` at
  `7389820175f72eac2a062e9ae9a2917ec8815aed`.
- Measurement date (UTC): `2026-08-29`.
- The CLI was run with opt-in `--codegen=ir` and without `--annotations`.
- The CLI default remains `legacy`; this measurement did not change compiler or
  code-generation code.

All five inputs compiled with `javac --release 17`. All 36 code-bearing input
methods had matching `// IR codegen:` markers, with no fallback or missing
method. All five generated C++ trees configured and linked successfully with
`CC=gcc CXX=g++`. None of the five transformed JAR runs completed normally:
all exited 1, so behavioral stdout parity was **0/5**.

| Fixture | Input admission | CMake configure / build | Oracle | IR native | Behavioral result |
| --- | ---: | --- | --- | --- | --- |
| `InvokeDynamicLambdaE2E` | 6/6 IR | exit 0 / exit 0 | exit 0 | exit 1 | **FAIL — crash** |
| `MethodHandlesE2E` | 8/8 IR | exit 0 / exit 0 | exit 0 | exit 1 | **FAIL — crash** |
| `NestPrivateAccessE2E` | 5/5 IR | exit 0 / exit 0 | exit 0 | exit 1 | **FAIL — crash** |
| `RecordSemanticsE2E` | 9/9 IR | exit 0 / exit 0 | exit 0 | exit 1 | **FAIL — crash** |
| `SealedHierarchyE2E` | 8/8 IR | exit 0 / exit 0 | exit 0 | exit 1 | **FAIL — crash** |
| **Observed total** | **36/36 IR** | **5/5 / 5/5** | **5/5 exit 0** | **0/5 exit 0** | **0 equal, 0 normal-exit differ, 5 crash** |

This is evidence from one Linux x86-64 VM. It is not a product-support claim,
and in particular it must not be read as “JDK 17 supported.” Admission is not
behavioral correctness. The default code generator remains legacy.

## Environment

The exact version-command output was:

```text
Linux cursor 6.12.94+ #1 SMP PREEMPT_DYNAMIC Fri Aug 28 16:08:20 UTC 2026 x86_64 x86_64 x86_64 GNU/Linux
openjdk version "21.0.10" 2026-01-20
OpenJDK Runtime Environment (build 21.0.10+7-Ubuntu-124.04)
OpenJDK 64-Bit Server VM (build 21.0.10+7-Ubuntu-124.04, mixed mode, sharing)
javac 21.0.10
cmake version 3.28.3
gcc (Ubuntu 13.3.0-6ubuntu2~24.04.1) 13.3.0
g++ (Ubuntu 13.3.0-6ubuntu2~24.04.1) 13.3.0
```

The obfuscator build completed with:

```text
> Task :obfuscator:shadowJar UP-TO-DATE
BUILD SUCCESSFUL in 3s
5 actionable tasks: 5 up-to-date
[exit 0]
```

## Admission counting

The denominator uses the #110 inventory rule: run `jar tf`, run
`javap -p -s -c` for each input class, and include only input methods for which
`javap` shows a `Code:` body. Markers are joined to that inventory by exact
`class + method + descriptor`. Markers for generated methods absent from the
input JAR are excluded. Unsupported-method logs are checked separately.

| Fixture | Input inventory | Raw IR markers | Excluded generated markers | Matched input IR | Fallback | Missing |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| `InvokeDynamicLambdaE2E` | 6 | 7 | 1 | 6 | 0 | 0 |
| `MethodHandlesE2E` | 8 | 11 | 3 | 8 | 0 | 0 |
| `NestPrivateAccessE2E` | 5 | 7 | 2 | 5 | 0 | 0 |
| `RecordSemanticsE2E` | 9 | 11 | 2 | 9 | 0 | 0 |
| `SealedHierarchyE2E` | 8 | 12 | 4 | 8 | 0 | 0 |
| **Total** | **36** | **48** | **12** | **36** | **0** | **0** |

The 36 matched input markers were:

```text
InvokeDynamicLambdaE2E  Main.<init>()V
InvokeDynamicLambdaE2E  Main.decorate(Ljava/lang/String;)Ljava/lang/String;
InvokeDynamicLambdaE2E  Main.lambda$main$0(II)I
InvokeDynamicLambdaE2E  Main.lambda$main$1(Ljava/lang/String;Ljava/util/function/IntUnaryOperator;Ljava/lang/Integer;)Ljava/lang/String;
InvokeDynamicLambdaE2E  Main.lambda$main$2()V
InvokeDynamicLambdaE2E  Main.main([Ljava/lang/String;)V
MethodHandlesE2E        Base.<init>()V
MethodHandlesE2E        Base.message()Ljava/lang/String;
MethodHandlesE2E        Main.<init>()V
MethodHandlesE2E        Main.main([Ljava/lang/String;)V
MethodHandlesE2E        Target.<init>(Ljava/lang/String;)V
MethodHandlesE2E        Target.fail()V
MethodHandlesE2E        Target.join(Ljava/lang/String;)Ljava/lang/String;
MethodHandlesE2E        Target.sum(II)I
NestPrivateAccessE2E    Main.<init>()V
NestPrivateAccessE2E    Main.main([Ljava/lang/String;)V
NestPrivateAccessE2E    Main.secret(I)Ljava/lang/String;
NestPrivateAccessE2E    Main$Worker.<init>()V
NestPrivateAccessE2E    Main$Worker.access(LMain;)Ljava/lang/String;
RecordSemanticsE2E      Main.<init>()V
RecordSemanticsE2E      Main.describe(Ljava/lang/reflect/RecordComponent;)Ljava/lang/String;
RecordSemanticsE2E      Main.main([Ljava/lang/String;)V
RecordSemanticsE2E      Point.<init>(ILjava/lang/String;)V
RecordSemanticsE2E      Point.equals(Ljava/lang/Object;)Z
RecordSemanticsE2E      Point.hashCode()I
RecordSemanticsE2E      Point.label()Ljava/lang/String;
RecordSemanticsE2E      Point.toString()Ljava/lang/String;
RecordSemanticsE2E      Point.x()I
SealedHierarchyE2E      Circle.<init>(I)V
SealedHierarchyE2E      Circle.area()I
SealedHierarchyE2E      Main.<init>()V
SealedHierarchyE2E      Main.area(LShape;)I
SealedHierarchyE2E      Main.main([Ljava/lang/String;)V
SealedHierarchyE2E      Rectangle.<init>(II)V
SealedHierarchyE2E      Rectangle.area()I
SealedHierarchyE2E      Shape.kind()Ljava/lang/String;
```

A search of all five obfuscator logs for `IR codegen unsupported`,
`falling back`, and `leaving constructor` returned no matches. The build was
still attempted for every fixture, as required.

## Exact commands

Setup and source fetch:

```bash
cd /workspace
git merge-base HEAD 5a6f6097524c1fe42cd82be2425f5e6736667688
git diff --name-only 5a6f6097524c1fe42cd82be2425f5e6736667688..HEAD
./gradlew :obfuscator:shadowJar --console=plain
git fetch origin cursor/test-jdk17-e2e-harness-6d81
git rev-parse origin/cursor/test-jdk17-e2e-harness-6d81
git archive origin/cursor/test-jdk17-e2e-harness-6d81 obfuscator/test_data/tests/jdk17 | tar -x -C /tmp/native-obfuscator-ir-jdk17-e2e-phase17/fetched-sources
```

For each fixture the exact expanded compile, oracle, inventory, IR generation,
marker, native-build, packaging, and transformed-run commands were as follows.
The CLI reported `/native0/`; because the required CLI invocation selects the
unpack loader, the built library was added to the transformed JAR at the
loader's Linux x64 resource path. The final Java invocation uses the same
HotSpot-facing system properties as `ClassicTest`.

### `InvokeDynamicLambdaE2E`

```bash
javac --release 17 -g -d /tmp/native-obfuscator-ir-jdk17-e2e-phase17/fixtures/InvokeDynamicLambdaE2E/classes /tmp/native-obfuscator-ir-jdk17-e2e-phase17/fetched-sources/obfuscator/test_data/tests/jdk17/InvokeDynamicLambdaE2E/Main.java
jar --create --file /tmp/native-obfuscator-ir-jdk17-e2e-phase17/fixtures/InvokeDynamicLambdaE2E/input.jar --main-class Main -C /tmp/native-obfuscator-ir-jdk17-e2e-phase17/fixtures/InvokeDynamicLambdaE2E/classes .
(cd /tmp/native-obfuscator-ir-jdk17-e2e-phase17/fixtures/InvokeDynamicLambdaE2E && java -Dseed=1337 -jar /tmp/native-obfuscator-ir-jdk17-e2e-phase17/fixtures/InvokeDynamicLambdaE2E/input.jar)
jar tf /tmp/native-obfuscator-ir-jdk17-e2e-phase17/fixtures/InvokeDynamicLambdaE2E/input.jar
javap -p -s -c -classpath /tmp/native-obfuscator-ir-jdk17-e2e-phase17/fixtures/InvokeDynamicLambdaE2E/input.jar Main
java -jar /workspace/obfuscator/build/libs/obfuscator.jar /tmp/native-obfuscator-ir-jdk17-e2e-phase17/fixtures/InvokeDynamicLambdaE2E/input.jar /tmp/native-obfuscator-ir-jdk17-e2e-phase17/fixtures/InvokeDynamicLambdaE2E/output --codegen=ir
rg --no-filename -o '// IR codegen: .+' /tmp/native-obfuscator-ir-jdk17-e2e-phase17/fixtures/InvokeDynamicLambdaE2E/output/cpp/output
(cd /tmp/native-obfuscator-ir-jdk17-e2e-phase17/fixtures/InvokeDynamicLambdaE2E/output/cpp && CC=gcc CXX=g++ cmake .)
(cd /tmp/native-obfuscator-ir-jdk17-e2e-phase17/fixtures/InvokeDynamicLambdaE2E/output/cpp && CC=gcc CXX=g++ cmake --build . --config Release)
install -D -m 755 /tmp/native-obfuscator-ir-jdk17-e2e-phase17/fixtures/InvokeDynamicLambdaE2E/output/cpp/build/lib/libnative_library.so /tmp/native-obfuscator-ir-jdk17-e2e-phase17/fixtures/InvokeDynamicLambdaE2E/pack/native0/x64-linux.so
jar --update --file /tmp/native-obfuscator-ir-jdk17-e2e-phase17/fixtures/InvokeDynamicLambdaE2E/output/input.jar -C /tmp/native-obfuscator-ir-jdk17-e2e-phase17/fixtures/InvokeDynamicLambdaE2E/pack native0/x64-linux.so
(cd /tmp/native-obfuscator-ir-jdk17-e2e-phase17/fixtures/InvokeDynamicLambdaE2E/output && java -Djava.library.path=. -Dseed=1337 -Dplatform=HOTSPOT -Dtest.src=/tmp/native-obfuscator-ir-jdk17-e2e-phase17/fixtures/InvokeDynamicLambdaE2E -jar /tmp/native-obfuscator-ir-jdk17-e2e-phase17/fixtures/InvokeDynamicLambdaE2E/output/input.jar)
```

### `MethodHandlesE2E`

```bash
javac --release 17 -g -d /tmp/native-obfuscator-ir-jdk17-e2e-phase17/fixtures/MethodHandlesE2E/classes /tmp/native-obfuscator-ir-jdk17-e2e-phase17/fetched-sources/obfuscator/test_data/tests/jdk17/MethodHandlesE2E/Main.java
jar --create --file /tmp/native-obfuscator-ir-jdk17-e2e-phase17/fixtures/MethodHandlesE2E/input.jar --main-class Main -C /tmp/native-obfuscator-ir-jdk17-e2e-phase17/fixtures/MethodHandlesE2E/classes .
(cd /tmp/native-obfuscator-ir-jdk17-e2e-phase17/fixtures/MethodHandlesE2E && java -Dseed=1337 -jar /tmp/native-obfuscator-ir-jdk17-e2e-phase17/fixtures/MethodHandlesE2E/input.jar)
jar tf /tmp/native-obfuscator-ir-jdk17-e2e-phase17/fixtures/MethodHandlesE2E/input.jar
javap -p -s -c -classpath /tmp/native-obfuscator-ir-jdk17-e2e-phase17/fixtures/MethodHandlesE2E/input.jar Base
javap -p -s -c -classpath /tmp/native-obfuscator-ir-jdk17-e2e-phase17/fixtures/MethodHandlesE2E/input.jar Main
javap -p -s -c -classpath /tmp/native-obfuscator-ir-jdk17-e2e-phase17/fixtures/MethodHandlesE2E/input.jar Target
java -jar /workspace/obfuscator/build/libs/obfuscator.jar /tmp/native-obfuscator-ir-jdk17-e2e-phase17/fixtures/MethodHandlesE2E/input.jar /tmp/native-obfuscator-ir-jdk17-e2e-phase17/fixtures/MethodHandlesE2E/output --codegen=ir
rg --no-filename -o '// IR codegen: .+' /tmp/native-obfuscator-ir-jdk17-e2e-phase17/fixtures/MethodHandlesE2E/output/cpp/output
(cd /tmp/native-obfuscator-ir-jdk17-e2e-phase17/fixtures/MethodHandlesE2E/output/cpp && CC=gcc CXX=g++ cmake .)
(cd /tmp/native-obfuscator-ir-jdk17-e2e-phase17/fixtures/MethodHandlesE2E/output/cpp && CC=gcc CXX=g++ cmake --build . --config Release)
install -D -m 755 /tmp/native-obfuscator-ir-jdk17-e2e-phase17/fixtures/MethodHandlesE2E/output/cpp/build/lib/libnative_library.so /tmp/native-obfuscator-ir-jdk17-e2e-phase17/fixtures/MethodHandlesE2E/pack/native0/x64-linux.so
jar --update --file /tmp/native-obfuscator-ir-jdk17-e2e-phase17/fixtures/MethodHandlesE2E/output/input.jar -C /tmp/native-obfuscator-ir-jdk17-e2e-phase17/fixtures/MethodHandlesE2E/pack native0/x64-linux.so
(cd /tmp/native-obfuscator-ir-jdk17-e2e-phase17/fixtures/MethodHandlesE2E/output && java -Djava.library.path=. -Dseed=1337 -Dplatform=HOTSPOT -Dtest.src=/tmp/native-obfuscator-ir-jdk17-e2e-phase17/fixtures/MethodHandlesE2E -jar /tmp/native-obfuscator-ir-jdk17-e2e-phase17/fixtures/MethodHandlesE2E/output/input.jar)
```

### `NestPrivateAccessE2E`

```bash
javac --release 17 -g -d /tmp/native-obfuscator-ir-jdk17-e2e-phase17/fixtures/NestPrivateAccessE2E/classes /tmp/native-obfuscator-ir-jdk17-e2e-phase17/fetched-sources/obfuscator/test_data/tests/jdk17/NestPrivateAccessE2E/Main.java
jar --create --file /tmp/native-obfuscator-ir-jdk17-e2e-phase17/fixtures/NestPrivateAccessE2E/input.jar --main-class Main -C /tmp/native-obfuscator-ir-jdk17-e2e-phase17/fixtures/NestPrivateAccessE2E/classes .
(cd /tmp/native-obfuscator-ir-jdk17-e2e-phase17/fixtures/NestPrivateAccessE2E && java -Dseed=1337 -jar /tmp/native-obfuscator-ir-jdk17-e2e-phase17/fixtures/NestPrivateAccessE2E/input.jar)
jar tf /tmp/native-obfuscator-ir-jdk17-e2e-phase17/fixtures/NestPrivateAccessE2E/input.jar
javap -p -s -c -classpath /tmp/native-obfuscator-ir-jdk17-e2e-phase17/fixtures/NestPrivateAccessE2E/input.jar Main
javap -p -s -c -classpath /tmp/native-obfuscator-ir-jdk17-e2e-phase17/fixtures/NestPrivateAccessE2E/input.jar 'Main$Worker'
java -jar /workspace/obfuscator/build/libs/obfuscator.jar /tmp/native-obfuscator-ir-jdk17-e2e-phase17/fixtures/NestPrivateAccessE2E/input.jar /tmp/native-obfuscator-ir-jdk17-e2e-phase17/fixtures/NestPrivateAccessE2E/output --codegen=ir
rg --no-filename -o '// IR codegen: .+' /tmp/native-obfuscator-ir-jdk17-e2e-phase17/fixtures/NestPrivateAccessE2E/output/cpp/output
(cd /tmp/native-obfuscator-ir-jdk17-e2e-phase17/fixtures/NestPrivateAccessE2E/output/cpp && CC=gcc CXX=g++ cmake .)
(cd /tmp/native-obfuscator-ir-jdk17-e2e-phase17/fixtures/NestPrivateAccessE2E/output/cpp && CC=gcc CXX=g++ cmake --build . --config Release)
install -D -m 755 /tmp/native-obfuscator-ir-jdk17-e2e-phase17/fixtures/NestPrivateAccessE2E/output/cpp/build/lib/libnative_library.so /tmp/native-obfuscator-ir-jdk17-e2e-phase17/fixtures/NestPrivateAccessE2E/pack/native0/x64-linux.so
jar --update --file /tmp/native-obfuscator-ir-jdk17-e2e-phase17/fixtures/NestPrivateAccessE2E/output/input.jar -C /tmp/native-obfuscator-ir-jdk17-e2e-phase17/fixtures/NestPrivateAccessE2E/pack native0/x64-linux.so
(cd /tmp/native-obfuscator-ir-jdk17-e2e-phase17/fixtures/NestPrivateAccessE2E/output && java -Djava.library.path=. -Dseed=1337 -Dplatform=HOTSPOT -Dtest.src=/tmp/native-obfuscator-ir-jdk17-e2e-phase17/fixtures/NestPrivateAccessE2E -jar /tmp/native-obfuscator-ir-jdk17-e2e-phase17/fixtures/NestPrivateAccessE2E/output/input.jar)
```

### `RecordSemanticsE2E`

```bash
javac --release 17 -g -d /tmp/native-obfuscator-ir-jdk17-e2e-phase17/fixtures/RecordSemanticsE2E/classes /tmp/native-obfuscator-ir-jdk17-e2e-phase17/fetched-sources/obfuscator/test_data/tests/jdk17/RecordSemanticsE2E/Main.java
jar --create --file /tmp/native-obfuscator-ir-jdk17-e2e-phase17/fixtures/RecordSemanticsE2E/input.jar --main-class Main -C /tmp/native-obfuscator-ir-jdk17-e2e-phase17/fixtures/RecordSemanticsE2E/classes .
(cd /tmp/native-obfuscator-ir-jdk17-e2e-phase17/fixtures/RecordSemanticsE2E && java -Dseed=1337 -jar /tmp/native-obfuscator-ir-jdk17-e2e-phase17/fixtures/RecordSemanticsE2E/input.jar)
jar tf /tmp/native-obfuscator-ir-jdk17-e2e-phase17/fixtures/RecordSemanticsE2E/input.jar
javap -p -s -c -classpath /tmp/native-obfuscator-ir-jdk17-e2e-phase17/fixtures/RecordSemanticsE2E/input.jar Main
javap -p -s -c -classpath /tmp/native-obfuscator-ir-jdk17-e2e-phase17/fixtures/RecordSemanticsE2E/input.jar Point
java -jar /workspace/obfuscator/build/libs/obfuscator.jar /tmp/native-obfuscator-ir-jdk17-e2e-phase17/fixtures/RecordSemanticsE2E/input.jar /tmp/native-obfuscator-ir-jdk17-e2e-phase17/fixtures/RecordSemanticsE2E/output --codegen=ir
rg --no-filename -o '// IR codegen: .+' /tmp/native-obfuscator-ir-jdk17-e2e-phase17/fixtures/RecordSemanticsE2E/output/cpp/output
(cd /tmp/native-obfuscator-ir-jdk17-e2e-phase17/fixtures/RecordSemanticsE2E/output/cpp && CC=gcc CXX=g++ cmake .)
(cd /tmp/native-obfuscator-ir-jdk17-e2e-phase17/fixtures/RecordSemanticsE2E/output/cpp && CC=gcc CXX=g++ cmake --build . --config Release)
install -D -m 755 /tmp/native-obfuscator-ir-jdk17-e2e-phase17/fixtures/RecordSemanticsE2E/output/cpp/build/lib/libnative_library.so /tmp/native-obfuscator-ir-jdk17-e2e-phase17/fixtures/RecordSemanticsE2E/pack/native0/x64-linux.so
jar --update --file /tmp/native-obfuscator-ir-jdk17-e2e-phase17/fixtures/RecordSemanticsE2E/output/input.jar -C /tmp/native-obfuscator-ir-jdk17-e2e-phase17/fixtures/RecordSemanticsE2E/pack native0/x64-linux.so
(cd /tmp/native-obfuscator-ir-jdk17-e2e-phase17/fixtures/RecordSemanticsE2E/output && java -Djava.library.path=. -Dseed=1337 -Dplatform=HOTSPOT -Dtest.src=/tmp/native-obfuscator-ir-jdk17-e2e-phase17/fixtures/RecordSemanticsE2E -jar /tmp/native-obfuscator-ir-jdk17-e2e-phase17/fixtures/RecordSemanticsE2E/output/input.jar)
```

### `SealedHierarchyE2E`

```bash
javac --release 17 -g -d /tmp/native-obfuscator-ir-jdk17-e2e-phase17/fixtures/SealedHierarchyE2E/classes /tmp/native-obfuscator-ir-jdk17-e2e-phase17/fetched-sources/obfuscator/test_data/tests/jdk17/SealedHierarchyE2E/Main.java
jar --create --file /tmp/native-obfuscator-ir-jdk17-e2e-phase17/fixtures/SealedHierarchyE2E/input.jar --main-class Main -C /tmp/native-obfuscator-ir-jdk17-e2e-phase17/fixtures/SealedHierarchyE2E/classes .
(cd /tmp/native-obfuscator-ir-jdk17-e2e-phase17/fixtures/SealedHierarchyE2E && java -Dseed=1337 -jar /tmp/native-obfuscator-ir-jdk17-e2e-phase17/fixtures/SealedHierarchyE2E/input.jar)
jar tf /tmp/native-obfuscator-ir-jdk17-e2e-phase17/fixtures/SealedHierarchyE2E/input.jar
javap -p -s -c -classpath /tmp/native-obfuscator-ir-jdk17-e2e-phase17/fixtures/SealedHierarchyE2E/input.jar Circle
javap -p -s -c -classpath /tmp/native-obfuscator-ir-jdk17-e2e-phase17/fixtures/SealedHierarchyE2E/input.jar Main
javap -p -s -c -classpath /tmp/native-obfuscator-ir-jdk17-e2e-phase17/fixtures/SealedHierarchyE2E/input.jar Rectangle
javap -p -s -c -classpath /tmp/native-obfuscator-ir-jdk17-e2e-phase17/fixtures/SealedHierarchyE2E/input.jar Shape
java -jar /workspace/obfuscator/build/libs/obfuscator.jar /tmp/native-obfuscator-ir-jdk17-e2e-phase17/fixtures/SealedHierarchyE2E/input.jar /tmp/native-obfuscator-ir-jdk17-e2e-phase17/fixtures/SealedHierarchyE2E/output --codegen=ir
rg --no-filename -o '// IR codegen: .+' /tmp/native-obfuscator-ir-jdk17-e2e-phase17/fixtures/SealedHierarchyE2E/output/cpp/output
(cd /tmp/native-obfuscator-ir-jdk17-e2e-phase17/fixtures/SealedHierarchyE2E/output/cpp && CC=gcc CXX=g++ cmake .)
(cd /tmp/native-obfuscator-ir-jdk17-e2e-phase17/fixtures/SealedHierarchyE2E/output/cpp && CC=gcc CXX=g++ cmake --build . --config Release)
install -D -m 755 /tmp/native-obfuscator-ir-jdk17-e2e-phase17/fixtures/SealedHierarchyE2E/output/cpp/build/lib/libnative_library.so /tmp/native-obfuscator-ir-jdk17-e2e-phase17/fixtures/SealedHierarchyE2E/pack/native0/x64-linux.so
jar --update --file /tmp/native-obfuscator-ir-jdk17-e2e-phase17/fixtures/SealedHierarchyE2E/output/input.jar -C /tmp/native-obfuscator-ir-jdk17-e2e-phase17/fixtures/SealedHierarchyE2E/pack native0/x64-linux.so
(cd /tmp/native-obfuscator-ir-jdk17-e2e-phase17/fixtures/SealedHierarchyE2E/output && java -Djava.library.path=. -Dseed=1337 -Dplatform=HOTSPOT -Dtest.src=/tmp/native-obfuscator-ir-jdk17-e2e-phase17/fixtures/SealedHierarchyE2E -jar /tmp/native-obfuscator-ir-jdk17-e2e-phase17/fixtures/SealedHierarchyE2E/output/input.jar)
```

## Native-build output

Every configure selected the requested GNU compilers. The following lines
appeared in each of the five real configure outputs:

```text
-- The C compiler identification is GNU 13.3.0
-- The CXX compiler identification is GNU 13.3.0
-- Check for working C compiler: /usr/bin/gcc - skipped
-- Check for working CXX compiler: /usr/bin/g++ - skipped
-- Found JNI: /usr/lib/jvm/default-java/include  found components: AWT JVM
-- Configuring done (0.3s)
-- Generating done (0.0s)
[exit 0]
```

The terminal build output for each fixture was:

```text
InvokeDynamicLambdaE2E:
[100%] Linking CXX shared library build/lib/libnative_library.so
[100%] Built target native_library
[exit 0]

MethodHandlesE2E:
[100%] Linking CXX shared library build/lib/libnative_library.so
[100%] Built target native_library
[exit 0]

NestPrivateAccessE2E:
[100%] Linking CXX shared library build/lib/libnative_library.so
[100%] Built target native_library
[exit 0]

RecordSemanticsE2E:
[100%] Linking CXX shared library build/lib/libnative_library.so
[100%] Built target native_library
[exit 0]

SealedHierarchyE2E:
[100%] Linking CXX shared library build/lib/libnative_library.so
[100%] Built target native_library
[exit 0]
```

There was no CMake, compiler, or linker failure to paste.

## HotSpot oracle versus IR-native output

Only stdout is compared. A transformed run with a nonzero exit is classified
as `crash`, even if its partial stdout happens to match an oracle prefix.

### `InvokeDynamicLambdaE2E` — crash

Oracle stdout, exit 0:

```text
[value=22, [LAMBDA]]
1
lambda-failure
```

Native stdout was empty. Native stderr, exit 1:

```text
Exception in thread "main" java.lang.BootstrapMethodError: java.lang.NoClassDefFoundError: native.magic.1.lookup.obfuscator0.5129965794332685
	at Main.main(Native Method)
Caused by: java.lang.NoClassDefFoundError: native.magic.1.lookup.obfuscator0.5129965794332685
	... 1 more
Caused by: java.lang.ClassNotFoundException: native.magic.1.lookup.obfuscator0.5129965794332685
	at java.base/jdk.internal.loader.BuiltinClassLoader.loadClass(BuiltinClassLoader.java:641)
	at java.base/jdk.internal.loader.ClassLoaders$AppClassLoader.loadClass(ClassLoaders.java:188)
	at java.base/java.lang.ClassLoader.loadClass(ClassLoader.java:526)
	... 1 more
```

### `MethodHandlesE2E` — crash

Oracle stdout, exit 0:

```text
13
created-virtual
42
base-special
42
method-handle-failure
```

Native stdout was empty. Native stderr, exit 1:

```text
Exception in thread "main" java.lang.NoSuchMethodError: invokeExact
	at Main.main(Native Method)
```

### `NestPrivateAccessE2E` — crash

Oracle stdout, exit 0:

```text
secret=25
25
Main
true
Main,Main$Worker
```

Native stdout was empty. Native stderr, exit 1:

```text
Exception in thread "main" java.lang.BootstrapMethodError: java.lang.NoClassDefFoundError: native.magic.1.lookup.obfuscator0.29852015779122487
	at Main.secret(Native Method)
	at Main$Worker.access(Native Method)
	at Main.main(Native Method)
Caused by: java.lang.NoClassDefFoundError: native.magic.1.lookup.obfuscator0.29852015779122487
	... 3 more
Caused by: java.lang.ClassNotFoundException: native.magic.1.lookup.obfuscator0.29852015779122487
	at java.base/jdk.internal.loader.BuiltinClassLoader.loadClass(BuiltinClassLoader.java:641)
	at java.base/jdk.internal.loader.ClassLoaders$AppClassLoader.loadClass(ClassLoaders.java:188)
	at java.base/java.lang.ClassLoader.loadClass(ClassLoader.java:526)
	... 3 more
```

### `RecordSemanticsE2E` — crash

Oracle stdout, exit 0:

```text
7
alpha
true
false
true
Point[x=7, label=alpha]
true
x:int,label:java.lang.String
```

Native stdout before exit 1:

```text
7
alpha
```

Native stderr:

```text
Exception in thread "main" java.lang.BootstrapMethodError: java.lang.NoClassDefFoundError: native.magic.1.lookup.obfuscator0.8625555278755609
	at Point.equals(Native Method)
	at Main.main(Native Method)
Caused by: java.lang.NoClassDefFoundError: native.magic.1.lookup.obfuscator0.8625555278755609
	... 2 more
Caused by: java.lang.ClassNotFoundException: native.magic.1.lookup.obfuscator0.8625555278755609
	at java.base/jdk.internal.loader.BuiltinClassLoader.loadClass(BuiltinClassLoader.java:641)
	at java.base/jdk.internal.loader.ClassLoaders$AppClassLoader.loadClass(ClassLoaders.java:188)
	at java.base/java.lang.ClassLoader.loadClass(ClassLoader.java:526)
	... 2 more
```

### `SealedHierarchyE2E` — crash

Oracle stdout, exit 0:

```text
9
20
Circle
Rectangle
true
Circle,Rectangle
```

Native stdout before exit 1:

```text
9
20
Circle
Rectangle
false
```

Native stderr:

```text
Exception in thread "main" java.lang.NullPointerException: Cannot read the array length because "array" is null
	at java.base/java.util.Arrays.stream(Arrays.java:5528)
	at Main.main(Native Method)
```

## Boundary of the measurement

The optional pre-existing ClassicTest fixtures were not run in this
measurement; the required five fetched JDK 17 fixtures were run in full. No
compiler fix was attempted after the failures. The result is therefore:

- compile admission: 36/36 input methods reached IR;
- native buildability on this VM: 5/5 generated trees linked;
- behavioral HotSpot parity: 0/5 transformed runs completed, so 0/5 passed;
- release implication: none; this is measurement evidence, not a support gate.
