# Phase-13 IR admission on checked-in and JDK 17 fixtures

## Scope and environment

- Compiler tip: draft PR #90, `b5a403fd398961870eb6aadafb50b882bc17f273`
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
compiled and no behavioral/E2E claim is made. The JDK 21 rows are an extra
corpus, not part of the JDK 17 result. This report makes no JDK 25 support
claim.

## Commands and counting method

The exact command used to reproduce all compilation, `javap`, obfuscator, and
`rg` steps was:

```bash
python3 docs/measurement/ir-admission-phase13/measure.py --include-jdk21
```

The helper verifies that the phase-13 tip is the branch base and that all
committed changes since that tip are under `docs/`. It prints every fully
resolved command and writes the same list to
`/tmp/native-obfuscator-ir-admission-phase13/commands.log`. Its setup commands
were:

```bash
./gradlew :obfuscator:shadowJar --rerun-tasks
git fetch origin cursor/test-jdk17-e2e-harness-6d81 cursor/jdk17-classfile-metadata-6d81 cursor/jdk21-25-e2e-6d81 cursor/jdk25-e2e-6d81
git archive origin/cursor/test-jdk17-e2e-harness-6d81 obfuscator/test_data/tests/jdk17 | tar -x -C /tmp/native-obfuscator-ir-admission-phase13/fetched-sources
git archive origin/cursor/jdk21-25-e2e-6d81 obfuscator/test_data/tests/jdk21 | tar -x -C /tmp/native-obfuscator-ir-admission-phase13/fetched-sources
```

For Corpus A, each Java fixture was compiled with `javac --release 8 -g`.
Corpus B used `javac --release 17 -g`; the separate extra corpus used
`javac --release 21 -g`. Each fixture then used these commands with the
fully resolved paths and class names emitted in `commands.log`:

```bash
jar --create --file <input.jar> -C <classes-dir> .
jar tf <input.jar>
javap -p -s -c -classpath <input.jar> <binary-class-name>
java -jar obfuscator/build/libs/obfuscator.jar <input.jar> <output-dir> --codegen=ir
rg --no-filename -o '// IR codegen: .+' <output-dir>/cpp/output
```

For example, the exact inventory and marker commands for the first fixture
were:

```bash
javap -p -s -c -classpath /tmp/native-obfuscator-ir-admission-phase13/fixtures/A/clinit__TestClInitStacktrace/input.jar Test
rg --no-filename -o '// IR codegen: .+' /tmp/native-obfuscator-ir-admission-phase13/fixtures/A/clinit__TestClInitStacktrace/output/cpp/output
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

This join excludes anything absent from the input JAR. In particular, every
excluded generated result in this run was an injected `<clinit>()V` IR marker.
No fallback log was excluded. Existing input `<clinit>()V` methods remain in
the inventory. Generated loader/hidden helper methods are absent from the
inventory; fixture classes whose own names contain `Loader` remain included
because they existed in the input JAR.

The raw marker/log command output, before and after that input-inventory join,
was:

```text
corpus	input_inventory	raw_ir_markers	excluded_ir_markers	matched_ir_markers	raw_fallback_logs	raw_left_logs	excluded_fallback_logs
A	108	110	41	69	37	2	0
B-jdk17	36	32	12	20	16	0	0
C-jdk21-extra	38	23	8	15	23	0	0
```

The final raw counts emitted by the helper were:

```text
corpus	inventory	IR	legacy-fallback	constructor-left-java	missing
A	108	69	37	2	0
B-jdk17	36	20	16	0	0
C-jdk21-extra	38	15	23	0	0
```

There were no Java compilation failures and no obfuscator failures.
`krak2 -V` failed as follows, so
`pull-requests/PullRequest72/TestStringConcatFactory.j` was skipped while its
Java `Main.java` was still compiled and measured:

```text
krak2: command not found
```

## Corpus A: checked-in ClassicTest fixtures

These are all eight fixture roots that `TestsGenerator` discovers from the
checked-in `obfuscator/test_data/tests/**` tree. The large
`java-obfuscator-test/JavaObfuscatorTest` fixture was compiled as one JAR,
matching ClassicTest's recursive source collection.

| Fixture | Inventory | IR | Legacy fallback | Constructor left Java |
| --- | ---: | ---: | ---: | ---: |
| `clinit/TestClInitStacktrace` | 4 | 3 | 1 | 0 |
| `empty/EmptyTest1` | 2 | 1 | 1 | 0 |
| `indy/IndyTest1` | 3 | 2 | 1 | 0 |
| `interface/InterfaceDefault` | 4 | 3 | 1 | 0 |
| `interface/InterfaceDefaultStacktrace` | 4 | 4 | 0 | 0 |
| `issues/Issue52` | 4 | 2 | 2 | 0 |
| `java-obfuscator-test/JavaObfuscatorTest` | 85 | 53 | 30 | 2 |
| `pull-requests/PullRequest72` (Java only) | 2 | 1 | 1 | 0 |
| **Corpus A** | **108** | **69** | **37** | **2** |

## Corpus B: fetched JDK 17 E2E fixtures

All five fetched JDK 17 fixtures compiled with `--release 17` and were
processed successfully.

| Fixture | Inventory | IR | Legacy fallback | Constructor left Java |
| --- | ---: | ---: | ---: | ---: |
| `InvokeDynamicLambdaE2E` | 6 | 2 | 4 | 0 |
| `MethodHandlesE2E` | 8 | 4 | 4 | 0 |
| `NestPrivateAccessE2E` | 5 | 3 | 2 | 0 |
| `RecordSemanticsE2E` | 9 | 4 | 5 | 0 |
| `SealedHierarchyE2E` | 8 | 7 | 1 | 0 |
| **Corpus B / JDK 17** | **36** | **20** | **16** | **0** |

## Extra corpus C: fetched JDK 21 fixtures

These rows are reported separately and must not be read as JDK 17 results.

| Fixture | Inventory | IR | Legacy fallback | Constructor left Java |
| --- | ---: | ---: | ---: | ---: |
| `PatternSwitchE2E` | 15 | 5 | 10 | 0 |
| `RecordPatternsE2E` | 21 | 9 | 12 | 0 |
| `SequencedCollectionsE2E` | 2 | 1 | 1 | 0 |
| **Extra corpus C / JDK 21** | **38** | **15** | **23** | **0** |

## First unsupported reason histogram

The full logged reason, including bytecode instruction index, is retained for
every method in
[`ir-admission-phase13-corpus-methods.tsv`](ir-admission-phase13-corpus-methods.tsv).
Grouping the same rows by result, opcode, and message gives:

| Corpus | Result | Count | Opcode | Message |
| --- | --- | ---: | ---: | --- |
| A | constructor-left-java | 2 | 18 | Unsupported instruction for phase-two IR |
| A | legacy-fallback | 30 | 18 | Unsupported instruction for phase-two IR |
| A | legacy-fallback | 2 | 14 | Unsupported instruction for phase-two IR |
| A | legacy-fallback | 2 | 188 | Unsupported instruction for phase-two IR |
| A | legacy-fallback | 2 | 197 | Unsupported instruction for phase-two IR |
| A | legacy-fallback | 1 | 50 | Unsupported instruction for phase-two IR |
| B-jdk17 | legacy-fallback | 16 | 18 | Unsupported instruction for phase-two IR |
| C-jdk21-extra | legacy-fallback | 21 | 18 | Unsupported instruction for phase-two IR |
| C-jdk21-extra | legacy-fallback | 1 | 54 | Local 5 is ref but this instruction requires i32 |
| C-jdk21-extra | legacy-fallback | 1 | 83 | Unsupported instruction for phase-two IR |

## Comparison with PR #92

PR #92 was 5/6 synthetic on the #89 tip; these numbers replace that as the
honest checked-in-fixture and JDK 17 corpus measurement on the #90 tip. This
report does not overwrite PR #92's report, and neither result establishes
production coverage.
