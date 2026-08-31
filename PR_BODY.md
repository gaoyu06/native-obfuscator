# test: admit extra-local int as second, third, and fourth five-arg NEW arguments

## English

Fixture-only IR admission for a five-argument `GregorianCalendar` constructor whose second, third, and fourth initializer arguments come from the same extra local (`ICONST_1; ILOAD 3; ILOAD 3; ILOAD 3; ICONST_5`).

- Adds admission, JVM verification, and Java/native parity tests
- Processor unchanged; retained `NEW; DUP; args; <init>` stays in the JVM prefix
- Native body remains `(II)V` / `RETURN` only; one hidden bridge
- Runtime verify: `threeImmediateNewExtraLocalFiveSecondThirdFourthArgChainInputsCompileAndRunWithJavaParity`
- Ship-ready: No. Does not flip `--codegen` off `legacy`.

## 中文

仅测试夹具的 IR 准入：五参数 `GregorianCalendar` 构造器的第二、第三、第四个初始化参数均来自同一个额外局部变量（`ICONST_1; ILOAD 3; ILOAD 3; ILOAD 3; ICONST_5`）。

- 新增准入、JVM 校验及 Java/native 一致性测试
- 处理器未改；保留的 `NEW; DUP; args; <init>` 仍在 JVM 前缀
- 原生方法体仍为 `(II)V` / 仅 `RETURN`；一条隐藏桥
- 运行时校验：`threeImmediateNewExtraLocalFiveSecondThirdFourthArgChainInputsCompileAndRunWithJavaParity`
- 可发布：否。不把 `--codegen` 从 `legacy` 改走。
