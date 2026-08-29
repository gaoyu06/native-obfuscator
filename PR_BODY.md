## Summary / 摘要

This branch adds AES-256-GCM authenticated encryption and decryption to the
callable C++ SDK surface and Java `NativePrimitives` facade. It is stacked on
`cursor/sdk-hmac-sha256-6d81`
([draft PR #72](https://github.com/gaoyu06/native-obfuscator/pull/72)), which is
stacked on NativeStrings
([draft PR #46](https://github.com/gaoyu06/native-obfuscator/pull/46)) and SDK
v1 ([PR #12](https://github.com/gaoyu06/native-obfuscator/pull/12)). This is a
neutral native crypto SDK API change, not an automatic transformation or
obfuscation feature.

本分支为可调用的 C++ SDK 接口及 Java `NativePrimitives` facade 新增
AES-256-GCM 认证加密与解密。分支基于 `cursor/sdk-hmac-sha256-6d81`
（[draft PR #72](https://github.com/gaoyu06/native-obfuscator/pull/72)），后者
依次基于 NativeStrings
（[draft PR #46](https://github.com/gaoyu06/native-obfuscator/pull/46)）和 SDK
v1（[PR #12](https://github.com/gaoyu06/native-obfuscator/pull/12)）。这是中性
的 native crypto SDK API 改动，不是自动转换或混淆功能。

## (a) Change scope / 本次改动范围

- Add Java `aes256GcmEncrypt`/`aes256GcmDecrypt` overloads with optional AAD.
  The profile requires a 32-byte key and 12-byte nonce and appends a 16-byte
  authentication tag to ciphertext.
- Register static JNI entry points and export
  `no_sdk_aes_256_gcm_encrypt_v1`/`no_sdk_aes_256_gcm_decrypt_v1`.
- Throw `NullPointerException` for null arrays, `IllegalArgumentException` for
  invalid lengths, and `AEADBadTagException` for failed authentication.
  Decrypt authenticates before writing plaintext.
- Vendor the AES-256 encryption subset of `kokke/tiny-AES-c` revision
  `23856752fbd139da0b8ca6e471a13d5bcc99a08d` under the Unlicense; compose GCM
  in-tree according to NIST SP 800-38D. No system crypto dependency is added.
- Verify three exact AES-256 vectors from NIST CAVP
  `gcmEncryptExtIV256.rsp`, including empty AAD and non-empty AAD.
- Preserve HMAC-SHA-256, SHA-256, constant-time equality, and NativeStrings.

- 新增支持可选 AAD 的 Java `aes256GcmEncrypt`/`aes256GcmDecrypt` 重载。
  该 profile 要求 32 字节 key 和 12 字节 nonce，并把 16 字节认证 tag
  追加在 ciphertext 后。
- 注册静态 JNI 入口，并导出 `no_sdk_aes_256_gcm_encrypt_v1` 和
  `no_sdk_aes_256_gcm_decrypt_v1`。
- null 数组抛出 `NullPointerException`，非法长度抛出
  `IllegalArgumentException`，认证失败抛出 `AEADBadTagException`；解密在
  写入 plaintext 前先完成认证。
- 以 Unlicense 内嵌 `kokke/tiny-AES-c` revision
  `23856752fbd139da0b8ca6e471a13d5bcc99a08d` 的 AES-256 加密子集，并按
  NIST SP 800-38D 在仓内组合 GCM；不新增系统 crypto 依赖。
- 使用 NIST CAVP `gcmEncryptExtIV256.rsp` 的 3 个精确 AES-256 向量验证，
  覆盖空 AAD 和非空 AAD。
- 保持 HMAC-SHA-256、SHA-256、constant-time equality 和 NativeStrings。

## (b) Is this a shipped product SDK? / 这是已交付的产品 SDK 吗？

**No / 否。** This remains a review-stage SDK surface and is not a shipped
product SDK.

这仍是处于 review 阶段的 SDK 接口，并非已交付的产品 SDK。

## (c) Is review required before shipping? / 交付前是否需要 review？

**Yes / 是。** Review the SP 800-38D composition, vendored AES adaptation,
nonce/key/tag profile, bounds checks, authenticate-before-decrypt behavior,
JNI exception contracts, C ABI additions, vector provenance, and generated
library packaging.

请审查 SP 800-38D 组合、内嵌 AES 改编、nonce/key/tag profile、边界检查、
先认证后解密的行为、JNI 异常契约、C ABI 新接口、向量来源及生成库打包。

## (d) Review preconditions / Review 前置条件

1. Use `cursor/sdk-hmac-sha256-6d81` as the base and confirm the diff is limited
   to this SDK addition, vendored source, tests, and documentation.
2. Re-run the generated-library integration test with GCC/G++ and
   `-Xcheck:jni`.
3. Re-run the existing SDK/module tests and assembly tasks.
4. Confirm all 3 documented NIST vectors pass for encryption and decryption,
   tampered key/ciphertext/AAD/tag fail authentication, and null/length
   contracts match the facade documentation.
5. Confirm authentication failure does not expose plaintext and nonce reuse is
   documented as forbidden.
6. Confirm no HMAC-SHA-256, SHA-256, constant-time equality, or NativeStrings
   regression is present.

1. 以 `cursor/sdk-hmac-sha256-6d81` 为 base，确认 diff 仅包含本次 SDK
   接口、内嵌源码、测试和文档。
2. 使用 GCC/G++ 重新运行生成库集成测试，并启用 `-Xcheck:jni`。
3. 重新运行现有 SDK/module 测试和组装任务。
4. 确认文档列出的 3 个 NIST 向量加解密均通过，且篡改 key/ciphertext/
   AAD/tag 都会认证失败；null 与长度契约符合 facade 文档。
5. 确认认证失败不会暴露 plaintext，并已明确禁止同一 key 重用 nonce。
6. 确认 HMAC-SHA-256、SHA-256、constant-time equality 和 NativeStrings
   均无回归。

## Verification / 验证结果

- `CC=gcc CXX=g++` generated-library integration: **PASS**, including CMake,
  G++, `-Xcheck:jni`, 3 NIST AES-256-GCM vectors, 4 authentication-failure
  cases, 5 length checks, 8 GCM null checks, and all inherited checks.
- Full `:sdk:test :obfuscator:test` with repository-required Krakatau2:
  **13/13 tests passed** (8 generated fixtures, 4 focused unit tests, 1 SDK
  integration test); `:sdk:test` has no test sources.
- `:sdk:jar :obfuscator:assemble`: **PASS**.
- Exported-symbol inspection includes both AES-256-GCM C ABI symbols.
- No AES-GCM benchmark or HotSpot performance comparison was run. The inherited
  NativeStrings diagnostic ran once during the recorded final suite: Java
  median 5,357,076 ns, NativeStrings median 16,289,443.5 ns, with raw samples
  and environment in `docs/sdk/v1-status.md`. NativeStrings was slower in this
  one local run.

- 使用 `CC=gcc CXX=g++` 的生成库集成测试：**通过**，覆盖 CMake、G++、
  `-Xcheck:jni`、3 个 NIST AES-256-GCM 向量、4 个认证失败场景、5 个长度
  检查、8 个 GCM null 检查及所有继承检查。
- 配置仓库要求的 Krakatau2 后，完整 `:sdk:test :obfuscator:test`：
  **13/13 通过**（8 个生成 fixture、4 个既有单元测试、1 个 SDK 集成测试）；
  `:sdk:test` 没有测试源码。
- `:sdk:jar :obfuscator:assemble`：**通过**。
- 导出符号检查包含两个 AES-256-GCM C ABI 符号。
- 未运行 AES-GCM benchmark 或 HotSpot 性能比较。最终完整 suite 中记录了
  一次继承的 NativeStrings 诊断：Java 中位数 5,357,076 ns，NativeStrings
  中位数 16,289,443.5 ns；原始样本和环境记录于
  `docs/sdk/v1-status.md`。在这一次本地运行中 NativeStrings 更慢。
