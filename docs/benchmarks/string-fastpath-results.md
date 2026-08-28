# Native string fast-path results

Measured on 2026-08-28 with:

- Linux 6.12.94+, x86_64, glibc 2.39
- OpenJDK 21.0.10 (Java 8 target bytecode)
- GCC 13.3.0
- CMake 3.28.3
- command: `./gradlew bench`
- 5 warmup iterations and 10 measured iterations

The workload is 127 calls of 256 iterations per sample. Each iteration
concatenates two UTF-16 strings, reads the resulting length, and computes the
Java-compatible hash. All modes produced checksum `231461089`.

## Raw timings

Unit: nanoseconds per complete sample.

| Mode | Raw samples | Median | Mean |
| --- | --- | ---: | ---: |
| Plain Java | 3158004, 2555951, 2174443, 2165137, 2192952, 2172326, 2178139, 2158742, 2179292, 2177140 | 2177639.5 | 2311212.6 |
| SDK `NativeStrings` | 9216666, 9071535, 9070728, 9060235, 9068544, 9112144, 9130169, 9201089, 9126001, 9088190 | 9100167.0 | 9114530.1 |
| Existing snippet-transpiled JNI | 16834852, 16708609, 16782814, 16666539, 16672703, 16752689, 16870576, 16784444, 16712263, 16725057 | 16738873.0 | 16751054.6 |

The SDK native median was 45.6% lower than the existing snippet-transpiled
JNI median (1.84x throughput for this workload). It remained 4.18x slower than
plain Java. These are local diagnostic measurements from one process, not a
release threshold.

## Verification

`./gradlew :obfuscator:test --tests by.radioegor146.sdk.NativePrimitivesIntegrationTest`
passed after compiling and loading the generated native library with
`-Xcheck:jni`. The verifier compares native length and hash against
`String.length()` and `String.hashCode()` for empty, ASCII, BMP Unicode,
surrogate-pair, and embedded-NUL inputs, and checks four concatenation cases.
