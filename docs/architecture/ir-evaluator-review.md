# IR shared evaluator backend — compiler review

Reviewer: GPT-5.6 Sol.

Subject: [`cursor/ir-evaluator-backend-6d81-875b` (PR #42)](https://github.com/gaoyu06/native-obfuscator/pull/42),
based on [`cursor/ir-phase4-fable-review-6d81` (PR #39)](https://github.com/gaoyu06/native-obfuscator/pull/39).
The subject is a sibling of phase 5, not a descendant of it. Design hook:
`docs/architecture/ir-compiler.md` §9.3. Status under review:
`docs/architecture/ir-evaluator-backend.md`.

This review covers ordinary Java-bytecode-to-JNI-C++ compiler correctness:
strategy selection, serialization, control flow, SSA edge copies, integer
semantics, JNI trampolines, fallback ordering, generated native integration,
defaults, and tests.

## Verdict

**Accept with nits (`accept-with-nits`).**

The evaluator lowering is correct for its documented static integer subset. The
Java serializer and C++ evaluator agree on the header, opcodes, operand widths,
branch conditions, zero sentinel, and little-endian encoding. Phi values are
copied in two phases through dedicated temporary registers, preserving parallel
copy semantics on both ordinary edges and loop backedges. `IADD`, `ISUB`, and
`IMUL` operate on copied `uint32_t` bit patterns and convert back with `memcpy`,
so all three wrap modulo 2^32 without C++ signed-overflow undefined behavior.

The selected lowering completes validation and serialization before
`MethodShellEmitter.beginEvaluator(...)` can alter `output`, `nativeMethods`, or
`ACC_NATIVE`. Unsupported methods therefore reach the existing per-method
legacy fallback with clean method state. Exact CLI generation confirmed that
`add` and `sumTo` become data-plus-`evaluate_i32` trampolines under
`--codegen=ir --ir-lower=eval`, while the defaults still select legacy codegen
and direct IR lowering.

I found **no correctness blocker**, so this review branch changes no compiler
code. The non-blocking nits are test/contract clarity issues described below.

## Evidence

Environment:

- OpenJDK 21.0.10 with JNI headers under
  `/usr/lib/jvm/java-21-openjdk-amd64/include{,/linux}`;
- g++ 13.3.0;
- CMake 3.28.3.

The required command completed successfully:

```text
./gradlew :obfuscator:test \
  --tests by.radioegor146.CodegenModeTest \
  --tests by.radioegor146.ir.IrCompilerTest \
  --tests by.radioegor146.ir.backend.InterpreterStreamStrategyTest
```

JUnit XML was inspected directly:

| Suite | Tests | Skipped | Failures | Errors |
| --- | ---: | ---: | ---: | ---: |
| `CodegenModeTest` | 4 | 0 | 0 | 0 |
| `IrCompilerTest` | 17 | 0 | 0 | 0 |
| `InterpreterStreamStrategyTest` | 6 | 0 | 0 | 0 |
| **Total** | **27** | **0** | **0** | **0** |

All three selected tests that invoke g++ really ran; none has a `<skipped>`
element in its JUnit testcase:

- `IrCompilerTest.generatedCppPassesGppSyntaxCheckWhenToolchainAvailable`
  compiled the direct-IR generated translation unit;
- `InterpreterStreamStrategyTest.evaluatorTranslationUnitPassesGppSyntaxSmokeWhenAvailable`
  compiled `native_jvm_eval.cpp`;
- `InterpreterStreamStrategyTest.sharedEvaluatorRunsAddAndSumToBlobsWhenToolchainAvailable`
  compiled, linked, and ran the evaluator harness.

I also configured and built the complete generated evaluator CMake project with
`CC=/usr/bin/gcc CXX=/usr/bin/g++`. It compiled the generated class source,
`native_jvm_eval.cpp`, and the existing runtime sources, then linked
`libnative_library.so` successfully.

## Files inspected

- Java serialization and trampoline:
  `ir/backend/InterpreterStreamStrategy.java`;
- lowering orchestration and result contract:
  `IrMethodCompiler.java`, `MethodLoweringStrategy.java`,
  `DirectCppStrategy.java`, `LoweredMethod.java`, and `LoweringContext.java`;
- C++ evaluator ABI and implementation:
  `native_jvm_eval.hpp`, `native_jvm_eval.cpp`, and the declaration in
  `native_jvm.hpp`;
- JNI shell and project integration:
  `MethodShellEmitter.java`, `NativeObfuscator.java`, and `Main.java`;
- CLI enum and all requested tests:
  `IrLoweringMode.java`, `CodegenModeTest`, `IrCompilerTest`, and
  `InterpreterStreamStrategyTest`;
- design/status text in `ir-compiler.md` §9.3 and
  `ir-evaluator-backend.md`.

## Generated method shape

Running the built CLI with the exact evaluator flags produced:

```cpp
// add(II)I
jint JNICALL __ngen_native_add0(JNIEnv *env, jclass clazz, jint arg0, jint arg1) {
    static const std::uint8_t ir_method_data[] = { /* ... */ };
    const jint ir_method_args[] = { arg0, arg1 };
    return native_jvm::ir_eval::evaluate_i32(
        ir_method_data, sizeof(ir_method_data), ir_method_args, 2);
}
```

`sumTo(I)I` has the same shape with its own data array and one argument. Neither
method contains `cstack`, the direct-IR marker, typed direct-IR temporaries, or
the direct arithmetic expression. This proves both methods stayed on the
evaluator path rather than silently falling back or selecting direct lowering.

The generated project contains `native_jvm_eval.cpp/.hpp`, and its
`CMakeLists.txt` places both files in the existing shared-library target.

## Default and selection behavior

- Picocli keeps `--codegen` at `legacy`.
- Picocli defaults `--ir-lower` to `direct`.
- The existing `NativeObfuscator.process(...)` overload without a codegen
  argument delegates with `CodegenMode.LEGACY, IrLoweringMode.DIRECT`.
- The overload with only `CodegenMode` delegates with
  `IrLoweringMode.DIRECT`.
- `IrMethodCompiler.processMethod(context)` delegates to direct lowering.
- Evaluator resources are added only when both selected modes are IR and eval.

An exact default CLI run emitted the legacy `cstack`/`clocal` body and no
evaluator runtime files. An exact `--codegen=ir` run without `--ir-lower`
emitted the existing straight-line direct-IR body and no evaluator runtime
files.

## Fallback before mutation

The relevant order in `IrMethodCompiler.processMethod` is:

1. `frontend.build(...)`;
2. `strategy.lower(...)`;
3. select a shell from the completed `LoweredMethod`;
4. `beginEvaluator(...)` or `beginIr(...)`;
5. append and finish.

For the evaluator strategy, `lower(...)` first calls `validate(method)`, then
runs `new Serializer(method).serialize()`, then creates the trampoline. Both
capability failures and serialization failures therefore occur before the
shell. The serializer's capability-shaped failures—missing phi input, a CFG
target outside the method, or an unresolved target—are
`UnsupportedIrConstructException`, so the caller's existing fallback catches
them.

`beginEvaluator(...)` is the first evaluator-path operation that can invoke
special-method preprocessing, set `ACC_NATIVE`, append a native registration,
or write the JNI function shell. The rejection regression confirms an
unsupported operation leaves `method.access`, `context.output`,
`context.nativeMethods`, and all four shared caches unchanged.

## ISA agreement

| Meaning | Java serializer | C++ evaluator |
| --- | ---: | ---: |
| constant i32 | `0x01` | `0x01` |
| register move | `0x02` | `0x02` |
| `IADD` | `0x10` | `0x10` |
| `ISUB` | `0x11` | `0x11` |
| `IMUL` | `0x12` | `0x12` |
| jump | `0x20` | `0x20` |
| signed branch | `0x21` | `0x21` |
| return i32 | `0x22` | `0x22` |
| literal-zero rhs | `0xffff` | `0xffff` |

The Java header writes `N`, `J`, `E`, version 1, register count, and argument
count. The C++ reader checks the same four leading bytes and reads the same two
u16 fields. Java `u16/u32/i32` writes least-significant byte first, matching the
C++ reader. Branch conditions agree exactly: `EQ=0`, `NE=1`, `LT=2`, `GE=3`,
`GT=4`, and `LE=5`. Both sides treat branch targets as absolute u32 offsets from
the start of the method data.

## Phi copies

For every edge into a block with phis, serialization first emits:

```text
temporary[i] <- incoming[i]
```

for every phi. Only after all incoming values have been staged does it emit:

```text
phi_result[i] <- temporary[i]
```

It then jumps to the target block. The temporary range starts at
`baseRegisterCount`, above every parameter, instruction result, and phi result,
and the header's register count includes the maximum simultaneous phi count.
No destination can overwrite a source that a later copy still needs. The
runtime `sumTo` test exercises loop-header phis and returns 15 for input 6.

## Integer semantics

The evaluator never performs the three arithmetic operations on signed `jint`
operands. It copies each operand's bits to `std::uint32_t` with `memcpy`, performs
unsigned add/subtract/multiply, then copies the low 32 bits back to `jint`.
Unsigned 32-bit arithmetic is defined modulo 2^32 and therefore matches JVM
integer wraparound.

An additional g++-compiled harness produced:

```text
2147483647 + 1  -> -2147483648
-2147483648 - 1 -> 2147483647
2147483647 * 2  -> -2
```

Signed branches compare the reconstructed `jint` values, so relational
conditions use JVM signed-int ordering.

## Non-blocking nits

1. `MethodLoweringStrategy.supports(...)` is part of the strategy contract and
   is implemented, but `IrMethodCompiler` never calls it; `lower(...)` repeats
   validation and drives fallback by exception. The behavior is correct, but
   the unused hook makes the extension-point contract less clear.
2. The committed integration test checks generated trampoline text and CMake
   membership, but it does not compile/link the complete generated CMake
   project. The separate tests compile the evaluator and run its harness; the
   manual full-project build above closes the evidence gap for this review.
3. The committed runtime values do not cover arithmetic wrap boundaries, and
   only one of the six branch conditions is exercised by the loop fixture.
   Code inspection and the extra wrap harness confirm correctness, but focused
   regression cases would preserve it better.
4. The status sentence saying `--ir-lower` is consulted only after a successful
   IR build is slightly too broad: generated-project runtime inclusion is
   decided from the two CLI modes before methods are built. Per-method lowering
   selection does wait for successful IR construction. This has no functional
   consequence.

## 中文摘要

结论：**接受，但有非阻塞小问题（accept-with-nits）**。

本次按普通 Java 字节码到 JNI C++ 编译器后端进行审阅。Java 序列化端与 C++ evaluator
在 header、opcode、字段宽度、分支条件、`0xffff` 零值标记和小端编码上完全一致。
控制流边上的 phi 复制先把所有 incoming 值写入独立临时寄存器，再统一写入 phi 结果，
因此是并行复制，不会发生顺序覆盖；`sumTo(6)` 的真实 evaluator 运行结果为 15。

`IADD`、`ISUB`、`IMUL` 都先用 `memcpy` 把 `jint` 位模式转成 `uint32_t`，执行模
2^32 的无符号运算后再拷回 `jint`，没有 C++ 有符号溢出未定义行为。额外 g++ harness
验证了最大值加一、最小值减一和乘法溢出的结果分别为 `INT_MIN`、`INT_MAX`、`-2`。

能力检查和完整序列化均发生在 `beginEvaluator(...)` 之前；不支持的节点或序列化目标
问题会抛出 `UnsupportedIrConstructException`，此时 `output`、`nativeMethods`、
`ACC_NATIVE` 和共享缓存都尚未被修改，逐方法 legacy fallback 安全。

精确 CLI 运行确认：`--codegen=ir --ir-lower=eval` 下的 `add`/`sumTo` 都是
`ir_method_data` 加 `evaluate_i32` 的 trampoline；默认运行仍生成 legacy body，
仅指定 `--codegen=ir` 时仍走 direct lowering。聚焦测试 27/27 通过，0 skipped、
0 failures、0 errors；三个调用 g++ 的测试都真实执行。完整生成 CMake 工程也已用
g++ 编译并链接成功。

未发现正确性阻塞项，因此未修改编译器代码。小问题仅涉及未被调用的 `supports` hook、
完整生成工程尚未纳入自动编译、边界值/全部分支条件覆盖不足，以及状态文档的一句选择时机
表述略宽。
