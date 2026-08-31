# test: admit extra-local int as first, second, and fifth five-arg NEW arguments

## English

Adds fixture-only IR admission coverage for a GregorianCalendar `NEW` initializer whose first, second, and fifth arguments load the extra-local int. The admission, JVM-verification, and compile-and-run parity fixtures cover the exact bytecode/CFG/JNI shape.

- Processor unchanged
- Expected parent XML total: **677**
- Ship-ready: **No**
- `--codegen` remains `legacy`

## 中文

新增仅测试夹具的 IR 准入覆盖：GregorianCalendar `NEW` 初始化器的第一、第二和第五个参数从额外的 int 局部变量加载。准入、JVM 验证和编译运行一致性夹具覆盖该精确的字节码/CFG/JNI 形状。

- 处理器未修改
- 预期父分支 XML 总数：**677**
- 可发布：**否**
- `--codegen` 仍为 `legacy`
