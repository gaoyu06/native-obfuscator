# test: admit extra-local int as second, fourth, and fifth five-arg NEW arguments

## English

### Summary

- Fixture-only IR admission; `ConstructorSpecialMethodProcessor` is unchanged.
- Adds the five-argument `GregorianCalendar` initializer leaf `ICONST_1; ILOAD 3; ICONST_3; ILOAD 3; ILOAD 3`.
- Keeps the complete `NEW; DUP; args; <init>` sequence in the retained JVM constructor prefix, with one hidden bridge and one native method per constructor.
- Does not switch `--codegen` away from `legacy` and does not mark the production goal complete.

### Verification

- Runtime verification: `threeImmediateNewExtraLocalFiveSecondFourthFifthArgChainInputsCompileAndRunWithJavaParity`
- Child-local XML: pending local run.
- Parent XML: pending parent re-run.

Ship-ready: **No**.

## 中文

### 概要

- 仅扩展 IR 测试夹具准入；`ConstructorSpecialMethodProcessor` 保持不变。
- 新增五参数 `GregorianCalendar` 初始化叶子：`ICONST_1; ILOAD 3; ICONST_3; ILOAD 3; ILOAD 3`。
- 完整的 `NEW; DUP; args; <init>` 序列保留在 JVM 构造器前缀中；每个构造器只有一个隐藏桥接和一个 native 方法。
- 不会将 `--codegen` 从 `legacy` 切走，也不会将生产目标标记为完成。

### 验证

- 运行时验证：`threeImmediateNewExtraLocalFiveSecondFourthFifthArgChainInputsCompileAndRunWithJavaParity`
- 子分支本地 XML：等待本地运行。
- 父分支 XML：等待父代理重新运行。

可发布：**否**。
