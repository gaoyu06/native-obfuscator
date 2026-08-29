# Blinded recovery: `ir-live-mix`

## Scope and method

This reconstruction was made from `published.so` alone. I used its dynamic
symbol table and x86-64 disassembly. The library contains four dynamically
registered integer methods in `example.Math`. In the pseudocode below, all
arithmetic is Java `int` arithmetic (32-bit two's-complement wraparound), and
`>>>` is an unsigned right shift.

Each wrapper first performs compiler/runtime JNI class-loader and pending-
exception checks. Those checks are common wrapper machinery rather than part of
the recovered Java arithmetic. On the normal path, the kernels are as follows.

## `add(int a, int b)`

Recovered algorithm:

```java
return a + b;
```

Confidence: **full**

Evidence:

- Native symbol: `__ngen_native_add1(JNIEnv_*, _jclass*, int, int)` at
  `0x3600`.
- The integer arguments are saved from `edx` and `ecx` at `0x3606` and
  `0x360b`.
- The sole arithmetic operation on the normal path is
  `lea eax,[r13+r12]` at `0x362f`, immediately followed by the return
  epilogue.

## `sumTo(int n)`

Recovered algorithm:

```java
int sum = 0;
for (int i = 0; i < n; i++) {
    sum += i;
}
return sum;
```

Equivalently for positive `n`, it computes `0 + 1 + ... + (n - 1)`, with
32-bit wrapping; for `n <= 0` it returns `0`.

Confidence: **full**

Evidence:

- Native symbol: `__ngen_native_sumTo2(JNIEnv_*, _jclass*, int)` at
  `0x36f0`; `n` is copied from `edx` to `ebx` at `0x36fb`.
- `test ebx,ebx` / `jle 0x380b` at `0x3723`-`0x3725` returns zero for
  `n <= 0`.
- The vector loop at `0x3739`-`0x3769` starts with consecutive lane values,
  repeatedly advances them by a fixed four-lane step (`paddd xmm1,xmm3`),
  and accumulates each group (`paddd xmm0,xmm2`).
- `psrldq` plus `paddd` at `0x376b`-`0x378a` horizontally reduces the four
  accumulated lanes.
- The scalar remainder starts from `n & -4` at `0x376f`-`0x3776`. At
  `0x3793`-`0x37e5`, it conditionally adds that index and each following
  index only while the index is less than `n`. The small-`n` entry at
  `0x381c` initializes both sum and index to zero and joins the same tail.
  This establishes the strict upper bound `i < n`, rather than `i <= n`.

## `subMul(int a, int b)`

Recovered algorithm:

```java
return (a - b) * b;
```

Confidence: **full**

Evidence:

- Native symbol: `__ngen_native_subMul3(JNIEnv_*, _jclass*, int, int)` at
  `0x3670`; `a` and `b` are saved from `edx` and `ecx` at `0x367c` and
  `0x3678`.
- `mov eax,ebp` then `sub eax,r12d` at `0x36a3`-`0x36a9` computes
  `a - b`.
- `imul eax,r12d` at `0x36ae` multiplies that difference by `b` before the
  immediate return.

## `mix(int a, int b)`

Recovered algorithm:

```java
int x = a * 0x045d9f3b;
x ^= x >>> 16;

int y = b * 0x119de1f3;
y ^= y << 7;

int z = x + y;
z ^= a << 5;
z = z * 33;
z += b >>> 3;
return z ^ (a & b);
```

Confidence: **full**

Evidence:

- Native symbol: `__ngen_native_mix4(JNIEnv_*, _jclass*, int, int)` at
  `0x3550`; `a` and `b` are saved from `edx` and `ecx` at `0x355c` and
  `0x3558`.
- `imul eax,ebp,0x45d9f3b`, `shr edx,0x10`, and `xor edx,eax` at
  `0x3583`-`0x359a` form `(a * 0x045d9f3b) ^ ((a * 0x045d9f3b) >>> 16)`.
- `imul ecx,r12d,0x119de1f3`, `shl eax,0x7`, and `xor eax,ecx` at
  `0x358d`-`0x35a1` form `(b * 0x119de1f3) ^ ((b * 0x119de1f3) << 7)`.
- `add edx,eax` then `xor edx,(a << 5)` at `0x35a3`-`0x35ad` combines those
  values and applies the shifted-`a` xor.
- `shl eax,0x5` plus `add eax,edx` at `0x35af`-`0x35b4` multiplies the
  current value by 33.
- `shr edx,0x3` at `0x35b6`-`0x35b9` is an unsigned `b >>> 3`; it is added
  at `0x35bc`.
- `and ebp,r12d` at `0x35a7` preserves `a & b`, which is applied by the
  final `xor eax,ebp` at `0x35be`.

