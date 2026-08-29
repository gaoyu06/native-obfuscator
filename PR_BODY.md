## English

(a) Change

Admit primitive Class `LDC` constants (`Z/B/C/S/I/F/J/D/V`) on the optional
typed CFG IR path. The frontend rewrites them on a private method copy to
`Boolean.TYPE`, `Byte.TYPE`, `Character.TYPE`, `Short.TYPE`, `Integer.TYPE`,
`Float.TYPE`, `Long.TYPE`, `Double.TYPE`, and `Void.TYPE`.

(b) Lowering and safety

The replacement bytecode is `GETSTATIC ... TYPE:Ljava/lang/Class;`, so it uses
the existing typed static-field IR and JNI `GetStaticFieldID` /
`GetStaticObjectField` path. The caller's `MethodNode` is not modified by the
frontend rewrite. Existing `jsr` reject-before-mutation sentinels remain, and
constructor leftover rules are unchanged. No interpreter/evaluator work and no
`native.magic` path were added.

(c) Validation

```text
CC=gcc CXX=g++ ./gradlew :obfuscator:test --rerun-tasks \
  --tests by.radioegor146.ir.IrCompilerTest \
  --tests by.radioegor146.CodegenModeTest
```

Result: `BUILD SUCCESSFUL`.

JUnit XML:

```text
IrCompilerTest: tests=142, skipped=0, failures=0, errors=0 (time=17.517 s)
CodegenModeTest: tests=7, skipped=0, failures=0, errors=0 (time=0.665 s)
Total: tests=149, skipped=0, failures=0, errors=0
```

The new CMake/g++ runtime test passes with `-Xverify:all -Xcheck:jni` and
matches HotSpot wrapper-`TYPE` identity/name output, including
`int.class == Integer.TYPE`.

(d) Release posture

- Ship-ready: **No**
- Stacked review: **No**
- Default flip: **No** (`codegen=legacy`, direct IR lowering, and C++ backend
  defaults remain unchanged)

## 中文

(a) 变更

可选 typed CFG IR 路径现在接收原始类型 Class `LDC`
（`Z/B/C/S/I/F/J/D/V`）。前端只在方法私有副本上改写，分别读取
`Boolean.TYPE`、`Byte.TYPE`、`Character.TYPE`、`Short.TYPE`、
`Integer.TYPE`、`Float.TYPE`、`Long.TYPE`、`Double.TYPE` 和
`Void.TYPE`。

(b) 降低路径与安全性

改写后的字节码为 `GETSTATIC ... TYPE:Ljava/lang/Class;`，复用现有
typed static-field IR 以及 JNI `GetStaticFieldID` /
`GetStaticObjectField` 路径。前端改写不会修改调用方的 `MethodNode`。
现有 `jsr` 拒绝前不变异哨兵仍保留，构造器残余规则未改变。没有新增
interpreter/evaluator 工作，也没有新增 `native.magic` 路径。

(c) 验证

```text
CC=gcc CXX=g++ ./gradlew :obfuscator:test --rerun-tasks \
  --tests by.radioegor146.ir.IrCompilerTest \
  --tests by.radioegor146.CodegenModeTest
```

结果：`BUILD SUCCESSFUL`。

JUnit XML：

```text
IrCompilerTest: tests=142, skipped=0, failures=0, errors=0 (time=17.517 s)
CodegenModeTest: tests=7, skipped=0, failures=0, errors=0 (time=0.665 s)
Total: tests=149, skipped=0, failures=0, errors=0
```

新增的 CMake/g++ 运行测试在 `-Xverify:all -Xcheck:jni` 下通过，并与
HotSpot 的 wrapper `TYPE` 身份与名称输出一致，其中包括
`int.class == Integer.TYPE`。

(d) 发布状态

- Ship-ready：**No**
- Stacked review：**No**
- 默认切换：**No**（`codegen=legacy`、direct IR lowering 和 C++
  backend 的默认值均未改变）
