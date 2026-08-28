# Gemini draft review notes

Reviewed as untrusted input:

- `cursor/docs-sdk-benchmarks-6d81` at `f82c4ba`
  - `docs/research/cpp-sdk-options.md`
  - `docs/research/benchmark-methodology.md`
  - `docs/research/sdk-api-sketch.md`
- `cursor/docs-java-cpp-paths-6d81` at `fa9abde`
  - `docs/research/java-to-cpp-paths.md`
  - `docs/research/java-to-cpp-paths-matrix.md`

No benchmark number or confidence percentage from those drafts is adopted.

| Draft | Accept | Revise | Reject |
|---|---|---|---|
| `cpp-sdk-options.md` | Separate high-level SDK operations from opcode translation; JNI is the broad baseline; one useful call should cross the boundary once. | FFM is a separate JDK 22+ standard adapter, not a JDK 21 production target. JNI and FFM both need modern native-access operations documented. String/array access uses a measured copy/direct/critical policy. | Unmeasured nanosecond/speedup ranges; “JDK 24 hard error” (default is warning/allow); critical access as guaranteed zero-copy; automatic standard-library substitution; unsupported licensing/security conclusions. |
| `benchmark-methodology.md` | JMH, correctness checks, forks/warmup, plain/current/direct variants, native isolation, and end-to-end transpile/compile/run are appropriate components. | Workloads, iteration policy, confidence level, and regression thresholds must be chosen from pilot evidence and recorded, not declared universal. `Calc` is a compatibility workload only. | Fabricated example result table; `Main.mainWithoutExit` (no such repository method); claims that ordinary non-volatile `count++` necessarily adds memory barriers; fixed local-reference limits presented as universal. |
| `sdk-api-sketch.md` | Java facade, JNI adapter, C++ core, and explicit build inputs are the right layers. | Use a versioned C ABI, registered private natives, checked statuses, conservative copying, exact Java error semantics, and a reviewed provider. Start smaller. | Placeholder crypto as implementation, `crypto_verify16` as arbitrary-length equality, SHA-512 context standing in for SHA-256, raw address allocation/free, immediate AEAD surface, and the claim that `native` declarations provide a pure-Java fallback. |
| `java-to-cpp-paths.md` | Replace string templates with a typed IR; preserve a stock-JVM/JAR integration path; distinguish pure native regions from JVM-dependent operations; consider multiple backends. | Begin with verifier-aware typed CFG/block parameters and add SSA selectively. JNI is explicit for Java operations, not “every opcode.” Compatibility requires feature fixtures and preserving class versions. | Performance multipliers, implementation timelines, “100%” JDK-readiness, analysis/obfuscation strength rankings, condy preprocessing (not present), guaranteed SIMD gains, and claims based on unshown assembly/benchmarks. |
| `java-to-cpp-paths-matrix.md` | A decision matrix and explicit trade-offs are useful. Direct C++, SDK, and interpreter concerns should be separable. | The optional interpreter follows the shared IR/direct-backend slice. External IRs and FFM remain evidence-gathering options rather than assumed roadmap winners. | Numeric suitability scores, production-ready labels without tests, speed ranges, automatic crypto recognition, critical pinning as the default, and “full” JDK support inferred from C ABI/parser availability. |

The drafts also overstate `HOTSPOT` portability. The current native runtime
looks up internal `MethodHandleNatives.linkCallSite`, while the repository's
`STD_JAVA` mode requires separate evidence. The current CI matrix is
configuration, not proof that either runtime path supports every feature of
JDK 8/11/17/21/25.

The accepted ideas were re-derived against `master`; the revised architecture,
SDK, compatibility policy, benchmark gates, and evaluation methodology are in
the other documents in this directory.
