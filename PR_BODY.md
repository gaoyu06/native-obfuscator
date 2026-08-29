## Summary / 摘要

This branch adds HMAC-SHA-256 to the callable C++ SDK surface and Java
`NativePrimitives` facade. It is stacked on `cursor/native-strings-on-sdk-6d81`
(draft PR #46), which is stacked on SDK v1 (PR #12). This is an SDK API change,
not an automatic transformation feature.

本分支为可调用的 C++ SDK 接口及 Java `NativePrimitives` facade 新增
HMAC-SHA-256。分支基于 `cursor/native-strings-on-sdk-6d81`（draft PR
#46），后者基于 SDK v1（PR #12）。这是 SDK API 改动，不是自动转换功能。

## (a) Change scope / 本次改动范围

- Add Java `NativePrimitives.hmacSha256(byte[] key, byte[] message)`.
- Register the corresponding static JNI entry point and expose
  `no_sdk_hmac_sha256_v1` in the existing C ABI.
- Implement RFC 2104 HMAC using the existing in-tree SHA-256 streaming code,
  including hashing keys longer than the 64-byte SHA-256 block.
- Test published vectors covering an empty key, a short key, a 131-byte key,
  and an empty message, plus Java null contracts.
- Preserve the existing SHA-256, constant-time equality, and NativeStrings
  behavior. No dependency or vendored SHA-256 source change is included.

- 新增 Java `NativePrimitives.hmacSha256(byte[] key, byte[] message)`。
- 注册对应的静态 JNI 入口，并在现有 C ABI 中公开
  `no_sdk_hmac_sha256_v1`。
- 使用仓内现有 SHA-256 流式实现完成 RFC 2104 HMAC，包括先哈希超过
  SHA-256 64 字节分组长度的 key。
- 使用公开向量测试空 key、短 key、131 字节 key、空消息，并测试 Java
  null 契约。
- 保持现有 SHA-256、constant-time equality 和 NativeStrings 行为；不新增
  依赖，也不修改 vendored SHA-256 源文件。

## (b) Is this a shipped product SDK? / 这是已交付的产品 SDK 吗？

**No / 否。** This remains a review-stage SDK surface and is not a shipped
product SDK.

这仍是处于 review 阶段的 SDK 接口，并非已交付的产品 SDK。

## (c) Is review required before shipping? / 交付前是否需要 review？

**Yes / 是。** Review the RFC 2104 construction, input-size checks, JNI
registration and exception contracts, C ABI addition, test-vector provenance,
and generated-library packaging.

请审查 RFC 2104 构造、输入长度检查、JNI 注册与异常契约、C ABI 新接口、
测试向量来源及生成库打包。

## (d) Review preconditions / Review 前置条件

1. Use `cursor/native-strings-on-sdk-6d81` as the base and confirm the diff is
   limited to this SDK addition, tests, and documentation.
2. Re-run the generated-library integration test with GCC/G++ and
   `-Xcheck:jni`.
3. Re-run the existing SDK/module tests and assembly tasks.
4. Confirm the four documented published vectors pass and both null arguments
   throw `NullPointerException`.
5. Confirm no dependency, vendored SHA-256 source, SHA-256 API, or
   NativeStrings regression is present.

1. 以 `cursor/native-strings-on-sdk-6d81` 为 base，确认 diff 仅包含本次 SDK
   接口、测试和文档。
2. 使用 GCC/G++ 重新运行生成库集成测试，并启用 `-Xcheck:jni`。
3. 重新运行现有 SDK/module 测试和组装任务。
4. 确认文档列出的 4 个公开向量通过，两个 null 参数均抛出
   `NullPointerException`。
5. 确认未新增依赖、未修改 vendored SHA-256 源码，且 SHA-256 API 与
   NativeStrings 无回归。

## Verification / 验证结果

Verification results will be recorded after the committed branch is tested.

提交分支完成测试后记录验证结果。
