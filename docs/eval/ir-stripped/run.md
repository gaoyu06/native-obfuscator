# Build and run record

## Fixture and blinding

Base revision:
`009fa35543f306f623c79025228668f739be822d`
(`origin/cursor/ir-compiler-phase3-6d81`).

The fixture was generated without reading its Java test source. A temporary
helper loaded compiled `IrCompilerTest` bytecode and invoked the existing
`addMethod`, `sumToMethod`, `subMulMethod`, `bitwiseShiftMethod`,
`unaryMethod`, and `intArrayMethod` factories. ASM wrote those six methods to
`example/Math.class`; a separately compiled reflection runner supplied six
deterministic cases per method. The original jar's output was captured without
displaying it.

`recovery.md` was written before opening
`obfuscator/src/test/java/by/radioegor146/ir/IrCompilerTest.java` or either
stdout capture. N=1 and the pre-reveal contamination are recorded in
`recovery.md` and `scores.md`.

## Build

The compiler was assembled with:

```text
./gradlew :obfuscator:shadowJar --no-daemon
```

The fixture was transpiled with the direct IR backend:

```text
java -jar obfuscator/build/libs/obfuscator.jar \
  --codegen=ir \
  --plain-lib-name irkernel \
  -w obfuscator/build/blind-eval/whitelist-all-methods.txt \
  obfuscator/build/blind-eval/integer-kernel.jar \
  obfuscator/build/blind-eval/transpiled-v2
```

The method-selection pattern included the class and all its method signatures.
The final transpile log reported `Preprocessing example/Math` and
`Processing example/Math`.

The shared library was built with CMake 3.28.3 and GCC 13.3.0:

```text
cmake -S .../cpp -B .../cpp/build-gcc \
  -DCMAKE_BUILD_TYPE=Release \
  -DCMAKE_C_COMPILER=/usr/bin/gcc \
  -DCMAKE_CXX_COMPILER=/usr/bin/g++
cmake --build .../cpp/build-gcc --config Release --parallel 2
strip --strip-all .../build/lib/libnative_library.so
```

The generated GNU CMake configuration adds `-O2 -s -DNDEBUG`. The final ELF has
no ordinary symbol table or debug sections. Generated methods remain named in
the dynamic symbol table, so stripping does not remove their names,
descriptors, boundaries, or JNI parameter types.

All six fixture methods, including `mix(II)I`, contain the generated
`// IR codegen:` body in the revealed C++ output. **No method fell back to the
legacy huge JNI-snippet emitter.** The build did not use the interpreter
backend and contains no opcode machine.

Only the final shared object and the jar needed to run it are published:

```text
docs/eval/ir-stripped/published.so
docs/eval/ir-stripped/published.jar
```

## Oracle validation

OpenJDK 21.0.10 ran the original jar to produce the oracle. The published jar
was then run against a runtime copy of `published.so` named
`libirkernel.so`, as required by `System.loadLibrary("irkernel")`:

```text
java -Djava.library.path=obfuscator/build/blind-eval/runtime \
  -jar docs/eval/ir-stripped/published.jar
cmp -s oracle.stdout native-v2.stdout
```

Result: **exit 0 and byte-for-byte stdout match**.

```text
add#0=0
add#1=3
add#2=30
add#3=10
add#4=2147483641
add#5=123456806
bump#0=throws:java.lang.ArrayIndexOutOfBoundsException;array=[]
bump#1=throws:java.lang.ArrayIndexOutOfBoundsException;array=[0]
bump#2=throws:java.lang.ArrayIndexOutOfBoundsException;array=[1, 2, 3, 4]
bump#3=4;array=[-1, 0, 7, -8]
bump#4=throws:java.lang.ArrayIndexOutOfBoundsException;array=[-2147483648, 2147483647]
bump#5=throws:java.lang.ArrayIndexOutOfBoundsException;array=[3, 1, 4, 1, 5, 9]
mix#0=0
mix#1=0
mix#2=0
mix#3=0
mix#4=0
mix#5=0
narrow#0=0
narrow#1=65535
narrow#2=1
narrow#3=65529
narrow#4=0
narrow#5=65515
subMul#0=0
subMul#1=-2
subMul#2=-992
subMul#3=12
subMul#4=2147483599
subMul#5=2098765124
sumTo#0=0
sumTo#1=0
sumTo#2=0
sumTo#3=21
sumTo#4=0
sumTo#5=1206807378
```

The revealed `mix` source is an AND/OR/XOR/left-shift/arithmetic-right-shift/
logical-right-shift chain. It simplifies to zero for all inputs, and GCC
removed the entire chain at `-O2`; consequently the interesting source-level
kernel is not recoverable from this artifact even though runtime behavior is.
