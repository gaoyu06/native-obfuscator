# Live-mix blinded-reader scores

Artifact count: **N=1**

The blinded reconstruction was committed as `893a6b6` before opening
`published.jar`, `run.md`, or the builder source. Scores below compare the
committed recovery against `src/example/Math.java`; they are algorithm scores,
not sample-I/O scores.

| Method | Score | Comparison |
| --- | --- | --- |
| `add(int, int)` | **full** | Exact match: `a + b`. |
| `sumTo(int)` | **full** | Exact loop and bounds: initialize zero, add each `i` while `i < n`, return the sum. |
| `subMul(int, int)` | **full** | Exact match: `(a - b) * b`. |
| `mix(int, int)` | **full** | Exact constants, shifts, xor/add/multiply order, unsigned shifts, and final `a & b` xor. |

Live-subject validity: **valid** — the artifact contains the kernel as a real
input-dependent arithmetic instruction sequence, not a constant-return stub.

