# Shared-library-only recovery (before source inspection)

## Reader boundary

- Reader input: `docs/eval/shared-lib/published.so`
- Java fixture source had not been opened when this file was written.
- No generated C++ source was opened or used.
- Binary inspection was limited to `nm`, `readelf`, `strings`, and
  `objdump -d`.

## Binary evidence

The dynamic symbols expose three integer JNI entry wrappers for `DemoKernel`.
The registration data maps them, in order, to `add(II)I`, `sumTo(I)I`, and
`mix(II)I`. Each wrapper calls the common `execute_i` function with one
descriptor:

| Method | Descriptor | Stream | Length |
| --- | --- | --- | ---: |
| `add` | `0x9ce0` | `0x7380` | 8 |
| `sumTo` | `0x9cc0` | `0x7340` | 58 |
| `mix` | `0x9ca0` | `0x72c0` | 106 |

Disassembling `execute_i` identifies the stream operations needed by these
methods: integer constant/load/store, add, multiply, xor, left shift, unsigned
right shift, rotate-left, conditional branch, unconditional branch, and
integer return. Decoding the three streams gives the following pseudocode,
with Java `int` overflow semantics.

## Recovered algorithms

```java
static int add(int a, int b) {
    return a + b;
}
```

```java
static int sumTo(int n) {
    int sum = 0;
    int i = 0;
    while (i < n) {
        sum += i;
        i++;
    }
    return sum;
}
```

```java
static int mix(int value, int rounds) {
    int x = value ^ 0x9e3779b9;
    for (int i = 0; i < rounds; i++) {
        x = x + (x << 6) + (x >>> 2);
        x = x ^ (x * 0x85ebca77);
        x = Integer.rotateLeft(x, 13);
    }
    return x;
}
```

The `mix` reconstruction includes both constants, operation order, loop
condition, and rotate distance. Evaluating this recovered pseudocode against
the already-recorded runtime cases reproduced all five `mix` results exactly;
the runtime output was used as a consistency check, not as the source of the
logic.

## Recovery result

- `add`: full recovery.
- `sumTo`: full recovery, including initialization and loop bounds.
- `mix`: full recovery of the interesting kernel.
- Class initialization: identified as runtime registration setup; no separate
  fixture algorithm was recovered from that wrapper.
