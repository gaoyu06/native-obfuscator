# Native SDK v1 status

## Surface

Java 8 class `by.radioegor146.sdk.NativePrimitives`:

- `static int abiVersion()`
- `static byte[] sha256(byte[] input)`
- `static boolean constantTimeEquals(byte[] left, byte[] right)`

Java 8 class `by.radioegor146.sdk.NativeStrings`:

- `static int length(String value)`
- `static int hashCode(String value)`
- `static String concat(String left, String right)`

Null array arguments throw `NullPointerException`. Equality content work has no
data-dependent exit for equal-length arrays; a length mismatch returns
`false` before content comparison. V1 exposes no native allocation, free, raw
address, or long-lived native context API. String operations use Java-compatible
UTF-16 code-unit length, hash, and concatenation semantics.

The generated class initializer invokes the generated `LoaderUnpack` or
`LoaderPlain` class. `JNI_OnLoad` registers the private primitive and string JNI
entry points with `RegisterNatives`. The primitive core also exports the C ABI symbols
`no_sdk_abi_version_v1`, `no_sdk_sha256_v1`, and
`no_sdk_equal_constant_time_v1`.

## Dependency and license

SHA-256 uses `amosnier/sha-2` revision
`565f65009bdd98267361b17d50cddd7c9beb3e6c`. It is available under Zero-Clause
BSD or Unlicense. The complete license and upstream checksums are retained
under `sources/sdk/third_party`. SDK integration code follows the repository's
GPL-3.0-only license. No GPL/LGPL third-party dependency was added.

## Verification

Environment:

- OpenJDK 21.0.10, compiling Java sources with source/target 8
- CMake 3.28.3
- GCC/G++ 13.3.0
- Linux x86-64

Native integration command:

```text
CC=gcc CXX=g++ ./gradlew :obfuscator:test \
  --tests by.radioegor146.sdk.NativePrimitivesIntegrationTest \
  --no-build-cache --rerun-tasks --info
```

Result on the NativeStrings-on-SDK branch: **PASS** (`BUILD SUCCESSFUL`, test
completed in 2792 ms). The test:

1. generates the output JAR and C++ tree;
2. confirms the primitive, NativeStrings, and SHA-256 sources are listed by
   generated CMake;
3. configures and links `libnative_library.so` with CMake and G++;
4. injects it as `native0/x64-linux.so` for `LoaderUnpack`;
5. runs the output JAR with `-Xcheck:jni`;
6. verifies the empty and `abc` SHA-256 vectors, 9 input sizes against
   `MessageDigest`, 5 equality cases, ABI version 1, and null contracts;
7. verifies native string length and hash for 5 UTF-16 vectors, 4
   concatenations, and string null contracts;
8. runs the minimal Java/NativeStrings string measurement and rejects unequal
   checksums.

Verifier output:

```text
NativePrimitivesVerifier: PASS (2 FIPS vectors, 9 MessageDigest cases, 5 equality cases, 5 string vectors, 4 concatenations)
```

### Local NativeStrings measurement

This branch does not copy the full benchmark harness from PR #10. The
integration test instead runs one dependency-free diagnostic workload in
separate Java and NativeStrings processes. Each sample performs 127 calls of
256 UTF-16 concat/length/hash iterations. Both modes produced checksum
`231461089`; 5 warmups and 10 measured iterations were used.

Unit: nanoseconds per complete sample.

| Mode | Raw samples | Median |
| --- | --- | ---: |
| Java | 5141571, 4953808, 4740505, 5415515, 5070776, 5479848, 5514323, 5411874, 5227697, 5225557 | 5226627 |
| SDK `NativeStrings` | 17949770, 18011104, 18384960, 16461922, 16669859, 16825945, 16250059, 16588246, 16011566, 17851418 | 16747902 |

In this run, `NativeStrings` was about 3.20x slower than Java for this exact
workload. This is local diagnostic evidence, not a portable performance claim.
The snippet-transpiled JNI comparison was not re-run because its harness lives
in PR #10/#27 and is intentionally not duplicated on this branch.

Assembly command:

```text
./gradlew :sdk:jar :obfuscator:assemble --no-build-cache --rerun-tasks
```

Result: **PASS** (`BUILD SUCCESSFUL`).

The first integration invocation stopped before CMake because Gradle 9.3.1
requires an explicit JUnit Platform launcher. Adding launcher 1.4.2 to match the
existing JUnit 5.4.2 runtime resolved that test-runner failure; all subsequent
native integration invocations passed.

Generated-library symbol inspection found:

```text
JNI_OnLoad
no_sdk_abi_version_v1
no_sdk_equal_constant_time_v1
no_sdk_sha256_v1
```

Zig execution was not run because `zig` is not installed in this environment.
The Zig builder recursively selects generated `.cpp` files; all of
`sdk/native_primitives.cpp`, `sdk/native_strings.cpp`, and
`sdk/third_party/sha-2/sha-256.cpp` are emitted under that selected tree.
