# Interpreter ISA v4 reference slice — Sol independent review

Reviewed [PR #148](https://github.com/gaoyu06/native-obfuscator/pull/148)
at `2003edbe700b3378e8b635b8ef86e31acb4187f3`, against
`origin/master` at `37f7d0327c05578dc1000fc7a545fd05ee482c2a`.

## Verdict

**Accept.**

No blocking compiler/VM correctness defect or concrete review nit was found.
The change is a narrow extension of the explicit interpreter backend, keeps
the existing fallback boundary, and leaves the default backend unchanged.
Ship-ready remains **No**. This review changes documentation only; it does not
modify interpreter or compiler code.

## Correctness findings

1. **Scope.** The implementation diff is limited to the interpreter Java
   emitter/processor, the interpreter C++ dispatcher/header, their tests, the
   implementation status note, and `PR_BODY.md`. It does not change
   `NativeObfuscator`, `ConstructorSpecialMethodProcessor`, the IR frontend,
   evaluator, `Main`, or any CLI/API default.
2. **ISA agreement.** Java and C++ both declare ISA version 4. Values 1–45 are
   unchanged from `origin/master`; both sides append exactly
   `ACONST_NULL=46`, `ALOAD=47`, `ASTORE=48`, `ARETURN=49`, `IFNULL=50`, and
   `IFNONNULL=51`. The dispatcher rejects any descriptor whose version is not
   exactly 4.
3. **Reference and long slots.** The trampoline allocates parallel numeric and
   `jobject` local/operand-stack arrays. Both use the JVM slot index and one
   shared logical stack depth: references advance by one slot, while long
   operations continue to reserve and bounds-check two consecutive numeric
   slots. JNI arguments advance by `Type.getSize()`, so a reference after a
   long is written at local slot 2. `ALOAD` and `ASTORE` copy the opaque
   `jobject` handle between the reference arrays; they do not write the
   numeric arrays. `ACONST_NULL` writes `nullptr`, `IFNULL`/`IFNONNULL` compare
   the popped reference with `nullptr`, and `ARETURN` passes that handle
   through `execute_l`.
4. **Branch encoding.** `IFNULL` and `IFNONNULL` use the same five-byte
   `JumpInsnNode` path as `IFEQ` and `GOTO`: one opcode followed by a
   little-endian four-byte absolute byte offset produced by `writeI32`. The
   C++ dispatcher consumes those bits with `read_u32`, validates a taken target
   against `code_len`, and assigns it directly to `pc`. The emitter golden
   test resolves the null branches to absolute offsets 24 and 26. There is no
   encoding mismatch behind the PR body's `i32 absolute target` description.
5. **Eligibility and mutation boundary.** `tryCompile` still requires a static,
   non-synchronized, non-abstract, non-native method with no try/catch table;
   `(method.access & ACC_STATIC) == 0` rejects instance methods and
   `name.startsWith("<")` rejects both `<init>` and `<clinit>`. Object/array
   descriptors are admitted only after every instruction reports a supported
   size. `NEW`, calls, fields, type operations, and other unsupported nodes
   return `null` during this preflight. `NativeObfuscator` invokes
   `processMethod`—the first mutating step—only after a non-null compiled
   method is returned.
6. **Default and limits.** `Main` still declares `--backend` with
   `defaultValue = "cpp"` and initializes it to `CompilerBackend.CPP`; the
   convenience API default is unchanged. Object creation, calls, fields, and
   exception dispatch remain outside this increment. No broad runtime-version
   support claim follows from this review.

## Independent verification

The requested focused rerun is pending in the initial documentation commit.
Its measured per-class counts and result will be recorded in a follow-up
documentation-only commit.

The implementation branch records a separate 128-test run that includes
`IrCompilerTest`. This review will report only the tests actually rerun here.
The generated-tree comparison was not rerun; its recorded commands compare
the complete generated `cpp/` trees, while the unchanged defaults above were
verified directly in source.

## 中文摘要

**结论：接受。** 未发现阻塞性的编译器/虚拟机正确性缺陷或具体审查小问题。
Java 与 C++ 均使用 ISA v4，1–45 号操作码不变，46–51 号新增操作码完全一致。
引用值保存在并行的 `jobject` 局部变量和操作数栈数组中并占一个 JVM 槽，
long 仍在数值数组中占连续两个槽。空引用分支与现有整数分支使用相同的四字节
小端绝对目标编码。静态方法、无 try/catch、非特殊方法以及逐指令预检等边界
保持不变，不支持的指令会在方法变更前回退。默认后端仍为 `cpp`。

Ship-ready 仍为 **No**。本次审查只修改文档，不修改解释器或编译器代码。
