# English

## Summary

- Admit only the isolated constructor chain-input leaf
  `ALOAD <declared reference argument>; GETFIELD <exact declared owner>.<field>`.
- Require an unchanged nonzero declared receiver local and a field carrier that
  matches the constructor-chain argument.
- Keep the receiver load and field read in the retained JVM constructor prefix.
- Add admit, JVM verification, native parity, and fail-closed mutation tests.
- Update only the flexible-constructor status document.

## Verification

- Required Gradle suites: passed.
- JUnit XML totals: 470 tests, 0 failures, 0 errors, 0 skipped across
  2 child XML files (child-only; no parent XML number).
- Default `--codegen` remains `legacy`.

## Readiness

Ship-ready: **No**

# 中文

## 概要

- 仅放行构造器链参数中的独立叶子：
  `ALOAD <已声明的引用参数>; GETFIELD <与声明类型完全一致的字段所有者>.<字段>`。
- 要求接收者是未被覆盖的非零已声明参数局部变量，并且字段 JVM
  载体与构造器链参数匹配。
- `ALOAD` 和 `GETFIELD` 继续保留在 JVM 构造器前缀中执行。
- 新增放行、JVM 校验、原生运行一致性以及拒绝前零变更测试。
- 仅更新灵活构造器状态文档。

## 验证

- 必跑 Gradle 测试：已通过。
- JUnit XML 汇总：2 个子 XML 文件，共 470 项测试、0 失败、0 错误、
  0 跳过（仅子测试；不虚构父 XML 数量）。
- 默认 `--codegen` 仍为 `legacy`。

## 就绪状态

可发布：**否**
