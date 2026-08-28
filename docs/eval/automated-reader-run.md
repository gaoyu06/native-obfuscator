# Automated reader run: generated compiler artifacts

## Scope and protocol

This is a compiler/readability evaluation only. It does not evaluate or claim
software-analysis resistance.

The run follows
`origin/cursor/docs-interpreter-backend-6d81:docs/architecture/interpreter-eval-protocol.md`
and compares C++ generated from one Java 8 class by the direct C++ and
interpreter backends at the same compiler commit. H0 is that the reader recovers
the algorithm equally well from both artifacts.

This is an N=1 anecdotal run by GPT-5.6 Sol on 2026-08-28, not a statistically
valid study. The protocol itself contains the complete `DemoKernel.mix` source,
and repository setup inspection exposed the existing `add`/`sumTo` fixture and
the slice's opcode names before this pass. Consequently this run is not an
unaided or uncontaminated GPT-5.6-class measurement. The artifact-viewing order
below was preserved, but that ordering does not remove the prior exposure.

## Reader pass 1: interpreter artifact only

This text was fixed after reading only the generated interpreter-side
`DemoKernel_0.cpp`, `native_jvm_interp.cpp`, `native_jvm_interp.hpp`, and
`DemoKernel_0.hpp`, before opening the generated direct artifact or reopening
the fixture source:

> The artifact is hybrid. `add` and `sumTo` are opcode arrays executed by a
> shared stack interpreter, while `mix` fell back to method-specific direct C++.
>
> Decoding `add` gives: load arguments 0 and 1, add them with 32-bit wrapping,
> and return the result.
>
> Decoding `sumTo` gives: initialize accumulator local 1 and counter local 2 to
> zero; while counter is less than argument local 0, add the counter to the
> accumulator and increment the counter; return the accumulator. In pseudocode:
> `acc = 0; for (i = 0; i < limit; ++i) acc += i; return acc`.
>
> The direct fallback body for `mix` gives: initialize
> `acc = seed ^ -1640531527` (`0x9E3779B9`); repeat for `r` from zero while
> `r < rounds`: add `(acc << 6) + (unsigned(acc) >> 2)` to `acc`, XOR `acc`
> with `acc * -2048144777` (`0x85EBCA77`), rotate the result left by 13, and
> increment `r`; return `acc`. Arithmetic has Java `int`/32-bit behavior.

Scores are intentionally deferred until after the direct-artifact pass and
comparison with the Java source.
