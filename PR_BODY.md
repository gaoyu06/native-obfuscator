# English

## Summary

- Strengthen the constructor-split unproven-`NEW` rejection test to prove
  instruction identity, generated buffers, hidden-method inventory, and the
  singular `MethodContext.proxyMethod` remain unchanged.
- Add fail-closed coverage for `ANEWARRAY` and `MULTIANEWARRAY` alongside the
  existing uninitialized, constructor-argument, `NEWARRAY`, and
  allocation-type-mismatch shapes.
- Load the untouched verifier-valid reject fixtures as Java 8/classfile-52
  classes and execute all ordinary constructor paths.
- Keep the isolated no-argument `NEW` admission and all codegen defaults
  unchanged.

## Scope

- Admitted: **No**
- Ship-ready: **No**
- Production proof changed: **No**

## Verification

```text
CC=gcc CXX=g++ ./gradlew :obfuscator:test --rerun-tasks \
  --tests by.radioegor146.ir.IrCompilerTest \
  --tests by.radioegor146.CodegenModeTest
```

The raw uninitialized-reference fixture remains rejected but cannot be part of
a successful JVM load because that bytecode is inherently verifier-invalid.
The verifier test covers every other rejected allocation shape.

# 中文

## 摘要

- 加强构造器拆分中未证明 `NEW` 输入的拒绝测试，确认指令对象身份、生成缓冲区、
  隐藏方法清单以及唯一的 `MethodContext.proxyMethod` 在拒绝后保持不变。
- 在已有未初始化对象、带参数构造器、`NEWARRAY` 和分配类型不匹配场景之外，
  增加 `ANEWARRAY` 与 `MULTIANEWARRAY` 的失败关闭覆盖。
- 将所有可通过验证的原始拒绝夹具写成 Java 8/classfile 52 类，加载后执行三个
  普通构造路径。
- 保持已支持的无参独立 `NEW` 以及所有代码生成默认值不变。

## 范围

- 新增准入：**否**
- 可发布：**否**
- 修改生产证明逻辑：**否**

## 验证

```text
CC=gcc CXX=g++ ./gradlew :obfuscator:test --rerun-tasks \
  --tests by.radioegor146.ir.IrCompilerTest \
  --tests by.radioegor146.CodegenModeTest
```

原始未初始化引用夹具仍会被拒绝，但这类字节码本身无法通过 JVM 验证，因此不能
参与成功加载测试。JVM 验证测试覆盖其余所有被拒绝的分配形态。
