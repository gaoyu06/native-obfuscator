# Liveness gate

All checks below apply to the stripped
`docs/eval/ir-live-mix/published.so`.

- [x] **Direct IR only for the fixture methods.** Generated-source markers were
  present for `add(II)I`, `sumTo(I)I`, `subMul(II)I`, `mix(II)I`, and
  `<clinit>()V`. The transpiler log had no per-method legacy fallback, and the
  generated class body had no `cstack` or `clocal` legacy slots.
- [x] **Supported, nontrivial integer bytecode.** `javap -c -p example.Math`
  showed three `imul` instructions in `mix`, plus `iushr`, `ishl`, `ixor`,
  `iadd`, and `iand`. It showed no exception table.
- [x] **Both arguments affect observable results.** With `b=0`, changing `a`
  from `0` (`mix#1`) to `1` (`mix#4`) changed the result from `0` to
  `-1877933050`. With `a=0`, changing `b` from `0` (`mix#1`) to `1`
  (`mix#2`) changed the result from `0` to `-854579501`. The disassembly also
  carries both input registers through the normal return path.
- [x] **Oracle and native executions completed.** The original fixture jar ran
  as the Java oracle. The transpiled jar ran with
  `-Djava.library.path=/tmp/ir-live-mix-build/runtime`; LoaderPlain loaded
  `libirkernel.so`. Native stderr was empty.
- [x] **Stdout is identical.** Binary `cmp` of `oracle.stdout` and
  `native.stdout` exited `0`.
- [x] **Mix output is diverse.** Exactly six `mix#` lines were printed. All six
  decimal results are distinct, five are nonzero, and therefore the results are
  neither all zero nor all equal (the required minimum was four distinct
  results).
- [x] **The stripped JNI implementation symbol exists.** `nm -D -C` reported
  the global dynamic symbol at `0x3550`:
  `native_jvm::classes::__ngen_example_Math_0::__ngen_native_mix4(...)`.
  `readelf -Ws` reported it as a 167-byte global function.
- [x] **Release optimization retained real arithmetic.** The normal return path
  contains two immediate `imul` instructions, two `shr`, three `shl`, one
  `and`, multiple `xor`, and multiple `add` instructions before `ret`.
  It is not a constant-return body. The zero-return path elsewhere in the
  function is only the shared JNI classloader/exception failure path.
- [x] **Artifact is explicitly stripped.** The GCC Release output was passed to
  `strip --strip-all`; `file published.so` identifies it as a stripped ELF
  x86-64 shared object.
- [x] **No reader pass was performed.** These files record compiler/build and
  liveness evidence only. No recovery or scoring document was created.

The six stdout lines, complete commands, symbol output, disassembly excerpt,
toolchain versions, and artifact hashes are recorded in `run.md`.
