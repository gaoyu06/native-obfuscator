# Blinded recovery: opcode backend

## Isolation and evidence

This recovery was written before opening the fixture's Java source or any prior
evaluation/recovery document. The behavioral evidence was limited to
`docs/eval/compact-blinded/opcodes/`, including the generated method file,
`string_pool.cpp`, and `native_jvm_interp.cpp`.

Procedural contamination: while checking that generation had produced both
trees, the direct tree's `CMakeLists.txt` and file listing were opened before
this recovery. They exposed only the class/file names and build source list, not
method bodies, constants, or control flow. A base-branch commit summary had
already exposed the class name `DemoKernel`.

## Recovered interface

The string pool and JNI registration table expose four static integer methods:

- `add(int, int) -> int`
- `sumTo(int) -> int`
- `mix(int, int) -> int`
- `divide(int, int) -> int`

## Recovered behavior

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

All integer arithmetic above uses Java `int` wraparound. The generated
`divide` body also makes the Java edge behavior explicit: division by zero
throws `ArithmeticException`, and `Integer.MIN_VALUE / -1` returns
`Integer.MIN_VALUE`.

## How the recovery was obtained

`native_jvm_interp.cpp` supplies the encoded-byte-to-operation table and the
operand widths. Applying that table to the three byte arrays gives:

- `add`: load local 0, load local 1, add, return.
- `sumTo`: initialize locals 1 and 2 to zero, loop while local 2 is less than
  local 0, accumulate local 2 into local 1, increment local 2, return local 1.
- `mix`: xor the input with `0x9e3779b9`, then run the three-step arithmetic
  update in a counted loop and return the final local.

`divide` did not lower to an opcode stream. It remains a generated per-method
`jvalue cstack` body whose load, division, exception, and return operations are
directly commented.

## Readability result before ground truth

Recovery is high-confidence and complete for all four methods. The three
lowered methods are compact byte arrays rather than named-opcode arrays, so the
runtime decode table is necessary to read them. The blobs plus runtime file are
enough to recover those three methods. They are not enough by themselves to
recover the full class because `divide` is present only as the fallback
per-method body in the same generated method file.
