# Fable review: opt-in IR behavioral E2E on the JDK 25 fixture corpus (PR #141)

- **Reviewed tip:** `origin/cursor/eval-ir-jdk25-e2e-6d81` at
  `a3ffcb2a08200be6c60ec59964e39189bede5a16`
  (docs: record JDK 25 IR-mode E2E (or host gap))
- **Base:** `master` at `a7e54539461f12fa0eddd21973c716bc5f99708e` (verified:
  this is the current `origin/master` tip and the merge base of the PR
  branch, and it matches the base SHA the write-up itself records)
- **Verdict: accept with nits.** All scope, consistency, and arithmetic
  checks pass. The nits below are write-up presentation observations only;
  none requires a new measurement and none is a blocking defect.

This review examines the written evidence. The full E2E was **not** re-run:
no claim required disproving, and this review VM is a fresh checkout without
the original run's `/tmp/native-obfuscator-ir-jdk25-e2e` artifacts. One
stronger-than-required cross-check was possible without inventing any data:
all four fixtures print deterministic stdout derivable from their committed
sources, so I recomputed each expected stdout byte stream and hashed it
independently — all four SHA-256 values match the write-up exactly (see
finding 6).

## Checklist findings

### 1. Diff is docs-only — verified

`git diff --name-status origin/master...HEAD` shows exactly two added files:

```text
A  PR_BODY.md
A  docs/benchmarks/ir-jdk25-e2e-corpus.md
```

No compiler, IR, evaluator, interpreter, fixture `Main.java`, README,
`project-status.md`, or build file is touched. `Main.java` (the CLI) on this
branch still declares `@CommandLine.Option(names = {"--codegen"},
defaultValue = "legacy", ...)`, so the `legacy` default is not flipped.

### 2. No "JDK 25 supported" claim; hybrid labeled hybrid — verified

The corpus doc states in bold: *"This must not be read as 'JDK 25
supported.'"* and enumerates why (one measurement, four small fixtures, one
Linux x86-64 host, default codegen still `legacy`, no wider corpus/vendor/
architecture). The PR body repeats the disclaimer in both languages.

`Main$Validated.<init>(I)V` is consistently labeled: the summary calls the
`FlexibleConstructorBodiesE2E` pass "a hybrid (one constructor stayed in
Java bytecode)", the admission section calls it "a hybrid result, not a
pure-IR one", and the table row reads 6/7 with `left in Java = 1`. Nowhere
is the fixture presented as full IR.

### 3. Internal consistency — verified, including against the tree

- **Admission arithmetic.** Inventory 2+7+5+7 = 21; matched IR
  2+6+5+7 = 20; raw markers minus excluded equals matched IR in every row
  (3−1=2, 10−4=6, 5−0=5, 8−1=7; totals 26−6=20); matched + left-in-Java =
  inventory for `FlexibleConstructorBodiesE2E` (6+1=7). The per-method join
  lists contain exactly 2, 7, 5, and 7 entries, matching the tables, and
  the headline 20/21 with exactly one exception is consistent throughout
  the doc and the PR body.
- **Excluded markers.** The 4 excluded markers on
  `FlexibleConstructorBodiesE2E` match its 4 classes (`Main`, `Main$Base`,
  `Main$Delegating`, `Main$Validated`), none of which has a source
  `<clinit>`; `ScopedValuesE2E` excludes 0 because its `Main` *does* have a
  source `<clinit>` (the static `ScopedValue` field), which appears in the
  inventory as IR. Both facts match the committed fixture sources.
- **Diagnostic text.** The quoted log line is byte-plausible against the
  code on `master`: `NativeObfuscator` logs
  `IR codegen unsupported for {}#{}{}: {}; leaving constructor bytecode
  unchanged` for `<init>` methods, `ConstructorSpecialMethodProcessor`
  throws exactly "Control flow before the this/super call cannot be split
  safely", and `UnsupportedIrConstructException` appends
  ` at bytecode instruction N (opcode M)`. Opcode 154 is `IFNE`, which is
  exactly what `Validated(int)`'s `if (normalized == 0)` branch before
  `super(...)` compiles to.
- **Toolchains.** The host block (`Linux 6.12.94+`, OpenJDK
  `21.0.10+7-Ubuntu-124.04`, CMake 3.28.3, GCC/G++ 13.3.0) matches this
  review VM's toolchain verbatim. The bundled ASM is 9.8 per
  `obfuscator/build.gradle`, matching the claim that the JDK 21-hosted
  transpiler reads class-file version 69. The Temurin download SHA-256
  (`dbb69839…cf41e`) matches Adoptium's published
  `OpenJDK25U-jdk_x64_linux_hotspot_25.0.4.1_1.tar.gz.sha256.txt` exactly.
- **Fixture list.** The four fixtures named are exactly the contents of
  `obfuscator/test_data/tests/jdk25/`; the referenced parser
  (`docs/measurement/ir-admission-phase18/measure.py`) exists. The JEP
  attributions (512/511 compact source + module import, 513 flexible
  constructor bodies, 506 scoped values, gatherers final since JDK 24) are
  correct for the committed sources.

### 4. JEP 472 warning recorded as a warning / future error — verified

The four-line restricted-native-access warning is quoted verbatim, its
scope explained (transformed runs only; oracle loads no native library and
had empty stderr), and the last line is explicitly called out as "a real
forward-compatibility signal: a future JDK will refuse `System::load` …
unless the user passes `--enable-native-access`". The production-answer
section repeats it as a known gap and asks review to decide on an
`--enable-native-access` story before any JDK 25 claim. It is not ignored
and not overstated into a present-day failure.

### 5. `#53` untouched, no invented timings, default unchanged — verified

The doc states "no benchmark numbers were measured — the issue #53
benchmark entry stays `N/A`", the diff touches no benchmark results file,
and no timing numbers appear anywhere in either added file. The `--codegen`
default is `legacy` on this branch (finding 1).

### 6. Stdout hashes independently reproduced — verified (beyond checklist)

All four fixtures are deterministic and their expected stdout is derivable
from the committed sources without running anything from the PR. Hashing
each expected byte stream reproduces the doc's table exactly:

| Fixture | Expected stdout (derived from source) | Recomputed SHA-256 matches doc |
| --- | --- | --- |
| `CompactSourceModuleImportE2E` | `COMPACT\|SOURCE\|MAIN`, `2025-09-19` | yes (`ca5587bf…`) |
| `FlexibleConstructorBodiesE2E` | `12`, `14`, `zero` | yes (`74c35c1d…`) |
| `ScopedValuesE2E` | `unbound`, `outer`, `inner`, `outer`, `unbound` | yes (`a6462979…`) |
| `StreamGatherersE2E` | `[[1, 2, 3], [4, 5, 6], [7]]`, `[2, 5, 10, 17]`, `41` | yes (`59d7490d…`) |

This corroborates that the recorded oracle outputs are the true program
outputs (not placeholders), and — because the doc records a single hash per
fixture asserting oracle == transformed — that the claimed byte-exact
matches are hashes of real content. Note the `FlexibleConstructorBodiesE2E`
stream includes `zero`, i.e. the exception path through the hybrid
(left-in-Java) constructor behaved identically in the transformed JAR.

## Nits (docs-only, non-blocking)

1. **Mixed notation in the summary total row.** Per-fixture rows record
   exit codes (`0 / 0`) in the "CMake configure / build", "Oracle exit",
   and "Native exit" columns, but the **Observed total** row switches to
   counts (`4/4 / 4/4`, `4/4 exit 0`) in the same columns. The meaning is
   recoverable, but a consistent notation (or a footnote) would read
   cleaner.
2. **Single-column hash table.** The stdout-evidence table header "Oracle
   and transformed stdout SHA-256" presents one hash for two files. This is
   correct precisely because they match, and the prose says so, but two
   explicit identical columns (as a reader might expect from "pairs") would
   make the equality self-evident without the prose.

Neither nit requires touching the PR branch; both could be folded into any
future corpus doc.

## (a)(b)(c)(d)

### English

- **(a) Scope:** This review is docs-only. It adds
  `docs/reviews/ir-jdk25-e2e-fable.md` and a `PR_BODY.md` on the review
  branch. No compiler, IR, evaluator, or interpreter source is touched, and
  the E2E was not re-run — written evidence plus source-derived hash
  reproduction sufficed.
- **(b) Ship-ready?** **No.** The reviewed PR is a single-host behavioral
  record of four fixtures, and this review does not change that. "JDK 25
  supported" remains an unmade claim; the hybrid constructor and the JEP
  472 warning are open items the reviewed doc itself flags.
- **(c) Review verdict:** This document *is* the independent review of
  PR #141: **accept with nits** (two presentation nits, non-blocking).
- **(d) Preconditions / status:** `--codegen` default remains `legacy`;
  the `#53` eval median remains `N/A`; no benchmark numbers exist for
  JDK 25. Any future JDK 25 support claim needs a wider corpus, more
  vendors/architectures, a decision on `--enable-native-access` for the
  generated loader, and a story for flexible constructor bodies that
  branch before `super(...)`.

### 中文

- **(a) 范围：** 本评审仅改文档：新增
  `docs/reviews/ir-jdk25-e2e-fable.md` 与评审分支上的 `PR_BODY.md`。
  未触碰编译器、IR、evaluator 或 interpreter 源码，也未重新运行 E2E ——
  书面证据加上由源码推导的 stdout 哈希复算已足够。
- **(b) 可以直接上线吗？** **否。** 被评审的 PR 是单主机、四个 fixture 的
  行为记录，本评审不改变这一性质。"支持 JDK 25"仍是未做出的声明；混合
  构造器与 JEP 472 警告是被评审文档自己标记的未决事项。
- **(c) 评审结论：** 本文档即是对 PR #141 的独立评审：**接受，附两条
  非阻塞性写作 nit**。
- **(d) 前置条件 / 状态：** `--codegen` 默认值仍为 `legacy`；#53 的 eval
  中位数保持 `N/A`；JDK 25 没有任何 benchmark 数字。未来任何 JDK 25 支持
  声明都需要更大的语料、更多厂商/架构、对生成 loader 的
  `--enable-native-access` 方案作出决定，以及处理在 `super(...)` 之前
  存在分支的 flexible constructor body。
