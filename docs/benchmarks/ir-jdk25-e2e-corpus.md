# Opt-in IR behavioral E2E on the JDK 25 fixture corpus

## Scope and result

- Base: `origin/master` at
  `a7e54539461f12fa0eddd21973c716bc5f99708e`. No compiler, IR, evaluator, or
  interpreter source was changed for this measurement; this branch adds only
  documentation.
- Measurement date (UTC): `2026-08-29`.
- The host JDK is 21. A real JDK 25 (Eclipse Temurin 25.0.4.1+1) was
  downloaded from Adoptium, checksum-verified, and unpacked to `/opt/jdk25`.
  Every fixture was compiled with `/opt/jdk25/bin/javac --release 25 -g`,
  without preview flags; all four fixtures use features that are final in
  JDK 25.
- The oracle (HotSpot) run and the transformed native run both used
  `/opt/jdk25/bin/java`. The transpiler itself ran on the host JDK 21; its
  bundled ASM 9.8 reads class-file version 69 without errors.
- Every transpiler invocation explicitly selected `--codegen=ir`.
- CMake was configured and built with `CC=gcc CXX=g++` and
  `JAVA_HOME=/opt/jdk25`; each configure reported
  `Found JNI: /opt/jdk25/include`.
- Only stdout was compared, byte for byte. A nonzero transformed-process exit
  would have been a failure even if its partial stdout matched.

All four sources compiled with exit 0 and their unmodified oracle runs exited
0. Of the 21 code-bearing input methods, 20 reached IR. The one exception is
`Main$Validated.<init>(I)V` in `FlexibleConstructorBodiesE2E`, whose flexible
constructor body branches before the `super(...)` call; the transpiler logged
`IR codegen unsupported ... Control flow before the this/super call cannot be
split safely at bytecode instruction 8 (opcode 154); leaving constructor
bytecode unchanged` and left that constructor as Java bytecode. There were no
legacy per-method fallbacks and no missing methods. All four generated C++
trees configured and linked, all four transformed JARs exited 0, and all four
stdout files matched their oracle exactly.

**This must not be read as “JDK 25 supported.”** It is one behavioral
measurement of four small fixtures on one Linux x86-64 host. The default code
generator remains `legacy`, the `FlexibleConstructorBodiesE2E` result is a
hybrid (one constructor stayed in Java bytecode), and no wider JDK 25 corpus,
vendor, or architecture was evaluated.

| Fixture | Input IR / inventory | Fallback / left-in-Java / missing | CMake configure / build | Oracle exit | Native exit | Exact stdout |
| --- | ---: | --- | --- | ---: | ---: | --- |
| `CompactSourceModuleImportE2E` | 2/2 | 0 / 0 / 0 | 0 / 0 | 0 | 0 | match |
| `FlexibleConstructorBodiesE2E` | 6/7 | 0 / 1 / 0 | 0 / 0 | 0 | 0 | match |
| `ScopedValuesE2E` | 5/5 | 0 / 0 / 0 | 0 / 0 | 0 | 0 | match |
| `StreamGatherersE2E` | 7/7 | 0 / 0 / 0 | 0 / 0 | 0 | 0 | match |
| **Observed total** | **20/21** | **0 / 1 / 0** | **4/4 / 4/4** | **4/4 exit 0** | **4/4 exit 0** | **4/4 match** |

The four fixtures cover JDK 25 language and library surface:

- `CompactSourceModuleImportE2E`: compact source file with an implicit class,
  instance `void main()`, and `import module java.base` (JEP 512/511);
- `FlexibleConstructorBodiesE2E`: statements before `super(...)` and
  `this(...)` in constructors, including a throwing prologue (JEP 513);
- `ScopedValuesE2E`: `ScopedValue` binding, nesting, and `orElse` (JEP 506);
- `StreamGatherersE2E`: `Gatherers.windowFixed`, `scan`, and `fold`
  (final since JDK 24).

## Environment

Host toolchain (ran the transpiler, Gradle, CMake, gcc):

```text
Linux cursor 6.12.94+ #1 SMP PREEMPT_DYNAMIC Fri Aug 28 16:08:20 UTC 2026 x86_64 x86_64 x86_64 GNU/Linux
openjdk version "21.0.10" 2026-01-20
OpenJDK Runtime Environment (build 21.0.10+7-Ubuntu-124.04)
OpenJDK 64-Bit Server VM (build 21.0.10+7-Ubuntu-124.04, mixed mode, sharing)
javac 21.0.10
cmake version 3.28.3
gcc (Ubuntu 13.3.0-6ubuntu2~24.04.1) 13.3.0
g++ (Ubuntu 13.3.0-6ubuntu2~24.04.1) 13.3.0
Python 3.12.3
```

JDK 25 toolchain (compiled the fixtures, ran the oracle and the transformed
JAR, provided the JNI headers):

```text
openjdk version "25.0.4.1" 2026-08-18 LTS
OpenJDK Runtime Environment Temurin-25.0.4.1+1 (build 25.0.4.1+1-LTS)
OpenJDK 64-Bit Server VM Temurin-25.0.4.1+1 (build 25.0.4.1+1-LTS, mixed mode, sharing)
javac 25.0.4.1
```

JDK 25 was installed non-interactively (Ubuntu 24.04 apt only offers
OpenJDK 21):

```bash
curl -sSfL -o /tmp/jdk25.tar.gz \
  "https://github.com/adoptium/temurin25-binaries/releases/download/jdk-25.0.4.1%2B1/OpenJDK25U-jdk_x64_linux_hotspot_25.0.4.1_1.tar.gz"
echo "dbb698396d478e7fa2b1e50f4103324b2a99b90569ee27c33f2261f9215cf41e  /tmp/jdk25.tar.gz" | sha256sum -c
sudo mkdir -p /opt/jdk25
sudo tar xzf /tmp/jdk25.tar.gz -C /opt/jdk25 --strip-components=1
```

The shadow-JAR build completed with `BUILD SUCCESSFUL` and exit 0. Each CMake
configure identified GNU C and C++ 13.3.0 and JNI under `/opt/jdk25/include`.
Each build ended with:

```text
[100%] Linking CXX shared library build/lib/libnative_library.so
[100%] Built target native_library
```

## Admission inventory

The denominator was derived independently from each input JAR. `jar tf`
enumerated input classes, and `/opt/jdk25/bin/javap -p -s -c` identified
methods with a `Code:` body. Results were joined by exact class, method, and
descriptor, with the same rules as the JDK 17/21 corpus docs:

- a matching `// IR codegen:` marker counted as IR;
- a matching `IR codegen unsupported` log counted as legacy fallback or
  constructor left in Java bytecode;
- an inventory method with neither counted as missing;
- generated markers absent from the input inventory were excluded. Every
  excluded marker in this run was a transpiler-inserted `<clinit>` on a class
  that had no source `<clinit>`.

| Fixture | Inventory | Raw IR markers | Excluded generated markers | Matched IR | Legacy fallback | Left in Java | Missing |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| `CompactSourceModuleImportE2E` | 2 | 3 | 1 | 2 | 0 | 0 | 0 |
| `FlexibleConstructorBodiesE2E` | 7 | 10 | 4 | 6 | 0 | 1 | 0 |
| `ScopedValuesE2E` | 5 | 5 | 0 | 5 | 0 | 0 | 0 |
| `StreamGatherersE2E` | 7 | 8 | 1 | 7 | 0 | 0 | 0 |
| **Total** | **21** | **26** | **6** | **20** | **0** | **1** | **0** |

Per-method join results:

```text
CompactSourceModuleImportE2E
  Main.<init>()V                          IR
  Main.main()V                            IR
FlexibleConstructorBodiesE2E
  Main.<init>()V                          IR
  Main.main([Ljava/lang/String;)V         IR
  Main$Base.<init>(I)V                    IR
  Main$Base.value()I                      IR
  Main$Delegating.<init>(I)V              IR
  Main$Delegating.<init>(Ljava/lang/String;)V  IR
  Main$Validated.<init>(I)V               constructor left in Java bytecode
ScopedValuesE2E
  Main.<clinit>()V                        IR
  Main.<init>()V                          IR
  Main.lambda$main$0()V                   IR
  Main.main([Ljava/lang/String;)V         IR
  Main.printRequest()V                    IR
StreamGatherersE2E
  Main.<init>()V                          IR
  Main.fixedWindows()Ljava/util/List;     IR
  Main.foldedTotal()I                     IR
  Main.lambda$foldedTotal$0()Ljava/lang/Integer;   IR
  Main.lambda$runningTotals$0()Ljava/lang/Integer; IR
  Main.main([Ljava/lang/String;)V         IR
  Main.runningTotals()Ljava/util/List;    IR
```

The one non-IR method produced this transpiler log line (the only
`IR codegen unsupported` line in the whole run):

```text
IR codegen unsupported for Main$Validated#<init>(I)V: Control flow before the this/super call cannot be split safely at bytecode instruction 8 (opcode 154); leaving constructor bytecode unchanged
```

This is the documented flexible-constructor-body limitation: `Validated(int)`
computes `Math.abs`, branches on the result, and conditionally throws before
calling `super(...)`. The constructor stayed as Java bytecode inside the
transformed JAR, so the `FlexibleConstructorBodiesE2E` pass is a hybrid
result, not a pure-IR one.

## Exact commands

The obfuscator was built with:

```bash
cd /workspace
./gradlew :obfuscator:shadowJar --console=plain --rerun-tasks
```

The following fixture list was used; it includes every checked-in `jdk25`
fixture:

```bash
fixtures=(
  CompactSourceModuleImportE2E
  FlexibleConstructorBodiesE2E
  ScopedValuesE2E
  StreamGatherersE2E
)
work=/tmp/native-obfuscator-ir-jdk25-e2e
```

For each value of `$fixture`, the compile, oracle, transpile, native build,
packaging, and transformed run commands were:

```bash
dir="$work/fixtures/$fixture"
mkdir -p "$dir/classes" "$dir/output" "$dir/pack/native0"

/opt/jdk25/bin/javac --release 25 -g \
  -d "$dir/classes" \
  "/workspace/obfuscator/test_data/tests/jdk25/$fixture/Main.java"

/opt/jdk25/bin/jar --create --file "$dir/input.jar" --main-class Main \
  -C "$dir/classes" .

(cd "$dir" && /opt/jdk25/bin/java -Dseed=1337 -jar "$dir/input.jar") \
  >"$dir/oracle.stdout" 2>"$dir/oracle.stderr"

java -jar /workspace/obfuscator/build/libs/obfuscator.jar \
  "$dir/input.jar" "$dir/output" --codegen=ir \
  >"$dir/obfuscator.stdout" 2>"$dir/obfuscator.stderr"

jar tf "$dir/input.jar"
/opt/jdk25/bin/javap -p -s -c -classpath "$dir/input.jar" CLASS_FROM_JAR_TF
rg --no-filename -o '// IR codegen: .+' "$dir/output/cpp/output"

(cd "$dir/output/cpp" && JAVA_HOME=/opt/jdk25 CC=gcc CXX=g++ cmake .)
(cd "$dir/output/cpp" && \
  JAVA_HOME=/opt/jdk25 CC=gcc CXX=g++ cmake --build . --config Release)

install -D -m 755 \
  "$dir/output/cpp/build/lib/libnative_library.so" \
  "$dir/pack/native0/x64-linux.so"
/opt/jdk25/bin/jar --update --file "$dir/output/input.jar" \
  -C "$dir/pack" native0/x64-linux.so

(cd "$dir/output" && \
  /opt/jdk25/bin/java -Djava.library.path=. \
       -Dseed=1337 \
       -Dplatform=HOTSPOT \
       -Dtest.src="$dir" \
       -jar "$dir/output/input.jar") \
  >"$dir/native.stdout" 2>"$dir/native.stderr"

cmp -s "$dir/oracle.stdout" "$dir/native.stdout"
```

`CLASS_FROM_JAR_TF` means each non-versioned `.class` entry returned by the
preceding `jar tf`, converted from an internal path to a binary class name.
Every `javap` command exited 0. The exact-join parser logic was reused from
`docs/measurement/ir-admission-phase18/measure.py`.

## Stdout comparison evidence

Each row below is the SHA-256 of both the oracle and transformed stdout file;
the bytewise `cmp -s` also exited 0 for every fixture, so there was no first
diff or crash to record.

| Fixture | Oracle and transformed stdout SHA-256 |
| --- | --- |
| `CompactSourceModuleImportE2E` | `ca5587bf6f5469c06e91f3d9cf2aadd0e5d1f3cc31bf772988a36dbc4ae36e8b` |
| `FlexibleConstructorBodiesE2E` | `74c35c1dd4905ca706a5e3409abd074407ac54cbbd8df8a024ed58fd89846193` |
| `ScopedValuesE2E` | `a64629795427fd34e89fe73b7f6b38c0922d40ce156d9f6f42b95cb027890ffd` |
| `StreamGatherersE2E` | `59d7490d3fdc60b28df706b8a508ced434ca540bc9e47e490b18d66c0dcf3f99` |

Unlike the JDK 17/21 corpus runs, native stderr was **not** empty. Every
transformed run on JDK 25 printed the four-line JEP 472 restricted-native-
access warning (oracle stderr was empty; oracle runs load no native library):

```text
WARNING: A restricted method in java.lang.System has been called
WARNING: java.lang.System::load has been called by native0.Loader in an unnamed module (file:.../output/input.jar)
WARNING: Use --enable-native-access=ALL-UNNAMED to avoid a warning for callers in this module
WARNING: Restricted methods will be blocked in a future release unless native access is enabled
```

This is a warning today, but the last line is a real forward-compatibility
signal: a future JDK will refuse `System::load` from the generated
`native0.Loader` unless the user passes `--enable-native-access` (or the
loader is moved to a module that requests native access). No fixture output
was affected, and only stdout was compared.

## Boundary and production answer

This run evaluates opt-in IR behavior for these four programs only. It does
not evaluate all Java 25 bytecode, other JDK 25 vendors or architectures,
preview features, or production workloads. No interpreter, evaluator, or
`--ir-lower` path was used or changed, and no benchmark numbers were
measured — the issue #53 benchmark entry stays `N/A`.

**(b) Can this ship to production as-is? No.** This is behavioral evidence,
not a support declaration. Known gaps observed in this very run: one flexible
constructor body was left as plain Java bytecode by design, and the generated
loader trips the JEP 472 restricted-native-access warning that a future JDK
will turn into an error. Review should rerun all four comparisons and decide
whether the loader needs an `--enable-native-access` story before any JDK 25
claim is made.
