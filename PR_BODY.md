# English

## Scope

- Admit one narrow mixed constructor exception-table shape on the opt-in IR split:
  `start` and `end` are in the suffix, while the prefix `handler` has the exact
  executable sequence `POP; RETURN`.
- Require the handler's preceding executable instruction to be `GOTO`, reject
  every normal jump/switch edge to it, and reject using its label as a protected
  range boundary. Stack-map frames between the handler label and its two
  opcodes are allowed.
- Clone that isolated handler into the IR suffix with the original exception
  table edge, and omit its now-dead copy from the bytecode wrapper.
- Keep every other mixed start/end/handler placement rejected before compiler
  mutation.
- Add split, JVM-verification, fail-closed, and CMake/g++ runtime parity tests
  for normal division and caught divide-by-zero paths.

## Readiness and review

- Ship-ready: **No**.
- Stacked review: none. The review gate is the focused test command below.
- This does not complete the production goal.

## Preconditions and unchanged boundaries

- Remaining constructor leftovers stay rejected.
- Unsafe constant-dynamic forms and `jsr`/`ret` stay rejected.
- `--codegen` still defaults to `legacy`; there is no `--ir-lower` or backend
  default flip.

## Test gate

```text
CC=gcc CXX=g++ ./gradlew :obfuscator:test --rerun-tasks \
  --tests by.radioegor146.ir.IrCompilerTest \
  --tests by.radioegor146.CodegenModeTest
```

JUnit XML: 158 tests, 0 failures, 0 errors, 0 skipped
(`IrCompilerTest` 151; `CodegenModeTest` 7).

# 中文

## 范围

- 在可选的 IR 构造器拆分路径中，仅接纳一种严格限定的混合异常表形状：
  `start` 和 `end` 位于后缀，前缀中的 `handler` 可执行指令必须精确为
  `POP; RETURN`。
- 要求处理器之前的可执行指令为 `GOTO`，拒绝任何跳转或 switch 的普通控制流
  进入该处理器，并拒绝将处理器标签同时用作受保护区间边界。允许处理器标签
  与两条指令之间存在栈映射帧。
- 将这个隔离处理器连同原异常表边复制到 IR 后缀，并从字节码包装器中移除
  已不可达的原副本。
- 其他所有混合 `start`/`end`/`handler` 布局仍在编译器产生修改前拒绝。
- 增加拆分、JVM 验证、失败关闭及 CMake/g++ 运行时一致性测试，覆盖普通除法
  和捕获除零异常两条路径。

## 就绪状态与评审

- 可发布：**否**。
- 无堆叠评审；评审门槛为下方聚焦测试命令。
- 本改动不代表生产目标完成。

## 前置条件与未变边界

- 其余构造器遗留形状继续拒绝。
- 不安全的常量动态形式以及 `jsr`/`ret` 继续拒绝。
- `--codegen` 默认值仍为 `legacy`；不更改 `--ir-lower` 或后端默认值。

## 测试门槛

```text
CC=gcc CXX=g++ ./gradlew :obfuscator:test --rerun-tasks \
  --tests by.radioegor146.ir.IrCompilerTest \
  --tests by.radioegor146.CodegenModeTest
```

JUnit XML：共 158 项测试，0 失败、0 错误、0 跳过
（`IrCompilerTest` 151；`CodegenModeTest` 7）。
