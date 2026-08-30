# English

## Summary

- Adds fixture-only IR admission coverage for
  `new-constructor-extra-local-argument-five-second`.
- Retains this leaf in each constructor CFG path:
  `NEW java/util/GregorianCalendar; DUP; ICONST_1; ILOAD 3; ICONST_3;`
  `ICONST_4; ICONST_5; INVOKESPECIAL <init>(IIIII)V`.
- Verifies the `(II)V` constructor prefix `ILOAD 2; ISTORE 3`, one native
  method containing only `RETURN`, proxy descriptor
  `(Ljava/lang/Object;II)V`, one hidden bridge, JVM verification, and JNI
  runtime parity across all three immediate-return paths.
- Does not change `ConstructorSpecialMethodProcessor` or production compiler
  code. Default selections remain unchanged, including
  `--codegen=legacy`.

## Tests

- `admitsThreeImmediateReturnsWithNewExtraLocalFiveSecondArgChainInputs`
- `rewrittenThreeImmediateNewExtraLocalFiveSecondArgChainInputsPassJvmVerification`
- `threeImmediateNewExtraLocalFiveSecondArgChainInputsCompileAndRunWithJavaParity`
- Focused child gate is pending before final handoff. The parent will rerun
  the focused gate and discard the child XML.

## Delivery

- Processor changed: No
- Ship-ready: No

# 中文

## 摘要

- 仅新增 IR 准入夹具覆盖：
  `new-constructor-extra-local-argument-five-second`。
- 每条构造函数 CFG 路径保留以下叶节点字节码：
  `NEW java/util/GregorianCalendar; DUP; ICONST_1; ILOAD 3; ICONST_3;`
  `ICONST_4; ICONST_5; INVOKESPECIAL <init>(IIIII)V`。
- 验证 `(II)V` 构造函数前缀 `ILOAD 2; ISTORE 3`、仅包含 `RETURN`
  的单个 native 方法、代理描述符 `(Ljava/lang/Object;II)V`、一个隐藏桥、
  JVM 验证，以及三条立即返回路径的 JNI 运行时一致性。
- 不修改 `ConstructorSpecialMethodProcessor` 或生产编译器代码。默认选项
  保持不变，包括 `--codegen=legacy`。

## 测试

- `admitsThreeImmediateReturnsWithNewExtraLocalFiveSecondArgChainInputs`
- `rewrittenThreeImmediateNewExtraLocalFiveSecondArgChainInputsPassJvmVerification`
- `threeImmediateNewExtraLocalFiveSecondArgChainInputsCompileAndRunWithJavaParity`
- 子代理聚焦门禁将在最终交接前运行；父代理会重新运行聚焦门禁，并丢弃
  子代理 XML。

## 交付状态

- Processor changed：No
- Ship-ready：No
