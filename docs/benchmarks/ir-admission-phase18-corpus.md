# Phase-18 IR admission on the #110 corpora

## Scope and environment

- Compiler tip: draft [PR #114](https://github.com/gaoyu06/native-obfuscator/pull/114)
  at `b78d6d7c74b11c416f5703df89ad6b0c1532aec2`
- Stack base: draft [PR #108](https://github.com/gaoyu06/native-obfuscator/pull/108)
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

This is an admission measurement of the opt-in `--codegen=ir` path. The CLI
default remains `legacy`; it was not changed. No compiler or runtime source was
changed for this measurement. Native compilation was not required or run, and
no native behavioral/E2E claim is made.

In particular, the Corpus B result of 36/36 is **not evidence that JDK 17 is
supported**. It is only the admission result for the 36 code-bearing methods in
these five fixtures. Corpus C is a separately labelled JDK 21 extra corpus and
is not part of the JDK 17 result. This report makes no JDK 25 support claim.

## Commands and counting method

The top-level measurement command was:

```bash
python3 docs/measurement/ir-admission-phase18/measure.py --include-jdk21
```

The helper verifies that the exact phase-18 tip is an ancestor and that every
committed change since that tip is under `docs/`. It writes all resolved
commands to
`/tmp/native-obfuscator-ir-admission-phase18/commands.log`. Its setup commands
were:

```bash
./gradlew :obfuscator:shadowJar --rerun-tasks
git fetch origin cursor/test-jdk17-e2e-harness-6d81 cursor/jdk21-25-e2e-6d81
git archive origin/cursor/test-jdk17-e2e-harness-6d81 obfuscator/test_data/tests/jdk17 | tar -x -C /tmp/native-obfuscator-ir-admission-phase18/fetched-sources
git archive origin/cursor/jdk21-25-e2e-6d81 obfuscator/test_data/tests/jdk21 | tar -x -C /tmp/native-obfuscator-ir-admission-phase18/fetched-sources
```

The shadow-JAR build completed with `BUILD SUCCESSFUL`. Corpus A was compiled
with `javac --release 8 -g`, Corpus B with `javac --release 17 -g`, and Corpus C
with `javac --release 21 -g`. All 16 Java fixture compilations and all 16
obfuscator invocations succeeded. Each obfuscator invocation explicitly passed
`--codegen=ir`; this does not alter the `legacy` CLI default.

The denominator is built independently for every fixture from its compiled
input JAR. The helper runs `jar tf`, then `javap -p -s -c` for each input class.
A method enters the inventory only when `javap` shows a `Code:` body. Abstract
and native methods without code are therefore absent.

Results are joined to the inventory by the exact
`class + method + descriptor` key:

- an exact `// IR codegen:` marker is `IR`;
- an exact unsupported log ending in `falling back to legacy for this method`
  is `legacy-fallback`;
- an exact unsupported constructor log ending in
  `leaving constructor bytecode unchanged` is `constructor-left-java`;
- an inventory key with none of those is `missing`.

Markers and logs for keys absent from the input JAR inventory, including
generated `<clinit>` and injected methods, are excluded. These are the same
inventory, exact-join, and exclusion rules used by
[PR #110](https://github.com/gaoyu06/native-obfuscator/pull/110) and
[PR #107](https://github.com/gaoyu06/native-obfuscator/pull/107).

The raw marker/log counts before and after that inventory join were:

```text
corpus	input_inventory	raw_ir_markers	excluded_ir_markers	matched_ir_markers	raw_fallback_logs	raw_left_logs	excluded_fallback_logs
A	108	149	41	108	0	0	0
B-jdk17	36	48	12	36	0	0	0
C-jdk21-extra	38	44	8	36	2	0	0
```

The 61 generated IR markers absent from the input inventories were excluded.
No fallback log was excluded. The final joined counts were:

```text
corpus	inventory	IR	legacy-fallback	constructor-left-java	missing
A	108	108	0	0	0
B-jdk17	36	36	0	0	0
C-jdk21-extra	38	36	2	0	0
```

`krak2` was unavailable:

```text
krak2: command not found
```

Consequently, `pull-requests/PullRequest72/TestStringConcatFactory.j` was
skipped exactly as in #110, while that fixture's Java `Main.java` was compiled
and measured.

## Corpus A: checked-in ClassicTest fixtures

These are the same eight fixture roots and the same 108-method inventory as
#110. `Fallback` combines `legacy-fallback` and `constructor-left-java`; every
such count is zero in this corpus.

| Fixture | Inventory | IR | Fallback | Missing |
| --- | ---: | ---: | ---: | ---: |
| `clinit/TestClInitStacktrace` | 4 | 4 | 0 | 0 |
| `empty/EmptyTest1` | 2 | 2 | 0 | 0 |
| `indy/IndyTest1` | 3 | 3 | 0 | 0 |
| `interface/InterfaceDefault` | 4 | 4 | 0 | 0 |
| `interface/InterfaceDefaultStacktrace` | 4 | 4 | 0 | 0 |
| `issues/Issue52` | 4 | 4 | 0 | 0 |
| `java-obfuscator-test/JavaObfuscatorTest` | 85 | 85 | 0 | 0 |
| `pull-requests/PullRequest72` (Java only) | 2 | 2 | 0 | 0 |
| **Corpus A** | **108** | **108** | **0** | **0** |

## Corpus B: fetched JDK 17 fixtures

All five fetched fixtures compiled with `--release 17` and were processed
successfully. This table is an admission inventory, not a JDK 17 support gate.

| Fixture | Inventory | IR | Fallback | Missing |
| --- | ---: | ---: | ---: | ---: |
| `InvokeDynamicLambdaE2E` | 6 | 6 | 0 | 0 |
| `MethodHandlesE2E` | 8 | 8 | 0 | 0 |
| `NestPrivateAccessE2E` | 5 | 5 | 0 | 0 |
| `RecordSemanticsE2E` | 9 | 9 | 0 | 0 |
| `SealedHierarchyE2E` | 8 | 8 | 0 | 0 |
| **Corpus B / JDK 17** | **36** | **36** | **0** | **0** |

## Extra corpus C: fetched JDK 21 fixtures

These rows are separate from Corpus B and must not be read as JDK 17 results.
Both fallbacks are `legacy-fallback`; no constructor was left in Java.

| Fixture | Inventory | IR | Fallback | Missing |
| --- | ---: | ---: | ---: | ---: |
| `PatternSwitchE2E` | 15 | 15 | 0 | 0 |
| `RecordPatternsE2E` | 21 | 19 | 2 | 0 |
| `SequencedCollectionsE2E` | 2 | 2 | 0 | 0 |
| **Extra corpus C / JDK 21** | **38** | **36** | **2** | **0** |

## Remaining fallback reasons

The complete method-level result and logged reason are retained in
[`ir-admission-phase18-corpus-methods.tsv`](ir-admission-phase18-corpus-methods.tsv).
The remaining first-failure families are:

| Corpus | Result | Count | Opcode | Logged reason |
| --- | --- | ---: | ---: | --- |
| C-jdk21-extra | legacy-fallback | 1 | 54 (`ISTORE`) | Local 5 is `ref`, but the instruction requires `i32` |
| C-jdk21-extra | legacy-fallback | 1 | 58 (`ASTORE`) | Local 9 is `i32`, but the instruction requires `ref` |

The exact methods are
`RecordPatternsE2E / Main.coordinateSum(Ljava/lang/Object;)I` at bytecode
instruction 22 and
`RecordPatternsE2E / Main.inspect(Ljava/lang/Object;)Ljava/lang/String;` at
bytecode instruction 480. No `NEWARRAY` or `MULTIANEWARRAY` fallback remained
in these measured corpora.

## Delta versus #110

#110 measured the same source revisions on #108 at
`5a6f6097524c1fe42cd82be2425f5e6736667688`. A direct TSV comparison found all
182 exact `corpus + fixture + class + method + descriptor` keys identical.
Only four rows changed result: the two `MULTIANEWARRAY` rows in `Issue52` and
the two `NEWARRAY` rows in `JavaObfuscatorTest` changed from
`legacy-fallback` to `IR`. The other 178 rows were unchanged.

`Delta` below is `phase 18 - #110`. Inventory and missing counts did not
change.

| Corpus | #110 inventory | #110 IR | #110 fallback | #110 missing | Phase-18 inventory | Phase-18 IR | Phase-18 fallback | Phase-18 missing | Delta inventory | Delta IR | Delta fallback | Delta missing |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| **A** | 108 | 104 | 4 | 0 | 108 | 108 | 0 | 0 | 0 | **+4** | **-4** | 0 |
| **B-jdk17** | 36 | 36 | 0 | 0 | 36 | 36 | 0 | 0 | 0 | 0 | 0 | 0 |
| **C-jdk21-extra** | 38 | 36 | 2 | 0 | 38 | 36 | 2 | 0 | 0 | 0 | 0 | 0 |

Across the separately labelled corpora, the admission delta versus #110 is
inventory `0`, IR `+4`, fallback `-4`, and missing `0`. This is an
admission-count delta only, not a production-coverage, compatibility,
behavioral, or performance result.

## (a) Change scope / 本次改动范围

**English:** Documentation and measurement artifacts only: a phase-18 copy of
the #110 helper, the 182-row joined method ledger, and this report. Compiler
and runtime code are unchanged.

**中文：**仅包含文档与测量产物：复用 #110 helper 的 phase-18 版本、182 行精确关联的
方法清单，以及本报告。未修改编译器或运行时代码。

## (b) Can this ship to production as-is? / 是否可直接上线？

**English:** No. These are admission counts on a draft compiler tip. Native
compilation and behavioral E2E were outside scope, and the result is not a
production coverage or compatibility gate.

**中文：**不能。这只是 draft 编译器提交上的接纳计数；范围不含 native 编译和行为
E2E，也不是生产覆盖率或兼容性门禁。

## (c) Is review required? / 上线前是否需要 review？

**English:** Yes. Review should verify the exact stack, the inventory join and
generated-method exclusions, the four #110-to-phase-18 transitions, and the
two unchanged Corpus C fallback reasons.

**中文：**需要。Review 应核对精确堆叠关系、inventory 关联与生成方法排除规则、相对
#110 的四个状态变化，以及 Corpus C 两个未变化的 fallback 原因。

## (d) Review preconditions / Review 的前置条件

1. **English:** Confirm #114 is measured at
   `b78d6d7c74b11c416f5703df89ad6b0c1532aec2`, stacked on #108 at
   `5a6f6097524c1fe42cd82be2425f5e6736667688`.
   **中文：**确认测量对象为 #114 的上述精确提交，且堆叠在 #108 的上述精确提交上。
2. **English:** Re-run
   `python3 docs/measurement/ir-admission-phase18/measure.py --include-jdk21`
   and require inventories `108/36/38` with zero missing methods.
   **中文：**重跑上述命令，并要求三个 inventory 为 `108/36/38` 且 missing 均为零。
3. **English:** Confirm the fetched fixture revisions and compare all 182 exact
   method keys against #110.
   **中文：**确认 fetched fixture 的提交版本，并以精确方法键逐一比对 #110 的全部
   182 个方法。
4. **English:** Require the branch diff from the #114 tip to stay under
   `docs/`, with no compiler or runtime change.
   **中文：**要求相对 #114 tip 的分支 diff 仅位于 `docs/`，且无编译器或运行时代码
   变更。
5. **English:** Keep the CLI default `legacy`; interpret `36/36` only as this
   fixture admission result, never as JDK 17 support.
   **中文：**保持 CLI 默认值为 `legacy`；`36/36` 只能解释为本 fixture 集的接纳结果，
   不能解释为支持 JDK 17。
