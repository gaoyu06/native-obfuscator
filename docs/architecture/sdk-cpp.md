# C++ SDK architecture

## Purpose and non-goals

The SDK supplies a small set of explicit, testable native operations that cross
the Java/native boundary once per useful operation. It is separate from the
general bytecode compiler:

- the compiler turns eligible Java control flow and arithmetic into C++;
- the SDK supplies reviewed C++ implementations of named hashing/encoding/
  cryptographic-style primitives;
- JNI and, later, FFM adapt Java calls to the same stable C ABI.

V1 is not a general native-memory API, a transparent replacement for JDK
providers, or an algorithm-recognition engine. It does not promise a speedup
before benchmark results exist.

## Design rules

1. **Explicit use.** Java calls an SDK API, or a later compiler intrinsic refers
   to an exact versioned SDK symbol. The compiler does not guess that arbitrary
   bytecode “looks like SHA-256.”
2. **C ABI at the seam.** C++ internals may evolve, but exported symbols use
   fixed-width C-compatible types, explicit capacities, and status returns.
   This lets JNI and FFM share one implementation.
3. **No exception or allocation ambiguity.** C++ exceptions never cross the C
   ABI. Caller-owned buffers and checked lengths are preferred. Java adapters
   map statuses to documented Java exceptions.
4. **Java semantics are explicit.** Null behavior, malformed encoding input,
   padding/alphabet policy, maximum lengths, overlap, output size, and
   constant-time scope are part of the API contract.
5. **No hand-written cryptography.** A security/legal review selects a pinned
   upstream implementation, update policy, and build configuration. The SDK
   wrapper is not itself evidence that the implementation is secure.
6. **Portable baseline first.** C++17, no CPU-specific instruction required.
   Optional optimized implementations are selected by tested runtime dispatch,
   never by producing a binary that crashes on the baseline CPU.
7. **Observable backend.** The Java API reports native availability, SDK ABI,
   implementation version, and capabilities. It never silently calls a Java
   fallback while reporting native execution.

## Proposed source and header layout

```text
sdk/
  include/native_obfuscator/sdk/
    export.h              # visibility and calling-convention macros
    version.h             # ABI version and capability identifiers
    status.h              # C ABI status codes
    c_api.h               # complete stable C ABI
    bytes.hpp             # internal C++17 byte spans, checked sizes
    hash.hpp              # C++ hash interface
    encoding.hpp          # C++ encoding interface
  src/
    c_api.cpp
    sha256.cpp            # adapter to approved implementation
    base64.cpp
    hex.cpp
    constant_time.cpp
  jni/
    jni_bridge.cpp
    jni_arrays.cpp
    jni_exceptions.cpp
  third_party/
    README.md              # provenance, license, pinned revision, update owner
    ...                    # only after human approval
```

Generated projects consume installed/copied SDK headers and sources as ordinary
build inputs. CMake and Zig manifests list each file explicitly; recursive
source discovery is avoided so an accidental file cannot enter a release.

## Stable C ABI sketch

Names include a major ABI suffix. Exact names and namespace are a human API
decision.

```c
/* c_api.h */
#include <stdint.h>

typedef enum no_sdk_status_v1 {
    NO_SDK_OK_V1 = 0,
    NO_SDK_NULL_V1 = 1,
    NO_SDK_INVALID_ARGUMENT_V1 = 2,
    NO_SDK_BUFFER_TOO_SMALL_V1 = 3,
    NO_SDK_INVALID_ENCODING_V1 = 4,
    NO_SDK_SIZE_OVERFLOW_V1 = 5,
    NO_SDK_UNAVAILABLE_V1 = 6,
    NO_SDK_INTERNAL_V1 = 7
} no_sdk_status_v1;

typedef struct no_sdk_bytes_v1 {
    const uint8_t *data;
    uint64_t size;
} no_sdk_bytes_v1;

typedef struct no_sdk_mut_bytes_v1 {
    uint8_t *data;
    uint64_t capacity;
} no_sdk_mut_bytes_v1;

uint32_t no_sdk_abi_version_v1(void);
uint64_t no_sdk_capabilities_v1(void);
const char *no_sdk_implementation_version_v1(void);

no_sdk_status_v1 no_sdk_sha256_v1(
    no_sdk_bytes_v1 input,
    no_sdk_mut_bytes_v1 output_32);

no_sdk_status_v1 no_sdk_base64_encoded_size_v1(
    uint64_t input_size,
    uint32_t flags,
    uint64_t *required);
no_sdk_status_v1 no_sdk_base64_encode_v1(
    no_sdk_bytes_v1 input,
    uint32_t flags,
    no_sdk_mut_bytes_v1 output,
    uint64_t *written);

no_sdk_status_v1 no_sdk_base64_decoded_max_size_v1(
    uint64_t input_size,
    uint64_t *required);
no_sdk_status_v1 no_sdk_base64_decode_v1(
    no_sdk_bytes_v1 input,
    uint32_t flags,
    no_sdk_mut_bytes_v1 output,
    uint64_t *written);

no_sdk_status_v1 no_sdk_hex_encode_v1(
    no_sdk_bytes_v1 input,
    uint32_t flags,
    no_sdk_mut_bytes_v1 output,
    uint64_t *written);
no_sdk_status_v1 no_sdk_hex_decode_v1(
    no_sdk_bytes_v1 input,
    uint32_t flags,
    no_sdk_mut_bytes_v1 output,
    uint64_t *written);

no_sdk_status_v1 no_sdk_equal_constant_time_v1(
    no_sdk_bytes_v1 left,
    no_sdk_bytes_v1 right,
    uint8_t *equal);
```

`export.h` defines visibility and the Windows calling convention consistently.
No STL type, `bool`, exception, compiler-specific class layout, or ownership of
returned heap memory crosses this boundary. The implementation-version string
is immutable process-lifetime storage.

The zero-length contract must state whether `{NULL, 0}` is accepted. Nonzero
length with null data is always invalid. Every conversion from `jint`, `jsize`,
`size_t`, or `uint64_t` is checked before allocation or pointer arithmetic.

### C++ convenience layer

`bytes.hpp`, `hash.hpp`, and `encoding.hpp` wrap the C ABI/internal
implementations for generated C++ without exporting C++ ABI:

```cpp
namespace native_obfuscator::sdk {
struct Bytes { const std::uint8_t* data; std::size_t size; };
struct MutableBytes { std::uint8_t* data; std::size_t size; };

Status sha256(Bytes input, MutableBytes output) noexcept;
Status base64_encode(Bytes input, Base64Options options,
                     MutableBytes output, std::size_t& written) noexcept;
Status base64_decode(Bytes input, Base64Options options,
                     MutableBytes output, std::size_t& written) noexcept;
}
```

The project uses a small C++17 span rather than requiring C++20 `std::span`.
Core functions perform no JNI calls and are independently fuzzable and
microbenchmarkable.

## Java API sketch

Package naming remains a human decision; the repository's current namespace
suggests `by.radioegor146.nativeobfuscator.sdk`.

```java
public final class NativeSdk {
    public static boolean isAvailable();
    public static int abiVersion();
    public static String implementationVersion();
    public static Set<Capability> capabilities();
    public static void requireAvailable();
}

public final class NativeHash {
    public static byte[] sha256(byte[] input);
}

public final class NativeEncoding {
    public static byte[] base64Encode(byte[] input, Base64Variant variant,
                                      boolean padding);
    public static byte[] base64Decode(byte[] input, Base64Variant variant,
                                      PaddingPolicy padding);
    public static String hexEncode(byte[] input, HexCase letterCase);
    public static byte[] hexDecode(CharSequence input);
}

public final class NativeBytes {
    /**
     * Content work is constant-time for equal-length inputs; lengths are not
     * secret and a length mismatch returns false.
     */
    public static boolean contentEqualsConstantTime(byte[] left, byte[] right);
}
```

Public methods are ordinary Java validation/availability wrappers around
package-private native methods. Null input throws `NullPointerException`;
malformed Base64/hex throws `IllegalArgumentException`; allocation failure
propagates `OutOfMemoryError`; an unavailable or ABI-mismatched library throws a
dedicated linkage exception. Exact messages are not stable unless explicitly
documented.

V1 has no raw address, `allocate/free`, mutable key-holder, callback, or
long-lived native context. A Java fallback, if wanted, is a separately selected
backend and reports itself as such.

## Which primitives come first

Implementation order is:

1. ABI/version/capability negotiation and error mapping.
2. Hex and strict RFC 4648 Base64 encode/decode. These validate buffer sizing,
   malformed input, and JNI/FFM parity without first taking cryptographic risk.
3. SHA-256 one-shot digest, using an approved implementation and published
   known-answer vectors.
4. Constant-time byte equality with a precise equal-length contract.

SHA-256 is chosen for interoperability and test-vector availability, not an
unmeasured performance advantage. Input sizes include empty, boundary, very
large, and allocation-failure cases.

Deferred beyond v1:

- AEAD/encryption until key ownership, nonce policy, authentication-failure
  behavior, zeroization limits, provider/FIPS requirements, and security review
  are approved;
- streaming digest state until lifecycle, concurrency, cleanup, and FFM arena
  ownership are designed;
- nonstandard hashes until there is a product use case and algorithm/version
  contract;
- string concatenation until the compiler string model is correct and
  benchmarks show value;
- raw native memory permanently unless a separate safety case is accepted.

## JNI adapter

### Binding and loading

- A small Java bridge owns the package-private `native` declarations.
- The existing loader (or a replacement) loads one library and checks ABI.
- `JNI_OnLoad` obtains the requested JNI environment, performs only
  failure-safe initialization, and uses `RegisterNatives` with explicit names
  and descriptors. Public API names are not coupled to exported
  `Java_package_Class_method` symbols.
- Initialization is idempotent and thread-safe. Failure leaves no partially
  initialized “available” state.
- JDK 24/25 documentation and tests cover `--enable-native-access` and
  `--illegal-native-access=deny`.

### Array policy

V1 uses checked copies as the conservative default:

1. validate null and `GetArrayLength`;
2. allocate bounded native storage or stream chunks;
3. copy with `GetByteArrayRegion`;
4. call the JNI-free C++ core;
5. allocate the exact Java result and use `SetByteArrayRegion`;
6. check for a pending exception after each JNI operation.

`GetPrimitiveArrayCritical` is not a generic zero-copy guarantee: an
implementation may copy, and the critical region constrains blocking and JNI
operations and can affect GC. It may be introduced only for a bounded,
non-blocking kernel after collector-aware measurements. No JNI call, allocation,
logging, lock wait, or exception construction occurs while a critical pointer
is held.

A later direct-`ByteBuffer` overload can avoid heap-array copying when the caller
chooses native memory. It validates directness, address, capacity, position/
limit semantics, read-only state, and lifetime.

### Error and reference discipline

- C status is translated after native buffers are released.
- Local reference scopes are bounded with `PushLocalFrame`/`PopLocalFrame` or
  equivalent explicit cleanup.
- No `JNIEnv*` or local reference is shared across threads.
- Native code checks pending exceptions and returns immediately rather than
  clearing application exceptions.
- All registration descriptors are unit-tested against the Java bridge.

## Compiler integration

Ordinary Java callers use the JNI adapter. A method compiled by the future IR
backend can recognize an exact SDK API owner/name/descriptor plus ABI version
and emit a direct call to the C++ core/C ABI, avoiding a callback through Java.
This is a declared intrinsic:

- the IR has a typed `SdkCall(id, abi, args)` operation;
- frontend validation confirms the exact SDK artifact/version;
- Java and direct-C++ paths share null/error/output semantics;
- differential tests force both paths;
- ABI mismatch is a build error, never a fallback;
- no user method is replaced based on bytecode similarity.

## Optional FFM adapter

FFM is a separate adapter over `c_api.h`, not a second implementation.

- The release adapter targets the standard `java.lang.foreign` API on JDK 22+.
  JDK 21 preview API is not shipped as a compatibility target.
- A separate artifact is preferred over a multi-release JAR so JDK 17 cannot
  accidentally verify or link FFM classes.
- Symbol lookup validates ABI/capabilities before exposing operations.
- Layouts correspond to fixed-width C ABI fields; size conversions are checked.
- V1 uses downcalls only, no upcalls or native-owned memory.
- JNI/FFM adapters run the same vectors and malformed-input suite and must return
  equivalent Java results/exceptions.
- Native access must be enabled according to deployment policy; FFM does not
  remove the JDK 24+ native-access operational requirement.

## Build, packaging, and supply chain

Each release records:

- SDK API and ABI versions;
- exact third-party source revision, checksum, license, patches, and build flags;
- generated SBOM and vulnerability scan;
- compiler/version/target/standard library and enabled CPU dispatch variants;
- reproducible source archive and known-answer/fuzz/sanitizer evidence;
- symbols exported from the final library, checked against an allowlist.

The library loader verifies it loaded the expected ABI before native method
registration. Java resources and native artifacts are checksummed in the build
manifest. Signing and platform notarization are production-release gates.

## Required tests

- C++ unit tests for every status, size boundary, alias/overlap rule, and
  capability combination;
- official SHA-256 and RFC 4648 vectors plus cross-checks with JDK providers;
- property tests and fuzzing for encode/decode round trips and malformed input;
- JNI `-Xcheck:jni`, ASan/UBSan, compiler warnings-as-errors, allocation-failure,
  repeated load, concurrency, and class-loader tests;
- JNI/FFM/direct-C++ parity tests;
- JMH and native microbenchmarks at multiple sizes with raw output;
- ABI symbol/layout checks on every supported target.

Security review, not test count, decides whether a cryptographic primitive can
ship.

## Revisions to the Gemini SDK proposal

The Gemini sketch had a useful high-level separation between Java facade,
native core, and JNI, but it is not an implementation specification:

- accept the one-high-level-call boundary and C++ core concept;
- revise “Java 8 through 25 seamlessly” into explicit per-version evidence;
- revise FFM to JDK 22+ standard API; JDK 21 is preview;
- reject raw `NativeMemory.allocate/free` from v1;
- reject automatic replacement of `MessageDigest`/`String` calls;
- reject unconditional `GetPrimitiveArrayCritical`/`GetStringCritical` as
  “zero-copy”;
- reject placeholder crypto code and mixed library claims as shippable code;
- replace mangled JNI exports with registered private natives over a versioned C
  ABI;
- add deterministic error, ABI, supply-chain, fuzzing, and native-access
  contracts before any performance statement.
