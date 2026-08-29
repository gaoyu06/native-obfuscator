# In-process interpreter ISA v4 allocation and constructor-invoke status

Status recorded on 2026-08-29 from `origin/master` at `e4fb63b`.

## Implemented increment

- The explicit, default-off `--backend=interpreter` bytecode stream remains
  ISA v4, and the C++ dispatcher still requires an exact version match.
- Opcodes 1–52 retain their existing numeric values, including `ATHROW = 52`.
  This increment appends:

  | Value | Opcode | Operand | Effect |
  |---:|---|---|---|
  | 53 | `DUP` | none | Duplicate one proven category-1 reference |
  | 54 | `NEW` | `u16 class index` | Allocate an uninitialized object with JNI |
  | 55 | `INVOKESPECIAL` | `u16 constructor index` | Invoke one `<init>` nonvirtually |

- Each admitted method has explicit, bounds-checked class and constructor side
  tables. Constructor entries identify the declaring class, descriptor,
  argument kinds, argument count, and JVM argument-slot width.
- `NEW` uses `FindClass` and `AllocObject`; `INVOKESPECIAL` uses
  `GetMethodID` and `CallNonvirtualVoidMethodA`. Allocation and construction
  remain separate dispatcher operations.
- Constructor arguments may be `int`, `long`, object, or array values. The
  dispatcher reads them from the existing parallel numeric/reference stacks
  in descriptor order and pops the receiver and arguments after the call.
- A pending JNI exception from allocation, class/method lookup, or constructor
  execution is cleared while the existing ordered exception table is walked.
  A covering handler receives the exception reference; an unmatched exception
  returns `pending_exception`.
- Reference `DUP` admission is conservative. The emitter accepts it only when
  the immediately established stack top is provably a reference from `NEW`,
  `ALOAD`, `ACONST_NULL`, or an already-proven reference `DUP`. Integer `DUP`
  and ambiguous control-flow cases use the selected per-method fallback.
- Eligibility remains static-only and requires every instruction, including
  handler instructions, to be representable. Constructors, class
  initializers, instance methods, synchronized methods, interface methods,
  non-constructor invokes, unsupported constructor descriptors, and field
  operations are rejected before method mutation.
- `--backend` remains `cpp` by default, `--codegen` remains `legacy`, and
  `--ir-lower` is unchanged.

Ship-ready: **No**. Review required: **Yes**; a Sol-only interpreter review is
acceptable.

## Deliberately excluded

This increment does not add fields, virtual/static/interface invokes,
`IF_ACMPEQ`, instance methods, interpreted constructors, or interpreted class
initializers.

## Verification

The required focused Gradle command, per-class JUnit counts, 67-check strict
C++ runtime harness, and default-off generated-tree comparison are recorded
after execution in the branch handoff and `PR_BODY.md`.
