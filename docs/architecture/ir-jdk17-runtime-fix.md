# IR JDK 17 runtime repair evidence

## Scope

This change repairs the five JDK 17 fixtures measured against the phase-17 IR
tip. It is narrow runtime evidence, not a claim that JDK 17 is product-supported.
The command-line default remains `legacy`; every transformed run below opted in
with `--codegen=ir`.

The compiler base was draft PR #108 commit
`5a6f6097524c1fe42cd82be2425f5e6736667688`. Fixture sources were fetched from
`origin/cursor/test-jdk17-e2e-harness-6d81` at
`7389820175f72eac2a062e9ae9a2917ec8815aed`.

## Changes

- Processed classes retain their input classfile major version. Only classes
  older than Java 8 are raised to the Java 8 floor. This keeps the Java 17
  nest, record, and permitted-subclass attributes valid.
- `ObjectMethods.bootstrap` is accepted when its third parameter is declared as
  `TypeDescriptor`; the value supplied by the preprocessor is still a
  `MethodType`.
- The IR emitter recognizes lookup, class-loader, current-class,
  `link_call_site`, and reverse-invoke preprocessor markers as intrinsics. It no
  longer emits symbolic calls to the deliberately nonexistent
  `native.magic.1.*` marker classes.
- Signature-polymorphic `MethodHandle.invokeExact` and `invoke` calls use the
  same generated Java trampoline strategy in IR and legacy modes. The
  trampoline calls `MethodHandle.invoke` from bytecode, where the JVM applies
  signature-polymorphic linkage, instead of asking JNI for an `invokeExact`
  method ID that cannot exist at the call-site descriptor.
- Generated trampoline classes are written to every output JAR, including
  HotSpot output. The existing non-Android native-library embedding and eager
  `DefineClass` path remains in place.

The `BootstrapMethods` data used by lambda and record call sites is consumed by
the existing invokedynamic preprocessor. If preprocessing removes every indy
instruction, ASM does not emit an unused literal `BootstrapMethods` attribute;
the bootstrap handles and arguments are instead represented by the lowered
code. The repair preserves their behavior and does not downgrade the
classfile.

## Reproduction

Environment:

```text
Linux cursor 6.12.94+ x86_64
openjdk version "21.0.10" 2026-01-20
javac 21.0.10
cmake version 3.28.3
gcc (Ubuntu 13.3.0-6ubuntu2~24.04.1) 13.3.0
g++ (Ubuntu 13.3.0-6ubuntu2~24.04.1) 13.3.0
```

Build and focused tests:

```bash
CC=gcc CXX=g++ ./gradlew :obfuscator:test \
  --tests by.radioegor146.ir.IrCompilerTest \
  --tests by.radioegor146.CodegenModeTest --console=plain
CC=gcc CXX=g++ ./gradlew :obfuscator:shadowJar --console=plain
```

Fixture setup (repeated for each fixture name):

```bash
git fetch origin cursor/test-jdk17-e2e-harness-6d81
mkdir -p /tmp/native-obfuscator-ir-jdk17-runtime-fix/sources
git archive origin/cursor/test-jdk17-e2e-harness-6d81 \
  obfuscator/test_data/tests/jdk17 | \
  tar -x -C /tmp/native-obfuscator-ir-jdk17-runtime-fix/sources

fixture=InvokeDynamicLambdaE2E # repeat with each of the five table names
fixture_dir="/tmp/native-obfuscator-ir-jdk17-runtime-fix/fixtures/$fixture"
mkdir -p "$fixture_dir/classes" "$fixture_dir/pack"
javac --release 17 -g -d "$fixture_dir/classes" \
  "/tmp/native-obfuscator-ir-jdk17-runtime-fix/sources/obfuscator/test_data/tests/jdk17/$fixture/Main.java"
jar --create --file "$fixture_dir/input.jar" --main-class Main \
  -C "$fixture_dir/classes" .
(cd "$fixture_dir" && java -Dseed=1337 -jar "$fixture_dir/input.jar")

java -jar /workspace/obfuscator/build/libs/obfuscator.jar \
  "$fixture_dir/input.jar" "$fixture_dir/output" --codegen=ir
jar tf "$fixture_dir/output/input.jar"
(cd "$fixture_dir/output/cpp" && CC=gcc CXX=g++ cmake .)
(cd "$fixture_dir/output/cpp" && \
  CC=gcc CXX=g++ cmake --build . --config Release)

install -D -m 755 \
  "$fixture_dir/output/cpp/build/lib/libnative_library.so" \
  "$fixture_dir/pack/native0/x64-linux.so"
jar --update --file "$fixture_dir/output/input.jar" \
  -C "$fixture_dir/pack" native0/x64-linux.so
(cd "$fixture_dir/output" && java -Djava.library.path=. -Dseed=1337 \
  -Dplatform=HOTSPOT -Dtest.src="$fixture_dir" \
  -jar "$fixture_dir/output/input.jar")
```

Only stdout was compared, and equality required both oracle and transformed
processes to exit zero. All CMake invocations explicitly selected
`CC=gcc CXX=g++`.

## Before and after

The phase-17 report in draft PR #112 recorded **36/36 IR admission, 5/5 CMake
builds, and 0/5 normal runtime exits**. This run used the same fixture revision
and inventory rule.

| Fixture | Input IR admission | Configure / build | Native exit | Stdout parity | HotSpot JAR hidden class |
| --- | ---: | --- | ---: | --- | --- |
| `InvokeDynamicLambdaE2E` | 6/6 | 0 / 0 | 0 | PASS | `native0/hidden/Hidden0.class` |
| `MethodHandlesE2E` | 8/8 | 0 / 0 | 0 | PASS | `native0/hidden/Hidden0.class` |
| `NestPrivateAccessE2E` | 5/5 | 0 / 0 | 0 | PASS | `native0/hidden/Hidden0.class` |
| `RecordSemanticsE2E` | 9/9 | 0 / 0 | 0 | PASS | `native0/hidden/Hidden0.class` |
| `SealedHierarchyE2E` | 8/8 | 0 / 0 | 0 | PASS | `native0/hidden/Hidden0.class` |
| **Total** | **36/36** | **5/5 / 5/5** | **5/5 exit 0** | **5/5 PASS** | **5/5 present** |

Raw IR marker counts were 7, 11, 7, 11, and 12 respectively. Excluding the
1, 3, 2, 2, and 4 generated `<clinit>` methods gives the 6, 8, 5, 9, and 8
code-bearing input methods. No log contained `IR codegen unsupported`,
`falling back`, or `leaving constructor`. No output JAR contained a
`native/magic/*` entry because those names are compiler-internal markers, not
loadable generated classes.

The exact equal stdout was:

```text
InvokeDynamicLambdaE2E:
[value=22, [LAMBDA]]
1
lambda-failure

MethodHandlesE2E:
13
created-virtual
42
base-special
42
method-handle-failure

NestPrivateAccessE2E:
secret=25
25
Main
true
Main,Main$Worker

RecordSemanticsE2E:
7
alpha
true
false
true
Point[x=7, label=alpha]
true
x:int,label:java.lang.String

SealedHierarchyE2E:
9
20
Circle
Rectangle
true
Circle,Rectangle
```

`javap -v` on transformed classes reported major version 61. It also showed:

- `Main` / `Main$Worker`: `NestMembers` and `NestHost`;
- `Point`: the `Record` components `int x` and `java.lang.String label`;
- `Shape`: `PermittedSubclasses` containing `Circle` and `Rectangle`;
- `native0.hidden.Hidden0`: major version 52 and generated `mhinvoke*` /
  `invokereverse*` methods whose bytecode invokes
  `java/lang/invoke/MethodHandle.invoke`.

## Remaining failures and boundary

There were no remaining failures in these five fixtures on this Linux x86-64
run. This does not cover arbitrary JDK 17 bytecode, other JVM implementations,
other architectures, Android behavior at runtime, or the full project test
corpus, and therefore does not establish JDK 17 product support.
