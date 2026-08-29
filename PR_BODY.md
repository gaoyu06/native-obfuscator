# Isolated prefix ATHROW constructor handlers / 构造器隔离前缀 ATHROW 处理器

## English

- **(a) Scope:** Admit only isolated suffix-range/prefix-handler rethrow
  sequences `ATHROW` and `ASTORE n; ALOAD n; ATHROW`. The existing prefix-skip,
  target, protected-range, suffix-range-use, and safe-local proofs remain
  required. GOTO-to-rethrow forms remain rejected.
- **(b) Ship-ready? / 可直接上线？** **No.** / **否。**
- **(c) Review focus:** Confirm that the caught exception is preserved exactly,
  slot 0 and non-identity local uses reject before mutation, relocated blocks
  disappear from the bytecode wrapper, and no hidden bridge argument is added.
  Compiler defaults are unchanged.
- **(d) Verification:** Adds direct and store/reload admission tests, JVM
  verification, CMake/g++ JNI parity under `-Xverify:all -Xcheck:jni`, and
  fail-closed negative shapes. The required focused Gradle gate will be recorded
  here after completion.

## 中文

- **(a) 范围：** 仅新增隔离的后缀保护区/前缀处理器重抛序列：
  `ATHROW` 与 `ASTORE n; ALOAD n; ATHROW`。继续强制执行既有的前缀跳过、
  跳转目标、保护区边界、仅由后缀保护区使用及安全局部变量证明。
  通过 `GOTO` 跳转到重抛块的形式仍然拒绝。
- **(b) Ship-ready? / 可直接上线？** **No.** / **否。**
- **(c) 审查重点：** 确认捕获的异常对象被原样保留；slot 0 与非恒等局部变量用法
  在修改前拒绝；迁移块从字节码 wrapper 中移除；且不增加 hidden bridge 参数。
  编译器默认值保持不变。
- **(d) 验证：** 新增直接重抛、存储后重载重抛、JVM 验证、
  `-Xverify:all -Xcheck:jni` 下的 CMake/g++ JNI 一致性测试，以及 fail-closed
  反例。所要求的聚焦 Gradle gate 完成后将在此记录结果。
