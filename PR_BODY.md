# English

## Summary

- Admit only the isolated constructor chain-input leaf
  `ALOAD <declared reference argument>; GETFIELD <exact declared owner>.<field>`.
- Require an unchanged nonzero declared receiver local and a field carrier that
  matches the constructor-chain argument.
- Keep the receiver load and field read in the retained JVM constructor prefix.
- Add admit, JVM verification, native parity, and fail-closed mutation tests.
- Update only the flexible-constructor status document.

## Rebase

Rebased onto `origin/master` `87da519` (post-#265 fail-closed spanning-catch
audit). Parent-verified JUnit XML after rebase: `IrCompilerTest` 464/464 and
`CodegenModeTest` 7/7, with zero failures, errors, or skips (total 471). The
runtime test is `threeImmediateGetfieldArgChainInputsCompileAndRunWithJavaParity`.

Default `--codegen` remains `legacy`.

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

## 变基

已变基到 `origin/master` `87da519`（#265 跨后缀/覆盖链调用 catch 失败关闭
审计之后）。父代理变基后聚焦 JUnit XML：`IrCompilerTest` 464/464、
`CodegenModeTest` 7/7，失败、错误和跳过均为 0（合计 471）。运行时测试为
`threeImmediateGetfieldArgChainInputsCompileAndRunWithJavaParity`。

默认 `--codegen` 仍为 `legacy`。

## 就绪状态

可发布：**否**
