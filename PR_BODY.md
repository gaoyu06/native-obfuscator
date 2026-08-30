# Leftover admitted

- Well-formed legacy `jsr` / `ret` subroutines are expanded with ASM's
  `JSRInlinerAdapter` on a private `MethodNode` before CFG construction.
- Ordinary methods admit one or repeated calls to the same subroutine.
- Constructors admit a straight-line prefix subroutine when the inlined clone
  can be placed before the proven `this` / `super` call, then use the existing
  one-hidden-bridge split.
- An inlining failure leaves every original method unchanged; preprocessing
  installs prepared copies only after all selected methods inline successfully.

# Still rejected

- Malformed bytecode such as `ret` outside a `jsr` subroutine.
- Constructor prefix subroutines with exception tables or non-straight-line
  inlined clone control flow when the existing chain-call proof cannot isolate
  them safely.
- All pre-existing IR capability misses outside this isolated leftover.

# Verification

- Passed:
  `CC=gcc CXX=g++ ./gradlew :obfuscator:test --rerun-tasks --tests by.radioegor146.ir.IrCompilerTest --tests by.radioegor146.CodegenModeTest`
- Parsed `obfuscator/build/test-results/test/`: 371 tests, 0 failures,
  0 errors, 0 skipped (`IrCompilerTest`: 364; `CodegenModeTest`: 7).
- Runtime gates use Java 6 classfiles and compare plain HotSpot output with the
  generated CMake/g++ JNI output under `-Xverify:all -Xcheck:jni`.

# 中文

- 已支持：在 CFG 构建前，通过 ASM `JSRInlinerAdapter` 在私有方法副本上展开合法的
  `jsr` / `ret`；普通方法支持单次及重复调用同一子程序；可证明安全的构造器前缀
  直线子程序继续复用现有单隐藏桥接拆分。
- 仍拒绝：无对应 `jsr` 的 `ret` 等畸形字节码，以及带异常表或复杂控制流、无法由
  现有构造器链调用证明安全隔离的前缀子程序。
- 失败关闭：任一内联失败时不安装任何工作副本，也不创建代理或隐藏方法。
- 验证：指定测试共 371 项，0 失败、0 错误、0 跳过；运行时门禁使用 Java 6
  classfile，并在 `-Xverify:all -Xcheck:jni` 下比较 HotSpot 与 JNI 输出。

Ship-ready: **No**
