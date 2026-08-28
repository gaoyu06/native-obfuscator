# Blinded recovery: direct C++ backend

## Isolation and evidence

This recovery was written after committing `recovery-opcodes.md` and before
opening the fixture's Java source or any prior evaluation/recovery document.
The behavioral evidence used here was limited to
`docs/eval/compact-blinded/direct/`, principally
`output/DemoKernel_0.cpp`.

The required sequential design means the opcode recovery was already known
when this tree was read. Every claim below is nevertheless independently
visible in the direct generated method body.

## Recovered interface and behavior

```java
static int add(int a, int b) {
    return a + b;
}

static int sumTo(int n) {
    int sum = 0;
    int i = 0;
    while (i < n) {
        sum += i;
        i++;
    }
    return sum;
}

static int mix(int value, int rounds) {
    int x = value ^ 0x9e3779b9;
    int i = 0;
    while (i < rounds) {
        x = x + (x << 6) + (x >>> 2);
        x = x ^ (x * 0x85ebca77);
        x = Integer.rotateLeft(x, 13);
        i++;
    }
    return x;
}

static int divide(int dividend, int divisor) {
    return dividend / divisor;
}
```

All integer arithmetic uses Java `int` wraparound. The division body explicitly
preserves Java's zero-divisor exception and `Integer.MIN_VALUE / -1` result.

## Direct evidence

- Generated comments expose the original method names, descriptors, and every
  source bytecode operation.
- `add` is four commented operations ending in a direct C++ return.
- `sumTo` has named labels, a directly rendered `i >= n` branch, accumulator
  update, increment, back edge, and return.
- `mix` shows both constants in decimal, the loop condition and updates,
  unsigned right shift, and the named `Integer.rotateLeft` call.
- `divide` shows the two loads, integer division edge handling, and return.

## Readability result before ground truth

Recovery is high-confidence and complete for all four methods. The interesting
methods are rendered as per-method `jvalue cstack`/`clocal` bodies. They are
verbose, but their names, comments, labels, constants, and individual
operations make the source behavior directly readable without a separate
runtime decoder.
