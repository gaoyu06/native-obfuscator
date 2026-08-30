## English

### Summary

- Admit isolated constructor-chain inputs of the form `NEW owner; DUP; arg1; arg2; INVOKESPECIAL owner.<init>(II)V` when both arguments are single-instruction proven int-family leaves and the allocated type exactly matches the chain-call argument.
- Keep the complete allocation and initialization sequence in the retained JVM bytecode prefix; the native body contains neither that `NEW` nor its initializer call.
- Reuse `new-constructor-two-arguments` as the admission fixture and retain one hidden bridge through the singular `MethodContext.proxyMethod`.
- Keep all code-generation defaults on `legacy`; this does not admit wide initializer arguments, computed initializer inputs, three-or-more initializer arguments, array allocation, or skip-super control flow.

### Tests

The required focused Gradle gate and JUnit XML totals will be recorded after the pre-test commit.

### Readiness

Ship-ready: **No**

## 中文

### 摘要

- 当两个参数都是已证明的单指令 int-family 叶子，且分配类型与构造器链调用参数完全一致时，接纳隔离的 `NEW owner; DUP; arg1; arg2; INVOKESPECIAL owner.<init>(II)V` 构造器链输入。
- 完整的对象分配和初始化序列保留在 JVM 字节码前缀中；native 方法体不包含该 `NEW` 或初始化调用。
- 将 `new-constructor-two-arguments` 从拒绝夹具改为接纳夹具，并通过唯一的 `MethodContext.proxyMethod` 保持一个隐藏桥接方法。
- 所有代码生成默认值仍为 `legacy`；本次不接纳宽类型初始化参数、计算型初始化输入、三个及以上初始化参数、数组分配或 skip-super 控制流。

### 测试

预测试提交完成后，将运行要求的 Gradle 聚焦门禁并记录 JUnit XML 汇总。

### 就绪状态

可发布：**否**
