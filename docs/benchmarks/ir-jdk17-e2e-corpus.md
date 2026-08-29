# Expanded JDK 17 IR-mode behavioral E2E corpus

## Scope and result

- Measurement date (UTC): `2026-08-29`.
- Base: `origin/master` at
  `e997d71c7525a4c607e29b6eb1ae9140a72dfd22`.
- Measured fixture commit:
  `53c046fe4a301d0e8ae3d4c8aa07a163ea295616`.
- Every fixture was compiled with `javac --release 17 -g`.
- Every obfuscator invocation explicitly selected `--codegen=ir`; the CLI
  default remains `legacy`.
- The oracle and transformed JAR were separate real Java processes. Native
  code was configured and built separately for every fixture with
  `CC=gcc CXX=g++`, and stdout was compared byte for byte.

The five pre-existing fixtures and six new fixtures all compiled, transpiled,
linked, and exited normally. The result on this one Linux x86-64 VM was
**11/11 exact stdout matches**. Independent input-method inventory matching
found **82/82 code-bearing input methods admitted to IR**, with no fallback
and no missing classification.

This is not a claim that “JDK 17 is supported.” It is evidence for eleven
specific `--release 17` programs on one OpenJDK 21 HotSpot runtime and one
native toolchain. It is not a runtime, platform, architecture, or broad JDK
compatibility matrix.

## Fixtures and behavioral outcomes

| Fixture | Observable coverage | Input IR / fallback | CMake configure / build | Oracle exit | Native exit | Stdout |
| --- | --- | ---: | --- | ---: | ---: | --- |
| `InstanceofPatternE2E` | JDK 16-final `instanceof` patterns, `&&` pattern scope, negated-pattern flow scope, arrays, `String.isBlank` and `strip` | 4 / 0 | 0 / 0 | 0 | 0 | **exact match** |
| `InvokeDynamicLambdaE2E` | Capturing lambdas, method/constructor references, primitive capture, and exception propagation | 6 / 0 | 0 / 0 | 0 | 0 | **exact match** |
| `MethodHandlesE2E` | Constructor, static, virtual, special and field handles, `invokeExact`, `asType`, and exception propagation | 8 / 0 | 0 / 0 | 0 | 0 | **exact match** |
| `NestPrivateAccessE2E` | Private nestmate access and nest reflection metadata | 5 / 0 | 0 / 0 | 0 | 0 | **exact match** |
| `RecordCompactConstructorE2E` | Compact-constructor parameter normalization and validation, record fields/accessors, loop logic, and generated `toString` | 10 / 0 | 0 / 0 | 0 | 0 | **exact match** |
| `RecordSemanticsE2E` | Record accessors, equality, hash code, `toString`, and record reflection metadata | 9 / 0 | 0 / 0 | 0 | 0 | **exact match** |
| `SealedHierarchyE2E` | Sealed-interface dispatch and permitted-subclass reflection metadata | 8 / 0 | 0 / 0 | 0 | 0 | **exact match** |
| `SealedRecordDispatchE2E` | Sealed interface implemented by records, interface dispatch, record accessors, and pattern matching without reflection | 16 / 0 | 0 / 0 | 0 | 0 | **exact match** |
| `StreamToListE2E` | JDK 16 `Stream.toList()`, ordered pipelines, method references, immutable result behavior, and null retention | 4 / 0 | 0 / 0 | 0 | 0 | **exact match** |
| `SwitchExpressionsE2E` | JDK 14-final switch expressions, arrow labels, multi-label cases, block arms, `yield`, and exhaustive enum switching | 9 / 0 | 0 / 0 | 0 | 0 | **exact match** |
| `TextBlocksE2E` | JDK 15-final text blocks, incidental indentation, line continuation, `\s`, `String.lines`, and native loop/string behavior | 3 / 0 | 0 / 0 | 0 | 0 | **exact match** |
| **Observed total** | **11 real programs** | **82 / 0** | **11/11 / 11/11** | **11/11 exit 0** | **11/11 exit 0** | **11/11 exact** |

No fixture crashed and no stdout differed, so there is no first differing line
or crash exit to report. Oracle stderr and transformed-run stderr were empty
for all eleven fixtures.

## Environment

The exact version-command output was:

```text
Linux cursor 6.12.94+ #1 SMP PREEMPT_DYNAMIC Fri Aug 28 16:08:20 UTC 2026 x86_64 x86_64 x86_64 GNU/Linux
openjdk version "21.0.10" 2026-01-20
OpenJDK Runtime Environment (build 21.0.10+7-Ubuntu-124.04)
OpenJDK 64-Bit Server VM (build 21.0.10+7-Ubuntu-124.04, mixed mode, sharing)
javac 21.0.10
gcc (Ubuntu 13.3.0-6ubuntu2~24.04.1) 13.3.0
g++ (Ubuntu 13.3.0-6ubuntu2~24.04.1) 13.3.0
cmake version 3.28.3
```

Only OpenJDK 21 was installed under `/usr/lib/jvm`; this report therefore does
not claim execution on a JDK 17 VM. The inputs are Java 17 classfiles produced
with `--release 17`.

The obfuscator build command was:

```bash
CC=gcc CXX=g++ ./gradlew :obfuscator:shadowJar --console=plain
```

It completed with `BUILD SUCCESSFUL` and exit 0. Each of the eleven CMake
configure logs selected:

```text
-- The C compiler identification is GNU 13.3.0
-- The CXX compiler identification is GNU 13.3.0
-- Found JNI: /usr/lib/jvm/default-java/include  found components: AWT JVM
```

Every build ended with `[100%] Built target native_library`; there was no
configure, compiler, or linker failure.

## Exact E2E commands

The fixture list supplied to the measurement loop was:

```bash
fixtures=(
  InstanceofPatternE2E
  InvokeDynamicLambdaE2E
  MethodHandlesE2E
  NestPrivateAccessE2E
  RecordCompactConstructorE2E
  RecordSemanticsE2E
  SealedHierarchyE2E
  SealedRecordDispatchE2E
  StreamToListE2E
  SwitchExpressionsE2E
  TextBlocksE2E
)
```

The loop expanded the following commands once per fixture. Each command's
stdout, stderr, and exit code were retained separately under
`/tmp/native-obfuscator-ir-jdk17-e2e-corpus/$fixture/`:

```bash
repo=/workspace
root=/tmp/native-obfuscator-ir-jdk17-e2e-corpus
obfuscator="$repo/obfuscator/build/libs/obfuscator.jar"
source_dir="$repo/obfuscator/test_data/tests/jdk17/$fixture"
work="$root/$fixture"
classes="$work/classes"
output="$work/output"
pack="$work/pack"

javac --release 17 -g -d "$classes" "$source_dir/Main.java"
jar --create --file "$work/input.jar" --main-class Main -C "$classes" .
(cd "$work" && java -Dseed=1337 -jar "$work/input.jar")
java -jar "$obfuscator" "$work/input.jar" "$output" --codegen=ir
(cd "$output/cpp" && CC=gcc CXX=g++ cmake .)
(cd "$output/cpp" && CC=gcc CXX=g++ cmake --build . --config Release)
install -D -m 755 \
  "$output/cpp/build/lib/libnative_library.so" \
  "$pack/native0/x64-linux.so"
jar --update --file "$output/input.jar" \
  -C "$pack" native0/x64-linux.so
(cd "$output" && \
  java -Djava.library.path=. \
    -Dseed=1337 \
    -Dplatform=HOTSPOT \
    -Dtest.src="$work" \
    -jar "$output/input.jar")
cmp -s "$work/oracle.stdout" "$work/native.stdout"
```

The transformed library was inserted at the unpack loader's reported Linux
x64 resource location. The final process used the same HotSpot-facing system
properties as `ClassicTest`.

## Admission accounting

Admission was counted independently from behavioral success. For each input
JAR, all non-versioned `.class` entries were enumerated. The following command
was run for every class:

```bash
javap -p -s -c -classpath "$work/input.jar" "$class_name"
```

Only methods with a `Code:` body entered the inventory. Inventory keys were
the exact class, method name, and descriptor. They were joined against exact
generated `// IR codegen: owner.method(descriptor)` markers. Obfuscator stdout
and stderr were also scanned for `IR codegen unsupported`, `falling back`, and
`leaving constructor bytecode unchanged`.

| Fixture | Input inventory | Raw IR markers | Excluded generated markers | Matched input IR | Fallback | Missing |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| `InstanceofPatternE2E` | 4 | 5 | 1 | 4 | 0 | 0 |
| `InvokeDynamicLambdaE2E` | 6 | 7 | 1 | 6 | 0 | 0 |
| `MethodHandlesE2E` | 8 | 11 | 3 | 8 | 0 | 0 |
| `NestPrivateAccessE2E` | 5 | 7 | 2 | 5 | 0 | 0 |
| `RecordCompactConstructorE2E` | 10 | 12 | 2 | 10 | 0 | 0 |
| `RecordSemanticsE2E` | 9 | 11 | 2 | 9 | 0 | 0 |
| `SealedHierarchyE2E` | 8 | 12 | 4 | 8 | 0 | 0 |
| `SealedRecordDispatchE2E` | 16 | 19 | 3 | 16 | 0 | 0 |
| `StreamToListE2E` | 4 | 5 | 1 | 4 | 0 | 0 |
| `SwitchExpressionsE2E` | 9 | 10 | 1 | 9 | 0 | 0 |
| `TextBlocksE2E` | 3 | 4 | 1 | 3 | 0 | 0 |
| **Total** | **82** | **103** | **21** | **82** | **0** | **0** |

The 21 excluded markers belong to methods generated by the obfuscator and
absent from the input JAR inventories. No unsupported/fallback log was found.
As with the previous 36/36 result, 82/82 admission is not itself behavioral
evidence; the separate native executions above provide that evidence for this
corpus.

## Observed stdout

For every block below, both the oracle and transformed process produced the
shown lines followed by a final newline. The one line containing trailing
spaces is rendered with explicit escapes so the Markdown has no invisible
trailing whitespace.

### `InstanceofPatternE2E`

The third raw line was `other:` followed by three U+0020 spaces and LF. It is
rendered below with `\x20` escapes.

```text
text:PATTERN
array:3:10
other:\x20\x20\x20
42
-1
```

### `InvokeDynamicLambdaE2E`

```text
[value=22, [LAMBDA]]
1
lambda-failure
```

### `MethodHandlesE2E`

```text
13
created-virtual
42
base-special
42
method-handle-failure
```

### `NestPrivateAccessE2E`

```text
secret=25
25
Main
true
Main,Main$Worker
```

### `RecordCompactConstructorE2E`

```text
3:9
6
42
Interval[start=3, end=9]
negative-start:-1
```

### `RecordSemanticsE2E`

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

### `SealedHierarchyE2E`

```text
9
20
Circle
Rectangle
true
Circle,Rectangle
```

### `SealedRecordDispatchE2E`

```text
number:6:12
word:sealed:6
number:-2:-4
total=14
```

### `StreamToListE2E`

```text
[1, 2, 2, 3, 4]
unmodifiable
[left, null, right]
true
```

### `SwitchExpressionsE2E`

```text
7
11
24
-9
RED=stop
AMBER=wait
GREEN=go
```

### `TextBlocksE2E`

```text
alpha|__beta|gamma-delta_|
3
26
991761422
```

## Test-harness boundary

`TestsGenerator` recursively discovers every directory containing Java source,
so the six new directories require no allowlist or harness edit. The broad
`TestsGenerator` suite was not run: it traverses all checked-in ClassicTest
data and each ClassicTest executes the legacy API path on all three `Platform`
values, while this task specifically requires the opt-in IR path. Instead, all
eleven JDK 17 fixtures were compiled, natively built, executed, and compared
directly under `--codegen=ir` as recorded above.

No compiler, evaluator, reader, default-codegen, or opcode-interpreter source
was changed.
