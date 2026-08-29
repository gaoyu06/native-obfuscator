# Opt-in IR behavioral E2E on the JDK 21 fixture corpus

## Scope and result

- Base: `origin/master` at
  `e997d71c7525a4c607e29b6eb1ae9140a72dfd22`.
- Compiler and fixtures measured after the narrow local-slot fix:
  `06857ce853d8eaacf2dc4551e5e36bdc68d16a86`.
- Measurement date (UTC): `2026-08-29`.
- Every source was compiled with `javac --release 21 -g`, without preview
  flags.
- Every transpiler invocation explicitly selected `--codegen=ir`.
- CMake was configured and built with `CC=gcc CXX=g++`.
- Only stdout was compared, byte for byte. A nonzero transformed-process exit
  would have been a failure even if its partial stdout matched.

All six sources compiled and their unmodified oracle runs exited 0. All 47
code-bearing input methods reached IR: there was no legacy fallback, unchanged
constructor, or missing input method after the fix. All six generated C++ trees
configured and linked, all transformed JARs exited 0, and all six stdout files
matched their oracle exactly.

This is one behavioral measurement on one Linux x86-64 host. It is **not** a
“JDK 21 supported” badge or a production-compatibility claim. The host itself
is JDK 21 and compiled the sources with `--release 21`. The default code
generator remains `legacy`.

| Fixture | Input IR / inventory | Fallback / missing | CMake configure / build | Oracle exit | Native exit | Exact stdout |
| --- | ---: | ---: | --- | ---: | ---: | --- |
| `PatternSwitchE2E` | 15/15 | 0 / 0 | 0 / 0 | 0 | 0 | match |
| `RecordPatternsE2E` | 21/21 | 0 / 0 | 0 / 0 | 0 | 0 | match |
| `SequencedCollectionsE2E` | 2/2 | 0 / 0 | 0 / 0 | 0 | 0 | match |
| `SequencedMapViewsE2E` | 2/2 | 0 / 0 | 0 / 0 | 0 | 0 | match |
| `SequencedSetE2E` | 2/2 | 0 / 0 | 0 / 0 | 0 | 0 | match |
| `VirtualThreadsE2E` | 5/5 | 0 / 0 | 0 / 0 | 0 | 0 | match |
| **Observed total** | **47/47** | **0 / 0** | **6/6 / 6/6** | **6/6 exit 0** | **6/6 exit 0** | **6/6 match** |

The three added fixtures cover operations absent from the previous corpus:

- `SequencedMapViewsE2E`: first/last polling and reversed sequenced map, key,
  and value views;
- `SequencedSetE2E`: first/last insertion, removal, and mutation through a
  reversed sequenced-set view;
- `VirtualThreadsE2E`: `Thread.startVirtualThread`, virtual thread builders,
  deterministic join/state checks, and virtual-thread naming.

## Environment

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

The post-fix shadow-JAR build completed with `BUILD SUCCESSFUL` and exit 0.
Each CMake configure identified GNU C and C++ 13.3.0 and JNI under
`/usr/lib/jvm/default-java/include`. Each build ended with:

```text
[100%] Linking CXX shared library build/lib/libnative_library.so
[100%] Built target native_library
```

## Admission inventory

The denominator was derived independently from each input JAR. `jar tf`
enumerated input classes, and `javap -p -s -c` identified methods with a
`Code:` body. Results were joined by exact class, method, and descriptor:

- a matching `// IR codegen:` marker counted as IR;
- a matching `IR codegen unsupported` log counted as fallback or unchanged
  constructor;
- an inventory method with neither counted as missing;
- generated markers absent from the input inventory were excluded.

| Fixture | Inventory | Raw IR markers | Excluded generated markers | Matched IR | Fallback | Missing |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| `PatternSwitchE2E` | 15 | 18 | 3 | 15 | 0 | 0 |
| `RecordPatternsE2E` | 21 | 25 | 4 | 21 | 0 | 0 |
| `SequencedCollectionsE2E` | 2 | 3 | 1 | 2 | 0 | 0 |
| `SequencedMapViewsE2E` | 2 | 3 | 1 | 2 | 0 | 0 |
| `SequencedSetE2E` | 2 | 3 | 1 | 2 | 0 | 0 |
| `VirtualThreadsE2E` | 5 | 6 | 1 | 5 | 0 | 0 |
| **Total** | **47** | **58** | **11** | **47** | **0** | **0** |

## Record-pattern fallback and narrow fix

The first full behavioral run was made at `13f4373`, before changing the IR
frontend. `RecordPatternsE2E` had the same two admission failures reported by
the phase-18 admission corpus:

```text
Main.coordinateSum(Ljava/lang/Object;)I
Local 5 is ref but this instruction requires i32 at bytecode instruction 22 (opcode 54)

Main.inspect(Ljava/lang/Object;)Ljava/lang/String;
Local 9 is i32 but this instruction requires ref at bytecode instruction 480 (opcode 58)
```

That baseline was 19/21 IR for `RecordPatternsE2E` and 45/47 IR overall.
The hybrid transformed JAR still built, exited 0, and matched its oracle, but
that result depended on two per-method legacy fallbacks.

The fix at `06857ce` is limited to the exact failure family. Before IR
construction, the frontend detects a non-parameter temporary slot used by both
reference and int local instructions. It clones the method and assigns the
second carrier an IR-only local slot. The original ASM method is not modified,
parameter/receiver slots are not rewritten, and other carrier combinations are
unchanged. A focused unit test covers valid sequential reference/int temporary
reuse and verifies that the source `MethodNode` remains untouched; the existing
invalid receiver-overwrite test continues to reject an int store into local 0.

After rebuilding, `RecordPatternsE2E` admitted 21/21 input methods with no
fallback. Its generated tree linked, its transformed JAR exited 0, and its
stdout still matched exactly.

## Exact commands

The obfuscator was built with:

```bash
cd /workspace
./gradlew :obfuscator:shadowJar --console=plain --rerun-tasks
```

The following fixture list was used; it includes every checked-in `jdk21`
fixture:

```bash
fixtures=(
  PatternSwitchE2E
  RecordPatternsE2E
  SequencedCollectionsE2E
  SequencedMapViewsE2E
  SequencedSetE2E
  VirtualThreadsE2E
)
work=/tmp/native-obfuscator-ir-jdk21-e2e
```

For each value of `$fixture`, the compile, oracle, transpile, native build,
packaging, and transformed run commands were:

```bash
dir="$work/fixtures/$fixture"
mkdir -p "$dir/classes" "$dir/output" "$dir/pack/native0"

javac --release 21 -g \
  -d "$dir/classes" \
  "/workspace/obfuscator/test_data/tests/jdk21/$fixture/Main.java"

jar --create --file "$dir/input.jar" --main-class Main \
  -C "$dir/classes" .

(cd "$dir" && java -Dseed=1337 -jar "$dir/input.jar") \
  >"$dir/oracle.stdout" 2>"$dir/oracle.stderr"

java -jar /workspace/obfuscator/build/libs/obfuscator.jar \
  "$dir/input.jar" "$dir/output" --codegen=ir \
  >"$dir/obfuscator.stdout" 2>"$dir/obfuscator.stderr"

jar tf "$dir/input.jar"
javap -p -s -c -classpath "$dir/input.jar" CLASS_FROM_JAR_TF
rg --no-filename -o '// IR codegen: .+' "$dir/output/cpp/output"

(cd "$dir/output/cpp" && CC=gcc CXX=g++ cmake .)
(cd "$dir/output/cpp" && \
  CC=gcc CXX=g++ cmake --build . --config Release)

install -D -m 755 \
  "$dir/output/cpp/build/lib/libnative_library.so" \
  "$dir/pack/native0/x64-linux.so"
jar --update --file "$dir/output/input.jar" \
  -C "$dir/pack" native0/x64-linux.so

(cd "$dir/output" && \
  java -Djava.library.path=. \
       -Dseed=1337 \
       -Dplatform=HOTSPOT \
       -Dtest.src="$dir" \
       -jar "$dir/output/input.jar") \
  >"$dir/native.stdout" 2>"$dir/native.stderr"

cmp -s "$dir/oracle.stdout" "$dir/native.stdout"
```

`CLASS_FROM_JAR_TF` means each non-versioned `.class` entry returned by the
preceding `jar tf`, converted from an internal path to a binary class name.
Every `javap` command exited 0. The exact-join parser was reused from
`docs/measurement/ir-admission-phase18/measure.py`.

The focused local-slot checks were:

```bash
./gradlew :obfuscator:test \
  --tests by.radioegor146.ir.IrCompilerTest.splitsReferenceAndIntReuseInTemporaryLocal \
  --tests by.radioegor146.ir.IrCompilerTest.rejectsIntStoreIntoInstanceReceiverLocal \
  --console=plain
```

Both tests passed.

## Stdout comparison evidence

Each row below is the SHA-256 of both the oracle and transformed stdout file;
the bytewise `cmp -s` also exited 0. Native stderr was empty for all six runs.
There was therefore no first diff or crash to record.

| Fixture | Oracle and transformed stdout SHA-256 |
| --- | --- |
| `PatternSwitchE2E` | `9cdd328f962983c7ce5a5d600b2d3166f23870c451dcf12c3691d4ceaedf20be` |
| `RecordPatternsE2E` | `eadd333d4ce6da792d25d7022d539557a21486a5aff0a7029b3372d615e37268` |
| `SequencedCollectionsE2E` | `33ece39bdd6bd65f56e925a244d0b185ad2be3cc7009453075c536834b57402d` |
| `SequencedMapViewsE2E` | `32dae9d96667b2f9638663552212835e7f8d8d98dd467e5712c27ebcaef0adff` |
| `SequencedSetE2E` | `32cbcaf28588e9bb51614dbeb330c036fd1174440c941cce5c0294597c4a5b57` |
| `VirtualThreadsE2E` | `b0d168568c51c289a2513e06aa7af4aba883e73a9dbeaa48ef53b235886ce735` |

## Boundary and production answer

This run evaluates opt-in IR behavior for these six programs only. It does not
evaluate all Java 21 bytecode, another JDK vendor or architecture, Java 22–25,
or production workloads. No interpreter, evaluator, or `--ir-lower` path was
used or changed.

**(b) Can this ship to production as-is? No.** The result is useful behavioral
evidence for requirement 4, not a support declaration or completion of the
production goal. Review should cover the local-slot clone/remap invariant,
the exact admission join, and a rerun of all six native comparisons.
