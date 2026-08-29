# Native SDK v1 status

## Surface

Java 8 class `by.radioegor146.sdk.NativePrimitives`:

- `static int abiVersion()`
- `static byte[] sha256(byte[] input)`
- `static byte[] hmacSha256(byte[] key, byte[] message)`
- `static boolean constantTimeEquals(byte[] left, byte[] right)`

Java 8 class `by.radioegor146.sdk.NativeStrings`:

- `static int length(String value)`
- `static int hashCode(String value)`
- `static String concat(String left, String right)`

Null array arguments throw `NullPointerException`. HMAC-SHA-256 follows RFC
2104 and accepts empty keys and messages. Equality content work has no
data-dependent exit for equal-length arrays; a length mismatch returns `false`
before content comparison. V1 exposes no native allocation, free, raw address,
or long-lived native context API. String operations use Java-compatible UTF-16
code-unit length, hash, and concatenation semantics.

The generated class initializer invokes the generated `LoaderUnpack` or
`LoaderPlain` class. `JNI_OnLoad` registers the private primitive and string JNI
entry points with `RegisterNatives`. The primitive core also exports the C ABI symbols
`no_sdk_abi_version_v1`, `no_sdk_sha256_v1`,
`no_sdk_hmac_sha256_v1`, and `no_sdk_equal_constant_time_v1`.

## Dependency and license

SHA-256 and HMAC-SHA-256 use `amosnier/sha-2` revision
`565f65009bdd98267361b17d50cddd7c9beb3e6c`. It is available under Zero-Clause
BSD or Unlicense. The complete license and upstream checksums are retained
under `sources/sdk/third_party`. SDK integration code follows the repository's
GPL-3.0-only license. No GPL/LGPL third-party dependency was added.

## HMAC-SHA-256 vectors

The generated-library verifier records and checks these full 256-bit published
vectors:

| Coverage | Source | Key | Message | Expected tag |
| --- | --- | --- | --- | --- |
| Empty key | BoringSSL `crypto/hmac_extra/hmac_tests.txt`, additional OpenSSL test | empty | ASCII `My test data` | `2274b195d90ce8e03406f4b526a47e0787a88a65479938f1a5baa3ce0f079776` |
| Short key | RFC 4231 test case 2 | ASCII `Jefe` | ASCII `what do ya want for nothing?` | `5bdcc146bf60754e6a042426089575c75a003f089d2739839dec58b964ec3843` |
| Key longer than SHA-256 block | RFC 4231 test case 6 | 131 bytes of `aa` | ASCII `Test Using Larger Than Block-Size Key - Hash Key First` | `60e431591ee0b67f0d8a26aacbf5b77f8e0bc6213728c5140546040f0ee37f54` |
| Empty message | Project Wycheproof HMAC-SHA-256 tcId 1, generator 0.8rc21 | `1e225cafb90339bba1b24076d4206c3e79c355805d851682bc818baa4f5a7779` | empty | `b175b57d89ea6cb606fb3363f2538abd73a4c00b4a1386905bac809004cf1933` |

Sources:

- <https://www.rfc-editor.org/rfc/rfc4231.html>
- <https://boringssl.googlesource.com/boringssl/+/fd49993c3b94aef54669e095f1e6c417efb202aa/crypto/hmac_extra/hmac_tests.txt>
- <https://boringssl.googlesource.com/boringssl/+/b19efcc1cf14d73bb4ce3fae62dae624aaec437f/third_party/wycheproof_testvectors/hmac_sha256_test.txt>

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

Result on the HMAC-SHA-256 SDK branch: **PASS** (`BUILD SUCCESSFUL`, test
completed in 2544 ms in the final full-module run). The test:

1. generates the output JAR and C++ tree;
2. confirms the primitive, NativeStrings, and SHA-256 sources are listed by
   generated CMake;
3. configures and links `libnative_library.so` with CMake and G++;
4. injects it as `native0/x64-linux.so` for `LoaderUnpack`;
5. runs the output JAR with `-Xcheck:jni`;
6. verifies the empty and `abc` SHA-256 vectors, 4 published HMAC-SHA-256
   vectors, 9 input sizes against `MessageDigest`, 5 equality cases, ABI
   version 1, and null contracts;
7. verifies native string length and hash for 5 UTF-16 vectors, 4
   concatenations, and string null contracts;
8. runs the minimal Java/NativeStrings string measurement and rejects unequal
   checksums.

Verifier output:

```text
NativePrimitivesVerifier: PASS (2 FIPS vectors, 4 HMAC-SHA-256 vectors, 9 MessageDigest cases, 5 equality cases, 5 string vectors, 4 concatenations)
```

### Local NativeStrings measurement

This branch does not copy the full benchmark harness from PR #10. The
integration test instead runs one dependency-free diagnostic workload in
separate Java and NativeStrings processes. Each sample performs 127 calls of
256 UTF-16 concat/length/hash iterations. Both modes produced checksum
`231461089`; 5 warmups and 10 measured iterations were used. The samples below
are from one local process pair in the final full-module run, not an aggregate
or a portable performance result. No HMAC performance measurement was made.

Unit: nanoseconds per complete sample.

| Mode | Raw samples | Median |
| --- | --- | ---: |
| Java | 3808864, 2541130, 2670761, 5375462, 5923559, 5223219, 5778503, 5265139, 5718555, 5320343 | 5292741 |
| SDK `NativeStrings` | 23952281, 21693655, 20047868, 15871892, 15751482, 16917534, 15799645, 15869699, 15846734, 20719102 | 16394713 |

In this run, `NativeStrings` had the higher median (16,394,713 ns versus
5,292,741 ns for Java). This is local diagnostic evidence, not a portable
performance claim. The snippet-transpiled JNI comparison was not re-run because
its harness lives in PR #10/#27 and is intentionally not duplicated here.

Assembly command:

```text
./gradlew :sdk:jar :obfuscator:assemble --no-build-cache --rerun-tasks
```

Result: **PASS** (`BUILD SUCCESSFUL`).

Full module test command:

```text
PATH="<Krakatau target/release>:$PATH" CC=gcc CXX=g++ ./gradlew \
  :sdk:test :obfuscator:test --no-build-cache --rerun-tasks --info
```

Result: **PASS**, 13 JUnit/dynamic tests (13 passed, 0 failed, 0 skipped).
This count comprises 8 generated fixture tests, 4 existing focused unit tests,
and 1 native SDK integration test. `:sdk:test` reported `NO-SOURCE`. Krakatau2
was built from `Storyyeller/Krakatau` following the repository CI setup because
the initial environment did not include `krak2`.

Generated-library symbol inspection found:

```text
JNI_OnLoad
no_sdk_abi_version_v1
no_sdk_equal_constant_time_v1
no_sdk_hmac_sha256_v1
no_sdk_sha256_v1
```

Zig execution was not run because `zig` is not installed in this environment.
The Zig builder recursively selects generated `.cpp` files; all of
`sdk/native_primitives.cpp`, `sdk/native_strings.cpp`, and
`sdk/third_party/sha-2/sha-256.cpp` are emitted under that selected tree.
