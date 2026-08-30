# Summary / 摘要

## English

- Adds the fixture-only IR admission shape
  `new-constructor-extra-local-argument-five-fourth`.
- Retains this constructor leaf in JVM bytecode:
  `NEW java/util/GregorianCalendar; DUP; ICONST_1; ICONST_2; ICONST_3; ILOAD 3; ICONST_5; INVOKESPECIAL <init>(IIIII)V`.
- Covers the `(II)V` constructor prefix `ILOAD 2; ISTORE 3`, three
  immediate constructor-chain paths, JVM verification, and Java/native
  runtime parity.
- Keeps the native constructor body to `RETURN`, uses proxy descriptor
  `(Ljava/lang/Object;II)V`, and checks exactly one hidden bridge.
- Does not change `ConstructorSpecialMethodProcessor` or other production
  compiler code. Codegen defaults remain unchanged:
  `--codegen=legacy`, without forcing `--ir-lower=direct` or
  `--backend=cpp`.
- Ship-ready: **No**.

## 中文

- 新增仅测试夹具的 IR 准入形状
  `new-constructor-extra-local-argument-five-fourth`。
- JVM 字节码中保留以下构造器叶节点：
  `NEW java/util/GregorianCalendar; DUP; ICONST_1; ICONST_2; ICONST_3; ILOAD 3; ICONST_5; INVOKESPECIAL <init>(IIIII)V`。
- 覆盖 `(II)V` 构造器前缀 `ILOAD 2; ISTORE 3`、三条立即构造器链路径、
  JVM 校验以及 Java/native 运行结果一致性。
- native 构造器方法体仅包含 `RETURN`，代理描述符为
  `(Ljava/lang/Object;II)V`，并验证恰好一个隐藏桥接方法。
- 未修改 `ConstructorSpecialMethodProcessor` 或其他生产编译器代码。
  代码生成默认值保持不变：`--codegen=legacy`，且不强制
  `--ir-lower=direct` 或 `--backend=cpp`。
- 可发布状态（Ship-ready）：**No**。

# Validation / 验证

- Test annotation invariant / 测试注解不变量:
  `625 @Test = 625 public void`.
- The focused Gradle gate will be run before handoff; child XML totals will
  be recorded here after that run. The parent will rerun the focused gate and
  discard the child XML.
- 交付前将运行聚焦的 Gradle 门禁；运行后会在此记录子代理 XML 总数。
  父代理会重新运行该门禁，并丢弃子代理 XML。
