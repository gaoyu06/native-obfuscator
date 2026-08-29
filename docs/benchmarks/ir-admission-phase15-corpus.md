# Phase-15 IR admission on the #97 corpora

## Scope and environment

- Compiler tip: draft [PR #99](https://github.com/gaoyu06/native-obfuscator/pull/99),
  `f46c3eae27f03071a8b3a9e161533b8f24c23735`
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
is made. Native compilation and behavioral E2E were not required for this
measurement. The optional native compile and HotSpot comparison were skipped,
including for fully admitted fixtures. The JDK 21 rows are an extra corpus,
not part of the JDK 17 result. This report makes no JDK 25 support claim.

## Commands and counting method

The exact top-level command used for the measurement was:

```bash
python3 docs/measurement/ir-admission-phase15/measure.py --include-jdk21
```

The helper verifies that the phase-15 tip is the branch base and that all
committed changes since that tip are under `docs/`. It prints every fully
resolved command and writes the same list to
`/tmp/native-obfuscator-ir-admission-phase15/commands.log`. Its setup commands
were:

```bash
./gradlew :obfuscator:shadowJar --rerun-tasks
git fetch origin cursor/test-jdk17-e2e-harness-6d81 cursor/jdk21-25-e2e-6d81
git archive origin/cursor/test-jdk17-e2e-harness-6d81 obfuscator/test_data/tests/jdk17 | tar -x -C /tmp/native-obfuscator-ir-admission-phase15/fetched-sources
git archive origin/cursor/jdk21-25-e2e-6d81 obfuscator/test_data/tests/jdk21 | tar -x -C /tmp/native-obfuscator-ir-admission-phase15/fetched-sources
```

For Corpus A, each Java fixture was compiled with `javac --release 8 -g`.
Corpus B used `javac --release 17 -g`; the separate extra corpus used
`javac --release 21 -g`. Each fixture then used `jar`, `javap`, the obfuscator,
and `rg`. For example, the exact commands for the first fixture were:

```bash
javac --release 8 -g -d /tmp/native-obfuscator-ir-admission-phase15/fixtures/A/clinit__TestClInitStacktrace/classes /workspace/obfuscator/test_data/tests/clinit/TestClInitStacktrace/Test.java
jar --create --file /tmp/native-obfuscator-ir-admission-phase15/fixtures/A/clinit__TestClInitStacktrace/input.jar -C /tmp/native-obfuscator-ir-admission-phase15/fixtures/A/clinit__TestClInitStacktrace/classes .
jar tf /tmp/native-obfuscator-ir-admission-phase15/fixtures/A/clinit__TestClInitStacktrace/input.jar
javap -p -s -c -classpath /tmp/native-obfuscator-ir-admission-phase15/fixtures/A/clinit__TestClInitStacktrace/input.jar Test
java -jar /workspace/obfuscator/build/libs/obfuscator.jar /tmp/native-obfuscator-ir-admission-phase15/fixtures/A/clinit__TestClInitStacktrace/input.jar /tmp/native-obfuscator-ir-admission-phase15/fixtures/A/clinit__TestClInitStacktrace/output --codegen=ir
rg --no-filename -o '// IR codegen: .+' /tmp/native-obfuscator-ir-admission-phase15/fixtures/A/clinit__TestClInitStacktrace/output/cpp/output
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

This join excludes anything absent from the input JAR. All 61 excluded
generated results were injected `<clinit>()V` IR markers. No fallback log was
excluded. Existing input `<clinit>()V` methods remain in the inventory.

The raw marker/log command output, before and after the input-inventory join,
was:

```text
corpus	input_inventory	raw_ir_markers	excluded_ir_markers	matched_ir_markers	raw_fallback_logs	raw_left_logs	excluded_fallback_logs
A	108	138	41	97	11	0	0
B-jdk17	36	35	12	23	13	0	0
C-jdk21-extra	38	25	8	17	21	0	0
```

The final per-corpus counts emitted by the helper were:

```text
corpus	inventory	IR	legacy-fallback	constructor-left-java	missing
A	108	97	11	0	0
B-jdk17	36	23	13	0	0
C-jdk21-extra	38	17	21	0	0
```

All 16 Java fixture compilations and all 16 obfuscator invocations succeeded.
`krak2 -V` failed exactly as follows, so
`pull-requests/PullRequest72/TestStringConcatFactory.j` was skipped while its
Java `Main.java` was still compiled and measured:

```text
krak2: command not found
```

## Corpus A: checked-in ClassicTest fixtures

These are the same eight fixture roots measured by #97. The large
`java-obfuscator-test/JavaObfuscatorTest` fixture was compiled as one JAR.

| Fixture | Inventory | IR | Legacy fallback | Constructor left Java |
| --- | ---: | ---: | ---: | ---: |
| `clinit/TestClInitStacktrace` | 4 | 3 | 1 | 0 |
| `empty/EmptyTest1` | 2 | 2 | 0 | 0 |
| `indy/IndyTest1` | 3 | 2 | 1 | 0 |
| `interface/InterfaceDefault` | 4 | 4 | 0 | 0 |
| `interface/InterfaceDefaultStacktrace` | 4 | 4 | 0 | 0 |
| `issues/Issue52` | 4 | 2 | 2 | 0 |
| `java-obfuscator-test/JavaObfuscatorTest` | 85 | 78 | 7 | 0 |
| `pull-requests/PullRequest72` (Java only) | 2 | 2 | 0 | 0 |
| **Corpus A** | **108** | **97** | **11** | **0** |

## Corpus B: fetched JDK 17 E2E fixtures

All five fetched JDK 17 fixtures compiled with `--release 17` and were
processed successfully.

| Fixture | Inventory | IR | Legacy fallback | Constructor left Java |
| --- | ---: | ---: | ---: | ---: |
| `InvokeDynamicLambdaE2E` | 6 | 3 | 3 | 0 |
| `MethodHandlesE2E` | 8 | 6 | 2 | 0 |
| `NestPrivateAccessE2E` | 5 | 3 | 2 | 0 |
| `RecordSemanticsE2E` | 9 | 4 | 5 | 0 |
| `SealedHierarchyE2E` | 8 | 7 | 1 | 0 |
| **Corpus B / JDK 17** | **36** | **23** | **13** | **0** |

## Extra corpus C: fetched JDK 21 fixtures

These rows are reported separately and must not be read as JDK 17 results.

| Fixture | Inventory | IR | Legacy fallback | Constructor left Java |
| --- | ---: | ---: | ---: | ---: |
| `PatternSwitchE2E` | 15 | 5 | 10 | 0 |
| `RecordPatternsE2E` | 21 | 10 | 11 | 0 |
| `SequencedCollectionsE2E` | 2 | 2 | 0 | 0 |
| **Extra corpus C / JDK 21** | **38** | **17** | **21** | **0** |

## First unsupported reason histogram

The full logged reason, including bytecode instruction index, is retained for
every method in
[`ir-admission-phase15-corpus-methods.tsv`](ir-admission-phase15-corpus-methods.tsv).
Grouping the same rows by result, opcode, and message gives:

| Corpus | Result | Count | Opcode | Message |
| --- | --- | ---: | ---: | --- |
| A | legacy-fallback | 4 | 50 | Unsupported instruction for phase-two IR |
| A | legacy-fallback | 2 | 188 | Unsupported instruction for phase-two IR |
| A | legacy-fallback | 2 | 197 | Unsupported instruction for phase-two IR |
| A | legacy-fallback | 2 | 95 | Unsupported instruction for phase-two IR |
| A | legacy-fallback | 1 | 83 | Unsupported instruction for phase-two IR |
| B-jdk17 | legacy-fallback | 12 | 95 | Unsupported instruction for phase-two IR |
| B-jdk17 | legacy-fallback | 1 | 83 | Unsupported instruction for phase-two IR |
| C-jdk21-extra | legacy-fallback | 19 | 95 | Unsupported instruction for phase-two IR |
| C-jdk21-extra | legacy-fallback | 1 | 54 | Local 5 is ref but this instruction requires i32 |
| C-jdk21-extra | legacy-fallback | 1 | 83 | Unsupported instruction for phase-two IR |

## Side-by-side with #97

[PR #97](https://github.com/gaoyu06/native-obfuscator/pull/97) measured the
same fixture names on the [#90](https://github.com/gaoyu06/native-obfuscator/pull/90)
compiler tip, `b5a403fd398961870eb6aadafb50b882bc17f273`. This report measures
the [#99](https://github.com/gaoyu06/native-obfuscator/pull/99) tip,
`f46c3eae27f03071a8b3a9e161533b8f24c23735`. They are deliberately not
presented as the same compiler tip. The method keys and inventory counts are
identical between the two runs.

`Δ` is `#99 - #97`. `F` means legacy fallback and `L` means constructor left
Java.

| Corpus / fixture | #97 inv | #97 IR | #97 F | #97 L | #99 inv | #99 IR | #99 F | #99 L | ΔIR | ΔF | ΔL |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| **A total** | **108** | **69** | **37** | **2** | **108** | **97** | **11** | **0** | **+28** | **-26** | **-2** |
| A / `clinit/TestClInitStacktrace` | 4 | 3 | 1 | 0 | 4 | 3 | 1 | 0 | 0 | 0 | 0 |
| A / `empty/EmptyTest1` | 2 | 1 | 1 | 0 | 2 | 2 | 0 | 0 | +1 | -1 | 0 |
| A / `indy/IndyTest1` | 3 | 2 | 1 | 0 | 3 | 2 | 1 | 0 | 0 | 0 | 0 |
| A / `interface/InterfaceDefault` | 4 | 3 | 1 | 0 | 4 | 4 | 0 | 0 | +1 | -1 | 0 |
| A / `interface/InterfaceDefaultStacktrace` | 4 | 4 | 0 | 0 | 4 | 4 | 0 | 0 | 0 | 0 | 0 |
| A / `issues/Issue52` | 4 | 2 | 2 | 0 | 4 | 2 | 2 | 0 | 0 | 0 | 0 |
| A / `java-obfuscator-test/JavaObfuscatorTest` | 85 | 53 | 30 | 2 | 85 | 78 | 7 | 0 | +25 | -23 | -2 |
| A / `pull-requests/PullRequest72` | 2 | 1 | 1 | 0 | 2 | 2 | 0 | 0 | +1 | -1 | 0 |
| **B-jdk17 total** | **36** | **20** | **16** | **0** | **36** | **23** | **13** | **0** | **+3** | **-3** | **0** |
| B-jdk17 / `InvokeDynamicLambdaE2E` | 6 | 2 | 4 | 0 | 6 | 3 | 3 | 0 | +1 | -1 | 0 |
| B-jdk17 / `MethodHandlesE2E` | 8 | 4 | 4 | 0 | 8 | 6 | 2 | 0 | +2 | -2 | 0 |
| B-jdk17 / `NestPrivateAccessE2E` | 5 | 3 | 2 | 0 | 5 | 3 | 2 | 0 | 0 | 0 | 0 |
| B-jdk17 / `RecordSemanticsE2E` | 9 | 4 | 5 | 0 | 9 | 4 | 5 | 0 | 0 | 0 | 0 |
| B-jdk17 / `SealedHierarchyE2E` | 8 | 7 | 1 | 0 | 8 | 7 | 1 | 0 | 0 | 0 | 0 |
| **C-jdk21-extra total** | **38** | **15** | **23** | **0** | **38** | **17** | **21** | **0** | **+2** | **-2** | **0** |
| C-jdk21-extra / `PatternSwitchE2E` | 15 | 5 | 10 | 0 | 15 | 5 | 10 | 0 | 0 | 0 | 0 |
| C-jdk21-extra / `RecordPatternsE2E` | 21 | 9 | 12 | 0 | 21 | 10 | 11 | 0 | +1 | -1 | 0 |
| C-jdk21-extra / `SequencedCollectionsE2E` | 2 | 1 | 1 | 0 | 2 | 2 | 0 | 0 | +1 | -1 | 0 |

Across all three separately labelled corpora, 31 #97 legacy-fallback methods
and both #97 constructor-left-Java methods became IR; all 104 methods that
were already IR remained IR. The 45 remaining fallback methods are reported
in the histogram above. These are admission-count deltas only, not production
coverage or performance results.
