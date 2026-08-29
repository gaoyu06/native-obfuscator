# Phase-17 IR admission on the #107 corpora

## Scope and environment

- Compiler tip: draft [PR #108](https://github.com/gaoyu06/native-obfuscator/pull/108)
  at `5a6f6097524c1fe42cd82be2425f5e6736667688`
- Measurement date (UTC): `2026-08-29`
- OS: `Linux cursor 6.12.94+ #1 SMP PREEMPT_DYNAMIC Fri Aug 28 16:08:20 UTC 2026 x86_64 x86_64 x86_64 GNU/Linux`
- Java:

  ```text
  openjdk version "21.0.10" 2026-01-20
  OpenJDK Runtime Environment (build 21.0.10+7-Ubuntu-124.04)
  OpenJDK 64-Bit Server VM (build 21.0.10+7-Ubuntu-124.04, mixed mode, sharing)
  ```

- Javac: `javac 21.0.10`
- JDK 17 source ref: `origin/cursor/test-jdk17-e2e-harness-6d81` at
  `7389820175f72eac2a062e9ae9a2917ec8815aed`
- Separately labelled JDK 21 source ref:
  `origin/cursor/jdk21-25-e2e-6d81` at
  `23e4cc194a5cae09e0953bcaf019651bbacf13d5`

This is an opt-in `--codegen=ir` admission measurement. It is **not
production coverage** and **not a speedup result**. No reader/recovery
experiment was run. No native library was compiled, and native E2E was
skipped, so no native behavioral/E2E claim is made. Corpus C is a separately
labelled JDK 21 extra corpus, not part of the JDK 17 result. This report makes
no JDK 25 support claim.

## Commands and counting method

The exact top-level measurement command was:

```bash
python3 docs/measurement/ir-admission-phase17/measure.py --include-jdk21
```

The helper verifies that the phase-17 tip is the branch base and that every
committed change since that tip is under `docs/`. It prints every fully
resolved command and writes the same list to
`/tmp/native-obfuscator-ir-admission-phase17/commands.log`. Its setup commands
were:

```bash
./gradlew :obfuscator:shadowJar --rerun-tasks
git fetch origin cursor/test-jdk17-e2e-harness-6d81 cursor/jdk21-25-e2e-6d81
git archive origin/cursor/test-jdk17-e2e-harness-6d81 obfuscator/test_data/tests/jdk17 | tar -x -C /tmp/native-obfuscator-ir-admission-phase17/fetched-sources
git archive origin/cursor/jdk21-25-e2e-6d81 obfuscator/test_data/tests/jdk21 | tar -x -C /tmp/native-obfuscator-ir-admission-phase17/fetched-sources
```

The shadow-JAR build completed with `BUILD SUCCESSFUL`. Corpus A was compiled
with `javac --release 8 -g`, corpus B with `javac --release 17 -g`, and the
separate corpus C with `javac --release 21 -g`. Each fixture then used `jar`,
`javap`, the obfuscator, and `rg`. The exact commands for the first fixture
were:

```bash
javac --release 8 -g -d /tmp/native-obfuscator-ir-admission-phase17/fixtures/A/clinit__TestClInitStacktrace/classes /workspace/obfuscator/test_data/tests/clinit/TestClInitStacktrace/Test.java
jar --create --file /tmp/native-obfuscator-ir-admission-phase17/fixtures/A/clinit__TestClInitStacktrace/input.jar -C /tmp/native-obfuscator-ir-admission-phase17/fixtures/A/clinit__TestClInitStacktrace/classes .
jar tf /tmp/native-obfuscator-ir-admission-phase17/fixtures/A/clinit__TestClInitStacktrace/input.jar
javap -p -s -c -classpath /tmp/native-obfuscator-ir-admission-phase17/fixtures/A/clinit__TestClInitStacktrace/input.jar Test
java -jar /workspace/obfuscator/build/libs/obfuscator.jar /tmp/native-obfuscator-ir-admission-phase17/fixtures/A/clinit__TestClInitStacktrace/input.jar /tmp/native-obfuscator-ir-admission-phase17/fixtures/A/clinit__TestClInitStacktrace/output --codegen=ir
rg --no-filename -o '// IR codegen: .+' /tmp/native-obfuscator-ir-admission-phase17/fixtures/A/clinit__TestClInitStacktrace/output/cpp/output
```

No `--annotations` option was passed. The CLI default therefore measured
every non-abstract, non-native method with bytecode, including methods carrying
fixture annotations. The inventory is parsed from each input JAR's
`javap -p -s -c` output before obfuscation; a method enters the inventory only
when `javap` shows a `Code:` body.

Results are joined to that inventory by `class + method + descriptor`.
`// IR codegen:` is `IR`; an unsupported log ending in `falling back to legacy
for this method` is `legacy-fallback`; and an unsupported constructor log
ending in `leaving constructor bytecode unchanged` is
`constructor-left-java`. Anything absent from the input JAR is excluded.
These are the same inventory, join, and exclusion rules used by
[PR #107](https://github.com/gaoyu06/native-obfuscator/pull/107).

The raw marker/log counts before and after the inventory join were:

```text
corpus	input_inventory	raw_ir_markers	excluded_ir_markers	matched_ir_markers	raw_fallback_logs	raw_left_logs	excluded_fallback_logs
A	108	145	41	104	4	0	0
B-jdk17	36	48	12	36	0	0	0
C-jdk21-extra	38	44	8	36	2	0	0
```

The 61 generated IR markers absent from the input inventory were excluded. No
fallback log was excluded. The final per-corpus counts were:

```text
corpus	inventory	IR	legacy-fallback	constructor-left-java	missing
A	108	104	4	0	0
B-jdk17	36	36	0	0	0
C-jdk21-extra	38	36	2	0	0
```

All 16 Java fixture compilations and all 16 obfuscator invocations succeeded.
`krak2 -V` failed exactly as follows, so
`pull-requests/PullRequest72/TestStringConcatFactory.j` was skipped while its
Java `Main.java` was still compiled and measured:

```text
krak2: command not found
```

## Corpus A: checked-in ClassicTest fixtures

These are the same eight fixture roots measured by #107. The large
`java-obfuscator-test/JavaObfuscatorTest` fixture was compiled as one JAR.

| Fixture | Inventory | IR | Legacy fallback | Constructor left Java |
| --- | ---: | ---: | ---: | ---: |
| `clinit/TestClInitStacktrace` | 4 | 4 | 0 | 0 |
| `empty/EmptyTest1` | 2 | 2 | 0 | 0 |
| `indy/IndyTest1` | 3 | 3 | 0 | 0 |
| `interface/InterfaceDefault` | 4 | 4 | 0 | 0 |
| `interface/InterfaceDefaultStacktrace` | 4 | 4 | 0 | 0 |
| `issues/Issue52` | 4 | 2 | 2 | 0 |
| `java-obfuscator-test/JavaObfuscatorTest` | 85 | 83 | 2 | 0 |
| `pull-requests/PullRequest72` (Java only) | 2 | 2 | 0 | 0 |
| **Corpus A** | **108** | **104** | **4** | **0** |

## Corpus B: fetched JDK 17 E2E fixtures

All five fetched JDK 17 fixtures compiled with `--release 17` and were
processed successfully.

| Fixture | Inventory | IR | Legacy fallback | Constructor left Java |
| --- | ---: | ---: | ---: | ---: |
| `InvokeDynamicLambdaE2E` | 6 | 6 | 0 | 0 |
| `MethodHandlesE2E` | 8 | 8 | 0 | 0 |
| `NestPrivateAccessE2E` | 5 | 5 | 0 | 0 |
| `RecordSemanticsE2E` | 9 | 9 | 0 | 0 |
| `SealedHierarchyE2E` | 8 | 8 | 0 | 0 |
| **Corpus B / JDK 17** | **36** | **36** | **0** | **0** |

## Extra corpus C: fetched JDK 21 fixtures

These rows are reported separately and must not be read as JDK 17 results.

| Fixture | Inventory | IR | Legacy fallback | Constructor left Java |
| --- | ---: | ---: | ---: | ---: |
| `PatternSwitchE2E` | 15 | 15 | 0 | 0 |
| `RecordPatternsE2E` | 21 | 19 | 2 | 0 |
| `SequencedCollectionsE2E` | 2 | 2 | 0 | 0 |
| **Extra corpus C / JDK 21** | **38** | **36** | **2** | **0** |

## First unsupported opcode histogram

The full logged reason, including bytecode instruction index, is retained for
every method in
[`ir-admission-phase17-corpus-methods.tsv`](ir-admission-phase17-corpus-methods.tsv).
Grouping those rows by result, opcode, and message gives:

| Corpus | Result | Count | Opcode | Message |
| --- | --- | ---: | ---: | --- |
| A | legacy-fallback | 2 | 188 | Unsupported instruction for phase-two IR |
| A | legacy-fallback | 2 | 197 | Unsupported instruction for phase-two IR |
| C-jdk21-extra | legacy-fallback | 1 | 54 | Local 5 is ref but this instruction requires i32 |
| C-jdk21-extra | legacy-fallback | 1 | 58 | Local 9 is i32 but this instruction requires ref |

Across the separately labelled corpora, the leftover first-opcode totals are
opcode 188 (`NEWARRAY`) 2, opcode 197 (`MULTIANEWARRAY`) 2, opcode 54
(`ISTORE`, rejected for the logged local-type mismatch) 1, and opcode 58
(`ASTORE`, rejected for the logged local-type mismatch) 1. Opcode 93
(`DUP2_X1`) is no longer a leftover first unsupported opcode.

## Side-by-side with #107

[PR #107](https://github.com/gaoyu06/native-obfuscator/pull/107) measured these
same fixture roots and exact fetched fixture revisions on draft
[PR #104](https://github.com/gaoyu06/native-obfuscator/pull/104) at
`dbfeb7816986ba886eb14e20c092f4f8f833a629`. This report measures draft
[PR #108](https://github.com/gaoyu06/native-obfuscator/pull/108) at
`5a6f6097524c1fe42cd82be2425f5e6736667688`. The compiler tips are deliberately
distinct. A direct TSV comparison proved all 182
`corpus + fixture + class + method + descriptor` keys identical.

`Delta` is `#108 - #107`. `F` means legacy fallback and `L` means constructor
left Java.

| Corpus / fixture | #107 inv | #107 IR | #107 F | #107 L | #108 inv | #108 IR | #108 F | #108 L | Delta IR | Delta F | Delta L |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| **A total** | **108** | **102** | **6** | **0** | **108** | **104** | **4** | **0** | **+2** | **-2** | **0** |
| A / `clinit/TestClInitStacktrace` | 4 | 4 | 0 | 0 | 4 | 4 | 0 | 0 | 0 | 0 | 0 |
| A / `empty/EmptyTest1` | 2 | 2 | 0 | 0 | 2 | 2 | 0 | 0 | 0 | 0 | 0 |
| A / `indy/IndyTest1` | 3 | 2 | 1 | 0 | 3 | 3 | 0 | 0 | +1 | -1 | 0 |
| A / `interface/InterfaceDefault` | 4 | 4 | 0 | 0 | 4 | 4 | 0 | 0 | 0 | 0 | 0 |
| A / `interface/InterfaceDefaultStacktrace` | 4 | 4 | 0 | 0 | 4 | 4 | 0 | 0 | 0 | 0 | 0 |
| A / `issues/Issue52` | 4 | 2 | 2 | 0 | 4 | 2 | 2 | 0 | 0 | 0 | 0 |
| A / `java-obfuscator-test/JavaObfuscatorTest` | 85 | 82 | 3 | 0 | 85 | 83 | 2 | 0 | +1 | -1 | 0 |
| A / `pull-requests/PullRequest72` | 2 | 2 | 0 | 0 | 2 | 2 | 0 | 0 | 0 | 0 | 0 |
| **B-jdk17 total** | **36** | **24** | **12** | **0** | **36** | **36** | **0** | **0** | **+12** | **-12** | **0** |
| B-jdk17 / `InvokeDynamicLambdaE2E` | 6 | 3 | 3 | 0 | 6 | 6 | 0 | 0 | +3 | -3 | 0 |
| B-jdk17 / `MethodHandlesE2E` | 8 | 7 | 1 | 0 | 8 | 8 | 0 | 0 | +1 | -1 | 0 |
| B-jdk17 / `NestPrivateAccessE2E` | 5 | 3 | 2 | 0 | 5 | 5 | 0 | 0 | +2 | -2 | 0 |
| B-jdk17 / `RecordSemanticsE2E` | 9 | 4 | 5 | 0 | 9 | 9 | 0 | 0 | +5 | -5 | 0 |
| B-jdk17 / `SealedHierarchyE2E` | 8 | 7 | 1 | 0 | 8 | 8 | 0 | 0 | +1 | -1 | 0 |
| **C-jdk21-extra total** | **38** | **18** | **20** | **0** | **38** | **36** | **2** | **0** | **+18** | **-18** | **0** |
| C-jdk21-extra / `PatternSwitchE2E` | 15 | 6 | 9 | 0 | 15 | 15 | 0 | 0 | +9 | -9 | 0 |
| C-jdk21-extra / `RecordPatternsE2E` | 21 | 10 | 11 | 0 | 21 | 19 | 2 | 0 | +9 | -9 | 0 |
| C-jdk21-extra / `SequencedCollectionsE2E` | 2 | 2 | 0 | 0 | 2 | 2 | 0 | 0 | 0 | 0 | 0 |

All 144 methods admitted by #107 remained IR. Of the 33 methods whose #107
first unsupported opcode was 93 (`DUP2_X1`), **32 became IR**. The remaining
method,
`C-jdk21-extra / RecordPatternsE2E / Main.inspect(Ljava/lang/Object;)Ljava/lang/String;`,
passed `DUP2_X1` but then fell back on opcode 58 (`ASTORE`) at bytecode
instruction 480 because local 9 was logged as `i32` where a reference was
required. Therefore the answer to whether all 33 became IR is **no: 32 of 33
did**.

Across all three separately labelled corpora, the admission-count delta versus
#107 is IR `+32`, legacy fallback `-32`, and constructor-left-Java `0`. These
are admission-count deltas only, not production-coverage or performance
results.
