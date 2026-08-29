# IR monitor admission: status

Status recorded on 2026-08-29 from `cursor/ir-if-acmp-6d81` at `34de8d6`.

## Implemented increment

- The opt-in typed CFG IR path (`--codegen=ir`, `--ir-lower=direct`) admits
  JVM `MONITORENTER` and `MONITOREXIT`.
- Dedicated `IrNodes.MonitorEnter` and `IrNodes.MonitorExit` instructions
  carry a typed `REFERENCE` monitor operand, bytecode offset, and source line.
  `AsmToIr` applies the JVM stack effect in both passes (pop one reference).
- `CfgBuilder` treats both operations as potentially throwing, so a null
  `MONITORENTER` inside a protected region follows the existing ordered IR
  exception edges.
- `IrCppEmitter` uses JNI `MonitorEnter` / `MonitorExit`. The enter path emits
  the explicit null check required for JVM `NullPointerException` behavior,
  and JNI failures use the same exceptional-exit or in-method catch dispatch
  as other throwing IR instructions.
- `MonitorStructureValidator` proves a LIFO monitor state across normal and
  exceptional CFG edges before lowering can mutate method or cache state.
  Monitor operands must be the same SSA reference or collapse to one concrete
  origin through phis. Different depths, mismatched exit references, returns
  while a monitor is held, and potentially escaping exceptions are rejected
  before mutation.
- Synchronized instance methods explicitly enter `this`; synchronized static
  methods explicitly enter the JNI `jclass`. Normal returns exit the method
  monitor. Exceptional returns preserve the pending throwable, clear it while
  calling JNI `MonitorExit`, and rethrow it after the release. In-method
  handlers keep the method monitor while control is dispatched to the handler.
- After successful frontend and lowering validation, the compiler clears
  `ACC_SYNCHRONIZED` before converting the method to native, because the IR
  body now owns the monitor operations.
- `rejectsUnsupportedWideOperationBeforeMutation` now uses
  `INVOKEDYNAMIC`, which remains unsupported.

## Out of scope

- `INVOKEDYNAMIC`, `ConstantDynamic`, method-handle LDC, `jsr` / `ret`, and
  additional constructor-split cases remain unsupported.
- Evaluator lowering does not serialize monitor instructions or synchronized
  methods. This increment targets direct IR lowering.
- No interpreter, evaluator ISA, packaging, CLI-default, README,
  project-status, or current-goal changes.
- `--codegen` remains `legacy`; this increment does not complete the active
  IR-admission goal or delete the snippet path.

## Verification

The acceptance command and exact JUnit XML counts are recorded in
`PR_BODY.md`. Focused execution covers synchronized static and instance
methods, an explicit enter/exit block, null enter to caught
`NullPointerException`, and reject-before-mutation for unstructured pairing.
The existing compiled-and-executed `LCMP` and `IF_ACMP` harnesses remain in
the selected test class.

## Ship readiness

Ship-ready: No. The default code generator remains the legacy path, and the
active IR-complete-method-bodies goal remains open.
