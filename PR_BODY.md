# English

## Summary

- Adds fixture-only coverage for one proven constructor-split composition: a copied extra-local `int` used as all three initializer arguments of an isolated three-argument `java.awt.Color` `NEW`.
- Adds the `new-constructor-extra-local-argument-three-all` fixture shape and three admission, JVM-verification, and Java/native-parity tests.
- Processor changed: No. No compiler or runtime source was changed.

## Scope

- Admitted: this composition only.
- Ship-ready: No.
- This change does not authorize a default flip. `--codegen=legacy`, `--ir-lower=direct`, and `--backend=cpp` remain unchanged.
- Leftover inventory citation: #318.
- Expected parent XML: 572 tests (565 `IrCompilerTest` + 7 `CodegenModeTest`).

## Verification

```sh
CC=gcc CXX=g++ ./gradlew :obfuscator:test --rerun-tasks \
  --tests by.radioegor146.ir.IrCompilerTest \
  --tests by.radioegor146.CodegenModeTest
```

Result: `BUILD SUCCESSFUL`; 565 + 7 tests, with zero failures, errors, or skips.

# 中文

## 摘要

- 仅新增测试夹具覆盖，纳入一个已证明安全的构造器拆分组合：复制到额外局部变量的 `int`，同时作为隔离的三参数 `java.awt.Color` `NEW` 的三个初始化参数。
- 新增 `new-constructor-extra-local-argument-three-all` 夹具形状，以及准入、JVM 验证和 Java/原生运行一致性三项测试。
- 处理器变更：否。未修改编译器或运行时源码。

## 范围

- 本次仅准入上述组合。
- 可发布：否。
- 本变更不授权切换默认值；`--codegen=legacy`、`--ir-lower=direct` 和 `--backend=cpp` 均保持不变。
- 剩余项清单引用：#318。
- 预期父分支 XML：572 项测试（565 项 `IrCompilerTest` + 7 项 `CodegenModeTest`）。

## 验证

上述命令执行结果为 `BUILD SUCCESSFUL`；565 + 7 项测试全部通过，无失败、错误或跳过。
