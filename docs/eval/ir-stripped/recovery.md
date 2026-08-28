# Blind recovery: stripped IR/direct-C++ shared library

This recovery was written before opening the Java fixture source or viewing
either oracle stdout file. The reader used only:

```text
nm -D --defined-only published.so
readelf -h -S -Ws -Wr published.so
strings -a -t x -n 3 published.so
objdump -d -Mintel published.so
```

The artifact is an ELF64 x86-64 shared object. It has no `.symtab`, `.strtab`,
or debug sections, but its dynamic symbol table still exports all six generated
native functions with class name, method name, ordinal, and JNI parameter
types. Plain strings also retain the Java method names and descriptors. Method
discovery and boundary recovery are therefore direct rather than heuristic.

## Recovered methods

The following is my pre-reveal reconstruction of observable Java behavior.
JNI class-loader checks and pending-exception paths are omitted.

```java
static int add(int a, int b) {
    return a + b;
}

static int sumTo(int n) {
    int sum = 0;
    for (int i = 0; i < n; i++) {
        sum += i;
    }
    return sum;
}

static int subMul(int a, int b) {
    return (a - b) * b;
}

static int mix(int a, int b) {
    return 0;
}

static int narrow(int value) {
    return (char) (byte) (-value);
}

static int bump(int[] values, int index) {
    values[index]++;
    return values.length;
}
```

Evidence by method:

- `add(II)I`, `0x36a0`, 111 bytes: the only payload operation is
  `lea eax,[r13+r12]`.
- `sumTo(I)I`, `0x3790`, 309 bytes: returns zero for `n <= 0`; otherwise
  accumulates `0..n-1`. GCC vectorized the main loop with `paddd` and emitted a
  scalar remainder.
- `subMul(II)I`, `0x3710`, 119 bytes: `sub eax,r12d` followed by
  `imul eax,r12d`.
- `mix(II)I`, `0x35d0`, 91 bytes: neither integer argument is preserved or
  consumed; every successful path returns zero. No source-level bitwise/shift
  sequence can be reconstructed from this optimized function beyond the
  constant observable result.
- `narrow(I)I`, `0x3630`, 97 bytes: `neg`, sign-extension of the low byte to
  16 bits, then zero-extension of the low 16 bits to 32 bits.
- `bump([II)I`, `0x3940`, 528 bytes: obtains array length, reads one element
  with `GetIntArrayRegion`, increments it, writes it with
  `SetIntArrayRegion`, and returns the saved length.

All arithmetic is 32-bit and naturally has Java `int` wraparound behavior.
The array method relies on JNI for null/bounds exceptions.

## Reader confidence and contamination

Pre-reveal confidence: `add`, `sumTo`, `subMul`, `narrow`, and `bump` high;
`mix` high for observable constant-zero behavior but no confidence about the
source expression that optimized to it.

N=1 reader. Before the final `.so` reading pass, build setup exposed the six
test factory names through `javap`, and a repository documentation search
exposed that the mix-like test exercises integer bitwise and shift operations.
No Java fixture source or oracle output was read. This contamination makes
method-family discovery non-blind, although the formulas above were recovered
from the final shared-object disassembly. In particular, the prior hint did not
make the erased `mix` source sequence recoverable.
