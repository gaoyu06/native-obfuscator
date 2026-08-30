# English

## Summary

- Strengthen the constructor-split unproven-`GETFIELD` rejection test to prove
  instruction identity, generated buffers, hidden-method inventory, and the
  singular `MethodContext.proxyMethod` remain unchanged.
- Keep local 0, an extra-local copy of `this`, overwritten source and
  extra-local holders, computed holders, field-carrier mismatch, and
  `GETSTATIC` fail closed.
- Load the untouched verifier-valid reject fixtures as Java 8/classfile-52
  classes and execute all three ordinary constructor paths with distinct
  replacement, computed, and static field values.
- Keep the admitted direct and proven extra-local object/primitive `GETFIELD`
  shapes and the legacy codegen default unchanged.

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

JUnit XML: 480 `IrCompilerTest` tests plus 7 `CodegenModeTest` tests; 487 total,
with 0 failures, 0 errors, and 0 skipped.

Direct or extra-local `GETFIELD` reads from uninitialized `this`, and passing
an int field to an Object constructor parameter, remain rejected but cannot be
part of a successful JVM load because those bytecode shapes are inherently
verifier-invalid. The JVM-verification test covers every other rejected
`GETFIELD` shape.

# 中文

## 摘要

- 加强构造器拆分中未证明 `GETFIELD` 输入的拒绝测试，确认指令对象身份、生成
  缓冲区、隐藏方法清单以及唯一的 `MethodContext.proxyMethod` 在拒绝后保持不变。
- 继续以失败关闭方式拒绝 local 0、`this` 的额外局部变量副本、被覆盖的源持有者
  和额外局部变量持有者、计算得到的持有者、字段载体不匹配以及 `GETSTATIC`。
- 将所有可通过验证的原始拒绝夹具写成 Java 8/classfile 52 类，加载后使用不同的
  替换值、计算值和静态字段值执行三个普通构造路径。
- 保持已准入的直接及已证明额外局部变量对象/基本类型 `GETFIELD` 形态和 legacy
  代码生成默认值不变。

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

JUnit XML：`IrCompilerTest` 480 项，`CodegenModeTest` 7 项，共 487 项；
0 项失败、0 项错误、0 项跳过。

直接或通过额外局部变量对未初始化 `this` 执行 `GETFIELD`，以及把 int 字段传给
Object 构造器参数的字节码本身无法通过 JVM 验证，因此不能参与成功加载测试，但
仍保持拒绝。JVM 验证测试覆盖其余所有被拒绝的 `GETFIELD` 形态。
