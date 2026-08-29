# JVM opcode and ASM instruction coverage

Legend:

- **S** — direct `cppsnippets.properties` expansion.
- **H+S** — handler supplies operands/cache setup, then expands a snippet.
- **H** — handler emits C++ directly or composes helper snippets.
- **P** — bytecode preprocessor must lower it before code generation.
- **N** — ASM normalizes this class-file encoding to another listed opcode/node.
- **U** — unsupported: processing throws, or the class reader cannot supply a handled node.

The evidence chain is the handler registration table (`MethodProcessor.java:55-79`), generic snippet lookup (`GenericInstructionHandler.java:19-61`), concrete stack switches, and all snippet definitions (`obfuscator/src/main/resources/sources/cppsnippets.properties:13-574`).

## Every JVM opcode

| Value(s) | Opcode(s) | Status and implementation |
|---|---|---|
| 0 | `nop` | **S**, `NOP`. |
| 1 | `aconst_null` | **S**. |
| 2-8 | `iconst_m1`, `iconst_0`, `iconst_1`, `iconst_2`, `iconst_3`, `iconst_4`, `iconst_5` | **S**. |
| 9-10 | `lconst_0`, `lconst_1` | **S**. |
| 11-13 | `fconst_0`, `fconst_1`, `fconst_2` | **S**. |
| 14-15 | `dconst_0`, `dconst_1` | **S**. |
| 16-17 | `bipush`, `sipush` | **H+S** via `IntHandler` (`IntHandler.java:10-32`). |
| 18 | `ldc` | **H+S** for String, int, float, long, double, and class `Type`; **P** for method `Type` and `Handle`; **U** for `ConstantDynamic` and every other constant kind (`LdcHandler.java:40-89`; `LdcPreprocessor.java:10-39`). |
| 19-20 | `ldc_w`, `ldc2_w` | **N** to ASM `LdcInsnNode`/opcode `LDC`; same type-dependent result as 18. |
| 21-25 | `iload`, `lload`, `fload`, `dload`, `aload` | **H+S** via `VarHandler` (`VarHandler.java:10-38`). |
| 26-45 | `iload_0..3`, `lload_0..3`, `fload_0..3`, `dload_0..3`, `aload_0..3` | **N** to 21-25 plus local index by ASM. |
| 46-53 | `iaload`, `laload`, `faload`, `daload`, `aaload`, `baload`, `caload`, `saload` | **S**; JNI array access and explicit null/exception handling (`cppsnippets.properties:42-73`). |
| 54-58 | `istore`, `lstore`, `fstore`, `dstore`, `astore` | **H+S** via `VarHandler`. |
| 59-78 | `istore_0..3`, `lstore_0..3`, `fstore_0..3`, `dstore_0..3`, `astore_0..3` | **N** to 54-58 plus local index by ASM. |
| 79-86 | `iastore`, `lastore`, `fastore`, `dastore`, `aastore`, `bastore`, `castore`, `sastore` | **S**; JNI array access (`cppsnippets.properties:79-110`). |
| 87-95 | `pop`, `pop2`, `dup`, `dup_x1`, `dup_x2`, `dup2`, `dup2_x1`, `dup2_x2`, `swap` | **S**; stack-pointer effects are enumerated in `InsnHandler.java:19-136`. |
| 96-115 | `iadd`, `ladd`, `fadd`, `dadd`, `isub`, `lsub`, `fsub`, `dsub`, `imul`, `lmul`, `fmul`, `dmul`, `idiv`, `ldiv`, `fdiv`, `ddiv`, `irem`, `lrem`, `frem`, `drem` | **S**; integer zero/overflow cases are special snippets (`cppsnippets.properties:120-151`). |
| 116-119 | `ineg`, `lneg`, `fneg`, `dneg` | **S**. |
| 120-125 | `ishl`, `lshl`, `ishr`, `lshr`, `iushr`, `lushr` | **S**. |
| 126-131 | `iand`, `land`, `ior`, `lor`, `ixor`, `lxor` | **S**. |
| 132 | `iinc` | **H+S** via `IincHandler` (`IincHandler.java:8-21`). |
| 133-147 | `i2l`, `i2f`, `i2d`, `l2i`, `l2f`, `l2d`, `f2i`, `f2l`, `f2d`, `d2i`, `d2l`, `d2f`, `i2b`, `i2c`, `i2s` | **S**. |
| 148-152 | `lcmp`, `fcmpl`, `fcmpg`, `dcmpl`, `dcmpg` | **S**, including explicit NaN ordering (`cppsnippets.properties:184-188`). |
| 153-158 | `ifeq`, `ifne`, `iflt`, `ifge`, `ifgt`, `ifle` | **H+S** via `JumpHandler` (`JumpHandler.java:10-44`). |
| 159-166 | `if_icmpeq`, `if_icmpne`, `if_icmplt`, `if_icmpge`, `if_icmpgt`, `if_icmple`, `if_acmpeq`, `if_acmpne` | **H+S**; reference comparisons use `IsSameObject`. |
| 167 | `goto` | **H+S**. |
| 168 | `jsr` | **U**: `JumpHandler` has no case and no snippet; it throws (`JumpHandler.java:21-44`). |
| 169 | `ret` | **U**: `VarHandler` has no case and no snippet; it throws (`VarHandler.java:21-38`). |
| 170 | `tableswitch` | **H**, composed from four switch snippets (`TableSwitchHandler.java:11-52`; `cppsnippets.properties:234-237`). |
| 171 | `lookupswitch` | **H**, composed from four switch snippets (`LookupSwitchHandler.java:11-52`; `cppsnippets.properties:238-241`). |
| 172-177 | `ireturn`, `lreturn`, `freturn`, `dreturn`, `areturn`, `return` | **S**. |
| 178-181 | `getstatic`, `putstatic`, `getfield`, `putfield` | **H+S**; specialized by field sort, with lazy class/field-ID setup (`FieldHandler.java:14-65`; `cppsnippets.properties:248-360`). |
| 182-185 | `invokevirtual`, `invokespecial`, `invokestatic`, `invokeinterface` | **H+S**; specialized by return sort, with argument snippets and lazy class/method-ID setup (`MethodHandler.java:151-224`; `cppsnippets.properties:395-565`). Method-handle invocation and synthetic preprocessor calls are further **H** special cases (`MethodHandler.java:32-149`). |
| 186 | `invokedynamic` | **P**. `IndyPreprocessor` replaces every node (`IndyPreprocessor.java:374-381`); reaching `InvokeDynamicHandler` throws on all methods (`InvokeDynamicHandler.java:6-20`). |
| 187 | `new` | **H+S** via `TypeHandler`; uses `AllocObject`, while the following constructor call is `invokespecial`. |
| 188 | `newarray` | **H+S**, selected by atype 4-11 (`IntHandler.java:11-15`; `cppsnippets.properties:362-393`). |
| 189 | `anewarray` | **H+S** via `TypeHandler`. |
| 190-191 | `arraylength`, `athrow` | **S**. |
| 192-193 | `checkcast`, `instanceof` | **H+S** via `TypeHandler`. |
| 194-195 | `monitorenter`, `monitorexit` | **S**. |
| 196 | `wide` | **N**: ASM exposes the widened load/store/`ret`/`iinc` as the ordinary node. Widened supported operations work; widened `ret` remains **U**. |
| 197 | `multianewarray` | **H+S**; primitive and reference paths use runtime recursive helpers (`MultiANewArrayHandler.java:13-36`; `cppsnippets.properties:567-570`; `native_jvm.cpp:180-237`; `native_jvm.hpp:31-76`). The source retains a `// TODO`, so coverage is implemented but not evidence of semantic completeness (`MultiANewArrayHandler.java:26`). |
| 198-199 | `ifnull`, `ifnonnull` | **H+S** via `JumpHandler`. |
| 200 | `goto_w` | **N** to ASM `GOTO`; supported as 167. |
| 201 | `jsr_w` | **N** to ASM `JSR`, then **U** as 168. |
| 202 | `breakpoint` | **U**; reserved for JVM implementation use and absent from handlers/snippets. |
| 203-253 | reserved | **U**; not defined class-file instructions and absent from handlers/snippets. |
| 254-255 | `impdep1`, `impdep2` | **U**; reserved for JVM implementation use and absent from handlers/snippets. |

## ASM node-type map

| `AbstractInsnNode` type | Handler | Result |
|---|---|---|
| `INSN` | `InsnHandler` | **S** for all accepted zero-operand opcodes. |
| `INT_INSN` | `IntHandler` | **H+S**: `BIPUSH`, `SIPUSH`, `NEWARRAY`. |
| `VAR_INSN` | `VarHandler` | **H+S**: typed loads/stores; `RET` is **U**. |
| `TYPE_INSN` | `TypeHandler` | **H+S**: `NEW`, `ANEWARRAY`, `CHECKCAST`, `INSTANCEOF`. |
| `FIELD_INSN` | `FieldHandler` | **H+S**, field-sort variants. |
| `METHOD_INSN` | `MethodHandler` | **H+S**, plus synthetic/method-handle **H** paths. |
| `INVOKE_DYNAMIC_INSN` | `InvokeDynamicHandler` | **U if reached**; required **P** path should remove it. |
| `JUMP_INSN` | `JumpHandler` | **H+S** except `JSR` **U**. |
| `LABEL` | `LabelHandler` | **H**: active-catch tracking and C++ label; deliberately bypasses snippet lookup (`LabelHandler.java:9-35`). |
| `LDC_INSN` | `LdcHandler` | Type-dependent **H+S/P/U**, as opcode 18. |
| `IINC_INSN` | `IincHandler` | **H+S**. |
| `TABLESWITCH_INSN` | `TableSwitchHandler` | **H**. |
| `LOOKUPSWITCH_INSN` | `LookupSwitchHandler` | **H**. |
| `MULTIANEWARRAY_INSN` | `MultiANewArrayHandler` | **H+S**. |
| `FRAME` | `FrameHandler` | **H** metadata: simulated types/reference cleanup, no JVM opcode (`FrameHandler.java:17-116`). |
| `LINE` | `LineNumberHandler` | **H** metadata: updates generated exception line, no JVM opcode (`LineNumberHandler.java:7-19`). |

All 16 ASM node-type slots are registered. That does not imply all legal constant kinds or legacy subroutine opcodes are supported.
