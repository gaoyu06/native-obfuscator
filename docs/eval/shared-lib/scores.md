# Shared-library readability scores

## Result

| Method | Score | Source comparison |
| --- | --- | --- |
| `add(int, int)` | Full | Exact expression and argument order recovered. |
| `sumTo(int)` | Full | Exact initial values, loop condition, increment, and accumulation recovered. |
| `mix(int, int)` | Full | Exact constants, initialization, loop condition, operation order, shift kinds, multiply, xor, and rotate distance recovered. |

Overall score: **full**. In particular, the interesting `mix` kernel was
recovered from the shared library alone, rather than inferred only from its
name or native wrapper.

## Hypothesis

- H0: **cannot recover critical logic** from the published shared library
  alone.
- Observation: all critical `mix` logic was reconstructed before viewing the
  Java fixture, and the reconstruction matches the source exactly.
- Decision: **reject H0 for this fixture**.

This is an N=1 compiler/readability evaluation. It demonstrates one complete
recovery and does not establish a general recovery rate for other programs,
compiler settings, architectures, or builds.

## Contamination record

- Java fixture contents were not viewed until after `recovery.md` was written.
- The fixture was materialized and compiled as a black box before source
  comparison.
- No generated C++ source was opened or used.
- The reader knew the requested method names `add`, `sumTo`, and `mix`; the
  whitelist and shared-library registration data also exposed their
  signatures.
- Oracle stdout was recorded before binary reading and later used to check the
  independently decoded pseudocode. It exposed sample input/output pairs, but
  not the operation sequence.
- The reader input was only `published.so`; the private compiler workspace was
  not used as reader evidence.
