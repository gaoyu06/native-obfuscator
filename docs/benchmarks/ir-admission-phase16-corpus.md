# Phase-16 IR admission on the #103 corpora

## Scope and environment

- Compiler tip: draft [PR #104](https://github.com/gaoyu06/native-obfuscator/pull/104),
  `dbfeb7816986ba886eb14e20c092f4f8f833a629`
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
production coverage** and **not a speedup result**. No native library was
compiled, no reader/recovery experiment was run, and no behavioral/E2E claim
is made. The optional native compile and native E2E comparison were skipped.
The JDK 21 rows are an extra corpus, not part of the JDK 17 result. This report
makes no JDK 25 support claim.

## Commands and counting method

The exact top-level command used for the measurement was:

```bash
python3 docs/measurement/ir-admission-phase16/measure.py --include-jdk21
```

The helper verifies that the phase-16 tip is the branch base and that every
committed change since that tip is under `docs/`. It prints every fully
resolved command and writes the same list to
`/tmp/native-obfuscator-ir-admission-phase16/commands.log`. Its setup commands
were:

```bash
./gradlew :obfuscator:shadowJar --rerun-tasks
git fetch origin cursor/test-jdk17-e2e-harness-6d81 cursor/jdk21-25-e2e-6d81
git archive origin/cursor/test-jdk17-e2e-harness-6d81 obfuscator/test_data/tests/jdk17 | tar -x -C /tmp/native-obfuscator-ir-admission-phase16/fetched-sources
git archive origin/cursor/jdk21-25-e2e-6d81 obfuscator/test_data/tests/jdk21 | tar -x -C /tmp/native-obfuscator-ir-admission-phase16/fetched-sources
```

The shadow-JAR build completed with `BUILD SUCCESSFUL`. Corpus A was compiled
with `javac --release 8 -g`, corpus B with `javac --release 17 -g`, and the
separate extra corpus C with `javac --release 21 -g`. Each fixture then used
`jar`, `javap`, the obfuscator, and `rg`. For example, the exact commands for
the first fixture were:

```bash
javac --release 8 -g -d /tmp/native-obfuscator-ir-admission-phase16/fixtures/A/clinit__TestClInitStacktrace/classes /workspace/obfuscator/test_data/tests/clinit/TestClInitStacktrace/Test.java
jar --create --file /tmp/native-obfuscator-ir-admission-phase16/fixtures/A/clinit__TestClInitStacktrace/input.jar -C /tmp/native-obfuscator-ir-admission-phase16/fixtures/A/clinit__TestClInitStacktrace/classes .
jar tf /tmp/native-obfuscator-ir-admission-phase16/fixtures/A/clinit__TestClInitStacktrace/input.jar
javap -p -s -c -classpath /tmp/native-obfuscator-ir-admission-phase16/fixtures/A/clinit__TestClInitStacktrace/input.jar Test
java -jar /workspace/obfuscator/build/libs/obfuscator.jar /tmp/native-obfuscator-ir-admission-phase16/fixtures/A/clinit__TestClInitStacktrace/input.jar /tmp/native-obfuscator-ir-admission-phase16/fixtures/A/clinit__TestClInitStacktrace/output --codegen=ir
rg --no-filename -o '// IR codegen: .+' /tmp/native-obfuscator-ir-admission-phase16/fixtures/A/clinit__TestClInitStacktrace/output/cpp/output
```

No `--annotations` option was passed. Thus the CLI default measured every
non-abstract, non-native method with bytecode, including methods carrying
fixture annotations. The inventory is parsed from each input JAR's
`javap -p -s -c` output before obfuscation. A method enters the inventory only
when `javap` shows a `Code:` body.

Results are joined to that input inventory by
`class + method + descriptor`. `// IR codegen:` is `IR`; an unsupported log
ending in `falling back to legacy for this method` is `legacy-fallback`; and
an unsupported constructor log ending in
`leaving constructor bytecode unchanged` is `constructor-left-java`.
Anything absent from the input JAR is excluded. The same inventory, join, and
exclusion rules were used in the [#103 report](https://github.com/gaoyu06/native-obfuscator/pull/103).

The raw marker/log command output, before and after the input-inventory join,
was:

```text
corpus	input_inventory	raw_ir_markers	excluded_ir_markers	matched_ir_markers	raw_fallback_logs	raw_left_logs	excluded_fallback_logs
A	108	143	41	102	6	0	0
B-jdk17	36	36	12	24	12	0	0
C-jdk21-extra	38	26	8	18	20	0	0
```

The 61 generated IR markers absent from the input inventory were excluded.
No fallback log was excluded. The final per-corpus counts emitted by the
helper were:

```text
corpus	inventory	IR	legacy-fallback	constructor-left-java	missing
A	108	102	6	0	0
B-jdk17	36	24	12	0	0
C-jdk21-extra	38	18	20	0	0
```

All 16 Java fixture compilations and all 16 obfuscator invocations succeeded.
`krak2 -V` failed exactly as follows, so
`pull-requests/PullRequest72/TestStringConcatFactory.j` was skipped while its
Java `Main.java` was still compiled and measured:

```text
krak2: command not found
```

## Corpus A: checked-in ClassicTest fixtures

These are the same eight fixture roots measured by #103. The large
`java-obfuscator-test/JavaObfuscatorTest` fixture was compiled as one JAR.

| Fixture | Inventory | IR | Legacy fallback | Constructor left Java |
| --- | ---: | ---: | ---: | ---: |
| `clinit/TestClInitStacktrace` | 4 | 4 | 0 | 0 |
| `empty/EmptyTest1` | 2 | 2 | 0 | 0 |
| `indy/IndyTest1` | 3 | 2 | 1 | 0 |
| `interface/InterfaceDefault` | 4 | 4 | 0 | 0 |
| `interface/InterfaceDefaultStacktrace` | 4 | 4 | 0 | 0 |
| `issues/Issue52` | 4 | 2 | 2 | 0 |
| `java-obfuscator-test/JavaObfuscatorTest` | 85 | 82 | 3 | 0 |
| `pull-requests/PullRequest72` (Java only) | 2 | 2 | 0 | 0 |
| **Corpus A** | **108** | **102** | **6** | **0** |

## Corpus B: fetched JDK 17 E2E fixtures

All five fetched JDK 17 fixtures compiled with `--release 17` and were
processed successfully.

| Fixture | Inventory | IR | Legacy fallback | Constructor left Java |
| --- | ---: | ---: | ---: | ---: |
| `InvokeDynamicLambdaE2E` | 6 | 3 | 3 | 0 |
| `MethodHandlesE2E` | 8 | 7 | 1 | 0 |
| `NestPrivateAccessE2E` | 5 | 3 | 2 | 0 |
| `RecordSemanticsE2E` | 9 | 4 | 5 | 0 |
| `SealedHierarchyE2E` | 8 | 7 | 1 | 0 |
| **Corpus B / JDK 17** | **36** | **24** | **12** | **0** |

## Extra corpus C: fetched JDK 21 fixtures

These rows are reported separately and must not be read as JDK 17 results.

| Fixture | Inventory | IR | Legacy fallback | Constructor left Java |
| --- | ---: | ---: | ---: | ---: |
| `PatternSwitchE2E` | 15 | 6 | 9 | 0 |
| `RecordPatternsE2E` | 21 | 10 | 11 | 0 |
| `SequencedCollectionsE2E` | 2 | 2 | 0 | 0 |
| **Extra corpus C / JDK 21** | **38** | **18** | **20** | **0** |

## First unsupported opcode histogram

The full logged reason, including bytecode instruction index, is retained for
every method in
[`ir-admission-phase16-corpus-methods.tsv`](ir-admission-phase16-corpus-methods.tsv).
Grouping the same rows by result, opcode, and message gives:

| Corpus | Result | Count | Opcode | Message |
| --- | --- | ---: | ---: | --- |
| A | legacy-fallback | 2 | 188 | Unsupported instruction for phase-two IR |
| A | legacy-fallback | 2 | 197 | Unsupported instruction for phase-two IR |
| A | legacy-fallback | 2 | 93 | Unsupported instruction for phase-two IR |
| B-jdk17 | legacy-fallback | 12 | 93 | Unsupported instruction for phase-two IR |
| C-jdk21-extra | legacy-fallback | 19 | 93 | Unsupported instruction for phase-two IR |
| C-jdk21-extra | legacy-fallback | 1 | 54 | Local 5 is ref but this instruction requires i32 |

Across the separately labelled corpora, the leftover first-opcode totals are
opcode 93 (`DUP2_X1`) 33, opcode 188 (`NEWARRAY`) 2, opcode 197
(`MULTIANEWARRAY`) 2, and opcode 54 (`ISTORE`, rejected here for the logged
local-type mismatch) 1.

## Side-by-side with #103

[PR #103](https://github.com/gaoyu06/native-obfuscator/pull/103) measured these
same fixture roots and exact fetched fixture revisions on draft
[PR #99](https://github.com/gaoyu06/native-obfuscator/pull/99) at
`f46c3eae27f03071a8b3a9e161533b8f24c23735`. This report measures draft
[PR #104](https://github.com/gaoyu06/native-obfuscator/pull/104) at
`dbfeb7816986ba886eb14e20c092f4f8f833a629`. They are deliberately not
presented as the same compiler tip. A direct TSV comparison proved all 182
`corpus + fixture + class + method + descriptor` keys identical.

`Δ` is `#104 - #103`. `F` means legacy fallback and `L` means constructor left
Java.

| Corpus / fixture | #103 inv | #103 IR | #103 F | #103 L | #104 inv | #104 IR | #104 F | #104 L | ΔIR | ΔF | ΔL |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| **A total** | **108** | **97** | **11** | **0** | **108** | **102** | **6** | **0** | **+5** | **-5** | **0** |
| A / `clinit/TestClInitStacktrace` | 4 | 3 | 1 | 0 | 4 | 4 | 0 | 0 | +1 | -1 | 0 |
| A / `empty/EmptyTest1` | 2 | 2 | 0 | 0 | 2 | 2 | 0 | 0 | 0 | 0 | 0 |
| A / `indy/IndyTest1` | 3 | 2 | 1 | 0 | 3 | 2 | 1 | 0 | 0 | 0 | 0 |
| A / `interface/InterfaceDefault` | 4 | 4 | 0 | 0 | 4 | 4 | 0 | 0 | 0 | 0 | 0 |
| A / `interface/InterfaceDefaultStacktrace` | 4 | 4 | 0 | 0 | 4 | 4 | 0 | 0 | 0 | 0 | 0 |
| A / `issues/Issue52` | 4 | 2 | 2 | 0 | 4 | 2 | 2 | 0 | 0 | 0 | 0 |
| A / `java-obfuscator-test/JavaObfuscatorTest` | 85 | 78 | 7 | 0 | 85 | 82 | 3 | 0 | +4 | -4 | 0 |
| A / `pull-requests/PullRequest72` | 2 | 2 | 0 | 0 | 2 | 2 | 0 | 0 | 0 | 0 | 0 |
| **B-jdk17 total** | **36** | **23** | **13** | **0** | **36** | **24** | **12** | **0** | **+1** | **-1** | **0** |
| B-jdk17 / `InvokeDynamicLambdaE2E` | 6 | 3 | 3 | 0 | 6 | 3 | 3 | 0 | 0 | 0 | 0 |
| B-jdk17 / `MethodHandlesE2E` | 8 | 6 | 2 | 0 | 8 | 7 | 1 | 0 | +1 | -1 | 0 |
| B-jdk17 / `NestPrivateAccessE2E` | 5 | 3 | 2 | 0 | 5 | 3 | 2 | 0 | 0 | 0 | 0 |
| B-jdk17 / `RecordSemanticsE2E` | 9 | 4 | 5 | 0 | 9 | 4 | 5 | 0 | 0 | 0 | 0 |
| B-jdk17 / `SealedHierarchyE2E` | 8 | 7 | 1 | 0 | 8 | 7 | 1 | 0 | 0 | 0 | 0 |
| **C-jdk21-extra total** | **38** | **17** | **21** | **0** | **38** | **18** | **20** | **0** | **+1** | **-1** | **0** |
| C-jdk21-extra / `PatternSwitchE2E` | 15 | 5 | 10 | 0 | 15 | 6 | 9 | 0 | +1 | -1 | 0 |
| C-jdk21-extra / `RecordPatternsE2E` | 21 | 10 | 11 | 0 | 21 | 10 | 11 | 0 | 0 | 0 | 0 |
| C-jdk21-extra / `SequencedCollectionsE2E` | 2 | 2 | 0 | 0 | 2 | 2 | 0 | 0 | 0 | 0 | 0 |

All 137 methods admitted by #103 remained IR. Seven #103 fallbacks became IR:
four whose first unsupported opcode was 50 (`AALOAD`) and three whose first
unsupported opcode was 83 (`AASTORE`). The 33 methods whose #103 first
unsupported opcode was 95 (`SWAP`) passed that instruction on #104 and then
fell back at opcode 93 (`DUP2_X1`), so they do not increase the whole-method IR
count. The other five fallbacks retained their prior first reason. Across all
three corpora, the admission-count delta is therefore IR `+7`, fallback `-7`,
left-Java `0`. These are admission-count deltas only, not production coverage
or performance results.
