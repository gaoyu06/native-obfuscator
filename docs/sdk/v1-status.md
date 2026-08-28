# Native primitives SDK v1 status

## Surface

Java 8 class `by.radioegor146.sdk.NativePrimitives`:

- `static int abiVersion()`
- `static byte[] sha256(byte[] input)`
- `static boolean constantTimeEquals(byte[] left, byte[] right)`

Null array arguments throw `NullPointerException`. Equality content work has no
data-dependent exit for equal-length arrays; a length mismatch returns
`false` before content comparison. V1 exposes no native allocation, free, raw
address, or long-lived native context API.

The generated class initializer invokes the generated `LoaderUnpack` or
`LoaderPlain` class. `JNI_OnLoad` registers the three private JNI entry points
with `RegisterNatives`. The core also exports the C ABI symbols
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

Result: **PASS** (`BUILD SUCCESSFUL`, latest test completed in 1568 ms). The test:

1. generates the output JAR and C++ tree;
2. confirms the SDK and SHA-256 sources are listed by generated CMake;
3. configures and links `libnative_library.so` with CMake and G++;
4. injects it as `native0/x64-linux.so` for `LoaderUnpack`;
5. runs the output JAR with `-Xcheck:jni`;
6. verifies the empty and `abc` SHA-256 vectors, 9 input sizes against
   `MessageDigest`, 5 equality cases, ABI version 1, and null contracts.

Verifier output:

```text
NativePrimitivesVerifier: PASS (2 FIPS vectors, 9 MessageDigest cases, 5 equality cases)
```

Assembly command:

```text
./gradlew :sdk:jar :obfuscator:assemble --no-build-cache
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
The Zig builder recursively selects generated `.cpp` files; both
`sdk/native_primitives.cpp` and `sdk/third_party/sha-2/sha-256.cpp` are emitted
under that selected tree.
