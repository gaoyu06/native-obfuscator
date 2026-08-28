## Summary / 摘要

Stacks `NativeStrings` on SDK v1 with preferred base
`cursor/cpp-sdk-v1-6d81` (PR #12). It ports only the Java/JNI/C++ UTF-16
`length`, `hashCode`, and `concat` path, its generated-library correctness
checks, and a minimal local string measurement. It does not copy PR #10's
benchmark harness or PR #12's commits.

本分支以 `cursor/cpp-sdk-v1-6d81`（PR #12）为首选 base，仅移植
`NativeStrings` 的 Java/JNI/C++ UTF-16 `length`、`hashCode`、`concat`
实现、生成库正确性测试和最小本地字符串测量；不重复引入 PR #10 的完整
benchmark harness，也不重复引入 PR #12 的提交。

## (a) Change scope / 本次改动范围

- Add `by.radioegor146.sdk.NativeStrings` and register its three JNI methods.
- Emit and compile `native_strings.hpp/.cpp` in each generated native project.
- Extend the existing SDK generated-library test with ASCII, BMP Unicode,
  surrogate-pair, embedded-NUL, concatenation, and null-contract cases.
- Add a dependency-free Java-versus-NativeStrings diagnostic measurement to
  that integration test; no second copy of the general benchmark harness.
- Update `docs/sdk/v1-status.md` with this run's commands and raw samples.
- Keep the existing vendored SHA-256 sources, checksums, and licenses unchanged.

- 新增 `by.radioegor146.sdk.NativeStrings` 并注册 3 个 JNI 方法。
- 在每个生成的原生工程中输出并编译 `native_strings.hpp/.cpp`。
- 扩展现有 SDK 生成库测试，覆盖 ASCII、BMP Unicode、代理对、内嵌 NUL、
  拼接及 null 契约。
- 在集成测试中加入无额外依赖的 Java 与 NativeStrings 诊断测量，不复制
  通用 benchmark harness。
- 用本次运行的命令和原始样本更新 `docs/sdk/v1-status.md`。
- 保持现有 SHA-256 vendor 源码、校验和与许可证不变。

## (b) Can this ship to production as-is? / 是否可直接上线？

**No / 否。** This is a small explicit SDK surface, not an automatic
`String.*` rewrite. Its JNI critical-region handling, UTF-16 semantics, and
product packaging still require review. The local measurement is diagnostic,
not a release gate.

这是一个显式调用的小型 SDK 接口，不会自动重写 `String.*`。JNI critical
region、UTF-16 语义和产品打包仍需审查；本地测量仅为诊断数据，不是发布门槛。

## (c) Is review required before shipping? / 上线前是否需要 review？

**Yes / 是。** Review JNI registration and reference lifetimes, pairing of
`GetStringCritical`/`ReleaseStringCritical`, overflow/allocation behavior in
`concat`, Unicode edge cases, and generated-loader initialization.

请审查 JNI 注册与引用生命周期、`GetStringCritical`/`ReleaseStringCritical`
配对、`concat` 的溢出与分配行为、Unicode 边界情况以及生成 loader 的初始化。

## (d) Review preconditions / Review 前置条件

1. Use `cursor/cpp-sdk-v1-6d81` as the PR base and confirm the diff contains no
   duplicate PR #10/#12 commits.
2. Re-run:
   `CC=gcc CXX=g++ ./gradlew :obfuscator:test --tests by.radioegor146.sdk.NativePrimitivesIntegrationTest --no-build-cache --rerun-tasks --info`.
3. Re-run:
   `./gradlew :sdk:jar :obfuscator:assemble --no-build-cache --rerun-tasks`.
4. Confirm `sources/sdk/third_party` and the SHA-256 license/checksum story are
   unchanged.
5. Treat performance numbers as local-only evidence. This run measured medians
   of 5,226,627 ns/sample for Java and 16,747,902 ns/sample for NativeStrings
   (5 warmups, 10 samples, equal checksum `231461089`): NativeStrings was
   slower than HotSpot Java. The snippet-transpiled JNI comparison was not
   re-run because the general harness was intentionally not copied.

1. 以 `cursor/cpp-sdk-v1-6d81` 为 PR base，确认 diff 不含 PR #10/#12 的重复提交。
2. 重新运行上述原生集成测试命令。
3. 重新运行上述 SDK/obfuscator 组装命令。
4. 确认 `sources/sdk/third_party` 及 SHA-256 许可证/校验和方案未改变。
5. 性能数据只能视为本地证据。本次中位数为 Java 5,226,627 ns/sample、
   NativeStrings 16,747,902 ns/sample（5 次预热、10 个样本，校验和同为
   `231461089`），NativeStrings 慢于 HotSpot Java。为避免复制通用 harness，
   本次未重跑 snippet-transpiled JNI 对照。

## Verification / 验证结果

- Native generated-library integration test: **PASS**, including `-Xcheck:jni`
  and the minimal measurement.
- `:sdk:jar` and `:obfuscator:assemble`: **PASS**.
- 原生生成库集成测试：**通过**，包含 `-Xcheck:jni` 与最小测量。
- `:sdk:jar` 和 `:obfuscator:assemble`：**通过**。
