# Direct-backend recovery

Reader input for this stage was the generated direct-backend C++. The Java
source remained unopened.

## Recovered methods

### `add(int a, int b)`

```java
return a + b;
```

### `sumTo(int n)`

```java
int sum = 0;
for (int i = 0; i < n; i++) {
    sum += i;
}
return sum;
```

The generated labels and `IF_ICMPGE` condition make the loop bounds explicit.

### `mix(int value, int rounds)`

```java
int x = value ^ -1640531527; // 0x9e3779b9
for (int i = 0; i < rounds; i++) {
    x = x + (x << 6) + (x >>> 2);
    x = x ^ (x * -2048144777); // 0x85ebca77
    x = Integer.rotateLeft(x, 13);
}
return x;
```

The generated instruction comments expose the signed constants, shifts,
integer operations, loop condition, and `Integer.rotateLeft` call directly.
Java `int` wraparound is implied throughout.

### `divide(int a, int b)`

```java
return a / b;
```

The generated `IDIV` path includes the Java edge cases for zero and
`Integer.MIN_VALUE / -1`.

## Readability observation before reveal

All four methods are recoverable without reconstructing a separate instruction
encoding. The source-level local names are gone, and the generated JNI setup
adds substantial visual noise, but the bytecode comments, labels, explicit
expressions, and signed constants preserve the method behavior.
