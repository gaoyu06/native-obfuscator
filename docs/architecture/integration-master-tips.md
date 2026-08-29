# Integration of preferred draft tips onto master

This document describes the wrap-up merge that folded preferred draft tips onto
`master`. It landed as [#118](https://github.com/gaoyu06/native-obfuscator/pull/118)
by fast-forwarding `master` to `5f017e3`. The Fable phase-18 accept note then
landed as [#119](https://github.com/gaoyu06/native-obfuscator/pull/119) (`e997d71`).
It is not a production-support claim. The CLI default remains `legacy`.
Requirement 7 remains unmet. The #53 eval-lower median stays `N/A`.

Current public status after that landing:
[project-status.md](project-status.md).

## Landed on this integration

- Design docs: #1–#5, #7
- Benchmark harness: #10
- JNI lookup cache: #11
- JDK 17/21/25 E2E harness and metadata: #6 → #9 → #14 → #41
- IR compiler through phase 18: #8…#108 → #114, Sol accept #116
- JDK 17 IR runtime repair: #115 (preferred over unfixed #113), Fable accept #117
- C++ SDK through AES-256-GCM Sol fix: #12 → #46 → #72 → #80 → #81, plus #15/#75 reviews
- Options brief through #111
- Admission and IR review notes that merged cleanly, including phase-18 admission

Phase 18 and the JDK 17 runtime fix were siblings on #108. Both code lines are
present here: NEWARRAY/*ALOAD/*ASTORE/MULTIANEWARRAY plus version preservation,
caller-local `invokeExact` trampolines, and rejected-constructor restore.

The constructor-restore regression from #115 used `NEWARRAY`/`FALOAD` as the
IR-reject trigger. After phase 18 those opcodes are admitted, so the fixture
now uses `MONITORENTER` to keep testing restore rather than accidental IR
admission.

## Not code-merged (sibling compiler stacks)

These rewrite the same compiler files as the phase-18 line and were left as
historical draft stacks. Their **docs and reader/bench evidence** were copied in.

- Shared evaluator (`--ir-lower=eval`): #42 → #87
- Opcode interpreter / link-only backend: #17 → #28
- Older IR-vs-legacy bench harness deltas that depended on those siblings
- Standalone NativeStrings #27 (superseded by #46/#81)

Do not treat this merge as “JDK 17 supported” or as flipping IR to default.
