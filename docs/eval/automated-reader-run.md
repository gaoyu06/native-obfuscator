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

## Reader pass 2: direct C++ artifact

This text was fixed after reading generated direct-side `DemoKernel_0.cpp` and
`DemoKernel_0.hpp`, still before reopening the Java fixture source:

> All three methods are method-specific C++ in this artifact. `add` returns the
> 32-bit sum of its two arguments.
>
> `sumTo` sets an accumulator and counter to zero, loops while the counter is
> less than the argument, adds the counter to the accumulator, increments the
> counter, and returns the accumulator: `0 + 1 + ... + (limit - 1)` for a
> positive limit, and zero when the initial loop condition is false.
>
> `mix` sets `acc = seed ^ -1640531527` (`0x9E3779B9`), then runs exactly
> `rounds` iterations when `rounds` is positive. Each iteration adds
> `(acc << 6) + (unsigned(acc) >> 2)`, XORs with
> `acc * -2048144777` (`0x85EBCA77`), and calls
> `Integer.rotateLeft(acc, 13)`. It returns the final 32-bit `acc`.

Scores remain deferred until the Java-source comparison.

## Source comparison and scores

After both recovery texts were fixed, the reader opened
`obfuscator/test_data/eval/automated-reader/DemoKernel.java`. The fixture confirms
the recovered `add`, `sumTo`, and `mix` algorithms, including both integer
constants, both shifts, multiply/XOR/add operations, the rotate distance, and
the `r < rounds` loop bound.

| Artifact | `add` | `sumTo` | `mix` | Overall |
|---|---|---|---|---|
| Interpreter-backend output | full | full | full | **full** |
| Direct-C++ output | full | full | full | **full** |

These are exact recovery classifications, not invented numeric scores. `mix`
is full in the interpreter-backend row because that method was visibly emitted
as direct C++ fallback: the slice cannot interpret its XOR, shift, multiply,
or invocation instructions. It is therefore not evidence about recovering an
interpreter-lowered `mix`. The genuinely paired interpreter-vs-direct methods
in this fixture are `add` and `sumTo`.

For this reader, the interpreter artifact was **harder** to read: recovering
`add` and `sumTo` required cross-referencing an opcode enum and executor, then
manually stepping byte arrays and little-endian operands. The direct artifact
expressed their data flow and branches as method-local statements. That
subjective effort difference did not produce an accuracy difference in this
run.

H0 is **not rejected**: both artifact recoveries scored full. N=1 supplies no
effect estimate, confidence interval, or statistical validity, and the prior
exposure described above is an additional confound. This run does not satisfy
a GPT-5.6-class unaided bar as a scientific result and makes no
"resistance" claim.

## Reproduction

Source revisions:

- interpreter compiler branch:
  `df83421fc33c8b3a315e325aafcc877b7a72c086`
- protocol branch:
  `0a6fc694451f07bd236d9bd4451a01e41ae90cab`
- executable fixture/whitelist revision:
  `23f8eb7`

Toolchain: OpenJDK/javac 21.0.10 targeting Java 8, Gradle 9.3.1, CMake
3.28.3, and GCC/G++ 13.3.0.

Commands used for the final successful generation and execution:

```bash
./gradlew :obfuscator:shadowJar

EVAL_ROOT=$(mktemp -d "/tmp/automated-reader-eval.XXXXXX")
mkdir -p "$EVAL_ROOT/classes"
javac --release 8 -d "$EVAL_ROOT/classes" \
  obfuscator/test_data/eval/automated-reader/DemoKernel.java \
  obfuscator/test_data/eval/automated-reader/Runner.java
jar --create --file "$EVAL_ROOT/input.jar" --main-class Runner \
  -C "$EVAL_ROOT/classes" .

java -jar obfuscator/build/libs/obfuscator.jar \
  --backend=cpp --plain-lib-name=native_library \
  -w obfuscator/test_data/eval/automated-reader/whitelist.txt \
  "$EVAL_ROOT/input.jar" "$EVAL_ROOT/direct"
java -jar obfuscator/build/libs/obfuscator.jar \
  --backend=interpreter --plain-lib-name=native_library \
  -w obfuscator/test_data/eval/automated-reader/whitelist.txt \
  "$EVAL_ROOT/input.jar" "$EVAL_ROOT/interpreter"

CC=gcc CXX=g++ cmake -S "$EVAL_ROOT/direct/cpp" \
  -B "$EVAL_ROOT/direct/cpp/build" -DCMAKE_BUILD_TYPE=Release
cmake --build "$EVAL_ROOT/direct/cpp/build" --config Release
CC=gcc CXX=g++ cmake -S "$EVAL_ROOT/interpreter/cpp" \
  -B "$EVAL_ROOT/interpreter/cpp/build" -DCMAKE_BUILD_TYPE=Release
cmake --build "$EVAL_ROOT/interpreter/cpp/build" --config Release

reference=$(java -cp "$EVAL_ROOT/classes" Runner)
direct=$(java \
  -Djava.library.path="$EVAL_ROOT/direct/cpp/build/build/lib" \
  -jar "$EVAL_ROOT/direct/input.jar")
interpreter=$(java \
  -Djava.library.path="$EVAL_ROOT/interpreter/cpp/build/build/lib" \
  -jar "$EVAL_ROOT/interpreter/input.jar")
test "$reference" = "$direct"
test "$reference" = "$interpreter"
```

The equality checks both exited zero and produced:

```text
reference == direct == interpreter
add(7,5)=12
sumTo(0)=0
sumTo(1)=0
sumTo(10)=45
sumTo(100)=4950
mix(0,0)=-1640531527
mix(0,1)=389078419
mix(1,4)=-1363050107
mix(MIN,3)=-2129079926
mix(0x12345678,16)=567676480
```

The final generated sources used by that successful run are committed at:

- `docs/eval/artifacts/direct/DemoKernel_0.cpp`
- `docs/eval/artifacts/interpreter/DemoKernel_0.cpp`
- `docs/eval/artifacts/interpreter/native_jvm_interp.cpp`
- `docs/eval/artifacts/interpreter/native_jvm_interp.hpp`

The two committed `DemoKernel_0.cpp` copies normalize only the generated files'
missing final newline; their content otherwise matches the executable build
inputs. The runtime files match byte-for-byte.

## Preflight corrections

An initial whitelist listed methods without the class entry, so the compiler
reported `Skipping DemoKernel`; those empty outputs were discarded. A later
preflight linked both libraries but exposed a fixture whitelist problem:
the compiler synthesized `<clinit>` after class filtering, while the exact
method whitelist omitted it, yielding a transformed class with an empty
non-native method. Adding the class and `<clinit>` entries produced the final
successful artifacts above. Regeneration added only the synthetic empty
`<clinit>` and registration scaffolding to each previously read translation
unit; the `add`, `sumTo`, and `mix` regions used for the recovery text did not
change.
