# test: admit extra-local int as the first and fifth six-arg NEW arguments

## English

### Summary

- Adds fixture coverage for `new-constructor-extra-local-argument-six-first-fifth`.
- Keeps `NEW GregorianCalendar; DUP; args; <init>` in the retained JVM prefix while the native `(II)V` body contains only `RETURN`.
- Exercises three direct constructor-chain paths, one hidden bridge, and proxy descriptor `(Ljava/lang/Object;II)V`.
- Uses the extra local as the first and fifth initializer arguments; the second, third, fourth, and sixth arguments remain constants.

### Baseline and status

- Leftover-docs baseline: [#434](https://github.com/gaoyu06/native-obfuscator/pull/434) at `3645eb36` (`3645eb3600e23115ab46885c7450fde97cb47666`).
- Latest compiler parent XML until the parent re-runs: [#434](https://github.com/gaoyu06/native-obfuscator/pull/434), 740 tests (`IrCompilerTest` 733 + `CodegenModeTest` 7).
- Expected parent XML after leftover-docs: 743 tests (740 + 3).
- Processor changed: No.
- Ship-ready: No.
- Defaults unchanged.
- Child XML: 743 tests, 0 failures, 0 errors, 0 skipped (`IrCompilerTest` 736 + `CodegenModeTest` 7).

### Verification

```bash
CC=gcc CXX=g++ ./gradlew :obfuscator:test --rerun-tasks \
  --tests by.radioegor146.ir.IrCompilerTest \
  --tests by.radioegor146.CodegenModeTest
```

## 中文

### 摘要

- 为 `new-constructor-extra-local-argument-six-first-fifth` 增加测试夹具覆盖。
- 将 `NEW GregorianCalendar; DUP; args; <init>` 完整保留在 JVM 前缀中；原生 `(II)V` 方法体仅包含 `RETURN`。
- 覆盖三条直接构造器链路径、一个隐藏桥接方法，以及 `(Ljava/lang/Object;II)V` 代理描述符。
- 额外局部变量用作初始化器的第一个和第五个参数；第二、第三、第四和第六个参数保持为常量。

### 基线与状态

- leftover-docs 基线：[PR #434](https://github.com/gaoyu06/native-obfuscator/pull/434)，提交 `3645eb36`（`3645eb3600e23115ab46885c7450fde97cb47666`）。
- 在父任务重新运行前，最新编译器父 XML 为 [PR #434](https://github.com/gaoyu06/native-obfuscator/pull/434) 的 740 项测试（`IrCompilerTest` 733 + `CodegenModeTest` 7）。
- leftover-docs 之后预期父 XML：743 项测试（740 + 3）。
- 处理器已更改：否。
- 可发布：否。
- 默认设置未更改。
- 子任务 XML：743 项测试，0 项失败、0 项错误、0 项跳过（`IrCompilerTest` 736 + `CodegenModeTest` 7）。

### 验证

```bash
CC=gcc CXX=g++ ./gradlew :obfuscator:test --rerun-tasks \
  --tests by.radioegor146.ir.IrCompilerTest \
  --tests by.radioegor146.CodegenModeTest
```
