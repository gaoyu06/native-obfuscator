# Opcode-backend recovery

Reader input was limited to the generated opcode-backend C++ and its interpreter
tables/runtime. The Java source and direct-backend C++ were not consulted.

## Recovered methods

### `add(int a, int b)`

```java
return a + b;
```

The stream loads locals 0 and 1, applies integer addition, and returns.

### `sumTo(int n)`

```java
int sum = 0;
int i = 0;
while (i < n) {
    sum += i;
    i++;
}
return sum;
```

The branch exits when `i >= n`, so non-positive inputs return zero.

### `mix(int value, int rounds)`

```java
int x = value ^ 0x9e3779b9;
for (int i = 0; i < rounds; i++) {
    x = x + (x << 6) + (x >>> 2);
    x = x ^ (x * 0x85ebca77);
    x = Integer.rotateLeft(x, 13);
}
return x;
```

All operations use 32-bit Java integer wraparound. The two four-byte immediates
decode little-endian to `0x9e3779b9` and `0x85ebca77`. The loop target and
`if_icmpge` establish the `i < rounds` condition.

### `divide(int a, int b)`

```java
return a / b;
```

This method appears as a generated per-method C++ fallback rather than an opcode
stream. Its checks preserve Java integer division behavior for division by zero
and `Integer.MIN_VALUE / -1`.

## Readability observation before reveal

`add` is immediate to recover. `sumTo` requires resolving byte offsets and
branch targets but is still unambiguous. `mix` requires decoding the opcode
numbers, little-endian constants, stack effects, and control-flow targets; after
that mechanical pass, the expression sequence is unambiguous. `divide` is
directly readable from its fallback C++ body.
