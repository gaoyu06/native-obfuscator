# Constructor prefix extra-local forwarding / 构造器前缀额外局部变量转发

## (a) Scope / 范围

English:

- Forward non-parameter locals written before the constructor this/super call
  and read by the initialized-this suffix through trailing hidden-bridge
  parameters.
- Require definite assignment and one compatible type on every prefix CFG path
  reaching the chain call.
- Preserve original local indexes, exact `I`/`J`/`F`/`D` primitive descriptors,
  category-2 slots, and `Object` reference carriers.
- Generate the independent suffix descriptor, native bridge descriptor, wrapper
  loads, and C++ JNI signature from the same forwarded-local layout.

中文：

- 将构造器 this/super 调用前写入、并由 initialized-this 后缀读取的非参数局部
  变量，作为隐藏桥接方法的尾部参数进行转发。
- 要求到达链式构造调用的每一条前缀 CFG 路径都完成确定赋值，并且类型一致且
  可证明。
- 保持原局部变量索引、`I`/`J`/`F`/`D` 原始类型描述符、二槽位布局以及
  `Object` 引用载体。
- 后缀描述符、隐藏桥接描述符、包装器加载和 C++ JNI 签名使用同一局部变量
  布局。

## (b) Ship-ready / 可发布

**No / 否。**

English: This is one bounded constructor-split admission increment, not a claim
that all constructor bytecode shapes are supported.

中文：这只是一个有边界的构造器拆分准入增量，不表示所有构造器字节码形态都
已支持。

## (c) Review and executed gate / 审查与已执行门禁

English: No stacked code-only review is requested. The gate is the executed
focused suite, including plain-Java/native Java parity with generated CMake C++
and `-Xverify:all -Xcheck:jni`.

中文：不要求叠加式的纯代码审查。门禁以实际执行的聚焦测试套件为准，其中
包含生成 CMake C++ 后的普通 Java/原生 Java 一致性测试，以及
`-Xverify:all -Xcheck:jni`。

```text
CC=gcc CXX=g++ ./gradlew :obfuscator:test --rerun-tasks \
  --tests by.radioegor146.ir.IrCompilerTest \
  --tests by.radioegor146.CodegenModeTest
```

JUnit XML:

- `IrCompilerTest`: 126 tests, 0 failures, 0 errors, 0 skipped.
- `CodegenModeTest`: 7 tests, 0 failures, 0 errors, 0 skipped.
- Total: 133 tests, 0 failures, 0 errors, 0 skipped.

## (d) Preconditions and unchanged boundaries / 前置条件与未变边界

English:

- Prefix `ASTORE 0` remains rejected.
- Prefix branch/switch targets in the suffix remain rejected.
- Try/catch regions crossing the split remain rejected.
- Multiple this/super candidates and `jsr`/`ret` remain rejected.
- Prefix extras not definitely assigned at the chain call remain rejected before
  mutation.
- CLI defaults remain `--codegen legacy`, `--ir-lower direct`, and
  `--backend cpp`.

中文：

- 前缀 `ASTORE 0` 仍然拒绝。
- 指向后缀的前缀分支/开关目标仍然拒绝。
- 跨越拆分点的 try/catch 区域仍然拒绝。
- 多个 this/super 候选以及 `jsr`/`ret` 仍然拒绝。
- 在链式构造调用处未确定赋值的前缀额外局部变量，会在任何修改前拒绝。
- CLI 默认值保持 `--codegen legacy`、`--ir-lower direct` 和
  `--backend cpp`。
