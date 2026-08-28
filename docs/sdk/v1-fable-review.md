# Native primitives SDK v1 — JNI/library correctness review

Scope: JNI binding correctness for `by.radioegor146.sdk.NativePrimitives`, the
vendored `amosnier/sha-2` SHA-256 implementation, and the constant-time byte
compare. This is a pure library/JNI-correctness review. Claims in
`docs/sdk/v1-status.md` were re-verified from source and by re-running the
build; nothing in that file was taken on trust.

审查范围：`by.radioegor146.sdk.NativePrimitives` 的 JNI 绑定正确性、内置的
`amosnier/sha-2` SHA-256 实现，以及常量时间字节比较。这是纯粹的库/JNI 正确性
审查。`docs/sdk/v1-status.md` 中的所有声明都从源码重新核对并重新运行构建加以
验证，未直接采信该文件。

## (a) What was reviewed / 审查对象

- `sdk/src/main/java/by/radioegor146/sdk/NativePrimitives.java` — Java surface
  (`abiVersion`, `sha256`, `constantTimeEquals`) and its null contracts.
- `obfuscator/src/main/resources/sources/sdk/native_primitives.cpp` /
  `native_primitives.hpp` / `c_api.h` — JNI entry points, the C ABI, and
  `register_natives`.
- `obfuscator/src/main/resources/sources/sdk/third_party/sha-2/**` — vendored
  SHA-256 source, header, `LICENSE.md`, and `third_party/README.md` provenance.
- `obfuscator/src/main/resources/sources/native_jvm_output.cpp` — `JNI_OnLoad`
  → `prepare_lib` → `register_natives` wiring.
- `obfuscator/src/main/java/by/radioegor146/NativeObfuscator.java` — resource
  copy, CMake source list, and SDK-class `<clinit>` injection.
- Tests: `NativePrimitivesIntegrationTest` and `NativePrimitivesVerifier`.

审查了 Java 接口层及其空值约定、JNI 入口与 C ABI、内置 SHA-256 源码与许可证、
`JNI_OnLoad` 注册链路、native-obfuscator 的资源拷贝与 `<clinit>` 注入，以及两个
测试类。

## (b) Findings / 结论

### JNI array access, pairing, null/length/overflow — correct / 正确

- The code uses copying accessors (`GetByteArrayRegion` / `SetByteArrayRegion`,
  `GetArrayLength`, `NewByteArray`); it does **not** use
  `GetPrimitiveArrayCritical`. There is therefore no critical/release pair that
  could leak or pin the heap, and no code runs inside a JNI critical region.
  This is a safe choice for these small, one-shot operations.
- Every JNI call that can raise is followed by `env->ExceptionCheck()`, and the
  functions bail out to `nullptr` / `JNI_FALSE` on a pending exception. Local
  references (`result`, `exception_class`, `primitives_class`) are released with
  `DeleteLocalRef`.
- Null handling is layered: the Java methods throw `NullPointerException` for
  null arrays, and the native `copy_byte_array` independently throws NPE if a
  `jbyteArray` is null. `constantTimeEquals` null-checks both arguments before
  touching lengths.
- Length/overflow: array length is read as `jsize` (≤ 2^31−1) and widened to
  `size_t`/`uint64_t`, so the JNI path cannot overflow. The C ABI adds defensive
  guards anyway — `representable_size` (`uint64_t` ≤ `size_t` max) and the
  SHA-256 bit-length guard (`size ≤ UINT64_MAX / 8`) — returning
  `NO_SDK_SIZE_OVERFLOW_V1` rather than overflowing.
- Empty input is handled explicitly: a zero-length array yields
  `{nullptr, 0}`, and `no_sdk_sha256_v1` substitutes a valid non-null pointer
  (`&EMPTY_INPUT`) so the digest of the empty string is computed correctly.

JNI 层使用复制式访问（`GetByteArrayRegion`/`SetByteArrayRegion` 等），未使用
`GetPrimitiveArrayCritical`，因此不存在 critical/release 配对泄漏或堆固定问题。
每个可能抛异常的 JNI 调用后都有 `ExceptionCheck`，并正确释放本地引用。空值在
Java 层与 native 层双重校验；长度来源于 `jsize`（≤ 2^31−1）再拓宽，JNI 路径不会
溢出，C ABI 另有防御性溢出检查。空输入被显式处理为合法的非空指针。

### SHA-256 vendor usage — correct / 正确

- The one-shot API `calc_sha_256(hash[32], input, len)` is used exactly as
  documented. The output buffer is a fixed 32-byte stack array
  (`SHA256_SIZE == 32`), and the C ABI checks `capacity < 32` before writing.
  The streaming API is not used, so there is no init/write/close mishandling.
- The vendored `sha-256.cpp` / `sha-256.h` are **byte-identical** to upstream
  `amosnier/sha-2` at revision `565f65009bdd98267361b17d50cddd7c9beb3e6c` apart
  from the added SPDX/provenance header comment and the `.c → .cpp` rename, all
  documented in `third_party/README.md`.

一次性 API `calc_sha_256` 的用法与文档完全一致，输出缓冲区固定为 32 字节，写入
前检查容量。未使用流式 API。内置源码与上游指定修订版逐字节一致（仅新增 SPDX 头
注释并将 `.c` 重命名为 `.cpp`）。

### Registration / loader hooks — will run / 注册与加载链路可运行

- `JNI_OnLoad` → `native_jvm::prepare_lib` → `native_obfuscator::sdk::
  register_natives` runs on library load. `register_natives` resolves
  `by/radioegor146/sdk/NativePrimitives` and binds all three natives with
  `RegisterNatives`.
- The three `JNINativeMethod` descriptors match the Java declarations exactly:
  `nativeAbiVersion ()I`, `nativeSha256 ([B)[B`,
  `nativeConstantTimeEquals ([B[B)Z`. `RegisterNatives` can bind
  `private static native` methods.
- `NativeObfuscator.buildSdkClass` injects `Loader.load()` into the SDK class
  `<clinit>` (creating a `<clinit>` when none exists), and the SDK class plus the
  remapped loader are written into every generated JAR unconditionally. So the
  first use of any `NativePrimitives` method loads the library, which triggers
  registration before the native is invoked. The SDK C++ sources are copied and
  added to the generated `CMakeLists.txt`, so they are actually compiled/linked.

`JNI_OnLoad` 在库加载时经 `prepare_lib` 调用 `register_natives` 绑定三个 native；
方法签名与 Java 声明逐一匹配，且可绑定 `private static native`。SDK 类的
`<clinit>` 被注入 `Loader.load()`，SDK 类与加载器无条件写入每个生成的 JAR，
SDK 的 C++ 源码也被拷贝并加入生成的 CMake，因此确实会被编译链接并在调用前完成
注册。

### Constant-time compare — correct for stated scope / 常量时间比较（在声明范围内）正确

- For equal-length inputs the loop has no data-dependent branch or early exit
  and accumulates differences into a `volatile uint8_t`, which is the standard
  way to keep the compiler from short-circuiting. Unequal lengths return early;
  both the Java doc and `v1-status.md` explicitly place length outside the
  constant-time guarantee, so this is consistent with the documented contract.

对于等长输入，比较循环无数据相关分支或提前退出，并把差异累加进 `volatile`
变量；长度不等时提前返回，这与文档明确声明的“长度不在常量时间保证范围内”一致。

### Tests — real vectors, and they run / 测试：向量真实且可运行

- The two hard-coded vectors are the genuine NIST values: SHA-256("") =
  `e3b0c442…7852b855` and SHA-256("abc") = `ba7816bf…f20015ad`. Beyond those,
  the verifier cross-checks the native digest against JDK `MessageDigest` at
  chunk-boundary lengths `{0,1,55,56,63,64,65,1024,65536}`, plus five equality
  cases (empty, equal, first-byte, last-byte, length mismatch) and three null
  contracts.
- The integration test is a real end-to-end run: it generates the JAR + C++
  tree, asserts the CMake source list, configures and builds the library with
  CMake/g++, injects it into the output JAR, and runs that JAR under
  `-Xcheck:jni`, asserting the verifier prints `PASS`.

两个硬编码向量是真实的 NIST 值；此外用 JDK `MessageDigest` 在多个分块边界长度上
交叉校验，并覆盖等值/首字节/末字节/长度不等及空值约定。集成测试是真正的端到端
流程：生成产物、断言 CMake 源清单、用 CMake/g++ 构建库、注入 JAR 并在
`-Xcheck:jni` 下运行断言 `PASS`。

### License files — present / 许可证齐备

- `third_party/sha-2/LICENSE.md` carries the full dual `0BSD OR Unlicense`
  text; every SDK source has an SPDX header (`GPL-3.0-only` for integration
  code, `0BSD OR Unlicense` for the vendored files); `third_party/README.md`
  records upstream URL, revision, license, local changes, and per-file upstream
  checksums. No GPL/LGPL third-party dependency is introduced.

许可证文件齐备：内置代码含完整双许可文本，各文件均有 SPDX 头，`README.md` 记录
上游地址、修订、许可、本地改动与逐文件校验和；未引入 GPL/LGPL 第三方依赖。

### Nits (not blocking) / 小问题（不影响合入）

- `throw_new` silently returns if `FindClass(exception_class)` yields null,
  leaving no pending exception for the caller to propagate. This can only happen
  for a core JDK exception class that is always available, so it is a
  theoretical robustness gap rather than a reachable bug.
- `jni_constant_time_equals` fully copies both arrays onto the C++ heap even
  though a length mismatch is rejected first; for large equal-length inputs this
  doubles transient memory. Acceptable for v1's one-shot use.

`throw_new` 在 `FindClass` 返回 null 时静默返回（仅对始终存在的核心异常类而言是
理论缺口）；等长大输入时会把两个数组都复制到堆上，属可接受的取舍。

## (c) Verification performed / 已执行的验证

Environment: OpenJDK 21.0.10 (source/target 8), CMake 3.28.3, GCC/G++ 13.3.0,
Linux x86-64 — matching `v1-status.md`.

1. Re-ran the native integration test on this branch:

   ```text
   CC=gcc CXX=g++ ./gradlew :obfuscator:test \
     --tests by.radioegor146.sdk.NativePrimitivesIntegrationTest \
     --no-build-cache --rerun-tasks
   ```

   Result: `BUILD SUCCESSFUL`, `generatedLibraryMatchesJdkAndKnownVectors() ->
   SUCCESS`. This compiles and links the generated library and runs the output
   JAR under `-Xcheck:jni`, so the `PASS` claim in `v1-status.md` reproduces.

2. Verified the vendored provenance by fetching upstream `amosnier/sha-2` at
   revision `565f65009bdd98267361b17d50cddd7c9beb3e6c`; the SHA-256 of
   `sha-256.c`, `sha-256.h`, and `LICENSE.md` matched `third_party/README.md`
   exactly, and a diff of the vendored files against upstream (ignoring the
   added SPDX header) showed the bodies are byte-identical.

在与文档一致的环境中重跑集成测试通过（`-Xcheck:jni` 下 `PASS`），并按指定修订版
抓取上游文件，三个校验和与 `README.md` 完全一致，去除 SPDX 头后代码逐字节相同。

## (d) Verdict / 结论

**accept-with-nits / 有小问题但建议接受。**

The JNI binding is correct: copying accessors with consistent exception checks
and reference cleanup, layered null/length/overflow handling, correct one-shot
use of a byte-identical vendored SHA-256, registration wired through
`JNI_OnLoad` with matching signatures and a `<clinit>`-driven load, real and
reproduced test vectors, and complete license/provenance. No correctness bug was
found, so no code change is included. The two nits above are minor and
non-blocking.

JNI 绑定正确：复制式访问 + 一致的异常检查与引用释放、分层的空值/长度/溢出处理、
对逐字节一致的内置 SHA-256 的正确一次性调用、经 `JNI_OnLoad` 完成且签名匹配的
注册、真实且可复现的测试向量，以及完整的许可与来源记录。未发现正确性缺陷，故未
附带代码改动；上述两点为非阻塞小问题。
