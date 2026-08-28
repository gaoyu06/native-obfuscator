# Liveness gate

Status: **PASS** against the published bytes.

## Runtime gate

The Java oracle and the published native jar were compared with `cmp`; exit
status was **0**. The native run's six `mix` lines were:

```text
mix(0,0)=-385
mix(1,2)=2028
mix(2,1)=1500
mix(-3,5)=-593
mix(7,-4)=3060
mix(123,456)=409744
```

Count: **6** cases; distinct outputs: **6**; at least one nonzero: **yes**.
This independently rules out an all-zero or input-independent live subject.

## Native method and trampoline

`javap -p -classpath published.jar fixture.IrKernel` reports
`public static native int mix(int, int)`. Despite `strip --strip-all`, the
dynamic symbol table retains:

```text
00000000000036b0 T native_jvm::classes::__ngen_fixture_IrKernel_0::__ngen_native_mix4(JNIEnv_*, _jclass*, int, int)
0000000000005f20 T native_jvm::ir_eval::evaluate_i32(unsigned char const*, unsigned long, int const*, unsigned long)
```

`objdump -d --demangle --start-address=0x36b0 --stop-address=0x3710
published.so` shows that `mix`:

- loads blob length `0x1f1` and blob address `0x72a0`;
- stores the two Java integer arguments into a two-element argument array;
- sets the argument count to `2`;
- calls `evaluate_i32@plt` at `0x36e3` and returns its result.

There is no straight-line implementation of the Java expression in this body.
The generated-source check agrees: `mix(II)I` contains `ir_method_data` and
`evaluate_i32`, with no `cstack` marker. The transpiler log's only legacy
fallback was the synthesized void `<clinit>()V`; there was no fallback for
`mix`.

## Live evaluator operations

The stripped evaluator is real executable code, not a folded result.
`objdump` over `evaluate_i32` shows its integer handlers:

```text
611e: lea  (%rcx,%rdi,1),%eax  # IADD
6129: imul %edi,%eax           # IMUL
6134: sub  %ecx,%eax           # ISUB
```

The same function dispatches opcodes through a jump table and implements signed
branch predicates with `cmp` plus `setg`, `setge`, `setl`, `setne`, and `setle`
near `0x63b8`–`0x63f7`.

## Live `mix` blob

The trampoline identifies the `mix` method-data bytes as
`published.so[0x72a0:0x7491]`. `objdump -s --start-address=0x72a0
--stop-address=0x7491 published.so` begins with:

```text
72a0 4e4a4501 37000200 0111001f 00000012
72b0 12000000 11000113 00110000 00121400
```

Decoding the full 497-byte blob according to the evaluator's documented
instruction widths reaches the end on an exact instruction boundary and gives:

```text
header=4e4a4501 registers=55 arguments=2
0x01 CONST=13
0x02 COPY=36
0x10 IADD=5
0x11 ISUB=5
0x12 IMUL=12
0x20 JUMP=6
0x21 BRANCH=2
0x22 IRETURN=2
```

Thus the published blob itself carries live multiply, add, subtract, branch,
and return operations. Together with diverse runtime outputs and the evaluator
trampoline, this proves `mix` stayed on the shared evaluator and was not
constant-folded.
