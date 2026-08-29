# Fable review: post-phase-19 three-mode bench re-measurement (PR #132)

- **Reviewed tip:** `origin/cursor/bench-ir-phase19-6d81` at
  `71b76a3` (docs: re-measure JVM/legacy/IR bench after phase 19)
- **Base:** `master` at `76ebeddb005e01033523384275c8c0c1641ada81` (verified:
  this is the current `origin/master` tip and the merge base of the PR branch)
- **Verdict: accept with nits.** All numeric and scope checks pass. The two
  nits below are write-up observations only; neither requires a new bench run
  and neither is a blocking defect in this PR.

This review examines the written evidence only. No `:obfuscator:bench` re-run
was performed and none was needed: no claim in the PR required disproving, and
this review VM is a fresh checkout without the original run's
`build/benchmarks/` artifacts.

## Checklist findings

### 1. Diff is docs-only — verified

`git diff --name-status origin/master...HEAD` on the PR branch shows exactly
three files:

```text
A  PR_BODY.md
M  docs/benchmarks/README.md
A  docs/benchmarks/results-ir-vs-legacy-phase19.md
```

No compiler, harness (`benchmarks/run.py`), CLI, interpreter, or SDK source is
touched. The README change is only the two-sentence pointer at the bottom:
the phase-19 file becomes "the latest three-mode run" and
`results-ir-vs-legacy-master.md` is explicitly kept as "the pre-phase-19
record". The old master results file itself is byte-identical to `master` —
it was not overwritten as if it were this run.

### 2. Numbers presented as measured on `76ebedd`, not invented or copied — verified

The phase-19 file records the commit SHA (`76ebedd…`, confirmed as the master
tip containing phase 19), the exact top-level and subprocess commands,
`uname`, `java -version`, `cmake --version`, and both GCC 13.3.0 compiler
versions, plus all ten raw samples per kernel per mode and per-kernel
checksums.

The IR-mode numbers are not copies of the pre-phase-19 run in
`results-ir-vs-legacy-master.md` (#122-era record on `e997d71`):

| Kernel | Pre-phase-19 IR median | Phase-19 IR median |
| --- | ---: | ---: |
| `integer-loop` | 183,656,531.5 (fallback) | 10,016,799.0 |
| `string-concat-hash` | 7,938,482.5 | 7,919,348.5 |
| `recursion` | 15,866,969.5 (mixed) | 11,240,118.5 |

Every raw-sample list also differs sample-by-sample between the two files
(e.g. `string-concat-hash` IR starts 6,943,661 in the old run versus
7,009,690 in the new one), which is what a fresh measurement looks like and
what a copy would not.

### 3. IR path audit — verified against the tree

The audit table lists all four measured methods
(`IntegerLoopKernel.run(I)J`, `StringConcatHashKernel.run(I)I`,
`RecursionKernel.run(II)J`, `RecursionKernel.recurse(IJ)J`) as IR, each with
`// IR codegen:` marker evidence language naming the generated `.cpp` file.
No row in the summary or IR-mode table is a fallback or mixed row labeled as
an IR timing; the file states the fallback log contained zero
`IR codegen unsupported` / `falling back to legacy` lines.

The claim is consistent with the code on `76ebedd`: `AsmToIr.java` admits
`LAND`/`LOR`/`LXOR` via `isLongBinaryOp` and `LSHL`/`LSHR`/`LUSHR` via
`isLongShiftOp`, and the two methods that fell back before phase 19 do so on
exactly those opcodes — `IntegerLoopKernel.run` uses long `>>>`/`<<`/`^`
(opcode 125 `LUSHR` was the recorded pre-phase-19 rejection) and
`RecursionKernel.recurse` uses long `^` (opcode 131 `LXOR`). The observed
timing shifts corroborate the path change: `integer-loop` IR moved from the
legacy-fallback ~183 ms regime to ~10 ms, and `recursion` from the mixed
~15.9 ms to ~11.2 ms.

### 4. `#53` eval median not back-filled — verified

`docs/benchmarks/results-ir-eval-lower.md` is untouched by the diff and still
records the `--ir-lower=eval` row as `**N/A**` / `**N/A**` / `No`. The
phase-19 file and PR body both state the median is not back-filled.

### 5. Defaults and claims unchanged — verified

- `Main.java` is untouched and still declares
  `@CommandLine.Option(names = {"--codegen"}, defaultValue = "legacy", ...)`.
- The phase-19 file's "Must not be read as" section explicitly disclaims a
  portable speedup, a "native code generally beats HotSpot" claim, a release
  gate, and a JDK support badge, and singles out the `integer-loop` IR median
  landing near the JVM median as a single-machine, non-generalizing
  observation.
- `docs/architecture/project-status.md` is untouched; nothing marks the
  production goal complete. Requirement 7 is stated as unmet in both the
  results file and the PR body.

### 6. Checksums match across the three modes — verified

In the written tables, each kernel's checksum is identical in the plain-JVM,
legacy-JNI, and IR-mode tables:

| Kernel | Checksum (all three modes) |
| --- | ---: |
| `integer-loop` | -5,663,617,524,014,644,874 |
| `string-concat-hash` | -124,029,673,400 |
| `recursion` | 3,055,512 |

These also equal the pre-phase-19 run's checksums, as expected for
deterministic kernels that this PR does not modify.

### 7. Medians and means recomputed from the raw samples — all 18 exact

I recomputed the median and mean of every listed ten-sample row (three
kernels × three modes) with an independent script. All nine medians and all
nine means match the written values exactly (to the printed 0.1 precision),
and the summary table matches the per-mode raw tables:

| Row | Recomputed median | Written median | Recomputed mean | Written mean |
| --- | ---: | ---: | ---: | ---: |
| JVM `integer-loop` | 10,022,141.0 | 10,022,141.0 | 10,042,374.0 | 10,042,374.0 |
| JVM `string-concat-hash` | 1,159,325.5 | 1,159,325.5 | 1,254,919.0 | 1,254,919.0 |
| JVM `recursion` | 65,068.0 | 65,068.0 | 64,754.9 | 64,754.9 |
| Legacy `integer-loop` | 182,379,510.0 | 182,379,510.0 | 182,354,948.7 | 182,354,948.7 |
| Legacy `string-concat-hash` | 11,850,265.5 | 11,850,265.5 | 11,540,741.6 | 11,540,741.6 |
| Legacy `recursion` | 15,837,322.5 | 15,837,322.5 | 15,838,187.3 | 15,838,187.3 |
| IR `integer-loop` | 10,016,799.0 | 10,016,799.0 | 10,024,490.0 | 10,024,490.0 |
| IR `string-concat-hash` | 7,919,348.5 | 7,919,348.5 | 7,697,399.5 | 7,697,399.5 |
| IR `recursion` | 11,240,118.5 | 11,240,118.5 | 11,239,278.4 | 11,239,278.4 |

No arithmetic fix was needed on this review branch.

### 8. PR #132 body — verified

The PR body matches the branch content: docs-only scope, fresh measurement on
`76ebedd`, all four methods on IR with zero fallback lines, ship-ready **No**,
review required **Yes**, preconditions listed, bilingual (a)(b)(c)(d)
sections present. No claim in the body exceeds what the results file records.

## Nits (no new bench run required)

1. **Stale title in the superseded file.** `results-ir-vs-legacy-master.md`
   is still titled "Current-master JVM versus legacy/IR JNI benchmark" even
   though it is now the pre-phase-19 record for `e997d71`. This PR correctly
   does not rewrite that file, and the README pointer plus the phase-19
   file's "supersedes" paragraph disambiguate, but a one-line title rename
   (e.g. "Pre-phase-19 …") in a follow-up docs commit would remove the last
   ambiguity for a reader who lands on the old file directly.
2. **Artifact paths are VM-local.** The audit evidence cites
   `build/benchmarks/work/ir/transpiled/cpp/output/` and
   `build/benchmarks/logs/transpile-ir.log`, which existed only on the
   measuring VM and are not committed (correctly — generated trees do not
   belong in git). This is inherent to the harness design and is already
   mitigated by the recorded per-method marker strings and file names; noted
   only so future readers know the logs are reproducible via the recorded
   commands rather than retrievable from the repository.

## (a)(b)(c)(d)

### English

- **(a) Scope:** This review is docs-only. It adds
  `docs/reviews/bench-ir-phase19-fable.md` and a `PR_BODY.md` on the review
  branch. No compiler, harness, CLI, interpreter, or SDK source is touched,
  and no benchmark was re-run.
- **(b) Ship-ready?** **No.** The reviewed PR is a single-VM diagnostic
  record and this review does not change that. Nothing here is a release
  gate or a portable performance claim.
- **(c) Review required?** This document *is* the independent review of
  PR #132; verdict accept-with-nits. Any follow-up (e.g. the title-rename
  nit) should get its own ordinary docs review.
- **(d) Preconditions / status:** The production goal remains incomplete;
  requirement 7 (resisting unaided Sol-class recovery) remains unmet. The
  `--codegen` default remains `legacy`. Post-phase-19 numbers must be cited
  only from `results-ir-vs-legacy-phase19.md`; the `#53` eval median remains
  `N/A`.

### 中文

- **(a) 范围：** 本评审仅改文档：新增
  `docs/reviews/bench-ir-phase19-fable.md` 与评审分支上的 `PR_BODY.md`。
  未触碰编译器、基准脚本、CLI、解释器或 SDK 源码，也未重新运行基准。
- **(b) 可以直接上线吗？** **否。** 被评审的 PR 是单虚拟机诊断记录，本评审
  不改变这一性质；这里没有任何发布门槛或可移植的性能结论。
- **(c) 需要评审吗？** 本文档即是对 PR #132 的独立评审，结论为
  “接受，附非阻塞性 nit”。后续跟进（如旧文件标题重命名）应走常规文档评审。
- **(d) 前置条件 / 状态：** 生产目标仍未完成；需求 7（抵御无辅助的 Sol 级
  恢复）仍未满足。`--codegen` 默认值仍为 `legacy`。第 19 阶段之后的数字只能
  引用 `results-ir-vs-legacy-phase19.md`；#53 的 eval 中位数保持 `N/A`。
