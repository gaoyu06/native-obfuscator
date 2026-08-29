# AES-256-GCM SDK review

Review date: 2026-08-29

Reviewed change: `origin/cursor/sdk-hmac-sha256-6d81..f11dbbd`, with the
review fix in `4f24179`.

## Verdict

**PASS WITH ONE CORRECTNESS FIX for the fixed 32-byte key / 12-byte nonce /
16-byte tag profile.** The SP 800-38D composition, AES-256 adaptation,
authenticate-before-decrypt ordering, fixed-work tag comparison, JNI
registration, Java exceptions, and C ABI behavior are consistent with the
documented profile after the output-length fix below.

This is not approval to ship a product SDK. The implementation uses
tiny-AES-c table lookups and therefore is not a side-channel-hardened AES
implementation. It also relies on the caller to ensure nonce uniqueness.

Compiler code changed by this review: **yes**,
`sources/sdk/native_primitives.cpp` only.

## Composition and profile findings

- The vendored AES subset retains the AES-256 key expansion, 14 encryption
  rounds, SubBytes, ShiftRows, MixColumns, and AddRoundKey behavior from
  `kokke/tiny-AES-c` revision
  `23856752fbd139da0b8ca6e471a13d5bcc99a08d`. The three upstream SHA-256
  values recorded in `sources/sdk/third_party/README.md` matched bytes fetched
  from that revision.
- For the required 96-bit nonce profile, `J0` is `nonce || 0x00000001`.
  Counter-mode encryption starts with `inc32(J0)`, and only the low 32 bits
  are incremented.
- GHASH processes padded AAD followed by padded ciphertext, then the two
  64-bit big-endian bit lengths. The tag is
  `AES_K(J0) XOR GHASH(H, A, C)`, where `H = AES_K(0^128)`.
- The C ABI enforces a 32-byte key, 12-byte nonce, full 16-byte tag, the
  SP 800-38D text limit of `2^39 - 256` bits, and 64-bit GHASH length-field
  bounds.
- All three cited vectors matched the official NIST CAVP
  `gcmEncryptExtIV256.rsp` archive byte-for-byte: parameter tuples
  `(PTlen, AADlen) = (0, 0), (128, 0), (408, 160)`, each with
  `Keylen=256`, `IVlen=96`, `Taglen=128`, and `Count=0`.

## Authentication and plaintext release

- Decrypt computes the expected tag over AAD and ciphertext before calling
  the counter-mode decrypt routine.
- Tag comparison always accumulates differences across all 16 tag bytes and
  has no tag-content-dependent early return. The comparison is constant-work
  for this fixed-length tag.
- Counter-mode decryption runs only when that comparison succeeds. A direct C
  ABI test filled the output with `0xa5`, changed a tag byte, received
  `NO_SDK_AUTHENTICATION_FAILED_V1`, and confirmed every output byte remained
  `0xa5`.
- The Java facade converts authentication failure to
  `AEADBadTagException`; no plaintext array is returned.

## JNI and C ABI findings

- Java null arrays are rejected with `NullPointerException`; invalid key,
  nonce, or ciphertext/tag lengths use `IllegalArgumentException`; failed
  authentication uses `AEADBadTagException`.
- JNI array reads use copied native buffers rather than pinned Java arrays.
  Native allocations use RAII, copied keys and the AES context are cleared,
  and the temporary exception and registration class references are deleted.
  No JNI resource leak was found.
- The generated `c_api.h` compiles as strict C11. The generated shared library
  exports both `no_sdk_aes_256_gcm_encrypt_v1` and
  `no_sdk_aes_256_gcm_decrypt_v1`, together with the inherited v1 symbols.
- The generated CMake cache records `/usr/bin/gcc` and `/usr/bin/g++`.

## Correctness bug fixed

The encrypt C ABI previously checked that `plaintext.size` fit `size_t`, but
did not check that `plaintext.size + 16` fit `size_t`. On a 32-bit target a
plaintext length near `SIZE_MAX` could pass validation even though the
ciphertext-plus-tag object and final tag pointer were not representable.

Commit `4f24179` now returns `NO_SDK_SIZE_OVERFLOW_V1` when the complete output
length is not representable as `size_t`. The Java path was already protected
by its stricter `jsize - 16` limit.

## Commands re-run and actual results

Toolchain: OpenJDK 21.0.10 (Java source/target 8), GCC/G++ 13.3.0, CMake
3.28.3, Gradle 9.3.1, Linux x86-64. Krakatau2 was built from the current
`Storyyeller/Krakatau` source, following the repository CI setup.

```text
PATH="/tmp/Krakatau-native-obfuscator-review/target/release:$PATH" \
  CC=gcc CXX=g++ ./gradlew :sdk:test :obfuscator:test \
  --no-build-cache --rerun-tasks --info
```

Result: **PASS, 13/13 tests** (13 passed, 0 failed, 0 skipped):

- 8 generated fixture tests;
- 2 `StringPoolTest` tests;
- 2 `ClassMethodListTest` tests;
- 1 `NativePrimitivesIntegrationTest`.

`:sdk:test` was `NO-SOURCE`; the 13 tests are from `:obfuscator:test`. The
integration test itself passed **1/1** and ran the generated JAR under
`-Xcheck:jni`. Its verifier reported:

```text
NativePrimitivesVerifier: PASS (2 FIPS vectors, 4 HMAC-SHA-256 vectors,
3 NIST AES-256-GCM vectors, 4 AES-GCM authentication failures,
5 AES-GCM length checks, 8 AES-GCM null checks, 9 MessageDigest cases,
5 equality cases, 5 string vectors, 4 concatenations)
```

The inherited Java and NativeStrings diagnostic processes produced the same
checksum, `231461089`.

```text
CC=gcc CXX=g++ ./gradlew :sdk:jar :obfuscator:assemble \
  --no-build-cache --rerun-tasks
```

Result: **PASS**, 8/8 actionable tasks executed.

```text
gcc -std=c11 -Wall -Wextra -Werror -pedantic-errors -fsyntax-only \
  -include obfuscator/src/main/resources/sources/sdk/c_api.h -x c /dev/null
```

Result: **PASS**.

An inline C11 harness was compiled against the generated shared library and
reported **4/4 checks passed**: CAVP encryption, CAVP decryption, tampered-tag
failure with untouched output, and invalid-key-length status.

The official NIST archive was fetched and parsed in memory with Python 3.
Result: **3/3 selected records matched**; downloaded archive SHA-256
`f9fc479e134cde2980b3bb7cddbcb567b2cd96fd753835243ed067699f26a023`.

## Regression and residual-security notes

- The full suite retained 2 SHA-256 known vectors, 9 JDK `MessageDigest`
  comparisons, 4 HMAC-SHA-256 published vectors, 5 equality cases, 5
  NativeStrings vectors, and 4 concatenations. No HMAC, SHA-256, or
  NativeStrings regression was observed.
- The 16-byte GCM tag comparison is constant-work; no non-constant-time tag
  comparison was found.
- tiny-AES-c indexes an S-box with secret-dependent state. This can leak
  through cache timing on shared general-purpose CPUs, so this implementation
  must not be described as constant-time or side-channel hardened.
- The API does not generate, persist, or detect nonce reuse. Reusing a nonce
  with the same key is forbidden and is documented in the Java API and SDK
  status document; callers must enforce uniqueness.
- No AES-GCM benchmark was run, and this review makes no AES performance
  claim.
