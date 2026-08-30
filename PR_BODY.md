# Summary / 摘要

## English

- Adds the fixture-only IR admission shape
  `new-constructor-extra-local-argument-five-first-second`.
- Retains this constructor leaf in JVM bytecode:
  `NEW java/util/GregorianCalendar; DUP; ILOAD 3; ILOAD 3; ICONST_3; ICONST_4; ICONST_5; INVOKESPECIAL <init>(IIIII)V`.
- Covers the `(II)V` constructor prefix `ILOAD 2; ISTORE 3`, three
  immediate constructor-chain paths, JVM verification, and Java/native
  runtime parity.
- Keeps the native constructor body to `RETURN`, uses proxy descriptor
  `(Ljava/lang/Object;II)V`, and checks exactly one hidden bridge.
- Does not change `ConstructorSpecialMethodProcessor` or other production
  compiler/runtime code. Codegen defaults remain unchanged:
  `--codegen=legacy`.
- Ship-ready: **No**.

## 中文

- 新增仅测试夹具的 IR 准入形状
  `new-constructor-extra-local-argument-five-first-second`。
- JVM 字节码中保留以下构造器叶节点：
  `NEW java/util/GregorianCalendar; DUP; ILOAD 3; ILOAD 3; ICONST_3; ICONST_4; ICONST_5; INVOKESPECIAL <init>(IIIII)V`。
- 覆盖 `(II)V` 构造器前缀 `ILOAD 2; ISTORE 3`、三条立即构造器链路径、
  JVM 校验以及 Java/native 运行结果一致性。
- native 构造器方法体仅包含 `RETURN`，代理描述符为
  `(Ljava/lang/Object;II)V`，并验证恰好一个隐藏桥接方法。
- 未修改 `ConstructorSpecialMethodProcessor` 或其他生产编译器/运行时代码。
  代码生成默认值保持为 `--codegen=legacy`。
- 可发布状态（Ship-ready）：**No**。

# Validation / 验证

- Test annotation invariant / 测试注解不变量:
  `634 @Test = 634 public void`.
- Focused child gate:
  `CC=gcc CXX=g++ ./gradlew :obfuscator:test --rerun-tasks --tests by.radioegor146.ir.IrCompilerTest --tests by.radioegor146.CodegenModeTest`
  — passed.
- All three added tests passed:
  `admitsThreeImmediateReturnsWithNewExtraLocalFiveFirstSecondArgChainInputs`,
  `rewrittenThreeImmediateNewExtraLocalFiveFirstSecondArgChainInputsPassJvmVerification`,
  and
  `threeImmediateNewExtraLocalFiveFirstSecondArgChainInputsCompileAndRunWithJavaParity`.
- Disposable child XML reported `641` tests, `0` skipped, `0` failures,
  and `0` errors (`IrCompilerTest`: `634`; `CodegenModeTest`: `7`).
  These are not parent totals; the parent will discard this XML and rerun the
  gate, where the expected XML total is `634 + 7 = 641`.
- 三个新增测试均通过。一次性子代理 XML 共报告 `641` 个测试，
  `0` 跳过、`0` 失败、`0` 错误（`IrCompilerTest`：`634`；
  `CodegenModeTest`：`7`）。这不是父代理统计；父代理会丢弃此次 XML
  并重新运行门禁，预期 XML 总数为 `634 + 7 = 641`。
