# IR eval lowering port — Sol independent review

Reviewed draft [PR #137](https://github.com/gaoyu06/native-obfuscator/pull/137)
at `c384cb5e17603d079e597d5bf0da81a929de8b7a`, against
`76ebedd`.

## Verdict / 结论

**Accept with nits. / 接受（附带测试覆盖小问题）。**

No blocking compiler-correctness defect was found. The optional eval lowering
is selected after successful IR construction, retains the existing per-method
fallback boundary, and leaves all defaults unchanged. The only nit is that the
committed `javaAndCppOpcodeMapsAgree` regression checks selected constants
rather than all 28 declared opcodes; the complete maps agree in this reviewed
tip, but a data-driven full-map assertion would guard future edits better.

未发现阻塞性的编译器正确性缺陷。可选 eval lowering 仅在 IR 构建成功后选择，
保留既有逐方法 fallback 边界，且没有改变任何默认值。唯一的小问题是已提交的
`javaAndCppOpcodeMapsAgree` 回归测试只检查部分常量，而不是全部 28 个已声明
opcode；本次审查确认完整映射一致，但数据驱动的全表断言能更好地约束后续修改。

## Static review evidence / 静态审查证据

1. Defaults and API compatibility:
   `Main.NativeObfuscatorRunner` declares `legacy`, `cpp`, and `direct`.
   The three pre-existing `NativeObfuscator.process` signatures remain present
   and delegate with `IrLoweringMode.DIRECT`; the new lowering-aware overload
   also defaults the backend to `CPP`.
2. Dispatch order:
   `InterpreterMethodEmitter.tryCompile` still runs first. Only when it returns
   null and `selectedCodegen == IR` does `IrMethodCompiler` build IR and select
   `selectedIrLowering`. Legacy codegen does not consult the lowering mode for
   method compilation.
3. Fallback boundary:
   `InterpreterStreamStrategy.lower` validates the complete IR and finishes
   serialization before returning `LoweredMethod`. Only afterward does
   `IrMethodCompiler` call `MethodShellEmitter.beginIr`, which writes the JNI
   shell, registration state, and native access change. Capability and
   serialization misses use `UnsupportedIrConstructException`.
4. Opcode agreement:
   an independent extraction found 28 Java constants and 28 C++ constants,
   with no missing names and no value mismatches. The assignments are
   `0x01`, `0x02`, `0x10`–`0x18`, `0x20`–`0x2a`, and `0x2d`–`0x32`.
   Neither side declares `0x2b` or `0x2c`; `LDIV` and `LREM` remain outside
   this lowering.
5. Ownership:
   `git diff origin/master...c384cb5 --` over `AsmToIr`, `CfgBuilder`,
   `IrNodes`, `IrCppEmitter`, and `IrCompilerTest` is empty.
6. Runtime-source selection:
   `evaluatorRuntimeEnabled` is exactly `codegen == IR && lowering == EVAL`.
   That same predicate controls both resource copying and CMake source entries.
7. Default-off output identity:
   independent regeneration and tree comparisons are recorded below.
8. Claims:
   the change does not modify `docs/benchmarks/**` or
   `docs/architecture/project-status.md`; the #53 median remains `N/A`.
   It adds no JDK support level and makes no requirement-7 claim.

1. 默认值和 API 兼容性：`Main.NativeObfuscatorRunner` 明确使用 `legacy`、
   `cpp` 和 `direct`。三个既有 `NativeObfuscator.process` 签名均保留并委托
   `IrLoweringMode.DIRECT`；新增 lowering overload 也默认使用 `CPP` backend。
2. 分派顺序：仍先调用 `InterpreterMethodEmitter.tryCompile`。只有其返回 null
   且 `selectedCodegen == IR` 时，`IrMethodCompiler` 才构建 IR 并选择
   `selectedIrLowering`。legacy 方法编译不会读取 lowering mode。
3. Fallback 边界：`InterpreterStreamStrategy.lower` 在返回 `LoweredMethod`
   前完成全部 IR 校验和序列化；之后 `IrMethodCompiler` 才调用
   `MethodShellEmitter.beginIr` 写入 JNI shell、注册状态并修改 native access。
   能力或序列化失败均使用 `UnsupportedIrConstructException`。
4. Opcode 一致性：独立提取结果为 Java 28 项、C++ 28 项，名称和值均完全一致。
   编号为 `0x01`、`0x02`、`0x10`–`0x18`、`0x20`–`0x2a` 和
   `0x2d`–`0x32`。两端均未声明 `0x2b`/`0x2c`，`LDIV`/`LREM`
   仍不属于本 lowering。
5. 代码归属：对 `AsmToIr`、`CfgBuilder`、`IrNodes`、`IrCppEmitter` 和
   `IrCompilerTest` 执行上述 diff，结果为空。
6. Runtime source 选择：`evaluatorRuntimeEnabled` 精确等于
   `codegen == IR && lowering == EVAL`，资源复制和 CMake source entry
   均由同一条件控制。
7. 默认关闭输出一致性：独立重新生成和目录比较结果记录在下节。
8. 声明边界：本改动未修改 `docs/benchmarks/**` 或
   `docs/architecture/project-status.md`；#53 median 仍为 `N/A`，
   没有新增 JDK 支持级别，也没有 requirement 7 声明。

## Independent verification / 独立验证

Dynamic reruns are pending on the review branch and will be recorded here
before handoff.

动态复跑正在 review 分支上进行，交付前会在此记录结果。

## Nit / 小问题

The opcode-map test currently checks 11 C++ declarations plus the absence of
`LDIV`/`LREM`. It does not directly assert all declarations used by the Java
serializer. This is non-blocking because the independent full-map extraction
found exact agreement, and serializer/runtime execution tests cover both i32
and i64 operations. A future regression should compare the complete map.

当前 opcode-map 测试检查 11 个 C++ 声明以及 `LDIV`/`LREM` 不存在，但没有
直接断言 Java serializer 使用的全部声明。独立全表提取已确认完全一致，且
serializer/runtime 测试覆盖 i32 和 i64 操作，因此不阻塞接受；后续可改为
完整映射比较。
