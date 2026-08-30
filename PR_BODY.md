# Admit proven extra-local int long-shift counts

Ship-ready: **No**

## English

### Summary

- Thread the existing proven prefix int-copy leaf set into constructor long-chain operand validation.
- Admit `ILOAD <proven-extra-local>` as the retained JVM count operand for `LSHL`, `LSHR`, and `LUSHR`.
- Keep computed, negated, unproven extra-local int counts and type-wrong extra-local long counts fail-closed.
- Keep long shifts in the retained constructor bytecode and preserve one hidden bridge per constructor; no shift-count masking is emitted in C++.

### Tests

- Added focused admission, rewritten JVM-verification, and CMake/g++ JNI Java-parity coverage for all three long shifts.
- Required Gradle verification: pending.

### Scope

- Production goal completion: **No**
- GitHub PR created or updated: **No** (not permitted for this task)

## 中文

### 摘要

- 将已有的构造器前缀 int 复制叶节点证明传入 long 链操作数校验。
- 允许 `ILOAD <已证明额外局部变量>` 作为保留在 JVM 构造器前缀中的 `LSHL`、`LSHR`、`LUSHR` 位移计数。
- 计算所得、取负、未证明的额外局部 int 计数以及类型错误的额外局部 long 计数仍然拒绝。
- long 位移继续保留在构造器字节码中，每个构造器仅使用一个隐藏 bridge；C++ 不生成位移计数 mask-63 逻辑。

### 测试

- 已为三种 long 位移增加准入、重写后 JVM 验证、CMake/g++ JNI Java 一致性测试。
- 必需的 Gradle 验证：待运行。

### 范围

- 生产目标完成：**否**
- 创建或更新 GitHub PR：**否**（本任务不允许）
