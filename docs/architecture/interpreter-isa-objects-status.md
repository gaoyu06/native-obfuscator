# In-process interpreter ISA v4 reference slice: status

Status recorded on 2026-08-29 from `origin/master` at `37f7d03`.

## Implemented increment

- The explicit, default-off `--backend=interpreter` opcode stream is now ISA
  v4. Java-emitted method descriptors and the C++ dispatcher require an exact
  version match; an ISA v3 descriptor is rejected.
- Opcodes 1 through 45 retain their existing values. The reference increment
  appends:

  | Value | Opcode | Operand | Effect |
  |---:|---|---|---|
  | 46 | `ACONST_NULL` | none | Push `nullptr` |
  | 47 | `ALOAD` | `u16 local` | Load one reference slot |
  | 48 | `ASTORE` | `u16 local` | Store one reference slot |
  | 49 | `ARETURN` | none | Return a reference |
  | 50 | `IFNULL` | `i32 target` | Branch when the popped reference is null |
  | 51 | `IFNONNULL` | `i32 target` | Branch when the popped reference is non-null |

- Reference values are opaque `jobject` values held in parallel reference
  local and operand-stack arrays. They use one JVM slot. Long values continue
  to use two consecutive 32-bit slots.
- Eligible descriptors now include `int`, `long`, object, and array arguments
  and returns. Eligibility otherwise remains static, non-synchronized,
  non-constructor, non-class-initializer, and without try/catch regions.
- The JNI trampoline writes reference arguments to reference locals at JVM
  descriptor slot offsets. Reference-returning methods use the typed
  `execute_l` dispatcher entry.
- The dispatcher performs no JNI operation for this increment. Object
  creation, method calls, field access, special calls, and exception dispatch
  remain unsupported and cause per-method fallback before interpreter
  mutation.
- `--backend` remains `cpp` by default and `--codegen` remains `legacy`.

## Verification

The required command completed with `BUILD SUCCESSFUL`: 128 tests, 0 skipped,
0 failures, and 0 errors.

```text
CC=gcc CXX=g++ ./gradlew :obfuscator:test --rerun-tasks \
  --tests by.radioegor146.MainBackendOptionTest \
  --tests by.radioegor146.interpreter.InterpreterMethodEmitterTest \
  --tests by.radioegor146.interpreter.InterpreterRuntimeTest \
  --tests by.radioegor146.interpreter.InterpreterBackendIntegrationTest \
  --tests by.radioegor146.ir.IrCompilerTest \
  --tests by.radioegor146.CodegenModeTest
```

The per-class counts were:

```text
MainBackendOptionTest:                    2
InterpreterMethodEmitterTest:           14
InterpreterRuntimeTest:                  1
InterpreterBackendIntegrationTest:       2
IrCompilerTest:                         102
CodegenModeTest:                          7
Total:                                  128
```

All suites reported 0 skipped, 0 failures, and 0 errors. The runtime test
compiled the ISA v4 dispatcher with
`g++ -std=c++17 -Wall -Wextra -Werror` and executed 54 numbered checks,
including object identity/null, both null branches, reference local
load/store, reference return, all existing i32/i64 cases, and an ISA v3
mismatch rejection.

The integration fixture generated 15 interpreted methods: six i32, six i64,
and three reference/array methods. The object identity method has descriptor
`(Ljava/lang/Object;)Ljava/lang/Object;`; a mixed
`(JLjava/lang/Object;)Ljava/lang/Object;` method places its reference argument
at local slot 2. An object-array identity method verifies the JNI array
carrier. A method containing an unsupported object operation used each
selected fallback path. The complete generated CMake project configured with
GCC/G++ 13.3.0 and built successfully through
`[100%] Built target native_library`.

## Default-off generated-tree proof

The same `DefaultOffFixture` JAR was processed by detached `origin/master` at
`37f7d03`, this branch with no backend option, and this branch with
`--backend=cpp`.

```text
diff -r /tmp/interpreter-objects-proof/master/cpp \
  /tmp/interpreter-objects-proof/branch-default/cpp
# exit 0, no output

diff -r /tmp/interpreter-objects-proof/branch-default/cpp \
  /tmp/interpreter-objects-proof/branch-cpp/cpp
# exit 0, no output
```

Both complete-tree comparisons exited 0. The no-option output therefore
matches both detached master and explicit C++ output.

## (a)(b)(c)(d) / （a）（b）（c）（d）

- **(a) Scope / 范围:** Add the first reference/object increment to the
  explicit in-process interpreter: null constants, reference local
  load/store, null branches, reference return, JNI argument transfer, and a
  typed reference-return entry. /
  为显式启用的进程内解释器加入首个引用/对象增量：空引用常量、引用局部变量
  读写、空引用分支、引用返回、JNI 参数传递，以及类型明确的引用返回入口。
- **(b) Ship-ready and review / 可直接发布及审查:** **Ship-ready: No /
  可直接发布：否。 Review required: Yes / 需要审查：是。** Sol-only review
  is acceptable. This is a narrow default-off increment, not a complete
  interpreter backend. /
  可由 Sol 单独审查。这是范围有限且默认关闭的增量，并非完整的解释器后端。
- **(c) Compatibility / 兼容性:** Opcodes 1–45 are stable, ISA versions must
  match exactly, unsupported methods retain per-method fallback, and the
  default backend remains C++. /
  操作码 1–45 保持稳定，ISA 版本必须完全匹配，不支持的方法继续逐方法回退，
  默认后端仍为 C++。
- **(d) Evidence / 证据:** All 128 required tests passed with no skips or
  failures; the strict C++17 runtime executable ran all 54 checks; the
  generated shared library built; and both complete-tree comparisons exited
  0 with no output. /
  128 项必需测试全部通过，无跳过或失败；严格编译的 C++17 运行时可执行文件完成
  54 项检查；生成的共享库构建成功；两次完整目录比较均以 0 退出且无输出。
