# Goal status and human options / 目标状态与人工选项

## Executive status

This is a maintainer snapshot of `origin/master` at `e7ca4c8` and the pull
requests returned by `gh pr list --state all --limit 100` on 2026-08-29. PRs
#1–#94 are all open drafts. `master` is unchanged from the preceding brief and
contains none of their code or documentation. This refresh carries the brief
through PR #94; the previous brief [#91](https://github.com/gaoyu06/native-obfuscator/pull/91)
covered work through #89. Results below are evidence recorded on the named
branch, not invented merge, review, or CI results.

PRs [#1](https://github.com/gaoyu06/native-obfuscator/pull/1) and
[#2](https://github.com/gaoyu06/native-obfuscator/pull/2) are Gemini research
inputs, not authorities. Only claims independently accepted or revised by the
Sol review in [#3](https://github.com/gaoyu06/native-obfuscator/pull/3)
(`docs/architecture/gemini-review-notes.md`) are used here.

## 双语维护者简报 / Bilingual maintainer brief

### (a) 本次改动范围 / Change scope

本次仅更新文档至新草稿：纳入 [#44](https://github.com/gaoyu06/native-obfuscator/pull/44)
对 evaluator #42 的 “accept with nits” 审阅、
[#45](https://github.com/gaoyu06/native-obfuscator/pull/45) 对 phase 5 #40 的
Fable “accept with nits” 审阅、[#46](https://github.com/gaoyu06/native-obfuscator/pull/46)
干净叠在 SDK v1 #12 上的 `NativeStrings`、[#47](https://github.com/gaoyu06/native-obfuscator/pull/47)
仍为 opt-in 的 switch 与 `ANEWARRAY` phase 6，以及
[#51](https://github.com/gaoyu06/native-obfuscator/pull/51) 在 Fable policy-block
后进行的 Sol phase-6 审阅。#51 的记录结论为 **accept with nits**，并修复了
数组组件 `ANEWARRAY`，使其以数组 descriptor 调用 `FindClass`。此外纳入
[#48](https://github.com/gaoyu06/native-obfuscator/pull/48) 的 live
`--ir-lower=eval` stripped `.so` artifact，以及
[#50](https://github.com/gaoyu06/native-obfuscator/pull/50) 对该 artifact
先恢复、后评分的 blinded reader：`add`、`sumTo`、`subMul`、`mix` 均为
**full**。保留 [#37](https://github.com/gaoyu06/native-obfuscator/pull/37)
对有效 live direct-IR stripped `.so` 的相同四方法完整恢复结论；本文不合并或
实现任何草稿。此外，[#53](https://github.com/gaoyu06/native-obfuscator/pull/53)
在 `IrFriendlyIntKernel.run(I)I` 上记录 JVM、legacy、direct IR 与 eval-lower
选择；direct IR 保持在 IR 路径，eval 因 `USHR` 回退到 legacy，故不声称 eval
timing。[#54](https://github.com/gaoyu06/native-obfuscator/pull/54) 为仍然 opt-in
的 IR phase 7 增加 `CHECKCAST`、`INSTANCEOF` 与部分两槽 `I64` 算术；其状态
文档声称 33 个 `IrCompilerTest` 加 2 个 `CodegenModeTest`。
[#56](https://github.com/gaoyu06/native-obfuscator/pull/56) 是 Sol 对 #54 的
纯文档审阅，记录结论 **accept**，并记录重新运行 35/35 个聚焦测试。独立的
[#57](https://github.com/gaoyu06/native-obfuscator/pull/57) 从 #44 扩展
evaluator ISA，加入 `IAND`、`IOR`、`IXOR`、`ISHL`、`ISHR` 与 `IUSHR`，使
`IrFriendlyIntKernel` 的等价整数操作流可保持在 eval 路径；其双语记录声称
28/28 个聚焦测试通过，并明确不新增 benchmark timing。在其上叠加的
[#59](https://github.com/gaoyu06/native-obfuscator/pull/59) 是独立的后续本地
诊断测量：每个进程 5 次 warmup、10 次记录样本，全部 JVM/native 运行的
checksum 均为 2,038,221,507；记录的 JVM、legacy、direct IR、evaluator IR
中位数依次为 10,017,146.0 ns、167,870,311.5 ns、10,021,957.0 ns 与
411,875,537.5 ns。目标方法存在 evaluator-data marker，且没有目标方法或
`IUSHR` fallback。该测量不是可移植加速结论，也不回填 #53：#53 的 eval
中位数仍为 `N/A`。[#61](https://github.com/gaoyu06/native-obfuscator/pull/61)
是 Sol 对 #57 evaluator IUSHR ISA 的纯文档审阅，记录结论 **accept**、未改
编译器及 28/28 个聚焦测试通过；该结论不是上线就绪声明。
[#62](https://github.com/gaoyu06/native-obfuscator/pull/62) 叠加在 #56 上，
将仍为 opt-in 的 IR phase 8 扩展至通过 `AllocObject` 支持 `NEW`、通过
`CallNonvirtualVoidMethod` 支持仅限构造器的 `INVOKESPECIAL`，并扩展
`I`/`J`/引用 invoke 形状。构造器方法体仍排除，默认仍为 legacy；其状态记录
36 + 2 = 38 个聚焦测试及包含 34 个方法的 g++ 烟测。该阶段仍不完整且未达
上线就绪。[#63](https://github.com/gaoyu06/native-obfuscator/pull/63) 是 Fable
对 #62 的纯文档审阅，记录结论 **accept**、无编译器改动及 38/38 个聚焦测试；
其唯一非阻塞观察是构造器 receiver 的 null check 在已分配且检查过的对象路径上
永不触发。[#64](https://github.com/gaoyu06/native-obfuscator/pull/64) 是 Sol 对
#62 的纯文档审阅，记录结论 **accept**、无编译器改动、38/38 个聚焦测试及
34-method g++ 语法检查。两项审阅均不构成上线就绪声明。
[#66](https://github.com/gaoyu06/native-obfuscator/pull/66) 将仍为 opt-in 的
direct IR phase 9 扩展至 `ARETURN`、`ACONST_NULL`、`IFNULL`/`IFNONNULL` 与
category-one `POP`；`POP2` 仍逐方法 fallback，默认仍为 legacy。其记录为
42 + 2 = 44 个聚焦测试及 39-method g++ 烟测，仍部分且未达上线就绪。
[#70](https://github.com/gaoyu06/native-obfuscator/pull/70) 是首选 phase-9
tip：Sol 审阅发现 `jobject` 与 `jarray` 的 `ARETURN` 边界不匹配，以显式
`jarray` cast 修复并增加数组返回回归；修复后结论为 **accept**，记录
43 + 2 = 45 个测试及 40-method g++ 烟测，但不是上线就绪结论。
[#71](https://github.com/gaoyu06/native-obfuscator/pull/71) 是纯文档 Fable
**accept-with-nits** 审阅，审阅的是未含 #70 修复的 #66 `32ac47d`，记录 44 个
测试；它不包含 `jarray` 修复，也不是上线就绪结论。独立的 evaluator 路线中，
[#68](https://github.com/gaoyu06/native-obfuscator/pull/68) 在 #57 上加入
`LLOAD`、`LSTORE`、`LADD`、`LSUB`、`LMUL`、`LRETURN`、`I2L` 与 `L2I`
（opcode `0x23`–`0x2a`）；`(J)J` 保持在 eval 路径，`LDIV`/`LREM` 仍 fallback，
记录 31/31，且不新增 benchmark 数字。
[#69](https://github.com/gaoyu06/native-obfuscator/pull/69) 是对 #68 的纯文档
Sol 审阅，记录 **accept** 与 31/31；共享 frontend 使 sibling direct-IR 文件
改动成为必要集成边界，而非隔离缺陷。该审阅不构成上线就绪声明。本文叠加在
此前 options brief [#74](https://github.com/gaoyu06/native-obfuscator/pull/74)
之上，并新增 [#73](https://github.com/gaoyu06/native-obfuscator/pull/73)：
它基于首选 #70 phase-9 tip，将仍为 opt-in 的 direct IR 扩展至 exact `I`、exact
`J` 与 object/array descriptor 的 `GETFIELD`、`PUTFIELD`、`GETSTATIC`、
`PUTSTATIC`。实例字段 null receiver 留下 pending `NullPointerException` 并走
异常出口；`Z`/`B`/`C`/`S`/`F`/`D` 仍 fallback，默认仍为 legacy。其记录为
47 + 2 = 49 个测试与 50-method g++ 烟测。[#76](https://github.com/gaoyu06/native-obfuscator/pull/76)
是纯文档 Fable **accept-with-nits** 审阅，记录 49/49；[#77](https://github.com/gaoyu06/native-obfuscator/pull/77)
是纯文档 Sol **PASS** 审阅，同样记录 49/49。两者都不是上线就绪结论。
SDK 路线新增 [#72](https://github.com/gaoyu06/native-obfuscator/pull/72)，它叠加
在 #46 上，公开 `NativePrimitives.hmacSha256` 与 C ABI
`no_sdk_hmac_sha256_v1`，以仓内 SHA-256 实现 RFC 2104 HMAC，并用公开向量
验证；其 module suite 记录 13/13，且未运行 HMAC benchmark。
[#75](https://github.com/gaoyu06/native-obfuscator/pull/75) 是纯文档 Sol
**PASS** 审阅，而不是已交付 SDK：记录 focused 1/1 与完整 suite 13/13（二者均
0 failed、0 skipped），完整 suite 包含 8 个 generated fixture、2 个
`StringPoolTest`、2 个 `ClassMethodListTest` 与 1 个 SDK integration test；
另记录独立向量核对 4/4、C ABI probe 11/11。generated-library verifier 还记录
2 个 SHA-256 向量、4 个 HMAC 向量、9 个 `MessageDigest` case、5 个 equality
case、5 个 string vector 与 4 个 concatenation，且未运行 HMAC benchmark。
Direct-IR 路线随后新增 [#78](https://github.com/gaoyu06/native-obfuscator/pull/78)：
它叠加在 #73 上，为 exact `I`、exact `J`、object/array reference 与 `V`
carrier 加入 `INVOKEINTERFACE` 和非构造器 `INVOKESPECIAL`。构造器方法体仍排除，
默认仍为 legacy；其记录为 53 + 2 = 55 个测试与 59-method g++ 烟测。
[#82](https://github.com/gaoyu06/native-obfuscator/pull/82) 是 Sol 的纯文档
**accept** 审阅，[#83](https://github.com/gaoyu06/native-obfuscator/pull/83)
是 Fable 的纯文档 **accept** 审阅；两者均记录 55/55，均不是上线就绪结论。
SDK 路线新增叠加在 #72 上的 [#80](https://github.com/gaoyu06/native-obfuscator/pull/80)：
它加入 AES-256-GCM，使用 NIST CAVP 向量，内嵌 tiny-AES-c，记录 13/13，且未运行
AES benchmark。[#81](https://github.com/gaoyu06/native-obfuscator/pull/81)
是首选 AES tip：Sol 审阅结论为 **PASS with one correctness fix**，修复 32-bit
目标上 `plaintext.size + 16` 溢出并返回 `NO_SDK_SIZE_OVERFLOW_V1`，记录
13/13、先认证后解密及固定长度 tag 的 constant-work 比较，同时明确 tiny-AES-c
并非 side-channel hardened。它优先于未修复的 #80，但仍不是已交付 SDK。
Direct-IR 路线继续到 [#84](https://github.com/gaoyu06/native-obfuscator/pull/84)：
它叠加在 #78 上，通过 hidden native bridge 支持构造器方法体；`<init>` 绝不设置
`ACC_NATIVE`，唯一的直接 `this(...)`/`super(...)` 调用及其参数前缀留在 Java
字节码中，默认仍为 legacy。其记录为 57 + 2 = 59 个测试及 61-method g++ 烟测。
[#89](https://github.com/gaoyu06/native-obfuscator/pull/89) 是首选 phase-12 tip：
Sol 审阅发现并修复前缀 `ASTORE` 覆盖 local 0 或被转发引用参数的 verifier-safety
问题，改为在 mutation 前保守拒绝；修复后结论为 **pass after one correctness
fix**，记录 58 + 2 = 60 个测试及 61-method g++ 烟测。[#88](https://github.com/gaoyu06/native-obfuscator/pull/88)
是对未修复 #84 的纯文档 Fable **accept** 审阅，记录 59 个测试，不包含 #89
修复。独立 evaluator 路线中，[#85](https://github.com/gaoyu06/native-obfuscator/pull/85)
在 #68 上加入 `LDIV=0x2b` 与 `LREM=0x2c`，使 `(JJ)J` divide/remainder 保持
在 eval 路径；记录 32/32，且不新增 benchmark 数字。[#87](https://github.com/gaoyu06/native-obfuscator/pull/87)
是对 #85 的纯文档 Sol **accept** 审阅，同样记录 32/32。
[#90](https://github.com/gaoyu06/native-obfuscator/pull/90) 叠加在首选
phase-12 tip #89 上，将仍为 opt-in 的 IR phase 13 扩展至 `Z`/`B`/`C`/`S`
字段与 invoke descriptor 的精确 Boolean/Byte/Char/Short JNI family；stack/local
carrier 保持 `I32` 并显式 widen/narrow，`F`/`D` 仍 fallback，默认仍为 legacy。
其状态记录 62 + 2 = 64 个聚焦测试及未跳过的 87-`JNICALL` g++ 烟测。#90 是首选
phase-13 tip；[#93](https://github.com/gaoyu06/native-obfuscator/pull/93) 是
Sol 对 #90 的纯文档审阅，记录 **pass/accept**、64/64 且无编译器改动，
[#94](https://github.com/gaoyu06/native-obfuscator/pull/94) 是 Fable 对 #90
的纯文档审阅，记录 **accept**、64/64 且无编译器改动；其唯一非阻塞 nit 是 boolean
*invoke 参数* 被 `& 1` 掩码，而 JVM 不在调用点做掩码，对 javac 输出不可观察，
仅为文档说明、无代码改动。两项审阅均不构成上线就绪结论。独立的测量路线中，
[#92](https://github.com/gaoyu06/native-obfuscator/pull/92) 叠加在 #89 上、仅做
测量：两个类、六个方法的 Java 8 语料记录 **5 IR / 1 fallback**，fallback 为
`AdmissionTarget.unsupported(I)I`，opcode 134（`I2F`），且 `<clinit>` 已排除。
该 5/6（83.3%）不是生产 IR 覆盖率，也不是加速结论，编译器/运行时代码未改。

This documentation-only update carries the brief through #44's
accept-with-nits review of evaluator #42, #45's Fable accept-with-nits review
of phase 5 #40, #46's clean `NativeStrings` stack on SDK v1 #12, #47's still
opt-in switch and `ANEWARRAY` phase 6, and #51's Sol phase-6 review after the
Fable policy block. #51 records an **accept with nits** verdict and fixes
array-component `ANEWARRAY` to call `FindClass` with the array descriptor. It
also carries #48's live
`--ir-lower=eval` stripped-`.so` artifact. #50 is the recovery-first blinded
reader on that artifact; `add`, `sumTo`, `subMul`, and `mix` all scored
**full**. It retains #37's full recovery of the same four methods from a valid
live direct-IR stripped `.so`. #53 records JVM, legacy, direct IR, and
eval-lower selection on `IrFriendlyIntKernel.run(I)I`: direct IR stayed on IR,
while eval fell back to legacy on `USHR`, so no eval timing is claimed. #54
adds `CHECKCAST`, `INSTANCEOF`, and a two-slot `I64` arithmetic slice to the
still-opt-in IR phase 7; its status document claims 33 `IrCompilerTest` plus
2 `CodegenModeTest`. #56 is Sol's documentation-only review of #54; it records
an **accept** verdict and a 35/35 focused-test rerun. Separately, #57 extends
the evaluator ISA from #44 with `IAND`, `IOR`, `IXOR`, `ISHL`, `ISHR`, and
`IUSHR`, allowing an `IrFriendlyIntKernel`-equivalent integer stream to stay
on eval. Its bilingual record claims 28/28 focused tests and explicitly adds
no benchmark timings. Stacked on #57, [#59](https://github.com/gaoyu06/native-obfuscator/pull/59)
is a separate follow-up local diagnostic: every process used 5 warmups and 10
recorded samples, every JVM/native run returned checksum 2,038,221,507, and
the recorded JVM, legacy, direct-IR, and evaluator-IR medians were respectively
10,017,146.0 ns, 167,870,311.5 ns, 10,021,957.0 ns, and 411,875,537.5 ns. The
target method had an evaluator-data marker and no target-method or `IUSHR`
fallback. This is not a portable speedup claim and does not back-fill #53:
#53's eval median remains `N/A`. [#61](https://github.com/gaoyu06/native-obfuscator/pull/61)
is Sol's documentation-only review of #57's evaluator IUSHR ISA; it records an
**accept** verdict, no compiler change, and 28/28 focused tests. It is not a
ship-readiness finding. [#62](https://github.com/gaoyu06/native-obfuscator/pull/62),
stacked on #56, extends still-opt-in IR phase 8 with `NEW` via `AllocObject`,
constructor-only `INVOKESPECIAL` via `CallNonvirtualVoidMethod`, and broader
`I`/`J`/reference invoke shapes. Constructor bodies remain excluded and legacy
remains the default. Its status records 36 + 2 = 38 focused tests and a
34-method g++ smoke translation unit. Phase 8 remains partial and not
ship-ready. [#63](https://github.com/gaoyu06/native-obfuscator/pull/63) is
Fable's documentation-only review of #62; it records an **accept** verdict,
no compiler change, and 38/38 focused tests. Its sole non-blocking observation
is a constructor-receiver null check that is never taken for the already
allocated and checked object path. [#64](https://github.com/gaoyu06/native-obfuscator/pull/64)
is Sol's documentation-only review of #62; it records an **accept** verdict,
no compiler change, 38/38 focused tests, and a 34-method g++ syntax check.
Neither review is a ship-readiness finding. This brief neither merges nor
implements any draft. [#66](https://github.com/gaoyu06/native-obfuscator/pull/66)
extends still-opt-in direct IR phase 9 with `ARETURN`, `ACONST_NULL`,
`IFNULL`/`IFNONNULL`, and category-one `POP`; `POP2` still falls back per
method and legacy remains the default. It records 42 + 2 = 44 focused tests
and a 39-method g++ smoke, and remains partial and not ship-ready.
[#70](https://github.com/gaoyu06/native-obfuscator/pull/70) is the preferred
phase-9 tip: Sol found the `jobject` versus `jarray` `ARETURN` boundary
mismatch, fixed it with an explicit `jarray` cast, and added an array-return
regression. Its post-fix verdict is **accept**, with 43 + 2 = 45 tests and a
40-method g++ smoke; it is not a ship-readiness finding.
[#71](https://github.com/gaoyu06/native-obfuscator/pull/71) is Fable's
documentation-only **accept-with-nits** review of the unfixed #66 tip
`32ac47d`; it records 44 tests, does not contain #70's `jarray` fix, and is not
ship-ready. In the separate evaluator lane,
[#68](https://github.com/gaoyu06/native-obfuscator/pull/68), stacked on #57,
adds `LLOAD`, `LSTORE`, `LADD`, `LSUB`, `LMUL`, `LRETURN`, `I2L`, and `L2I`
at opcodes `0x23`–`0x2a`. `(J)J` stays on eval, `LDIV`/`LREM` still fall back,
31/31 focused tests are recorded, and no benchmark numbers are added.
[#69](https://github.com/gaoyu06/native-obfuscator/pull/69) is Sol's
documentation-only **accept** review of #68 with 31/31 focused tests. It
records that sibling direct-IR file edits are required by the shared frontend,
not an isolation defect, and does not establish ship-readiness. This brief is
stacked on the prior options brief
[#74](https://github.com/gaoyu06/native-obfuscator/pull/74).
[#73](https://github.com/gaoyu06/native-obfuscator/pull/73), based on the
preferred #70 phase-9 tip, extends still-opt-in direct IR with `GETFIELD`,
`PUTFIELD`, `GETSTATIC`, and `PUTSTATIC` for exact `I`, exact `J`, and
object/array descriptors. A null instance-field receiver leaves a pending
`NullPointerException` and takes the exceptional exit; `Z`/`B`/`C`/`S`/`F`/`D`
still fall back, and legacy remains the default. It records 47 + 2 = 49 tests
and a 50-method g++ smoke. [#76](https://github.com/gaoyu06/native-obfuscator/pull/76)
is Fable's documentation-only **accept-with-nits** review with 49/49, while
[#77](https://github.com/gaoyu06/native-obfuscator/pull/77) is Sol's
documentation-only **PASS** review with 49/49. Neither is a ship-readiness
finding. In the SDK lane, [#72](https://github.com/gaoyu06/native-obfuscator/pull/72)
stacks on #46 and exposes `NativePrimitives.hmacSha256` plus the
`no_sdk_hmac_sha256_v1` C ABI, implementing RFC 2104 HMAC with the in-tree
SHA-256 and checking published vectors. It records a 13/13 module suite and no
HMAC benchmark. [#75](https://github.com/gaoyu06/native-obfuscator/pull/75) is
Sol's documentation-only **PASS** review, not a shipped SDK: it records a 1/1
focused run and 13/13 full suite, both with 0 failed and 0 skipped. The full
suite comprises 8 generated fixtures, 2 `StringPoolTest`, 2
`ClassMethodListTest`, and 1 SDK integration test. It also records a 4/4
independent vector check and 11/11 C ABI probe. Its generated-library verifier
records 2 SHA-256 vectors, 4 HMAC vectors, 9 `MessageDigest` cases, 5 equality
cases, 5 string vectors, and 4 concatenations; no HMAC benchmark was run.
[PR #78](https://github.com/gaoyu06/native-obfuscator/pull/78), stacked on
#73, then adds `INVOKEINTERFACE` and non-constructor `INVOKESPECIAL` for exact
`I`, exact `J`, object/array references, and `V`. Constructor bodies remain
excluded and legacy remains the default. It records 53 + 2 = 55 tests and a
59-method g++ smoke. [#82](https://github.com/gaoyu06/native-obfuscator/pull/82)
is Sol's documentation-only **accept** review and
[#83](https://github.com/gaoyu06/native-obfuscator/pull/83) is Fable's
documentation-only **accept** review; both record 55/55 and neither is a
ship-readiness finding. In the SDK lane,
[#80](https://github.com/gaoyu06/native-obfuscator/pull/80), stacked on #72,
adds AES-256-GCM with NIST CAVP vectors, vendored tiny-AES-c, a 13/13 suite,
and no AES benchmark. [#81](https://github.com/gaoyu06/native-obfuscator/pull/81)
is the preferred AES tip: Sol records **PASS with one correctness fix**, fixing
32-bit `plaintext.size + 16` overflow with `NO_SDK_SIZE_OVERFLOW_V1`, 13/13,
authenticate-before-decrypt ordering, and constant-work comparison of the
fixed-length tag. It also records that tiny-AES-c is not side-channel hardened.
#81 is preferred over unfixed #80, but it is not a shipped SDK.
[PR #84](https://github.com/gaoyu06/native-obfuscator/pull/84), stacked on #78,
then supports constructor bodies through a hidden native bridge. `<init>` is
never `ACC_NATIVE`; the one direct `this(...)`/`super(...)` call and its
argument-producing prefix stay in Java bytecode; legacy remains the default.
It records 57 + 2 = 59 tests and a 61-method g++ smoke.
[#89](https://github.com/gaoyu06/native-obfuscator/pull/89) is the preferred
phase-12 tip: Sol found and fixed verifier-unsafe prefix `ASTORE` writes to
local 0 or forwarded reference-parameter locals by conservatively rejecting
them before mutation. Its post-fix verdict is **pass after one correctness
fix**, with 58 + 2 = 60 tests and a 61-method g++ smoke.
[#88](https://github.com/gaoyu06/native-obfuscator/pull/88) is Fable's
documentation-only **accept** review of unfixed #84, records 59 tests, and
does not include #89's fix. In the evaluator lane,
[#85](https://github.com/gaoyu06/native-obfuscator/pull/85), stacked on #68,
adds `LDIV=0x2b` and `LREM=0x2c`; generated `(JJ)J` divide/remainder methods
stay on eval. It records 32/32 and adds no benchmark numbers.
[#87](https://github.com/gaoyu06/native-obfuscator/pull/87) is Sol's
documentation-only **accept** review of #85 and also records 32/32.
[#90](https://github.com/gaoyu06/native-obfuscator/pull/90), stacked on
preferred phase-12 tip #89, extends still-opt-in IR phase 13 with the exact
Boolean/Byte/Char/Short JNI families for `Z`/`B`/`C`/`S` field and invoke
descriptors; stack/local carriers stay `I32` with explicit widen/narrow, `F`/`D`
still fall back, and legacy remains the default. Its status records 62 + 2 = 64
focused tests and an unskipped 87-`JNICALL` g++ smoke. #90 is the preferred
phase-13 tip. [#93](https://github.com/gaoyu06/native-obfuscator/pull/93) is
Sol's documentation-only **pass/accept** review of #90 with 64/64 and no
compiler change, and [#94](https://github.com/gaoyu06/native-obfuscator/pull/94)
is Fable's documentation-only **accept** review of #90 with 64/64 and no
compiler change. #94's sole non-blocking nit is that boolean *invoke arguments*
are masked `& 1` while the JVM does not mask at the call site; it is unobservable
for javac output and is a docs-only note with no code change. Neither review is
a ship-readiness finding. Separately,
[#92](https://github.com/gaoyu06/native-obfuscator/pull/92) is a
measurement-only six-method admission report stacked on #89: a two-class Java 8
corpus recorded **5 IR / 1 fallback**, the one fallback being
`AdmissionTarget.unsupported(I)I` at opcode 134 (`I2F`), with `<clinit>`
excluded. Its 5/6 (83.3%) is not production IR coverage or a speedup claim and
changes no compiler or runtime code.

### (b) 是否可直接上线 / Can this ship to production as-is?

**No / 否。** PRs #1–#94 均为草稿，`master` 未包含这些能力；默认 codegen
仍是 legacy。#56 的 accept 审阅不把 #54 的不完整 opt-in phase 7 变成上线
批准；#61 对 #57 的 accept 审阅同样不是上线批准；#57 仍是窄范围、opt-in
且逐方法 fallback 的 evaluator lowering。#62 仍是部分、opt-in 且逐方法
fallback 的 phase 8，构造器方法体仍排除；#63 与 #64 对 #62 的 accept
审阅也都明确不是上线就绪声明。#46
、#53 与 #59 的本地测量均不是可移植性能结论；#53 的 eval timing 仍为
`N/A`，#57 本身不含新 benchmark 数字，#59 只是叠加在 #57 上的独立诊断。
#37 与 #50 分别从有效 live direct-IR 与 shared-evaluator stripped `.so`
完整恢复了四个方法，因此 requirement 7 并未满足。#66 仍是部分 opt-in
phase 9；#70 修复并接受其数组返回边界，且仍是首选 tip，但不是上线批准；
#71 又仅审阅未修复的 #66 tip。#73 叠加在 #70 上，仍是部分、opt-in 且默认
legacy 的 phase 10；#76 的 accept-with-nits 与 #77 的 PASS 均只是纯文档审阅，
不构成上线批准。#68 仍是 opt-in evaluator 扩展，#69 的 accept 审阅也不是上线
批准。#78 仍是部分、opt-in、默认 legacy 的 phase 11，#82/#83 的纯文档 accept
均不是上线批准。#72/#75 仍是 review-stage HMAC SDK surface/审阅；#80 的
AES-256-GCM 实现并非已交付 SDK，首选 #81 虽修复输出长度溢出并记录 PASS，仍明确
不是上线批准，且 tiny-AES-c 并非 side-channel hardened。#84 的构造器支持仍是
部分、opt-in 且默认 legacy；首选 #89 修复 forwarded-reference-local 正确性问题，
但不是上线批准，#88 又仅审阅未修复 #84。#85 仍是窄范围 opt-in evaluator 扩展，
#87 的纯文档 accept 同样不是上线批准。#90 将仍为 opt-in 的 phase 13 扩展至
`Z`/`B`/`C`/`S` 字段与 invoke，`F`/`D` 仍 fallback，默认仍为 legacy；#93 的
pass 与 #94 的 accept 均为 #90 的纯文档审阅，不改编译器，也不是上线批准。
#92 是叠加在 #89 上、仅测量的六方法 5/6 接纳率，不代表生产 IR 覆盖率，
也不是上线批准。

**No.** PRs #1–#94 remain drafts and `master` has none of these capabilities;
the default codegen remains legacy. #56's accept review does not turn #54's
incomplete opt-in phase 7 into ship approval, and #61's accept review of #57
is likewise not ship approval. #57 remains a narrow, opt-in evaluator lowering
with per-method fallback. #62 remains a partial, opt-in phase 8 with per-method
fallback and constructor bodies still excluded; #63's and #64's accept reviews
of #62 are also explicitly not ship-readiness findings. The local measurements in
#46, #53, and #59 are not portable performance claims; #53's eval timing
remains `N/A`, #57 itself contains no new benchmark numbers, and #59 is a
separate diagnostic stacked on #57. #37 and #50 respectively recovered all
four methods from valid live direct-IR and shared-evaluator stripped `.so`
subjects, so requirement 7 is not met. #66 remains a partial opt-in phase 9;
#70 fixes and accepts its array-return boundary and remains the preferred tip,
but is not ship approval, while #71 reviews only the unfixed #66 tip. #73 is
stacked on #70 and remains a partial, opt-in, legacy-default phase 10; #76's
accept-with-nits and #77's PASS are documentation-only reviews, not ship
approval. #68 remains an opt-in evaluator extension, and #69's accept review
is likewise not ship approval. #78 remains a partial, opt-in, legacy-default
phase 11, and #82/#83's documentation-only accept reviews are not ship
approval. #72/#75 remain a review-stage HMAC SDK surface/review. #80's
AES-256-GCM implementation is not a shipped SDK; preferred #81 fixes the
output-length overflow and records PASS, but explicitly is not ship approval,
and tiny-AES-c is not side-channel hardened. #84's constructor support remains
partial, opt-in, and legacy-default; preferred #89 fixes the forwarded-reference
local correctness issue but is not ship approval, while #88 reviews only
unfixed #84. #85 remains a narrow opt-in evaluator extension, and #87's
documentation-only accept is likewise not ship approval. #90 extends still-opt-in
phase 13 to `Z`/`B`/`C`/`S` field and invoke families with `F`/`D` still on
fallback and legacy still default; #93's pass and #94's accept are
documentation-only reviews of #90 that change no compiler code and are not ship
approval. #92 is a measurement-only six-method 5/6 admission report stacked on
#89; it is not production IR coverage and not ship approval.

### (c) 上线前是否需要 review / Is review required?

**Yes / 是。** 每个实现 PR 均需独立代码审查、rebase 后复测及适用的产品/发布
审批。本文中的 benchmark、reader 与 #92 的 admission 测量结论也必须按其记录的
kernel、artifact、语料和方法边界审查，不能外推；#92 的 5/6 尤其不得当作生产
覆盖率门槛。

**Yes.** Each implementation PR still needs independent code review, post-rebase
verification, and applicable product/release approval. Benchmark, reader, and
#92's admission-measurement claims must also be reviewed within their recorded
kernel, artifact, corpus, and method boundaries rather than generalized; #92's
5/6 in particular is not a production coverage gate.

### (d) review 的前置条件 / Review preconditions

1. 继续以 #34–#42 的对应记录为其事实来源；新增结论仅引用 #44 的
   `docs/architecture/ir-evaluator-review.md`、#45 的
   `docs/architecture/ir-phase5-fable-review.md`、#46 的
   `docs/sdk/v1-status.md`、#47 的
   `docs/architecture/ir-phase6-status.md`、#48 的
   `docs/eval/ir-eval-lower/run.md` 与 `liveness.md`，以及 #50 的
   `docs/eval/ir-eval-lower/recovery.md` 与 `scores.md`，再加 #51 的
   `docs/architecture/ir-phase6-review.md` 与更新后的
   `docs/architecture/ir-phase6-status.md`，以及 #53 的
   `docs/benchmarks/results-ir-eval-lower.md`、#54 的
   `docs/architecture/ir-phase7-status.md`、#56 的
   `docs/architecture/ir-phase7-review.md`，以及 #57 的
   `docs/architecture/ir-evaluator-backend.md` 与双语 `PR_BODY.md`，再加
   #59 的 `docs/benchmarks/results-ir-eval-ushr.md` 与双语 `PR_BODY.md`、
   #61 的 `docs/architecture/ir-evaluator-ushr-review.md`、#62 的
   `docs/architecture/ir-phase8-status.md` 与双语 `PR_BODY.md`、#63 的
   `docs/architecture/ir-phase8-fable-review.md`，以及 #64 的
   `docs/architecture/ir-phase8-review.md`；#66 的
   `docs/architecture/ir-phase9-status.md`、#70 的
   `docs/architecture/ir-phase9-review.md`、#71 的
   `docs/architecture/ir-phase9-fable-review.md`、#68 的
   `docs/architecture/ir-evaluator-backend.md` 与双语 `PR_BODY.md`，以及
   #69 的 `docs/architecture/ir-evaluator-i64-review.md`；#72 的双语
   `PR_BODY.md` 与 `docs/sdk/v1-status.md`、#75 的
   `docs/sdk/hmac-sha256-review.md`、#73 的
   `docs/architecture/ir-phase10-status.md`、#76 的
   `docs/architecture/ir-phase10-fable-review.md`、#77 的
   `docs/architecture/ir-phase10-review.md`、#78 的
   `docs/architecture/ir-phase11-status.md`、#82 的
   `docs/architecture/ir-phase11-review.md`、#83 的
   `docs/architecture/ir-phase11-fable-review.md`、#80 的双语
   `PR_BODY.md` 与 `docs/sdk/v1-status.md`，以及 #81 的
   `docs/sdk/aes-256-gcm-review.md`；#84 的
   `docs/architecture/ir-phase12-status.md`、#89 的
   `docs/architecture/ir-phase12-review.md`、#88 的
   `docs/architecture/ir-phase12-fable-review.md`、#85 的
   `docs/architecture/ir-evaluator-backend.md` 与双语 `PR_BODY.md`，以及
   #87 的 `docs/architecture/ir-evaluator-ldiv-review.md`；#90 的
   `docs/architecture/ir-phase13-status.md`、#93 的
   `docs/architecture/ir-phase13-review.md`、#94 的
   `docs/architecture/ir-phase13-fable-review.md`，以及 #92 的
   `docs/benchmarks/ir-admission-phase12.md`。
   Continue to use the #34–#42 records for their claims, and use only those
   named branch documents for the new #44–#94 claims.
2. #53 仅对 `IrFriendlyIntKernel.run(I)I` 记录本地中位数：JVM
   12,207,144.5 ns、legacy 202,090,247.0 ns、direct IR 11,311,481.5 ns。
   direct IR 保持在 IR 路径；eval 因 `USHR` 回退到 legacy，eval 中位数为
   `N/A`，不得引用 fallback observation 作为 eval timing，也不得把任何本地
   数值视为可移植性能结论。 #53 records local medians only for that method:
   12,207,144.5 ns for JVM, 202,090,247.0 ns for legacy, and 11,311,481.5 ns
   for direct IR. Direct IR stayed on IR; eval fell back to legacy on `USHR`,
   its eval median is `N/A`, and no fallback observation may be cited as an
   eval timing or any local value treated as portable.
3. #59 是叠加在 #57 上、与 #53 分开的后续本地诊断。每个进程记录 5 次
   warmup 与 10 次测量，全部运行的 checksum 为 2,038,221,507；JVM、legacy、
   direct IR、evaluator IR 的中位数依次为 10,017,146.0 ns、
   167,870,311.5 ns、10,021,957.0 ns 与 411,875,537.5 ns。目标方法的
   evaluator-data marker 存在，且没有目标方法或 `IUSHR` fallback。不得把该
   单次本地诊断变成可移植或加速声明，也不得用其回填 #53。 #59 is a
   follow-up local diagnostic stacked on #57 and separate from #53. Each
   process recorded 5 warmups and 10 measurements, all runs returned checksum
   2,038,221,507, and the JVM, legacy, direct-IR, and evaluator-IR medians were
   respectively 10,017,146.0 ns, 167,870,311.5 ns, 10,021,957.0 ns, and
   411,875,537.5 ns. The target method had its evaluator-data marker and no
   target-method or `IUSHR` fallback. Do not generalize this one local
   diagnostic into a portable result or speedup, and do not back-fill #53.
4. #31 的 `mix` 被 DCE，仍不能计入 reader bar；#37 与 #50 分别读取有效存活的
   direct-IR 与 shared-evaluator artifact，均先提交恢复、再对 oracle 评分，并均
   报告四个方法为 full，因此 requirement 7 未满足。 #31 remains invalid;
   #37 and #50 respectively read valid live direct-IR and shared-evaluator
   artifacts, committed recovery before oracle scoring, and report all four
   methods as full. Requirement 7 is not met.
5. #61 仅记录对 #57 的 **accept** 技术审阅、纯文档范围、无编译器改动及
   28/28 个聚焦测试；它不构成上线就绪结论。#62 叠加在 #56 上，仅记录
   opt-in phase 8 的 `NEW`/`AllocObject`、仅限构造器的 `INVOKESPECIAL`/
   `CallNonvirtualVoidMethod` 与更广的 `I`/`J`/引用 invoke 形状；构造器方法体
   仍排除，默认仍为 legacy。其聚焦测试记录为 36 + 2 = 38，g++ 烟测翻译单元
   包含 34 个方法；该阶段仍部分且未达上线就绪。 #61 records only an
   **accept** technical review of #57, a documentation-only scope, no compiler
   change, and 28/28 focused tests; it is not a ship-readiness finding. #62 is
   stacked on #56 and records only still-opt-in phase-8 `NEW`/`AllocObject`,
   constructor-only `INVOKESPECIAL`/`CallNonvirtualVoidMethod`, and broader
   `I`/`J`/reference invoke shapes. Constructor bodies remain excluded and
   legacy remains the default. Its focused result is 36 + 2 = 38 tests and its
   g++ smoke translation unit contains 34 methods; the phase remains partial
   and not ship-ready. #63 is Fable's documentation-only review of #62 and
   records **accept**, no compiler change, and 38/38 focused tests; its
   non-blocking observation is a never-taken receiver null check on the
   constructor path. #64 is Sol's documentation-only review of #62 and records
   **accept**, no compiler change, 38/38 focused tests, and a 34-method g++
   syntax check. Neither review is a ship-readiness finding. #63 是 Fable 对
   #62 的纯文档审阅，记录 **accept**、无编译器改动及 38/38 个聚焦测试；其
   非阻塞观察为构造器路径上永不触发的 receiver null check。#64 是 Sol 对
   #62 的纯文档审阅，记录 **accept**、无编译器改动、38/38 个聚焦测试及
   34-method g++ 语法检查。两者均不是上线就绪结论。
6. #66 仅记录仍为 opt-in 的 phase-9 `ARETURN`、`ACONST_NULL`、
   `IFNULL`/`IFNONNULL` 与 category-one `POP`；`POP2` 仍 fallback，默认仍为
   legacy，记录 42 + 2 = 44 个测试及 39-method g++ 烟测。#70 发现并修复
   `jobject`/`jarray` 数组 `ARETURN` 边界，以 cast 与数组返回回归将记录更新为
   **accept**、43 + 2 = 45 个测试及 40-method g++ 烟测；它是首选 phase-9 tip。
   #71 仅为 **accept-with-nits** 文档审阅，审阅未修复的 #66 `32ac47d`，记录
   44 个测试且不包含 #70 修复。三者均不是上线就绪结论。#68 在 #57 上为
   evaluator 加入 `LLOAD`/`LSTORE`/`LADD`/`LSUB`/`LMUL`/`LRETURN`/`I2L`/`L2I`
   的 `0x23`–`0x2a` 映射，`(J)J` 保持在 eval，`LDIV`/`LREM` 仍 fallback，
   记录 31/31 且不新增 benchmark；#69 是纯文档 **accept** 审阅，记录 31/31
   并说明 shared frontend 要求 sibling direct-IR 文件同步支持。两者均未达
   上线就绪。 #66 records only still-opt-in phase-9 `ARETURN`, `ACONST_NULL`,
   `IFNULL`/`IFNONNULL`, and category-one `POP`; `POP2` still falls back,
   legacy remains the default, and it records 42 + 2 = 44 tests plus a
   39-method g++ smoke. #70 found and fixed the `jobject`/`jarray` array
   `ARETURN` boundary with a cast and array-return regression, recording
   **accept**, 43 + 2 = 45 tests, and a 40-method g++ smoke; it is the
   preferred phase-9 tip. #71 is only a documentation **accept-with-nits**
   review of unfixed #66 at `32ac47d`, records 44 tests, and does not contain
   #70's fix. None is a ship-readiness finding. Stacked on #57, #68 adds
   evaluator `LLOAD`/`LSTORE`/`LADD`/`LSUB`/`LMUL`/`LRETURN`/`I2L`/`L2I` at
   `0x23`–`0x2a`; `(J)J` stays on eval, `LDIV`/`LREM` still fall back, 31/31
   is recorded, and no benchmark is added. #69 is a documentation-only
   **accept** review with 31/31 and records the shared frontend's required
   sibling direct-IR support. Neither is ship-ready.
7. #72 叠加在 #46 上，仅记录 review-stage 的
   `NativePrimitives.hmacSha256`、C ABI `no_sdk_hmac_sha256_v1`、RFC 2104
   与仓内 SHA-256 构造、公开向量、13/13 module suite，且无 HMAC benchmark。
   #75 是纯文档 **PASS** 审阅，不是已交付 SDK；记录 focused 1/1 与完整 suite
   13/13（二者均 0 failed、0 skipped），完整 suite 构成 8 + 2 + 2 + 1，另有
   独立向量核对 4/4、C ABI probe 11/11，以及 verifier 的 2 个 SHA-256 向量、
   4 个 HMAC 向量、9 个 `MessageDigest` case、5 个 equality case、5 个 string
   vector、4 个 concatenation。#73 叠加在首选 #70 上，为 exact `I`、
   exact `J`、object/array descriptor 增加四种 instance/static field opcode；
   null receiver 以 pending NPE 走异常出口，`Z`/`B`/`C`/`S`/`F`/`D` 仍
   fallback，默认仍为 legacy，记录 47 + 2 = 49 个测试及 50-method g++ 烟测。
   #76 是纯文档 **accept-with-nits** 审阅，#77 是纯文档 **PASS** 审阅，均记录
   49/49，均不是上线就绪结论。 #72 is stacked on #46 and records only the
   review-stage `NativePrimitives.hmacSha256`, C ABI
   `no_sdk_hmac_sha256_v1`, RFC 2104 construction with the in-tree SHA-256,
   published vectors, a 13/13 module suite, and no HMAC benchmark. #75 is a
   documentation-only **PASS** review, not a shipped SDK; it records a 1/1
   focused run and 13/13 full suite (both 0 failed/0 skipped), a full-suite
   breakdown of 8 + 2 + 2 + 1, a 4/4 independent vector check, 11/11 C ABI
   probe, and verifier counts of 2 SHA-256 vectors, 4 HMAC vectors,
   9 `MessageDigest` cases, 5 equality cases, 5 string vectors, and
   4 concatenations. #73 is stacked on preferred #70 and adds all four
   instance/static field opcodes for exact `I`, exact `J`, and object/array
   descriptors. A null receiver takes the exceptional exit with NPE pending;
   `Z`/`B`/`C`/`S`/`F`/`D` still fall back, legacy remains the default, and
   47 + 2 = 49 tests plus a 50-method g++ smoke are recorded. #76 is a
   documentation-only **accept-with-nits** review and #77 a documentation-only
   **PASS** review; both record 49/49 and neither is a ship-readiness finding.
8. #78 叠加在 #73 上，为 exact `I`、exact `J`、object/array reference 与 `V`
   carrier 加入 `INVOKEINTERFACE` 和非构造器 `INVOKESPECIAL`；构造器方法体仍
   排除，默认仍为 legacy，记录 53 + 2 = 55 个测试及 59-method g++ 烟测。
   #82 与 #83 分别是 Sol 与 Fable 的纯文档 **accept** 审阅，均记录 55/55，
   均不是上线就绪结论。 #78 is stacked on #73 and adds `INVOKEINTERFACE` and
   non-constructor `INVOKESPECIAL` for exact `I`, exact `J`, object/array
   references, and `V`; constructor bodies remain excluded, legacy remains
   the default, and 53 + 2 = 55 tests plus a 59-method g++ smoke are recorded.
   #82 and #83 are respectively Sol's and Fable's documentation-only
   **accept** reviews, both record 55/55, and neither is a ship-readiness
   finding.
9. #80 叠加在 #72 上，加入以 NIST CAVP 向量验证、内嵌 tiny-AES-c 的
   AES-256-GCM，记录 13/13 且未运行 AES benchmark。#81 是包含编译器修复的
   首选 AES tip：其 **PASS with one correctness fix** 修复 32-bit
   `plaintext.size + 16` 溢出并返回 `NO_SDK_SIZE_OVERFLOW_V1`，记录 13/13、
   authenticate-before-decrypt 与固定长度 tag 的 constant-work 比较，并说明
   tiny-AES-c 并非 side-channel hardened。#81 优先于未修复 #80；两者均不是
   已交付 SDK。 #80 is stacked on #72 and adds AES-256-GCM checked against
   NIST CAVP vectors with vendored tiny-AES-c, records 13/13, and runs no AES
   benchmark. #81 is the preferred AES tip containing the compiler fix: its
   **PASS with one correctness fix** fixes 32-bit `plaintext.size + 16`
   overflow with `NO_SDK_SIZE_OVERFLOW_V1`, records 13/13,
   authenticate-before-decrypt, and constant-work fixed-length tag comparison,
   and notes that tiny-AES-c is not side-channel hardened. #81 is preferred
   over unfixed #80; neither is a shipped SDK.
10. #84 叠加在 #78 上，以 hidden static native bridge 支持构造器方法体；
   `<init>` 从不设置 `ACC_NATIVE`，唯一的直接 `this(...)`/`super(...)` 调用及其
   参数前缀留在 Java 字节码中，默认仍为 legacy，记录 57 + 2 = 59 个测试及
   61-method g++ 烟测。#89 是包含编译器修复的首选 phase-12 tip：它拒绝前缀
   `ASTORE` 覆盖 local 0 或被转发引用参数 local，修复后记录 **pass after one
   correctness fix**、58 + 2 = 60 个测试及 61-method g++ 烟测。#88 是对未修复
   #84 的纯文档 Fable **accept** 审阅，记录 59 个测试且不包含 #89 修复。#85
   叠加在 #68 上，为 evaluator 加入 `LDIV=0x2b` 与 `LREM=0x2c`，使 `(JJ)J`
   保持在 eval，记录 32/32 且不新增 benchmark；#87 是纯文档 Sol **accept**
   审阅，同样记录 32/32。以上均不是上线就绪结论。 #84 is stacked on #78 and
   supports constructor bodies through a hidden static native bridge; `<init>`
   never receives `ACC_NATIVE`, the one direct `this(...)`/`super(...)` call
   and its argument prefix remain in Java bytecode, legacy remains the default,
   and 57 + 2 = 59 tests plus a 61-method g++ smoke are recorded. #89 is the
   preferred phase-12 tip containing the compiler fix: it rejects prefix
   `ASTORE` writes to local 0 or forwarded reference-parameter locals and
   records **pass after one correctness fix**, 58 + 2 = 60 tests, and a
   61-method g++ smoke. #88 is Fable's documentation-only **accept** review of
   unfixed #84, records 59 tests, and lacks #89's fix. Stacked on #68, #85
   adds evaluator `LDIV=0x2b` and `LREM=0x2c`, keeps `(JJ)J` on eval, records
   32/32, and adds no benchmark; #87 is Sol's documentation-only **accept**
   review with 32/32. None is a ship-readiness finding.
11. #90 叠加在首选 #89 上，将仍为 opt-in 的 phase 13 扩展至 `Z`/`B`/`C`/`S`
   字段与 invoke 的精确 Boolean/Byte/Char/Short JNI family；stack/local carrier
   保持 `I32` 并显式 widen/narrow，`F`/`D` 仍 fallback，默认仍为 legacy，记录
   62 + 2 = 64 个测试及未跳过的 87-`JNICALL` g++ 烟测。#93 是 Sol 对 #90 的
   纯文档 **pass/accept** 审阅，#94 是 Fable 对 #90 的纯文档 **accept** 审阅，
   均记录 64/64 且无编译器改动；#94 的唯一非阻塞 nit 是 boolean invoke 参数被
   `& 1` 掩码，而 JVM 不在调用点掩码，对 javac 输出不可观察。#90 是首选 phase-13
   tip，#93/#94 仅为其上的纯文档审阅。#92 是叠加在 #89 上、仅测量的六方法接纳率
   报告，记录 5 IR / 1 fallback（`AdmissionTarget.unsupported(I)I`，opcode 134
   `I2F`，`<clinit>` 已排除），5/6 不是生产覆盖率，也不改编译器。以上均不是上线
   就绪结论。 #90 is stacked on preferred #89 and extends still-opt-in phase 13
   to the exact Boolean/Byte/Char/Short JNI families for `Z`/`B`/`C`/`S` field
   and invoke descriptors; stack/local carriers stay `I32` with explicit
   widen/narrow, `F`/`D` still fall back, legacy remains the default, and it
   records 62 + 2 = 64 tests plus an unskipped 87-`JNICALL` g++ smoke. #93 is
   Sol's documentation-only **pass/accept** review and #94 is Fable's
   documentation-only **accept** review; both record 64/64 with no compiler
   change, and #94's sole non-blocking nit is that boolean invoke arguments are
   masked `& 1` while the JVM does not mask at the call site, unobservable for
   javac output. #90 is the preferred phase-13 tip and #93/#94 are only
   documentation-only reviews on it. #92 is a measurement-only six-method
   admission report stacked on #89 recording 5 IR / 1 fallback
   (`AdmissionTarget.unsupported(I)I`, opcode 134 `I2F`, `<clinit>` excluded);
   its 5/6 is not production coverage and changes no compiler code. None is a
   ship-readiness finding.
12. 选项 A 仍是先前简报对 v1 **产品范围**的建议，不是缩小书面工程目标的建议；
   下一工程方向不是继续调整 encoding，而是设计不会把源算法直线、可读地降为
   native code 或可解码 evaluator blob 的 lowering；#45 → #47 → #51 →
   #54 → #56 → #62 → #63/#64 → #66 → #70/#71 → #73 → #76/#77 →
   #78 → #82/#83 → #84 → #89/#88 → #90 → #93/#94 的
   direct-IR coverage/review（#73 仅叠加在首选且含修复的 #70 上，#90 叠加在首选
   #89 上，#93/#94 是 #90 的纯文档审阅）、
   #34 → #53 的 benchmark 与叠加
   在 #57 上的独立 #59 后续测量、叠加在 #89 上、仅测量的 #92 admission 报告、
   #42 → #44 → #48 → #50 的独立 evaluator
   实验、#57/#61、#68/#69 与 #85/#87 ISA/review sibling、SDK #12 → #15 → #46 →
   #72 → #75 → #80 → #81（首选 AES tip）、compatibility #6 → #9 → #14 →
   #41，以及 options brief … → #74 → #79 → #86 → #91 → 本 PR 分别继续。
   Option A remains the prior v1 **product** recommendation, not a recommendation
   to shrink the written goal; the next lowering must avoid straight-line
   readable native output of the source algorithm and decodable evaluator
   blobs. The #45 → #47 → #51 → #54 → #56 → #62 → #63/#64 → #66 →
   #70/#71 → #73 → #76/#77 → #78 → #82/#83 → #84 → #89/#88 → #90 →
   #93/#94 direct-IR coverage/review lane,
   with #73 stacked only on preferred, fixed #70, #90 stacked on preferred #89,
   and #93/#94 as documentation-only reviews of #90,
   the #34 → #53 benchmark lane plus separate #59 follow-up stacked on #57,
   the measurement-only #92 admission report stacked on #89,
   the #42 → #44 → #48 → #50 evaluator experiment with #57/#61, #68/#69,
   and #85/#87 as ISA/review siblings,
   SDK #12 → #15 → #46 → #72 → #75 → #80 → #81 (the preferred AES tip),
   compatibility #6 → #9 → #14 → #41, and options briefs … → #74 → #79 →
   #86 → #91 → this PR continue as separate lanes.

| Area | Done on a draft branch | In flight | Not started or not evidenced |
|---|---|---|---|
| IR | Fable's typed-CFG/structured-C++ design is documented in [#5](https://github.com/gaoyu06/native-obfuscator/pull/5). The opt-in direct-IR implementation runs through phase 5 in [#40](https://github.com/gaoyu06/native-obfuscator/pull/40); [#45](https://github.com/gaoyu06/native-obfuscator/pull/45) is Fable's docs-only **accept with nits** review of that phase and changes no compiler code. [#44](https://github.com/gaoyu06/native-obfuscator/pull/44) separately records an **accept with nits** review of evaluator [#42](https://github.com/gaoyu06/native-obfuscator/pull/42), with no compiler change. | [#47](https://github.com/gaoyu06/native-obfuscator/pull/47), [#51](https://github.com/gaoyu06/native-obfuscator/pull/51), [#54](https://github.com/gaoyu06/native-obfuscator/pull/54), and [#56](https://github.com/gaoyu06/native-obfuscator/pull/56) form the still-opt-in phase-6/7 path. [#62](https://github.com/gaoyu06/native-obfuscator/pull/62) adds phase 8; [#63](https://github.com/gaoyu06/native-obfuscator/pull/63) and [#64](https://github.com/gaoyu06/native-obfuscator/pull/64) are its docs-only **accept** reviews. [#66](https://github.com/gaoyu06/native-obfuscator/pull/66) adds opt-in phase-9 `ARETURN`, `ACONST_NULL`, `IFNULL`/`IFNONNULL`, and category-one `POP`, records 44 focused tests and a 39-method g++ smoke, and leaves `POP2` on fallback and legacy as default. [#70](https://github.com/gaoyu06/native-obfuscator/pull/70) is the preferred phase-9 tip: it fixes the `jobject`/`jarray` array-return boundary and records **accept**, 45 tests, and a 40-method smoke. [#71](https://github.com/gaoyu06/native-obfuscator/pull/71) is a docs-only **accept-with-nits** review of unfixed #66 at `32ac47d`, records 44 tests, and lacks #70's fix. Stacked only on preferred #70, [#73](https://github.com/gaoyu06/native-obfuscator/pull/73) adds phase-10 instance/static field access for exact `I`, exact `J`, and object/array descriptors, records 49 focused tests and a 50-method g++ smoke, leaves six primitive sorts on fallback, and keeps legacy as default. [#76](https://github.com/gaoyu06/native-obfuscator/pull/76) is its docs-only **accept-with-nits** Fable review with 49/49; [#77](https://github.com/gaoyu06/native-obfuscator/pull/77) is its docs-only **PASS** Sol review with 49/49. The evaluator experiment #42 → #44 → [#48](https://github.com/gaoyu06/native-obfuscator/pull/48) → [#50](https://github.com/gaoyu06/native-obfuscator/pull/50) remains separate. [#57](https://github.com/gaoyu06/native-obfuscator/pull/57)/[#61](https://github.com/gaoyu06/native-obfuscator/pull/61) form one ISA/review sibling. Stacked on #57, [#68](https://github.com/gaoyu06/native-obfuscator/pull/68) adds the eight `0x23`–`0x2a` i64 operations with 31/31 focused tests and no benchmark numbers; [#69](https://github.com/gaoyu06/native-obfuscator/pull/69) is its docs-only **accept** review with 31/31 and records required shared-frontend/direct-IR integration. None establishes ship-readiness. | Full JVM semantics and parity remain incomplete, including broad descriptors/wide values, monitors, broader object construction, most primitive and multidimensional array allocation, complete invokes and exceptions, reference lifetime, class initialization, native-JAR differential E2E, and any reviewed default switch. #50 shows that the shared-evaluator lowering does not meet requirement 7 on this subject. |
| JDK compatibility | [#6](https://github.com/gaoyu06/native-obfuscator/pull/6) restores actual JUnit execution and adds JDK 17 behavioral fixtures. The stacked fix [#9](https://github.com/gaoyu06/native-obfuscator/pull/9) preserves modern class versions and accepts `TypeDescriptor` for record bootstrap rewriting; its Sol-verified run recorded 16 pass, 1 `krak2` skip, 0 fail. [#14](https://github.com/gaoyu06/native-obfuscator/pull/14) records all three new JDK 21 fixtures passing on the three harness modes, with 19 pass, 1 pre-existing skip, 0 fail. | [#41](https://github.com/gaoyu06/native-obfuscator/pull/41), stacked on #14, adds four ClassicTest fixtures compiled independently with `javac --release 25` (class-file major 69). Its status document records 24 total: 23 passed, 1 pre-existing `krak2` skip, 0 failed; each new fixture reached `OK` on `HOTSPOT`, `STD_JAVA`, and `ANDROID`. The full #6 → #9 → #14 → #41 stack remains draft. | #41 is not a blanket full-JDK-25 claim: it does not cover every language feature, library API, runtime mode, generated class shape, preview feature, or separate JDK 22–24 class file. `ConstantDynamic`, multi-release JARs, hidden classes, preview policy, virtual-thread behavior, and device-level Android evidence remain gaps. |
| Benchmarks | [#10](https://github.com/gaoyu06/native-obfuscator/pull/10) adds a checksum-gated plain-HotSpot versus current transpiled-JNI harness with raw samples and environment data. [#11](https://github.com/gaoyu06/native-obfuscator/pull/11) removes repeated warm instance-member lookup work; its one-run deltas are explicitly mixed. [#34](https://github.com/gaoyu06/native-obfuscator/pull/34) runs JVM, legacy, and IR tasks through the same harness. | [#53](https://github.com/gaoyu06/native-obfuscator/pull/53), stacked on #34, records `IrFriendlyIntKernel.run(I)I` local medians of 12,207,144.5 ns for JVM, 202,090,247.0 ns for legacy, and 11,311,481.5 ns for direct IR. Direct IR stayed on IR. Eval rejected `USHR` and used legacy fallback, so the evaluator median is `N/A` and no eval timing is claimed. Separately, [#59](https://github.com/gaoyu06/native-obfuscator/pull/59), stacked on #57, records 5/10 warmup/iterations, checksum 2,038,221,507, and JVM/legacy/direct-IR/evaluator-IR medians of 10,017,146.0 / 167,870,311.5 / 10,021,957.0 / 411,875,537.5 ns. Its target evaluator-data marker was present with no target-method or `IUSHR` fallback. Both are one-VM diagnostics, not portable results; #59 does not revise or back-fill #53. | JMH/forked baselines, confidence intervals, native-only isolation, controlled multi-machine repetitions, workload-derived release budgets, and continuous regression gates. |
| SDK | [#12](https://github.com/gaoyu06/native-obfuscator/pull/12) implements a Java 8/JNI/C-ABI v1 with ABI query, one-shot SHA-256, and equal-length constant-time byte comparison. The Linux CMake/G++ `-Xcheck:jni` integration run passed. [#15](https://github.com/gaoyu06/native-obfuscator/pull/15) independently re-ran it, checked the vendored source/license and JNI path, and concluded accept-with-nits. | [#46](https://github.com/gaoyu06/native-obfuscator/pull/46) cleanly stacks `NativeStrings` length/hash/concat on #12 without copying the general benchmark harness. [#72](https://github.com/gaoyu06/native-obfuscator/pull/72) adds review-stage `NativePrimitives.hmacSha256` and `no_sdk_hmac_sha256_v1` using RFC 2104 plus the in-tree SHA-256, records published vectors and 13/13 tests, and runs no HMAC benchmark. [#75](https://github.com/gaoyu06/native-obfuscator/pull/75) is the docs-only **PASS** review with 1/1 focused, 13/13 full-suite, 4/4 independent-vector, and 11/11 C-ABI checks. Neither is a shipped SDK. #46's local diagnostic remeasurement was slower than Java; the status document explicitly says this is not portable and not a speedup claim. The #12 → #15 → #46 → #72 → #75 lane remains draft. | The product surface, embedding and provider/update policy, target matrix, Zig execution, broader approved v1 surface if required, fuzz/allocation/concurrency/sanitizer/ABI target coverage, SBOM/update process, optional JDK 22+ FFM adapter, and release security sign-off remain unresolved. |
| Interpreter | [#7](https://github.com/gaoyu06/native-obfuscator/pull/7) documents the optional, default-off backend, ISA, and evaluation protocol. [#17](https://github.com/gaoyu06/native-obfuscator/pull/17) implements the initial integer slice; [#20](https://github.com/gaoyu06/native-obfuscator/pull/20) fixes dispatcher target validation; [#22](https://github.com/gaoyu06/native-obfuscator/pull/22) lowers the evaluation kernel's `mix` method; [#24](https://github.com/gaoyu06/native-obfuscator/pull/24) changes the generated method representation to compact hexadecimal byte blobs; and [#28](https://github.com/gaoyu06/native-obfuscator/pull/28) adds opt-in link-only publication of the transformed JAR and shared library without the generated C++ tree. | The implementation remains an open draft stack, default off, and integer-only. The three source-tree reader runs in [#21](https://github.com/gaoyu06/native-obfuscator/pull/21), [#23](https://github.com/gaoyu06/native-obfuscator/pull/23), and [#25](https://github.com/gaoyu06/native-obfuscator/pull/25) recovered both compared trees fully; the shared-library-only run in [#30](https://github.com/gaoyu06/native-obfuscator/pull/30) then recovered `add`, `sumTo`, and `mix` fully from the published `.so` without the C++ tree. | Stable shared-IR integration, broad opcode/runtime semantics, resource limits, wider differential tests, target/toolchain gates, and a human default/selection policy. |
| Automated-reader evaluation | [#21](https://github.com/gaoyu06/native-obfuscator/pull/21), [#23](https://github.com/gaoyu06/native-obfuscator/pull/23), and [#25](https://github.com/gaoyu06/native-obfuscator/pull/25) record three GPT-5.6 Sol reader runs on successive generated source-tree forms; both compared trees scored full in every run, and H0 was not rejected. [#30](https://github.com/gaoyu06/native-obfuscator/pull/30) records a fourth run using the published interpreter `.so` alone. [#37](https://github.com/gaoyu06/native-obfuscator/pull/37), stacked on the live direct-IR artifact [#35](https://github.com/gaoyu06/native-obfuscator/pull/35), records a recovery-first blinded read in which `add`, `sumTo`, `subMul`, and `mix` all scored full. [#50](https://github.com/gaoyu06/native-obfuscator/pull/50), stacked on evaluator artifact [#48](https://github.com/gaoyu06/native-obfuscator/pull/48), records the same four full scores after recovery was committed before source/oracle scoring. | Every usable run is an `N=1` tool-assisted case study with the limitations below. [#31](https://github.com/gaoyu06/native-obfuscator/pull/31) remains invalid reader-bar evidence because optimization reduced `mix` to constant-zero behavior. #37 and #50 use valid live direct-IR and shared-evaluator subjects; both full recoveries mean requirement 7 is not met. | A materially different lowering is needed: not another encoding tweak, not straight-line readable native output of the source algorithm, and not a decodable evaluator blob shipped with its evaluator. Independent readers, a frozen corpus, preregistered hypotheses, calibration, and uncontaminated repetitions remain necessary for a broader empirical claim. |

Current additions after the table's inherited wave-13 entries: the direct-IR
lane continues #73 → #76/#77 → #78 → #82/#83. #78 records phase 11's two
new invoke families, 55 focused tests, and a 59-method g++ smoke; both reviews
accept it without establishing ship-readiness. The SDK lane continues #72 →
#75 → #80 → #81; #81 is the preferred AES tip because it contains the
`NO_SDK_SIZE_OVERFLOW_V1` correctness fix absent from #80. Neither AES draft is
a shipped SDK, and tiny-AES-c is not side-channel hardened.
The prior wave continued direct IR through #84 → #89/#88. #84 records constructor
bodies via a hidden bridge, a non-native `<init>`, retained bytecode
`this`/`super` prefix, 59 tests, and a 61-method smoke. Preferred #89 contains
the forwarded-reference-local safety fix and records 60 tests; #88 accepts
only unfixed #84 with 59 tests. The evaluator lane continued #68/#69 →
#85/#87: `LDIV=0x2b` and `LREM=0x2c`, `(JJ)J` retained on eval, 32/32 on both
implementation and docs-only review, and no new benchmark numbers.

This refresh extends direct IR through #90 → #93/#94. #90, stacked on preferred
phase-12 tip #89, adds still-opt-in phase-13 `Z`/`B`/`C`/`S` field and invoke
JNI families with explicit widen/narrow; `F`/`D` still fall back and legacy
remains the default. It records 62 + 2 = 64 focused tests and an unskipped
87-`JNICALL` g++ smoke. #93 is Sol's documentation-only **pass/accept** review
and #94 is Fable's documentation-only **accept** review; both record 64/64 and
change no compiler code, and #94's sole non-blocking nit is that boolean invoke
arguments are masked `& 1` while the JVM does not mask at the call site,
unobservable for javac output. Separately, #92 is a measurement-only six-method
admission report stacked on #89: it records 5 IR / 1 fallback, the fallback
being `AdmissionTarget.unsupported(I)I` at opcode 134 (`I2F`) with `<clinit>`
excluded. Its 5/6 is not production IR coverage and changes no compiler code.

### Reader-eval evidence

The first three usable runs compared direct-C++ and interpreter-backend trees
generated from the same fixture revision, deferred the source/oracle comparison
until after both recoveries were written, and confirmed matching executable output.
The fourth run read the link-only published `.so` before opening the Java source
and used no generated C++ tree. The full/partial/fail scores below are the
recorded categorical outcomes, not a new derived metric.

#31 did run a reader, but its interesting `mix` kernel had been optimized to
constant-zero behavior, so the algorithm was not present as live code and the
result remains excluded. #35 repaired that artifact precondition: its builder
recorded six distinct `mix` outputs and live multiply, shift, bitwise, and add
instructions in the stripped direct-IR `.so`.

PR [#37](https://github.com/gaoyu06/native-obfuscator/pull/37) is the blinded reader evaluation stacked on #35: the recovery was committed first, then `add`, `sumTo`, `subMul`, and `mix` all scored **full**.
PR [#50](https://github.com/gaoyu06/native-obfuscator/pull/50) is the blinded reader evaluation stacked on #48: recovery was committed before source/oracle scoring, then `add`, `sumTo`, `subMul`, and `mix` all scored **full** on the valid live shared-evaluator stripped-`.so` subject.
Requirement 7 is **not met**: unaided readers fully recovered both the valid
live IR/direct subject in #37 and the valid live shared-evaluator subject in
#50, rather than DCE'd kernels.

| Run | What was controlled | What failed or limited the run | Measured outcome |
|---|---|---|---|
| [#21: first reader](https://github.com/gaoyu06/native-obfuscator/pull/21) | Same Java 8 class and compiler commit; interpreter tree read before direct C++; Java source reopened only after both recovery texts; Java, direct, and interpreter outputs matched. | `mix` fell back to method-specific direct C++ because the slice could not lower it. The reader had prior exposure to protocol text, fixture names, and opcode names, so the run was contaminated rather than unaided. | Interpreter and direct trees were both **full** for `add`, `sumTo`, and `mix`; H0 was not rejected. The `mix` result did not test opcode recovery. |
| [#23: blinded run after `mix` lowering](https://github.com/gaoyu06/native-obfuscator/pull/23) | Same preserved fixture for both trees; opcode recovery committed before direct recovery; fixture construction opened only afterward; `mix` was confirmed on the opcode path; both builds produced identical output. | A status-document query exposed method names and a fallback notice before recovery. No constants, instruction sequence, control flow, or source oracle was exposed, but the status-text contamination prevents an unaided claim. | Opcode and direct trees each scored **4 full / 0 partial / 0 fail**; H0 was not rejected. |
| [#25: compact-blob blinded run](https://github.com/gaoyu06/native-obfuscator/pull/25) | Same preserved fixture and source-last ordering; opcode and direct recoveries committed separately before opening fixture construction; original, direct, and opcode outputs matched exactly. | Class/build metadata and a fallback test name were exposed; the same reader handled the opcode condition before the direct condition. `divide` remained direct fallback. | Both trees scored **4 full / 0 partial / 0 fail**. The blobs plus `native_jvm_interp.cpp` were sufficient to recover all three lowered methods exactly; H0 was not rejected. |
| [#30: published-`.so`-only reader](https://github.com/gaoyu06/native-obfuscator/pull/30) | GPT-5.6 Sol received only the Linux x86-64 shared library published by #28. The directory contained the transformed JAR and `.so`, no `.cpp`; no generated C++ or private compiler tree was used. Recovery was written before Java source inspection, and published output matched the oracle. | The reader knew the names and signatures of `add`, `sumTo`, and `mix`; recorded oracle input/output examples were available as a consistency check. This is still one tool-assisted reader on one fixture and target. | `add`, `sumTo`, and `mix` all scored **full**. `mix` was recovered exactly, including constants, operation order, loop condition, shifts, multiply, xor, and rotate distance. The run rejected its “cannot recover critical logic from the published shared library alone” H0 for this fixture. |
| [#31: stripped direct-IR attempt](https://github.com/gaoyu06/native-obfuscator/pull/31) | A stripped IR/direct-C++ `.so` was read without an opcode machine. | Optimization left only constant-zero behavior for `mix`; the live algorithm was absent. | **Excluded from the reader bar.** “`mix` not recovered” cannot count as success when DCE removed the kernel. |
| [#35 artifact](https://github.com/gaoyu06/native-obfuscator/pull/35) → [#37 reader](https://github.com/gaoyu06/native-obfuscator/pull/37) | #35's builder evidence records diverse `mix` outputs and live integer operations in the stripped direct-IR `.so`. #37 committed reconstruction before opening the jar, run record, or source. | One unaided reader on one x86-64 artifact (`N=1`). | `add`, `sumTo`, `subMul`, and `mix` all scored **full**. The subject was valid and input-dependent, not a constant-return stub; requirement 7 was not met. |
| [#48 artifact](https://github.com/gaoyu06/native-obfuscator/pull/48) → [#50 reader](https://github.com/gaoyu06/native-obfuscator/pull/50) | #48's liveness gate confirms a stripped `--ir-lower=eval` `.so`, matching oracle/native output, evaluator trampolines, and live operations in the evaluator/blobs. #50 committed recovery before opening source/oracle material for scoring. | One unaided reader on one x86-64 artifact (`N=1`). | `add`, `sumTo`, `subMul`, and `mix` all scored **full**. The subject was valid live evaluator code, not DCE; requirement 7 was not met. |

These runs do not establish a population effect or equal reading effort. The
interpreter runs establish that **removing the C++ sources is not sufficient
while a decodable opcode stream and its opcode machine remain in the shipped
binary.** #37 separately establishes that this direct-IR lowering also misses
the reader bar: its live source algorithm remained readable in straight-line
native code in the stripped `.so`. #50 establishes that the shared-evaluator
lowering also misses the bar on its valid live stripped `.so`: the reader
recovered all four method formulas/control flow from the trampolines, blobs,
and evaluator semantics.

## Decisions

“Does not need a human” below means no new product-policy choice is needed:
the engineering stance is already justified by repository evidence or the
Sol/Fable cross-check. It does **not** waive normal code review for draft PRs.

### Already taken; no new human decision

| Decision already taken | Rationale and boundary |
|---|---|
| Treat #1/#2 as untrusted research except where #3 accepts or revises a claim. | The drafts contain unmeasured rankings and performance claims. #3 re-derived accepted ideas against the repository and explicitly rejects fabricated numbers and unsupported production labels. |
| Restore executable JUnit and use behavioral reference-versus-transformed oracles. | #4 found that the prior Gradle invocation executed no JUnit tests; #6 fixes that and requires exact observable output. Test infrastructure can be merged without defining a product support tier. |
| Preserve input class-file versions and reject unsupported semantics rather than blindly stamping version 52. | #6 reproduced broken nest/record/sealed metadata; #9 fixed the cause and Sol independently verified major 61 retention and the 16/1/0 run. This is a correctness repair, not a market-position choice. |
| Build a project-owned typed CFG over ASM and emit structured C++ before a second backend. | Independent Sol #3 and Fable #5 designs converge on this migration shape. It addresses the audited string-template limitations while keeping backend semantics shared. Whether and when IR becomes the public default remains a human decision. |
| Validate an entire IR method before mutating output, and keep legacy as the migration default. | #13/#16 verify clean per-method fallback and compileability for their narrow slices. This contains experimental risk; it does not establish parity or authorize an eventual default flip. |
| Require checksums, raw samples, environment metadata, and scoped wording for performance evidence. | #10 demonstrates both modes actually ran and agreed. #11's mixed result shows why a single local run cannot become a global speed or non-regression claim. |
| Keep reader outcomes scoped to the measured fixture and recorded limitations. | The usable runs are `N=1`, tool-assisted case studies with different artifact boundaries and recorded limitations. Their full recoveries support the kernel-specific conclusions above, but not a population effect or broader claim. |

### Human decisions still required

#### Reader-eval maintainer options

- **A. Accept that this bar is out of scope for the v1 product.** Ship only the
  compiler, compatibility, and SDK work that passes its own correctness and
  release gates. This is a product-scope option, not a proposal to shrink the
  written engineering goal.
- **B. Fund a different backend/product design.** **B1, link-only publication,**
  is already evidenced as insufficient by [#28](https://github.com/gaoyu06/native-obfuscator/pull/28)
  and [#30](https://github.com/gaoyu06/native-obfuscator/pull/30): removing the
  C++ tree still leaves a decodable opcode stream and its machine in the
  shipped library. #37 now also shows that direct IR lowering to straight-line
  readable native code exposes the live source algorithm. #50 shows that
  shared-evaluator trampolines plus live decodable blobs also expose all four
  methods on this subject. The remaining B is a lowering that avoids all three
  evidenced forms. If that design is not funded, drop the
  reader bar from the v1 product gate under A.
- **C. Keep iterating encodings.** This is likely wasted effort while the
  opcode machine and stream remain together in the generated tree or shipped
  library and the reader can recover method semantics from them.

**Product recommendation retained from the earlier briefs:** choose A for v1
unless a materially different lowering is explicitly funded. This does **not**
recommend rewriting or shrinking the written goal to A. #37 supersedes the
wait for a live-kernel reader. The next reader-bar design must not leave the
source algorithm as straight-line readable native code or a decodable
evaluator blob shipped with its evaluator; encoding tweaks alone are not that
design. Wider opt-in direct-IR coverage/review in #45 → #47 → #51 → #54 →
#56 → #62 → #63/#64 → #66 → #70/#71 → #73 → #76/#77 → #78 → #82/#83 →
#84 → #89/#88 → #90 → #93/#94
(with #73 based only on preferred, fixed #70, #90 based on preferred #89, and
#93/#94 as documentation-only reviews of #90), the #34 → #53 benchmark lane plus
#59 stacked on #57, the measurement-only #92 admission report stacked on #89,
the separate #42 → #44 → #48 → #50 evaluator experiment with
#57/#61, #68/#69, and #85/#87 as ISA/review siblings, the SDK #12 → #15 →
#46 → #72 → #75 → #80 → #81, with #81 preferred over unfixed #80, and
compatibility #6 → #9 → #14 → #41 stacks, and options briefs
… → #74 → #79 → #86 → #91 → this PR continue as separate engineering lanes.

#### Other product decisions

| Decision | Concrete options | Recommendation | Main risk |
|---|---|---|---|
| Production Java promise | Baseline 8, 11, 17, or 21; use one “supports JDK N” badge or publish host/input/output/runtime dimensions separately. | Make JDK 17 the first required baseline; publish each dimension and keep 8/11 as separately tested legacy profiles, with 21/25 promoted only after feature-corpus evidence. | A high floor excludes users; a broad badge without feature evidence creates a false compatibility promise. |
| IR rollout and unsupported methods | Keep legacy indefinitely; flip to IR at partial coverage; flip only after parity; fail closed or allow explicit Java/legacy fallback with a manifest. | Keep legacy default now. Flip only after supported-op parity and full native differential gates; use precise refusal by default and an explicitly selected, recorded fallback profile. | An early flip breaks workloads; indefinite fallback doubles semantics and can hide unsupported methods. |
| SDK v1 product and supply-chain contract | Ship current SHA-256/equality surface; add encoding or BLAKE3 first; always embed or opt in; keep the pinned vendored provider or mandate a system/FIPS provider. | Prefer the smallest opt-in v1 justified by a real workload. Freeze the API only with security/license/update approval; avoid adding BLAKE3 without a concrete use case. | Public ABI mistakes persist; unconditional embedding increases footprint and update duty; provider policy can create compliance or side-channel liability. |
| JNI data-access/native-access policy | Copy arrays; direct buffers; bounded critical access; size-based hybrid. For modern JDKs: document enablement, warning-allowed operation, or deny-by-policy. | Keep checked copies as the default, add caller-selected direct buffers only when useful, and allow critical access only after bounded collector-aware evidence. Explicitly test/document modern native-access flags. | Copies can be slow; pinning can harm GC or violate JNI constraints; missing deployment policy can turn warnings or denial into production failures. |
| Interpreter product policy | Off; explicit per-method/build opt-in; automatic fallback; default backend. Keep format internal or promise public compatibility. | Keep it off by default and explicitly selected with manifest/resource limits; version the format for rejection but keep it internal. | Automatic/default interpretation can conceal compiler gaps, add runtime attack surface, and impose dispatch cost; a public format freezes evolution. |
| Native target/toolchain tiers | Linux only; x86-64 Linux/Windows/macOS; add arm64; CMake host compilers, Zig, or both. | Start with an evidence-backed x86-64 Linux/Windows/macOS CMake tier; validate arm64 and Zig separately before promotion. | A broad matrix multiplies ABI, loader, sanitizer, signing, and support work; a narrow one excludes customers. |
| Performance release gates | No threshold; absolute limits; relative-to-HotSpot limits; per-workload regression budgets. | Establish repeatable baselines first, then approve correctness-first, workload-specific budgets; never use one global speedup target. | No gate permits regressions; premature/noisy thresholds can be gamed and optimize the wrong workloads. |

## Suggested merge order

Every item below still needs its own review and branch evidence re-run after
rebasing. For a stacked PR, merge the base first, retarget the next PR to
`master`, verify that only the intended delta remains, then merge it.

1. Merge the authority/design set in dependency order:
   [#3](https://github.com/gaoyu06/native-obfuscator/pull/3) →
   [#4](https://github.com/gaoyu06/native-obfuscator/pull/4) →
   [#5](https://github.com/gaoyu06/native-obfuscator/pull/5) →
   [#7](https://github.com/gaoyu06/native-obfuscator/pull/7). #1/#2 are optional
   research archives only after their untrusted status and #3 corrections are
   preserved; they are not implementation prerequisites.
2. Merge the compatibility stack exactly
   [#6](https://github.com/gaoyu06/native-obfuscator/pull/6) →
   [#9](https://github.com/gaoyu06/native-obfuscator/pull/9) →
   [#14](https://github.com/gaoyu06/native-obfuscator/pull/14) →
   [#41](https://github.com/gaoyu06/native-obfuscator/pull/41). This first
   establishes a real test oracle, fixes the failures it exposes, extends the
   corpus to JDK 21, then adds the narrowly scoped `javac --release 25`
   fixtures; #41 is not a full-JDK-25 support claim.
3. Merge [#10](https://github.com/gaoyu06/native-obfuscator/pull/10) before
   [#11](https://github.com/gaoyu06/native-obfuscator/pull/11), rebase #11 over
   #6/#10, and rerun both correctness and benchmark evidence. #11's mixed local
   result is not a speed gate.
4. Merge the IR stack exactly
   [#8](https://github.com/gaoyu06/native-obfuscator/pull/8) →
   [#13](https://github.com/gaoyu06/native-obfuscator/pull/13) →
   [#16](https://github.com/gaoyu06/native-obfuscator/pull/16) →
   [#19](https://github.com/gaoyu06/native-obfuscator/pull/19) →
   [#29](https://github.com/gaoyu06/native-obfuscator/pull/29) →
   [#33](https://github.com/gaoyu06/native-obfuscator/pull/33) →
   [#36](https://github.com/gaoyu06/native-obfuscator/pull/36) →
   [#39](https://github.com/gaoyu06/native-obfuscator/pull/39) →
   [#40](https://github.com/gaoyu06/native-obfuscator/pull/40) →
   [#45](https://github.com/gaoyu06/native-obfuscator/pull/45) →
   [#47](https://github.com/gaoyu06/native-obfuscator/pull/47) →
   [#51](https://github.com/gaoyu06/native-obfuscator/pull/51) →
   [#54](https://github.com/gaoyu06/native-obfuscator/pull/54) →
   [#56](https://github.com/gaoyu06/native-obfuscator/pull/56) →
   [#62](https://github.com/gaoyu06/native-obfuscator/pull/62) →
   [#63](https://github.com/gaoyu06/native-obfuscator/pull/63) /
   [#64](https://github.com/gaoyu06/native-obfuscator/pull/64) →
   [#66](https://github.com/gaoyu06/native-obfuscator/pull/66) →
   [#70](https://github.com/gaoyu06/native-obfuscator/pull/70) /
   [#71](https://github.com/gaoyu06/native-obfuscator/pull/71) →
   [#73](https://github.com/gaoyu06/native-obfuscator/pull/73) →
   [#76](https://github.com/gaoyu06/native-obfuscator/pull/76) /
   [#77](https://github.com/gaoyu06/native-obfuscator/pull/77) →
   [#78](https://github.com/gaoyu06/native-obfuscator/pull/78) →
   [#82](https://github.com/gaoyu06/native-obfuscator/pull/82) /
   [#83](https://github.com/gaoyu06/native-obfuscator/pull/83) →
   [#84](https://github.com/gaoyu06/native-obfuscator/pull/84) →
   [#89](https://github.com/gaoyu06/native-obfuscator/pull/89) /
   [#88](https://github.com/gaoyu06/native-obfuscator/pull/88) →
   [#90](https://github.com/gaoyu06/native-obfuscator/pull/90) →
   [#93](https://github.com/gaoyu06/native-obfuscator/pull/93) /
   [#94](https://github.com/gaoyu06/native-obfuscator/pull/94). Rebase after
   #6 so the duplicated JUnit-launcher change is resolved once. Do not squash
   away review fixes or treat #47/#51/#54/#56/#62 as parity or ship-ready. #39 and
   #45 are the docs-only Fable reviews of phases 4 and 5; #51 is Sol's phase-6
   accept-with-nits review after Fable was policy-blocked and includes the
   array-component `FindClass` fix. #54 adds the still-opt-in phase-7
   `CHECKCAST`/`INSTANCEOF` and initial `I64` slice. #56 is Sol's docs-only
   **accept** review and records the 35/35 focused-test rerun.
   #62 is stacked on #56 and adds the still-opt-in phase-8 allocation,
   constructor-call, and broader invoke slice; constructor bodies remain
   excluded, legacy remains the default, and the recorded 38 focused tests
   plus 34-method g++ smoke do not make it ship-ready.
   #63 and #64 are parallel documentation-only reviews of #62 rather than
   compiler successors. Both record **accept**, no compiler change, and 38/38
   focused tests; #63 records one non-blocking never-taken constructor-receiver
   null-check observation, while #64 records a 34-method g++ syntax check.
   Neither review establishes ship-readiness.
   #66 adds still-opt-in phase-9 reference returns, null values/branches, and
   category-one discard; `POP2` remains fallback, legacy remains default, and
   its 44 focused tests plus 39-method g++ smoke do not establish readiness.
   #70 is the preferred phase-9 tip because it fixes the `jobject`/`jarray`
   array-return carrier mismatch, adds the regression, records **accept**,
   45 tests, and a 40-method smoke. #71 is only a parallel docs review of the
   unfixed #66 `32ac47d` tip; its **accept-with-nits** and 44-test record do not
   contain or supersede #70's fix. #73 is based on #70, not #66/#71, and adds
   exact-`I`, exact-`J`, and reference field access with null-receiver NPE
   exits; six primitive sorts still fall back, legacy remains default, and it
   records 49 tests plus a 50-method g++ smoke. #76 and #77 are parallel
   documentation-only reviews of #73: #76 records **accept-with-nits** and
   49/49; #77 records **PASS** and 49/49. None is a ship-readiness finding.
   #78 is based on #73 and adds interface and non-constructor special invokes
   for exact `I`, exact `J`, references, and `V`; constructor bodies remain
   excluded, legacy remains default, and it records 55 tests plus a 59-method
   smoke. #82 and #83 are parallel documentation-only **accept** reviews with
   55/55. Neither is a ship-readiness finding.
   #84 is based on #78 and adds the verifier-safe constructor split through a
   hidden native bridge; `<init>` remains non-native, the direct `this`/`super`
   prefix stays in bytecode, legacy remains default, and it records 59 tests
   plus a 61-method smoke. Prefer #89 because its compiler fix rejects unsafe
   prefix `ASTORE` writes to local 0 and forwarded reference-parameter locals;
   its post-fix record is 60 tests and a 61-method smoke. #88 is a parallel
   docs-only **accept** review of unfixed #84 with 59 tests and does not contain
   #89's fix. None is a ship-readiness finding.
   #90 is based on preferred #89 and adds still-opt-in phase-13 `Z`/`B`/`C`/`S`
   field and invoke JNI families with explicit widen/narrow; `F`/`D` still fall
   back, legacy remains default, and it records 62 + 2 = 64 tests plus an
   unskipped 87-`JNICALL` g++ smoke. #93 and #94 are parallel documentation-only
   reviews of #90, not compiler successors: #93 records Sol's **pass/accept** and
   #94 records Fable's **accept**, both with 64/64 and no compiler change; #94's
   sole non-blocking nit is that boolean invoke arguments are masked `& 1` while
   the JVM does not mask at the call site, unobservable for javac output. Prefer
   #90 as the implementation tip over treating either review as the change. None
   is a ship-readiness finding.
   [#42](https://github.com/gaoyu06/native-obfuscator/pull/42) is a separate
   sibling lane from #39, not the next item in the direct-IR stack. Review it
   through [#44](https://github.com/gaoyu06/native-obfuscator/pull/44), then
   retain [#48](https://github.com/gaoyu06/native-obfuscator/pull/48) as its
   artifact-only successor and [#50](https://github.com/gaoyu06/native-obfuscator/pull/50)
   as the recovery-first reader record: #42 → #44 → #48 → #50. Retain
   `direct` as the default lowering. #50 is evaluation evidence, not an
   implementation merge prerequisite. [#57](https://github.com/gaoyu06/native-obfuscator/pull/57)
   is a separate ISA sibling from #44, not a successor to #50: it adds the six
   recorded bitwise/shift operations so the equivalent integer kernel can stay
   on eval, claims 28/28 focused tests, and records no new benchmark timing.
   [#61](https://github.com/gaoyu06/native-obfuscator/pull/61) is Sol's
   documentation-only review of #57; it records **accept**, no compiler change,
   and a 28/28 focused-test rerun, but no ship-readiness finding.
   [#68](https://github.com/gaoyu06/native-obfuscator/pull/68), stacked on #57,
   is the next ISA sibling: it adds the eight `0x23`–`0x2a` i64 operations,
   keeps `(J)J` on eval and `LDIV`/`LREM` on fallback, records 31/31 focused
   tests, and adds no benchmark numbers.
   [#69](https://github.com/gaoyu06/native-obfuscator/pull/69) is its
   documentation-only **accept** review with 31/31; it records that shared
   frontend admission requires the sibling direct-IR file support and does not
   establish ship-readiness.
   [#85](https://github.com/gaoyu06/native-obfuscator/pull/85), stacked on #68,
   then adds `LDIV=0x2b` and `LREM=0x2c`, keeps generated `(JJ)J`
   divide/remainder methods on eval, records 32/32, and adds no benchmark.
   [#87](https://github.com/gaoyu06/native-obfuscator/pull/87) is its
   documentation-only Sol **accept** review with 32/32 and no compiler change.
   [#59](https://github.com/gaoyu06/native-obfuscator/pull/59) is a benchmark
   follow-up stacked on #57. It records evaluator-path timing only for its own
   no-fallback run and must not be used to back-fill #53.
   Keep the original benchmark evidence in order:
   [#34](https://github.com/gaoyu06/native-obfuscator/pull/34) →
   [#53](https://github.com/gaoyu06/native-obfuscator/pull/53). #53 integrates
   evaluator selection but records no eval timing because `USHR` caused legacy
   fallback; it is evidence, not an implementation merge prerequisite. Keep
   #59 as the distinct sibling/follow-up benchmark stacked on #57 rather than
   collapsing the two runs.
   [#92](https://github.com/gaoyu06/native-obfuscator/pull/92) is a
   measurement-only admission report stacked on #89: a two-class, six-method
   Java 8 corpus recorded 5 IR / 1 fallback (the `AdmissionTarget.unsupported(I)I`
   method at opcode 134, `I2F`, with `<clinit>` excluded). It is scoped
   measurement evidence, not a production coverage gate and not an implementation
   merge prerequisite; do not generalize its 5/6 beyond that corpus.
   Separately,
   [#35](https://github.com/gaoyu06/native-obfuscator/pull/35) is an eval-only
   live-artifact sibling on #33; keep the #35 →
   [#37](https://github.com/gaoyu06/native-obfuscator/pull/37) evaluation lane
   separate from both #40 and #42. Preserve #31 as an invalid reader-bar record
   and #37 as #35's recovery-first reader record. Neither evaluation draft is
   an implementation merge prerequisite.
5. Merge [#12](https://github.com/gaoyu06/native-obfuscator/pull/12) →
   [#15](https://github.com/gaoyu06/native-obfuscator/pull/15) →
   [#46](https://github.com/gaoyu06/native-obfuscator/pull/46) →
   [#72](https://github.com/gaoyu06/native-obfuscator/pull/72) →
   [#75](https://github.com/gaoyu06/native-obfuscator/pull/75) →
   [#80](https://github.com/gaoyu06/native-obfuscator/pull/80) →
   [#81](https://github.com/gaoyu06/native-obfuscator/pull/81) after resolving
   the same #6 launcher overlap and retargeting the clean #46 delta over the
   reviewed SDK base. The Fable accept-with-nits review is not the human
   product/security approval listed above; #46's local slower-than-Java result
   is not a portable release gate. #72 adds the review-stage HMAC API/C ABI and
   no HMAC benchmark; #75's documentation-only **PASS**, including its 13/13
   suite and 11/11 C ABI probe, is not a shipped-product decision. #80 adds
   NIST-CAVP-checked AES-256-GCM with vendored tiny-AES-c, records 13/13, and
   runs no AES benchmark. Prefer #81 because its **PASS with one correctness
   fix** includes the `NO_SDK_SIZE_OVERFLOW_V1` output-length fix absent from
   #80. #81 still is not a shipped SDK, and tiny-AES-c is not side-channel
   hardened.
6. Land [#7](https://github.com/gaoyu06/native-obfuscator/pull/7) first, then
   review the interpreter implementation stack in order:
   [#17](https://github.com/gaoyu06/native-obfuscator/pull/17) →
   [#20](https://github.com/gaoyu06/native-obfuscator/pull/20) →
   [#22](https://github.com/gaoyu06/native-obfuscator/pull/22) →
   [#24](https://github.com/gaoyu06/native-obfuscator/pull/24) →
   [#28](https://github.com/gaoyu06/native-obfuscator/pull/28). Keep it default
   off and review it against the stable shared IR before placing it after the
   direct-C++ slice. Preserve the corresponding reader records:
   [#21](https://github.com/gaoyu06/native-obfuscator/pull/21) for #17,
   [#23](https://github.com/gaoyu06/native-obfuscator/pull/23) for #22, and
   [#25](https://github.com/gaoyu06/native-obfuscator/pull/25) for #24, followed
   by [#30](https://github.com/gaoyu06/native-obfuscator/pull/30) for #28; these
   document measured outcomes and are not implementation prerequisites.

The independent compatibility, benchmark, IR, and SDK lanes may be reviewed in
parallel, but their order within each arrowed stack must be preserved.

## Honest gaps

- #41 adds four ClassicTest fixtures compiled independently with
  `javac --release 25`; its status document records 23 passed, 1 pre-existing
  skip, and 0 failed. This is evidence only for class-file major 69 and the
  four listed surfaces on that VM, not full JDK 25 support, preview coverage,
  or separate JDK 22–24 class-file coverage.
- The usable automated-reader evaluations are `N=1`, tool-assisted case
  studies with recorded limitations. The first three produced
  full/full source-tree outcomes and did not reject H0; the fourth fully
  recovered all three methods from the published `.so` and rejected its
  shared-library-only H0. They support only the kernel-and-artifact conclusion
  in the reader-eval subsection. The fifth, #37, fully recovered all four
  methods from #35's valid live direct-IR stripped `.so`; #50 fully recovered
  the same four methods from #48's valid live shared-evaluator stripped `.so`.
  #31 remains invalid for the reader bar because its `mix` kernel was DCE'd.
- IR is opt-in and incomplete. #47 adds the recorded switch and object
  `ANEWARRAY` phase-6 slice. #51's Sol review records **accept with nits** after
  fixing array-component resolution to use descriptor-based `FindClass`.
  #54 adds phase-7 `CHECKCAST`/`INSTANCEOF` and an initial two-slot `I64` slice;
  its status document claims 33 `IrCompilerTest` plus 2 `CodegenModeTest`, all
  with 0 skipped/failures/errors, and keeps legacy as the codegen default. #56
  is Sol's docs-only **accept** review of #54 and records 35/35 focused tests
  rerun. The #42 evaluator supports a narrower integer subset, is selected only
  with `--ir-lower=eval`, and keeps `direct` as the lowering default. #44
  accepts it with nits; #48 publishes a live stripped artifact, and #50's
  recovery-first reader scores all four methods full. As a sibling from #44,
  #57 adds `IAND`/`IOR`/`IXOR`/`ISHL`/`ISHR`/`IUSHR`, records the equivalent
  integer kernel staying on eval, claims 28/28 focused tests, and adds no
  benchmark timings. #61 is Sol's docs-only **accept** review of #57, records
  no compiler change and 28/28 focused tests, and is not a ship-readiness
  finding. #62 is stacked on #56 and adds opt-in `NEW` via `AllocObject`,
  constructor-only `INVOKESPECIAL` via `CallNonvirtualVoidMethod`, and broader
  `I`/`J`/reference invoke shapes. Constructor bodies remain excluded, legacy
  remains the default, and its recorded 38 focused tests plus 34-method g++
  smoke do not make this partial phase ship-ready. #63 and #64 are
  documentation-only **accept** reviews of #62 with no compiler changes and
  38/38 focused tests. #63's sole non-blocking observation is a never-taken
  constructor-receiver null check; #64 additionally records a 34-method g++
  syntax check. Neither is a ship-readiness finding.
  #66 adds opt-in phase-9 `ARETURN`, `ACONST_NULL`, `IFNULL`/`IFNONNULL`, and
  category-one `POP`; `POP2` still falls back and legacy remains default. Its
  44-test and 39-method-smoke record remains partial. #70 fixes the
  `jobject`/`jarray` array-return mismatch and records **accept**, 45 tests, and
  a 40-method smoke, making it the preferred phase-9 tip but not ship-ready.
  #71's docs-only **accept-with-nits** review covers unfixed #66 at `32ac47d`,
  records 44 tests, and does not include #70's fix.
  #68 adds the eight evaluator i64 operations at `0x23`–`0x2a`, records 31/31,
  leaves `LDIV`/`LREM` on fallback, and adds no benchmark numbers. #69's
  docs-only **accept** review records 31/31 and the required shared-frontend
  sibling direct-IR support; neither is ship-ready.
- #73 remains an opt-in, partial phase 10 on preferred #70. Its exact `I`, `J`,
  and reference instance/static field slice records 49 tests and a 50-method
  g++ smoke, while `Z`/`B`/`C`/`S`/`F`/`D` and the broader documented gaps
  still fall back. #76 and #77 are documentation-only reviews with 49/49;
  neither makes the phase ship-ready.
- #78 remains an opt-in, partial phase 11 on #73. It adds interface and
  non-constructor special invokes only for exact `I`, exact `J`, references,
  and `V`; constructor bodies remain excluded and legacy remains default. Its
  55 tests and 59-method smoke, and #82/#83's documentation-only **accept**
  reviews with 55/55, do not make it ship-ready.
- #84 remains an opt-in, partial phase 12 on #78. Its hidden bridge keeps
  `<init>` non-native and retains the verifier-required `this`/`super` prefix
  in bytecode; its 59 tests and 61-method smoke do not make it ship-ready.
  Prefer #89 because it fixes unsafe forwarded-reference-local prefix writes
  and records 60 tests after the fix. #88 reviews unfixed #84, records 59, and
  does not contain #89's fix.
- #85 adds evaluator `LDIV=0x2b` and `LREM=0x2c` on #68, keeps `(JJ)J` on eval,
  records 32/32, and adds no benchmark. #87 is its docs-only **accept** review
  with 32/32; neither is ship-ready.
- #90 remains an opt-in, partial phase 13 on preferred #89. It adds the exact
  Boolean/Byte/Char/Short JNI families for `Z`/`B`/`C`/`S` field and invoke
  descriptors with explicit widen/narrow, but `F`/`D`, non-int arrays, `POP2`,
  and invokedynamic still fall back and legacy remains the default. Its 64 tests
  and unskipped 87-`JNICALL` g++ smoke do not make it ship-ready. #93 and #94
  are documentation-only reviews (Sol **pass/accept** and Fable **accept**), both
  64/64 with no compiler change; #94's only nit is that boolean invoke arguments
  are masked `& 1` while the JVM does not mask at the call site, unobservable for
  javac output. Neither review is a compiler fix or a ship-readiness finding.
- #92 is a measurement-only admission report on the #89 tip, not a coverage
  gate. Its two-class, six-method Java 8 corpus recorded 5 IR / 1 fallback (the
  `AdmissionTarget.unsupported(I)I` method at opcode 134, `I2F`, with `<clinit>`
  excluded). This 5/6 (83.3%) is synthetic and must not be generalized to
  production IR coverage; it changes no compiler code.
- #46 cleanly stacks `NativeStrings` on SDK v1 without duplicating the general
  benchmark harness. Its status document records the local diagnostic as
  slower than Java and explicitly rejects a portable or speedup claim.
- #72's HMAC-SHA-256 API/C ABI remains review-stage. #75 records a
  documentation-only **PASS** with 1/1 focused and 13/13 full-suite checks
  (both 0 failed/0 skipped), the 8 + 2 + 2 + 1 suite breakdown, 4/4
  independent-vector, and 11/11 C-ABI checks, but it is not a shipped SDK and
  no HMAC benchmark was run.
- #80's AES-256-GCM SDK surface remains review-stage. It records NIST CAVP
  vectors, vendored tiny-AES-c, 13/13, and no AES benchmark. #81 is preferred
  because it fixes 32-bit `plaintext.size + 16` overflow with
  `NO_SDK_SIZE_OVERFLOW_V1`; its PASS, authenticate-before-decrypt ordering,
  and constant-work fixed-length tag comparison do not make it a shipped SDK.
  tiny-AES-c is not side-channel hardened.
- #10's one local checksum-correct run shows the current transpiled-JNI path
  much slower than plain HotSpot for all three exact kernels: median ratios are
  about 18× for the integer loop, 23× for string concat/hash, and 199× for
  recursion. This is diagnostic evidence for those workloads, not a portable
  estimate, but it directly contradicts any present speedup claim.
- #53 advances the #34 benchmark lane on `IrFriendlyIntKernel.run(I)I`. Its
  recorded local medians are 12,207,144.5 ns for JVM, 202,090,247.0 ns for
  legacy, and 11,311,481.5 ns for direct IR. Direct IR stayed on IR. Eval
  rejected `USHR` and fell back to legacy, so its median is `N/A` and no eval
  timing is claimed. None of these local values is portable.
- #59 is a separate remeasurement stacked on #57, not a correction to #53.
  It records 5 warmups / 10 measured iterations, checksum 2,038,221,507, and
  medians of 10,017,146.0 ns for JVM, 167,870,311.5 ns for legacy,
  10,021,957.0 ns for direct IR, and 411,875,537.5 ns for evaluator IR. The
  target evaluator-data marker was present and no target-method or `IUSHR`
  fallback occurred. This is one local diagnostic, not a portable result or
  speedup claim; #53's eval median remains `N/A`.
- PRs #1–#94 are still open drafts. `master` contains none of their work.

## Before any production claim

A production claim requires evidence on the exact release commit and artifacts,
not the union of claims from draft branches:

1. Merge and review the applicable stacks, resolve overlaps, and rerun their
   full commands after rebasing. Required jobs must report actual test counts
   and artifacts, not merely a configured matrix or successful compilation.
2. Approve and publish the Java support dimensions and native target/toolchain
   tiers. Treat #41's four JDK 25 fixtures as narrow evidence, then add the
   remaining required metadata, bootstrap, module/multi-release, refusal,
   preview-policy, runtime, and broader feature cases before any full-JDK-25
   claim.
3. For any production IR claim, complete the declared semantic surface, prove
   reference-Java versus generated-native behavior across the supported matrix,
   compile generated C++ with warnings-as-errors/sanitizers, run `-Xcheck:jni`,
   retain #89's forwarded-reference-local constructor fix rather than using
   unfixed #84/#88, and make a reviewed default/fallback/legacy-retirement
   decision. Do not flip the default: #90 adds only the still-opt-in phase-13
   `Z`/`B`/`C`/`S` field and invoke families with `F`/`D` still on fallback, and
   #93/#94 are documentation-only reviews of #90, not compiler fixes. Treat #92's
   5/6 admission measurement as scoped evidence, not a coverage gate, and prefer
   #90 as the implementation tip over either review.
4. Replace the one-machine diagnostic benchmark with controlled repeated raw
   results, forked/JMH and native-only isolation where applicable, end-to-end
   cost data, and human-approved workload budgets. Either meet those budgets or
   explicitly accept the current JNI cost; do not market a speedup from present
   evidence.
5. If the SDK ships, freeze its API/ABI and embedding/provider choices, close
   the #15 nits and review #46's JNI lifetime, UTF-16, concat-overflow, and
   loader behavior as appropriate. Review #72's RFC 2104 construction,
   input-size/JNI exception contracts, C ABI, vector provenance, and generated
   packaging; #75's PASS does not replace product/security approval. Test all
   AES changes from preferred #81 rather than unfixed #80, retaining the
   `NO_SDK_SIZE_OVERFLOW_V1` check. Review authenticate-before-decrypt, the
   fixed-length tag comparison, nonce-uniqueness policy, NIST vector
   provenance, and tiny-AES-c's lack of side-channel hardening. Test all
   tier-1 targets and loaders
   (including Zig only if supported), and complete fuzzing, sanitizer,
   allocation, concurrency, license/provenance, SBOM, vulnerability/update,
   and security review gates. Do not turn #46's local measurement into a
   portable performance claim.
6. If the interpreter ships, first submit and review its implementation against
   the shared IR, then prove differential parity, deterministic refusal,
   resource limits, format/version rejection, and target/toolchain behavior.
   Otherwise exclude it from the release claim.
7. Do not claim that the reader bar has been met. #30 recovered `mix` from the
   interpreter `.so` without the C++ tree, and #37 recovered `add`, `sumTo`,
   `subMul`, and `mix` from #35's valid live IR/direct stripped `.so`. #31
   remains invalid because its `mix` was DCE'd; it does not offset #37. #50
   also recovered all four methods from #48's valid live shared-evaluator
   stripped `.so`. The written goal therefore needs a lowering that is neither
   a straight-line readable native form of the source algorithm nor a
   decodable evaluator blob shipped with its evaluator, not another encoding
   tweak. Any broader reader claim needs raw reproducible results whose scope
   and limitations support its exact wording, plus privacy and methodology
   approval. The reader claim is not a v1 product prerequisite if option A is
   selected, but selecting A does not shrink the written engineering goal.
8. Produce reproducible signed/provenanced artifacts, an SBOM and symbol
   allowlist, package/native-access documentation, crash/support and
   incident-response ownership, upgrade/rollback instructions, and final
   release approval for the residual compatibility, performance, and security
   risks.
