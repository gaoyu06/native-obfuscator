# Interpreter evaluation protocol

## Status and scope

This document defines a **methodology only**. No evaluation has been run and this
document makes **no** claim — measured or otherwise — that interpreter-lowered
output is harder to read, more secure, or more analysis-resistant than direct
C++ output. It defines *how one would measure* a single, narrow, falsifiable
question and how to report the answer honestly, including the null result.

It specializes the general methodology in
[`eval-automated-analysis.md`](eval-automated-analysis.md) (on
`cursor/docs-production-roadmap-6d81`) to the interpreter backend. Where the two
differ, the general document governs; this one only adds the interpreter-specific
variant, demo method, and reader protocol.

## The one question this protocol measures

> When a method is lowered to the interpreter's opcode stream, is its **algorithm
> not trivially obvious** from the generated C++ *source* — because the algorithm
> is now **data** (an opcode array plus constant indices) rather than
> method-specific control-flow C++?

Framed as a measurable, paired comparison:

**H0 (null, assumed true until disproven):** an automated reader recovers the
method's algorithm from the interpreter-backend C++ source *as well as* it does
from the direct-backend C++ source. (Interpreting changes nothing a reader
cares about.)

**H1 (alternative):** the reader recovers the algorithm *less* accurately from
the interpreter-backend source, on a defined comprehension metric, with a
reported effect size and confidence interval.

We test H0 and are prepared to keep it. A failure to reject H0 is a perfectly
good, publishable result: it would mean the interpreter has no source-opacity
effect and should not be advertised as one.

### What this protocol explicitly does **not** measure

- Not run-time / dynamic analysis (debugger, tracing, instrumenting the loop).
  The claim is about the *generated C++ source as data*, so the reader sees
  source, not a running process. Dynamic analysis of a shipped library is a
  different question and out of scope.
- Not "resistance" in any security sense. Low reader comprehension is **not**
  evidence of security; per the general eval doc it may equally indicate poor
  debuggability or a bad benchmark. No composite "resistance score" is produced.
- Not performance. See the roadmap's performance lane.
- Not whether the opcode stream can be re-decoded by a purpose-built tool. A
  disassembler that knows the ISA trivially recovers the opcodes; that is
  expected and is not what "not trivially obvious" refers to. This protocol
  measures the *general* reader working from C++ source without being handed the
  ISA spec (and, in a separate arm, with it — see contamination controls).

## Artifact variants (paired, same source, same compiler commit)

For every corpus method, build these from one IR and one toolchain commit, so
differences are attributable to the backend and not to names, comments, or
optimization level. This follows the general doc's variant table and adds the
interpreter rows.

| Variant | Purpose |
|---|---|
| Java source + `javap` bytecode | Source-level and bytecode baselines |
| Direct-backend C++ (debug) | The comparison control, readable names/line maps |
| Direct-backend C++ (release) | Normal strip/optimize policy control |
| **Interpreter C++ (debug)** | Trampoline + `method_desc` + `code[]`/tables with pc→offset map |
| **Interpreter C++ (release)** | Same, stripped, no debug side tables |
| Hand-written equivalent C++ | Calibrates generic C++ reading difficulty |

Rules carried from the general doc, restated because they are the easiest way to
produce a *false* opacity result:

- **Never compare a stripped interpreter artifact against an annotated direct
  artifact.** Debug-vs-debug and release-vs-release only. A "harder to read"
  finding that is really "we removed the comments" is disqualified.
- **Match on artifact size / token length.** The interpreter's C++ is small and
  uniform (a trampoline + a byte array); the direct backend's C++ scales with
  the method. If the reader does worse on the interpreter variant purely because
  the algorithm's bytes are opaque *and* short, that is the effect; but if it
  does worse because a *different-sized* context truncated, that is a confound
  and must be stratified out.
- **Every candidate artifact must first pass semantic-equivalence** (Java vs
  C++ vs interpreter differential test). Broken output is never a valid
  "harder to analyze" result.

## Demo method

A single, self-contained, deterministic method with a checkable oracle, drawn
from a family the repository already exercises (`pack/tests/bench/Calc`-style
integer kernels), so it is realistic and license-clean.

```java
// DemoKernel.java  — deterministic, pure, exhaustively checkable on small inputs
public final class DemoKernel {
    // A small integer state-mixing routine: the "algorithm" a reader must recover
    // is "rotate-xor-multiply mixing loop with an accumulator", plus the exact
    // constants and the loop bound.
    public static int mix(int seed, int rounds) {
        int acc = seed ^ 0x9E3779B9;          // constant #1
        for (int r = 0; r < rounds; r++) {
            acc += (acc << 6) + (acc >>> 2);   // shift/mix
            acc ^= (acc * 0x85EBCA77);         // constant #2, multiply
            acc = Integer.rotateLeft(acc, 13); // rotate  (lowers to shifts/or)
        }
        return acc;
    }
}
```

Why this method:

- **Pure and deterministic** → the oracle is `mix` itself, run in reference Java;
  output-prediction tasks are scored against execution, not opinion.
- **Small but non-trivial** → it has a recoverable "shape" (a mixing loop) and
  specific magic constants and a rotate; a reader can be scored on whether it
  recovers *the loop structure*, *the two constants*, *the operations*, and *the
  loop bound* — each a discrete, gradable fact.
- **Exercises exactly the opcodes in question** → `IPUSH`/`LDC_INT`, `IXOR`,
  `ISHL`/`IUSHR`, `IMUL`, `IADD`, the `IF_ICMP*`/`GOTO` loop, `ILOAD`/`ISTORE`,
  `IRETURN`. In the direct backend these appear as named `cstackN.i = ... ^ ...`
  statements; in the interpreter they are bytes in `code[]` plus shared handler
  expressions.
- **`Integer.rotateLeft`** adds one `invokestatic` (unless inlined by javac),
  letting the cross-boundary-trace task check that the reader still finds the
  JNI/invoke edge in both variants.

A second, control method (`add(int,int){return a+b;}`) is included as a *clean,
trivially-obvious* case: if the reader cannot recover even `add` from the
interpreter variant, the benchmark is broken, not the method opaque. Held-out
methods from other algorithm families are used for the scored test set so the
demo is not the thing being generalized from.

## Automated-reader protocol

Two reader classes, run identically on every variant, order-randomized, with
backend identity hidden behind opaque artifact IDs (the reader must not be able
to infer the backend from filenames).

### Deterministic baselines (run first, cheap, reproducible)

Pinned toolchain over every C++ variant:

- parse/index/compile success and diagnostic counts;
- `clang-tidy` with a check set frozen **before** results are seen;
- CFG and call-graph extraction (e.g. via the compiler's own IR or a pinned
  analyzer) — measures whether *structural* recovery differs (the direct backend
  exposes the method's CFG as C++ control flow; the interpreter exposes only the
  executor's single dispatch loop, with the method CFG hidden in `code[]`);
- cyclomatic complexity, source size, symbol count, string-literal inventory.

These are baselines, not ground truth for Java semantics. A predictable,
*expected* result here is that CFG extraction on the interpreter variant returns
the executor's dispatch loop rather than the method's loop — that is the
"algorithm as data" phenomenon stated mechanically, and it needs no LLM to
observe. It is reported as-is, not inflated into a comprehension claim.

### LLM-based readers (the actual comprehension measurement)

Follow the general doc's controls exactly:

- **At least two pinned model/config families**; record provider, exact
  version/immutable digest, date, region, retention/training policy, sampling
  params, seed where available, and repetition count.
- **Two context conditions, reported separately and never mixed within a
  comparison:**
  1. *no-ISA:* the reader is given only the generated C++ translation unit(s),
     as a maintainer new to the project would see them.
  2. *with-ISA:* the reader is additionally given
     [`interpreter-isa.md`](interpreter-isa.md). This measures whether the
     opacity, if any, is merely "the reader didn't know the encoding" — which is
     a knowledge gap, not an intrinsic property. A large no-ISA/with-ISA gap
     means the effect is trivially removed by publishing the ISA (which we do),
     so it must not be reported as opacity.
- **Frozen prompts / schemas** for each task; machine-checkable outputs.
- **Untrusted-content handling:** source comments and string literals are
  untrusted evaluation data; the harness delimits them, records any
  instruction-following deviation, and includes benign contamination controls.
- **No cross-variant leakage:** no variant gets compiler execution, source maps,
  or the ISA that another variant in the same comparison lacks.

### Task suite (subset of the general doc, specialized)

Scored per method, per variant, paired:

1. **Function summary (structured schema):** purpose, inputs, output, side
   effects, exceptions. For `mix`, graded on discrete recovered facts: *is it a
   loop?*, *loop bound = `rounds`?*, *constant `0x9E3779B9` present?*, *constant
   `0x85EBCA77` present?*, *rotate-by-13 recognized?*, *ops = xor/shift/mul/add?*
   Each is exact-match or F1 against the gold from the IR, not prose grading.
2. **Output prediction:** predict `mix(seed, rounds)` for held-out boundary
   inputs (0 rounds, 1 round, `Integer.MIN_VALUE` seed, large `rounds`); score
   against execution. This is the strongest single "did you actually understand
   the algorithm" signal and is backend-blind.
3. **Graph recovery:** emit the method CFG using stable IDs; score precision/
   recall/F1 vs IR-derived gold. Expected divergence: interpreter variant lacks
   method-level CFG in the source.
4. **Cross-boundary trace:** find every JNI/invoke edge and map to Java
   method/offset. Checks the `rotateLeft` invoke is still locatable.
5. **Defect localization:** seed one realistic defect (e.g. wrong shift mask,
   `>>` vs `>>>`, off-by-one loop bound) into an *isolated evaluation copy* —
   never into release code — and measure top-k localization and clean-control
   false-positive rate, in both variants.
6. **Patch task:** produce a minimal fix; score only by compile + hidden-test
   pass + patch scope + regression count. Prose plausibility does not count.

### Metrics & statistics

- Report per-dimension metrics from the general doc's table (buildability,
  structural recovery, comprehension F1, defect top-k/MRR, patch pass-rate,
  efficiency), as **distributions and paired differences**, not single means.
- Missing / refused / truncated / timed-out reader responses are **separate
  outcome classes**, not silently scored as wrong.
- Choose sample size and the single primary metric (proposed:
  output-prediction accuracy on held-out inputs) **before** looking at candidate
  results. Pair by source method; report effect size + bootstrap CI; cluster by
  algorithm family; correct for multiple comparisons.
- A result is **inconclusive** (not "opaque") when CIs are wide, the no-ISA vs
  with-ISA gap explains the difference, model drift is detected, or artifact
  size confounds it.

## Honest-reporting rules (the point of this document)

1. **Default conclusion is H0.** The write-up leads with "no measured
   source-opacity effect" unless H1 is supported with an effect size and CI.
2. **No composite score.** Comprehension, traceability, and defect detection are
   reported separately; they are never summed into a "hardness"/"resistance"
   number.
3. **State the trivial refutation.** Because the ISA is published, any opacity is
   removable by handing a reader this spec. Report the with-ISA arm prominently;
   if it closes the gap, the honest headline is "the encoding was unfamiliar,"
   not "the algorithm was hidden."
4. **Name the model and date.** Results say "model X on date Y," never "LLMs."
5. **Publish the null and the failures.** Exclusions, refusals, and broken
   artifacts are reported, not dropped.
6. **Structural fact ≠ comprehension fact.** "CFG extraction returns the
   dispatch loop" is a true, unsurprising structural observation about the
   backend; it is reported as such and is *not* restated as "the algorithm is
   hidden from readers."

## Human decisions this evaluation needs

- **D20–D22 (roadmap):** whether hosted models may receive these artifacts at
  all (privacy), which analyzer configurations count, and whether any
  "readability" number may be published — all gated before external services see
  artifacts. Owner: product + security.
- **Whether opacity is a supported, advertised property at all.** Only a
  positive, reproduced H1 with a durable (survives with-ISA) effect could justify
  advertising it; absent that, the interpreter is documented purely as a
  code-size / compatibility-fallback / differential-testing backend. Owner:
  product + security.
- **Sign-off on the frozen prompt set and primary metric** before any release-
  candidate run, to prevent metric-shopping. Owner: evaluation owner.
