# HMAC-SHA-256 SDK/JNI review

## Verdict

**PASS for the requested C++ SDK/JNI correctness scope.** The reviewed range is
`origin/cursor/native-strings-on-sdk-6d81...1fabd18`. No incorrect HMAC result,
missing long-key hash, input-length overflow, or JNI resource leak was found.
This review therefore changes documentation only; no compiler, SDK source, or
test code was changed and no bug was fixed.

This verdict is not a product-readiness decision. The API remains a
review-stage SDK surface, not a shipped product SDK.

## Findings

### RFC 2104 and the in-tree SHA-256

- The implementation forms a zero-padded 64-byte key block. A key longer than
  the SHA-256 block is first reduced with the existing in-tree
  `calc_sha_256`; the 32-byte digest occupies the start of the already-zeroed
  key block.
- It XORs that block with RFC 2104's `0x36` and `0x5c` pads, computes
  `SHA256(ipad || message)`, then computes
  `SHA256(opad || inner_digest)` with the in-tree streaming API.
- Key hashing is limited to `(2^64 - 1) / 8` bytes, and the inner stream checks
  `message.size <= (2^64 - 1) / 8 - 64`. Both input sizes must also fit
  `size_t`. These checks prevent the in-tree SHA-256 bit-length counter from
  wrapping.
- The reviewed diff does not modify the vendored SHA-256 implementation or add
  a build dependency.

### Vectors and regressions

The generated-library verifier passed all four documented 256-bit vectors:

1. BoringSSL/OpenSSL empty-key vector;
2. RFC 4231 case 2 (short key);
3. RFC 4231 case 6 (131-byte key, exercising hash-key-first);
4. Wycheproof HMAC-SHA-256 tcId 1 (empty message).

The expected tags were also cross-checked with Python's independent
`hashlib`/`hmac` implementation: **4/4 passed**. The same generated-library run
passed the two fixed SHA-256 vectors, 9 comparisons with JDK
`MessageDigest`, the existing equality cases, and the existing NativeStrings
checks.

Sources cited by the reviewed branch:

- <https://www.rfc-editor.org/rfc/rfc2104.html>
- <https://www.rfc-editor.org/rfc/rfc4231.html>
- <https://boringssl.googlesource.com/boringssl/+/fd49993c3b94aef54669e095f1e6c417efb202aa/crypto/hmac_extra/hmac_tests.txt>
- <https://boringssl.googlesource.com/boringssl/+/b19efcc1cf14d73bb4ce3fae62dae624aaec437f/third_party/wycheproof_testvectors/hmac_sha256_test.txt>

### JNI contracts and ownership

- `NativePrimitives.hmacSha256` rejects a null key or message with
  `NullPointerException`; the native entry point also checks both arguments.
- JNI input arrays are copied with `GetByteArrayRegion` into `unique_ptr`
  storage. No pinned JNI array or UTF buffer needs a matching release, and the
  owning copies are released on every return path.
- Temporary exception-class references are deleted, and a result array is
  deleted if `SetByteArrayRegion` raises an exception.
- The generated JAR ran under `-Xcheck:jni` without a JNI warning or error.

### C ABI

The header and generated shared library expose
`no_sdk_hmac_sha256_v1(no_sdk_bytes_v1 key, no_sdk_bytes_v1 message,
no_sdk_mut_bytes_v1 output_32)`. An in-process `ctypes` probe against the
generated GCC library passed **11/11 checks**: ABI version; empty key/message,
RFC 4231 cases 2 and 6, and the Wycheproof empty-message result; null key,
message, and output rejection; short-output rejection; key-length overflow;
and inner-stream length overflow.

`nm -D --defined-only` confirmed these relevant exported symbols:

```text
JNI_OnLoad
no_sdk_abi_version_v1
no_sdk_equal_constant_time_v1
no_sdk_hmac_sha256_v1
no_sdk_sha256_v1
```

## Commands re-run

Environment: OpenJDK 21.0.10 (Java source/target 8), GCC/G++ 13.3.0,
CMake 3.28.3, Linux x86-64. Krakatau2 was built from
`Storyyeller/Krakatau` as required by the repository test workflow.

Focused native/JNI integration:

```sh
CC=gcc CXX=g++ ./gradlew :obfuscator:test \
  --tests by.radioegor146.sdk.NativePrimitivesIntegrationTest \
  --no-build-cache --rerun-tasks --info
```

Result: **PASS, 1/1 JUnit test, 0 failed, 0 skipped**. Its verifier reported
2 SHA-256 vectors, 4 HMAC-SHA-256 vectors, 9 `MessageDigest` cases, 5 equality
cases, 5 string vectors, and 4 concatenations. It also checked the Java null
contracts and ran the generated JAR with `-Xcheck:jni`.

Full SDK/module suite:

```sh
PATH="/tmp/Krakatau/target/release:$PATH" CC=gcc CXX=g++ ./gradlew \
  :sdk:test :obfuscator:test --no-build-cache --rerun-tasks --info
```

Result: **PASS, 13/13 tests, 0 failed, 0 skipped**: 8 generated fixture tests,
2 `StringPoolTest` tests, 2 `ClassMethodListTest` tests, and 1 SDK integration
test. `:sdk:test` reported `NO-SOURCE`.

Assembly:

```sh
./gradlew :sdk:jar :obfuscator:assemble --no-build-cache --rerun-tasks
```

Result: **PASS** (`BUILD SUCCESSFUL`; no tests are part of this command).

Independent vector cross-check:

```sh
python3  # hashlib/hmac check of the four documented key/message/tag triples
```

Result: **PASS, 4/4 vectors**.

Generated-library ABI inspection:

```sh
nm -D --defined-only <generated-libnative_library.so> | \
  awk '{print $3}' | LC_ALL=C sort
```

Result: **PASS**; `no_sdk_hmac_sha256_v1` and the existing SHA-256, equality,
ABI-version, and `JNI_OnLoad` symbols were present. The supplemental Python
`ctypes` C ABI probe passed **11/11 checks**.

No HMAC benchmark was run, and this review makes no HMAC, NativeStrings, or
HotSpot performance claim.
