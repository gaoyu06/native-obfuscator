# Opcode backend v2 independent review / Opcode 后端 v2 独立审查

Reviewed PR: [#127](https://github.com/gaoyu06/native-obfuscator/pull/127),
`origin/cursor/interpreter-isa-widen-7170` at
`cfef920361f881435c729e9bb084fdb42e596cf1`.

Implementation increment:
`e1996b20677578dd016fc7de47d8f83eb19871dd..5567749b1a5565c632d4f0d5bd7d63807d595ca8`.

Merged `origin/master` reviewed at
`2a6873107bcffbb4961e9fad936b5c3774c530ff`.

## Verdict / 结论

**Accept with nits (docs only). / 接受（附带小问题，仅文档）。**

No blocking correctness bug was found. The v2 table preserves opcode values
1--19 and appends the new integer operations as 20--29. Lowering and dispatch
agree on every value; 32-bit arithmetic wraps without signed C++ overflow;
all shift distances use the JVM `count & 0x1f` rule; and divide/remainder
handle zero and the `Integer.MIN_VALUE`/`-1` edge explicitly. No compiler
source or default was changed by this review.

未发现阻塞性正确性缺陷。v2 表保留操作码 1--19，并将新增整数操作追加为
20--29；lowering 与 dispatcher 对每个值均一致。32 位算术不依赖 C++ 有符号
溢出，所有移位距离都遵循 JVM 的 `count & 0x1f` 规则，除法与取余也显式处理
零除数及 `Integer.MIN_VALUE`/`-1` 边界。本审查未修改编译器源码或默认选项。

## Correctness findings / 正确性结论

- `Main` retains `--backend=cpp` and `--codegen=legacy`. The
  `NativeObfuscator` convenience overloads also retain C++ and legacy defaults;
  interpreter lowering is attempted only for explicit
  `CompilerBackend.INTERPRETER`. /
  `Main` 继续使用 `--backend=cpp` 与 `--codegen=legacy`；`NativeObfuscator`
  的便捷重载同样保留 C++ 与 legacy 默认值。只有显式选择
  `CompilerBackend.INTERPRETER` 才会尝试解释器 lowering。
- Java and C++ both declare ISA version 2. Generated method descriptors embed
  the Java emitter's numeric version, while `execute_i` compares that word
  with the runtime `ISA_VERSION` before reading the stream. The runtime test
  passes a version-1 descriptor and receives `invalid_stream`. /
  Java 与 C++ 均声明 ISA version 2。生成的方法描述符嵌入 Java emitter 的数值
  版本，`execute_i` 在读取操作码流前将该 version word 与运行时
  `ISA_VERSION` 比较；运行时测试传入 version-1 描述符并得到
  `invalid_stream`。
- `IADD`, `ISUB`, `IMUL`, and `INEG` operate through `uint32_t`, then restore
  the signed bit pattern with `memcpy`. This gives JVM two's-complement
  wraparound without C++ signed-overflow undefined behavior. Bitwise
  operations use the same exact 32-bit representation. /
  `IADD`、`ISUB`、`IMUL` 与 `INEG` 通过 `uint32_t` 运算，再用 `memcpy`
  恢复有符号位模式，因此实现 JVM 二进制补码回绕且不触发 C++ 有符号溢出的
  未定义行为；位运算也使用相同的精确 32 位表示。
- `ISHL`, `ISHR`, and `IUSHR` each mask the right operand with `0x1fU`.
  Left and logical-right shifts use unsigned values. Arithmetic right shift
  explicitly fills the high bits and special-cases distance zero, so it does
  not rely on implementation-defined signed shifting or shift by 32. /
  `ISHL`、`ISHR` 与 `IUSHR` 均以 `0x1fU` 屏蔽右操作数。左移和逻辑右移
  使用无符号值；算术右移显式填充高位并单独处理距离为零，因此不依赖实现定义的
  有符号右移，也不会发生移位 32 位。
- `IDIV` and `IREM` check the signed divisor before C++ division. Zero returns
  `arithmetic_exception`; `MIN_VALUE / -1` returns `MIN_VALUE`, and
  `MIN_VALUE % -1` returns zero. Other signed division truncates toward zero,
  matching JVM integer semantics. /
  `IDIV` 与 `IREM` 在执行 C++ 除法前检查有符号除数。零除数返回
  `arithmetic_exception`；`MIN_VALUE / -1` 返回 `MIN_VALUE`，
  `MIN_VALUE % -1` 返回零；其他有符号除法向零截断，与 JVM 整数语义一致。
- The JNI trampoline maps only `arithmetic_exception` to
  `utils::throw_re(..., "java/lang/ArithmeticException", ...)` and returns the
  JNI zero value while the exception remains pending. Invalid streams still
  follow the distinct fatal-error path. /
  JNI 跳板仅将 `arithmetic_exception` 映射到
  `utils::throw_re(..., "java/lang/ArithmeticException", ...)`，并在异常保持
  pending 时返回 JNI 零值；无效操作码流仍走独立的 fatal-error 路径。
- Unsupported methods still fall back per method to the active legacy or IR
  code generator. The default C++ path does not copy, compile, or call the
  interpreter runtime. /
  不支持的方法仍逐方法回退到当前 legacy 或 IR code generator；默认 C++ 路径
  不复制、不编译也不调用解释器运行时。

## Independent verification / 独立验证

Both requested commands were run with `CC=gcc CXX=g++`:

```text
./gradlew :obfuscator:test \
  --tests by.radioegor146.ir.IrCompilerTest \
  --tests by.radioegor146.CodegenModeTest
```

Result: `BUILD SUCCESSFUL`.

```text
IrCompilerTest: tests=91, skipped=0, failures=0, errors=0
CodegenModeTest: tests=5, skipped=0, failures=0, errors=0
```

```text
./gradlew :obfuscator:test \
  --tests by.radioegor146.MainBackendOptionTest \
  --tests by.radioegor146.interpreter.InterpreterMethodEmitterTest \
  --tests by.radioegor146.interpreter.InterpreterRuntimeTest \
  --tests by.radioegor146.interpreter.InterpreterBackendIntegrationTest
```

Result: `BUILD SUCCESSFUL`.

```text
MainBackendOptionTest: tests=2, skipped=0, failures=0, errors=0
InterpreterMethodEmitterTest: tests=8, skipped=0, failures=0, errors=0
InterpreterRuntimeTest: tests=1, skipped=0, failures=0, errors=0
InterpreterBackendIntegrationTest: tests=2, skipped=0, failures=0, errors=0
```

两条指定命令均在 `CC=gcc CXX=g++` 环境下成功。第一组为 91 项
`IrCompilerTest` 与 5 项 `CodegenModeTest`；第二组依次为 2、8、1、2 项，
全部 0 skipped、0 failures、0 errors。`InterpreterRuntimeTest` 未跳过，
因此 C++17 dispatcher 的 `-Wall -Wextra -Werror` 编译与执行确实发生。

I also built the runnable obfuscator, compiled `DefaultOffFixture`, and
generated its output twice from the same input:

```text
java -jar obfuscator.jar fixture.jar no-flag
java -jar obfuscator.jar fixture.jar explicit-cpp --backend=cpp
diff -r no-flag/cpp explicit-cpp/cpp
```

`diff -r` exited 0 with no output. An explicit interpreter generation was then
configured and built with GCC/G++; CMake compiled `native_jvm_interp.cpp` and
finished with `[100%] Built target native_library`.

另以同一输入分别省略 `--backend` 和显式指定 `--backend=cpp` 生成结果；
完整 `cpp/` 目录的 `diff -r` 以 0 退出且无输出。随后使用 GCC/G++ 配置并构建
显式解释器输出；CMake 实际编译 `native_jvm_interp.cpp`，最终显示
`[100%] Built target native_library`。

## Nits / 小问题

The generated JNI exception boundary is covered compositionally rather than
end to end: the runtime test executes zero-divisor `IDIV` and `IREM` and checks
`arithmetic_exception`, while the integration test inspects the generated
`ArithmeticException` call. A future test that loads the generated shared
library and invokes both transformed methods with zero divisors would pin the
pending-JNI-exception behavior in one executable assertion. The code path is
straightforward and correct on inspection, so this is not blocking.

生成 JNI 异常边界目前采用分段覆盖而非端到端覆盖：运行时测试执行零除数
`IDIV`/`IREM` 并检查 `arithmetic_exception`，集成测试则检查生成的
`ArithmeticException` 调用。后续可增加测试，加载生成的共享库并以零除数调用
两个转换后方法，从而用单个可执行断言固定 pending JNI exception 行为。代码路径
经审查是直接且正确的，因此该问题不阻塞接受。

## (a)(b)(c)(d) / （a）（b）（c）（d）

- **(a) Scope / 范围:** Accept the default-off ISA-v2 integer increment:
  multiply, bitwise operations, three shifts, negate, divide, and remainder,
  with versioned side tables, a shared C++17 dispatcher, JNI trampolines, and
  per-method fallback. /
  接受默认关闭的 ISA-v2 整数增量：乘法、位运算、三种移位、取负、除法与取余，
  以及带版本的方法侧表、共享 C++17 dispatcher、JNI 跳板和逐方法回退。
- **(b) Ship-ready? / 可直接发布？:** **No / 否。** This remains an optional,
  deliberately limited integer backend slice rather than a complete backend. /
  这仍是可选且刻意受限的整数后端子集，并非完整后端。
- **(c) Review result / 审查结果:** Accept with the non-blocking end-to-end
  JNI test nit above. No compiler fix and no default change are requested. /
  接受，并保留上述不阻塞的 JNI 端到端测试小问题；不要求修复编译器，也不更改
  默认选项。
- **(d) Integration evidence / 集成证据:** Both requested GCC/G++ Gradle
  suites passed with no skipped or failed tests; omitted and explicit C++
  generation matched by complete-tree `diff -r`; and the generated interpreter
  shared library built successfully. /
  两组指定的 GCC/G++ Gradle 测试均通过且无跳过或失败；省略后端参数与显式 C++
  生成结果通过完整目录 `diff -r` 验证一致；生成的解释器共享库也构建成功。
