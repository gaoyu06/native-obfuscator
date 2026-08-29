# IR compiler phase 13 / IR 编译器第十三阶段

Required base / 必须基于:
`cursor/ir-phase12-sol-review-6d81`
(`481b7b108388380bfbbdf94703ee56eb4b601b02`,
[draft PR #89](https://github.com/gaoyu06/native-obfuscator/pull/89)).
This is the preferred phase-12 tip with the constructor prefix-local
correction; the unfixed phase-12 branch and alternate review branch are not
used. /
必须基于 `cursor/ir-phase12-sol-review-6d81`
（`481b7b108388380bfbbdf94703ee56eb4b601b02`，
[草稿 PR #89](https://github.com/gaoyu06/native-obfuscator/pull/89)）。
这是包含构造函数 prefix-local 修正的首选 phase-12 tip；不使用未修正的
phase-12 分支或另一审阅分支。

## Summary / 摘要

Phase 13 admits `Z`, `B`, `C`, and `S` field and invoke descriptors into the
optional Java bytecode → typed CFG IR → C++/JNI compiler. Operand-stack and
local carriers remain `I32`, while field access and method calls use the exact
Boolean/Byte/Char/Short JNI families. `F` and `D` remain unsupported. The
default remains `legacy`.

第十三阶段将 `Z`、`B`、`C`、`S` 字段与调用描述符纳入可选的 Java 字节码 →
typed CFG IR → C++/JNI 编译路径。操作数栈与局部变量 carrier 仍为 `I32`，
字段访问和方法调用则使用精确的 Boolean/Byte/Char/Short JNI family。`F` 与
`D` 仍不受支持。默认值仍为 `legacy`。

## (a) Change scope / 本次改动范围

- Admits `Z`/`B`/`C`/`S` field descriptors for instance and static
  `GETFIELD`/`PUTFIELD`/`GETSTATIC`/`PUTSTATIC`.
- Emits exact `Get/Set[Static]BooleanField`,
  `Get/Set[Static]ByteField`, `Get/Set[Static]CharField`, and
  `Get/Set[Static]ShortField` accessors.
- Keeps heap values as `I32` in IR. Reads explicitly widen JNI results:
  byte/short sign-extend and boolean/char zero-extend.
- Narrows field writes explicitly: boolean uses `(uint32_t)value & 1`,
  byte/short truncate through `jbyte`/`jshort`, and char truncates to the low
  16 bits through `jchar`.
- Admits these four argument and return sorts for static, virtual, interface,
  and special invokes. Arguments are narrowed before JNI varargs calls, and
  results are widened back to `I32`.
- Selects exact `Call[Static|Nonvirtual]Boolean/Byte/Char/ShortMethod`
  families from the return descriptor. Existing Void/Int/Long/Object families
  are unchanged.
- Preserves the constructor special-void path and
  `CallNonvirtualVoidMethod`, including the verifier-safe bridge and retained
  `this(...)`/`super(...)` prefix.
- Continues to reject `F` and `D` field/invoke descriptors before mutation.
- Adds field cases for boolean false/true, negative byte/short, and char 200;
  all four invoke forms and sorts; null-receiver exits; phase-13
  fallback-before-mutation; and the expanded real-g++ smoke unit.
- Preserves phase-9 array returns, phase-10 `I`/`J`/reference fields,
  phase-11 interface/special invokes, phase-12 constructor and prefix-local
  regressions, the `legacy` default, and all snippet resources.

- 接受实例与静态 `GETFIELD`/`PUTFIELD`/`GETSTATIC`/`PUTSTATIC` 的
  `Z`/`B`/`C`/`S` 字段描述符。
- 生成精确的 `Get/Set[Static]BooleanField`、
  `Get/Set[Static]ByteField`、`Get/Set[Static]CharField` 与
  `Get/Set[Static]ShortField` accessor。
- IR 中的堆值仍使用 `I32`。读取 JNI 结果时显式扩展：byte/short 符号扩展，
  boolean/char 零扩展。
- 字段写入显式窄化：boolean 使用 `(uint32_t)value & 1`，byte/short 通过
  `jbyte`/`jshort` 截断，char 通过 `jchar` 保留低 16 位。
- 接受 static、virtual、interface、special 调用中的这四种参数与返回 sort。
  参数在 JNI varargs 调用前窄化，返回结果扩展回 `I32`。
- 根据返回描述符选择精确的
  `Call[Static|Nonvirtual]Boolean/Byte/Char/ShortMethod` family；现有
  Void/Int/Long/Object family 不变。
- 保留构造函数 special-void 路径及 `CallNonvirtualVoidMethod`，包括
  verifier-safe bridge 与保留的 `this(...)`/`super(...)` 前缀。
- `F`、`D` 字段/调用描述符继续在 mutation 前被拒绝。
- 新增 boolean false/true、负 byte/short、char 200 字段用例，四种调用形态与
  四种 sort、空接收者出口、phase-13 mutation 前 fallback，以及扩展后的真实
  g++ smoke unit。
- 保留 phase-9 数组返回、phase-10 `I`/`J`/引用字段、phase-11
  interface/special 调用、phase-12 构造函数与 prefix-local 回归、`legacy`
  默认值及全部 snippet resource。

## (b) Can this ship to production as-is? / 是否可直接上线？

**No / 否。**

This remains a partial, opt-in compiler slice. Float/double operations and
descriptors, non-`int` primitive arrays, `MULTIANEWARRAY`, `POP2`,
`invokedynamic`, and other operations outside the documented subset still
fall back. Focused unit tests and C++ syntax checks do not replace native
runtime-parity gates on every supported platform.

这仍是部分、可选的编译器增量。float/double 运算及描述符、非 `int` primitive
array、`MULTIANEWARRAY`、`POP2`、`invokedynamic` 及文档范围外的其他操作仍会
fallback。聚焦单测与 C++ 语法检查不能替代全部受支持平台上的 native
运行时等价性门禁。

## (c) Is review required? / 上线前是否需要 review？

**Yes / 是。**

Review must confirm descriptor-driven JNI family selection, JVM-compatible
narrowing, byte/short sign extension, char zero extension, exact invoke
argument order, null-receiver exceptional exits, and rejection before
mutation. The constructor bridge and stacked phase-9 through phase-12
regressions must remain unchanged.

Review 必须确认由描述符驱动的 JNI family 选择、符合 JVM 的窄化、
byte/short 符号扩展、char 零扩展、精确的 invoke 参数顺序、空接收者异常出口，
以及 mutation 前拒绝。构造函数 bridge 与堆叠的 phase-9 至 phase-12 回归必须
保持不变。

## (d) Review preconditions / Review 前置条件

1. Compare against `cursor/ir-phase12-sol-review-6d81` at
   `481b7b108388380bfbbdf94703ee56eb4b601b02`
   ([draft PR #89](https://github.com/gaoyu06/native-obfuscator/pull/89)).
   Do not use the unfixed phase-12 branch, alternate review branch, or
   `master`.
   必须与 `cursor/ir-phase12-sol-review-6d81` 的
   `481b7b108388380bfbbdf94703ee56eb4b601b02`
   （[草稿 PR #89](https://github.com/gaoyu06/native-obfuscator/pull/89)）
   比较，不得改用未修正的 phase-12 分支、另一审阅分支或 `master`。
2. Re-run:

   ```text
   CC=gcc CXX=g++ ./gradlew :obfuscator:test \
     --tests by.radioegor146.ir.IrCompilerTest \
     --tests by.radioegor146.CodegenModeTest \
     --rerun-tasks
   ```

   Read the counts from JUnit XML. Recorded result:
   `IrCompilerTest` 62 plus `CodegenModeTest` 2, total 64; zero skipped,
   failures, or errors.
   从 JUnit XML 读取计数。记录结果：`IrCompilerTest` 62 加
   `CodegenModeTest` 2，共 64；跳过、失败、错误均为零。
3. With g++ and JNI headers present, require
   `generatedCppPassesGppSyntaxCheckWhenToolchainAvailable` to be unskipped.
   It ran in 0.281 s. Independently run `g++ -std=c++17 -fsyntax-only` on
   `/tmp/ir-compile-smoke7831278959029876564/ir-smoke.cpp`; the retained unit
   has 87 `JNICALL` functions and the independent check exited zero with empty
   diagnostics.
   当 g++ 与 JNI header 存在时，必须确认
   `generatedCppPassesGppSyntaxCheckWhenToolchainAvailable` 未跳过；本次运行
   耗时 0.281 s。还需对
   `/tmp/ir-compile-smoke7831278959029876564/ir-smoke.cpp` 独立执行
   `g++ -std=c++17 -fsyntax-only`；该保留 unit 含 87 个 `JNICALL` 函数，
   独立检查以零退出且无诊断。
4. Inspect emitted field access for all instance/static `Z`/`B`/`C`/`S`
   families. Confirm reads widen to `jint`, boolean writes use low-bit
   narrowing, byte/short writes truncate with signed JNI types, and char writes
   truncate with `jchar`.
   检查全部实例/静态 `Z`/`B`/`C`/`S` 字段 family。确认读取扩展为 `jint`，
   boolean 写入按低位窄化，byte/short 通过有符号 JNI 类型截断，char 通过
   `jchar` 截断。
5. Inspect static, virtual, interface, and special invokes for every new sort.
   Confirm the exact Call family, descriptor-order argument narrowing, `I32`
   result carrier, and unchanged constructor `CallNonvirtualVoidMethod`.
   检查每种新 sort 的 static、virtual、interface、special 调用。确认精确的
   Call family、按描述符顺序的参数窄化、`I32` 返回 carrier，以及未改变的
   构造函数 `CallNonvirtualVoidMethod`。
6. Inspect the phase-13 fallback regression. After valid new field and invoke
   forms followed by an unsupported float instruction, the method, generated
   output, native metadata, and all four caches must remain unchanged.
   检查 phase-13 fallback 回归：有效的新字段及调用形态之后出现不支持的 float
   指令时，方法、生成输出、native metadata 与四类 cache 必须保持不变。
7. Verify source and tests still retain the `legacy` CLI/API default, phase-9
   `jarray` return cast, phase-10 fields, phase-11 invokes, phase-12
   constructor bridge and prefix-local rejection, and
   `sources/cppsnippets.properties`.
   确认源码与测试仍保留 `legacy` CLI/API 默认值、phase-9 `jarray` 返回转换、
   phase-10 字段、phase-11 调用、phase-12 构造函数 bridge 与 prefix-local
   拒绝，以及 `sources/cppsnippets.properties`。

Detailed evidence / 详细证据:
`docs/architecture/ir-phase13-status.md`.
