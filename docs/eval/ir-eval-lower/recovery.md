# Independent recovery from `published.so`

## Blinding statement

This reconstruction was completed before consulting any source or oracle
material.  The only subject file viewed was
`docs/eval/ir-eval-lower/published.so`, using `nm`, `readelf`, `objdump`, and
`strings`.  In particular, no `src/**`, `run.md`, `liveness.md`,
`published.jar` class, other `docs/eval/**` recovery/score, Java test or mix
source, or generated C++ tree was viewed.

## Evaluator model recovered from the ELF

The dynamic symbol table exposes four generated integer entry points and one
shared function:

```text
native_jvm::classes::__ngen_fixture_IrKernel_0::__ngen_native_add(...)
native_jvm::classes::__ngen_fixture_IrKernel_0::__ngen_native_sumTo(...)
native_jvm::classes::__ngen_fixture_IrKernel_0::__ngen_native_subMul(...)
native_jvm::classes::__ngen_fixture_IrKernel_0::__ngen_native_mix(...)
native_jvm::ir_eval::evaluate_i32(unsigned char const *, unsigned long,
                                  int const *, unsigned long)
```

The entry points at virtual addresses `0x3590`, `0x35f0`, `0x3650`, and
`0x36b0` copy their Java integer arguments into a contiguous stack array and
call `evaluate_i32` through its PLT entry.  Their respective blob
address/length/argument-count triples are:

| Method | Blob | Length | Arguments |
|---|---:|---:|---:|
| `add` | `0x7590` | `0x12` | 2 |
| `sumTo` | `0x74c0` | `0xc8` | 1 |
| `subMul` | `0x74a0` | `0x19` | 3 |
| `mix` | `0x72a0` | `0x1f1` | 2 |

Each blob begins with `NJE\x01`, followed by little-endian 16-bit slot and
argument counts.  Disassembly of `evaluate_i32` at `0x5f20` shows that it
validates that header, allocates and zeroes the declared `int32` slots, copies
the arguments into the first slots, and dispatches blob opcodes.  The opcodes
needed by these methods decode as:

```text
01 dst:u16 imm:i32                  slot[dst] = imm
02 dst:u16 src:u16                  slot[dst] = slot[src]
10 dst:u16 lhs:u16 rhs:u16          slot[dst] = slot[lhs] + slot[rhs]
11 dst:u16 lhs:u16 rhs:u16          slot[dst] = slot[lhs] - slot[rhs]
12 dst:u16 lhs:u16 rhs:u16          slot[dst] = slot[lhs] * slot[rhs]
20 target:u32                       jump to blob-relative target
21 pred:u8 lhs:u16 rhs:u16 t:u32 f:u32
                                      signed comparison, then jump to t or f
22 src:u16                          return slot[src]
```

For opcode `21`, predicates used here are `3` = `>=` and `5` = `<=`.
Operand `0xffff` denotes literal zero in the comparison.  All arithmetic is
32-bit two's-complement arithmetic, matching Java `int` wraparound, and
comparisons are signed.

## Per-method reconstruction

### `int add(int x, int y)`

Recovered formula:

```java
return x + y;
```

Confidence: **full**.

Evidence: the `0x7590` blob declares three slots and two arguments.  Its only
two instructions are `10 02 00 00 00 01 00` (add argument slots 0 and 1 into
slot 2) and `22 02 00` (return slot 2).  The trampoline supplies exactly two
arguments and blob length `0x12`.

### `int subMul(int x, int y, int z)`

Recovered formula:

```java
return (x - y) * z;
```

Confidence: **full**.

Evidence: the `0x74a0` blob declares five slots and three arguments.  It
subtracts slot 1 from slot 0 into slot 3, multiplies slot 3 by slot 2 into
slot 4, and returns slot 4.  The trampoline supplies three arguments and blob
length `0x19`.

### `int sumTo(int n)`

Recovered control flow:

```java
int sum = 0;
int i = 0;
while (i < n) {
    sum = sum + i;
    i = i + 1;
}
return sum;
```

Equivalently, it returns `0` when `n <= 0`; for positive `n` it computes
`0 + 1 + ... + (n - 1)` with Java `int` wraparound (mathematically
`n * (n - 1) / 2`, reduced to an `int`).

Confidence: **full**.

Evidence: the `0x74c0` blob declares 18 slots and one argument.  It initializes
the loop-carried sum and index to zero, then branches at blob offset `0x39`
on `index >= n`.  The false edge at `0xa5` copies the loop-carried values to
body inputs; the body at `0x47` computes `sum + index` and `index + 1`, then
jumps back to `0x39`.  The true edge at `0x82` copies the carried values to
exit slots and reaches `22 08 00`, returning the sum.  The trampoline supplies
one argument and blob length `0xc8`.

### `int mix(int x, int y)`

Recovered control flow, retaining intermediate operations so overflow and
signed branch behavior are explicit:

```java
int p = x * 31 + y * 17 - 7;
int q;
if (x <= y) {
    q = p * 5 - x * 7 + y;
} else {
    q = p * 3 + x - y * 5;
}

if (q >= 0) {
    return q * 7 - x * 5 + y * 19;
} else {
    return q * 11 + x * 13 - y * 3;
}
```

Every displayed operation is an `int32` evaluator operation.  Ignoring the
placement of intermediate overflow (which is algebraically equivalent modulo
2^32), the four result regions simplify to:

```text
x <= y, q >= 0:  1031*x + 621*y - 245
x <= y, q <  0:  1641*x + 943*y - 385
x >  y, q >= 0:   653*x + 341*y - 147
x >  y, q <  0:  1047*x + 503*y - 231
```

The second branch must still test the wrapped signed value of `q`, where
`q = 148*x + 86*y - 35` on the first path and
`q = 94*x + 46*y - 21` on the second path.

Confidence: **full**.

Evidence: the `0x72a0` blob declares 55 slots and two arguments.  Offsets
`0x08` through `0x39` form `p` and perform predicate-5 (`<=`) on argument
slots 0 and 1, targeting `0x165` or `0x188`.  Those targets feed the
`p*5-x*7+y` block at `0x94` or the `p*3+x-y*5` block at `0x47`.
Both converge at `0xe1`, whose predicate-3 branch compares `q` with the
`0xffff` zero operand and targets `0x1ab` or `0x1ce`.  The former feeds the
return block at `0x12a` (`q*7-x*5+y*19`); the latter feeds the return block at
`0xef` (`q*11+x*13-y*3`).  The trampoline supplies two arguments and blob
length `0x1f1`.

## Subject shape

The recovered shape is four small per-method trampolines plus four live,
method-specific `NJE` blobs interpreted by the shared `evaluate_i32`
implementation.  The formulas above come from those blobs and evaluator
semantics, not from native constant-folded method bodies.
