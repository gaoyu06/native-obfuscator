# Shared-evaluator compiler artifact run

This directory publishes only the compiler-side subject for a later independent
reader: a plain-loader jar and a stripped GNU/Linux x86-64 shared library built
from `--codegen=ir --ir-lower=eval`. No packing or recovery pass was performed.

## Toolchain

- OpenJDK / `javac` 21.0.10, fixture compiled with `--release 8`
- Gradle 9.3.1
- CMake 3.28.3
- GCC / G++ 13.3.0
- GNU binutils `strip` / `objdump` 2.42
- CMake build type: `Release`

## Commands

The fixture source stayed outside the repository. It contains static `add`,
`sumTo`, `subMul`, and `mix(II)I` methods; the driver is left as Java while a
`fixture/IrKernel*` whitelist selects the kernel class and its methods. The
`mix` source is intentionally not reproduced here.

```bash
./gradlew :obfuscator:shadowJar

javac --release 8 -d /tmp/ir-eval-lower-6d81/classes \
  /tmp/ir-eval-lower-6d81/src/fixture/IrKernel.java \
  /tmp/ir-eval-lower-6d81/src/fixture/Driver.java
jar cfm /tmp/ir-eval-lower-6d81/input/irkernel.jar \
  /tmp/ir-eval-lower-6d81/manifest.mf \
  -C /tmp/ir-eval-lower-6d81/classes .

java -jar obfuscator/build/libs/obfuscator.jar \
  /tmp/ir-eval-lower-6d81/input/irkernel.jar \
  /tmp/ir-eval-lower-6d81/generated \
  --codegen=ir --ir-lower=eval \
  --plain-lib-name irkernel \
  --white-list=/tmp/ir-eval-lower-6d81/whitelist.txt

CC=/usr/bin/gcc CXX=/usr/bin/g++ cmake \
  -S /tmp/ir-eval-lower-6d81/generated/cpp \
  -B /tmp/ir-eval-lower-6d81/generated/cpp/build-release \
  -DCMAKE_BUILD_TYPE=Release \
  -DCMAKE_C_COMPILER=/usr/bin/gcc \
  -DCMAKE_CXX_COMPILER=/usr/bin/g++
cmake --build /tmp/ir-eval-lower-6d81/generated/cpp/build-release \
  --config Release --verbose -j2
strip --strip-all \
  /tmp/ir-eval-lower-6d81/generated/cpp/build-release/build/lib/libnative_library.so
```

CMake identified both compilers as `GNU 13.3.0`; verbose compilation and link
commands used `/usr/bin/g++`, `-DNDEBUG`, and optimization flags. The explicit
`strip --strip-all` left an ELF64 x86-64 shared object reported as `stripped`.

The transpiler log processed `fixture/IrKernel` and skipped the Java driver.
Its only fallback was the synthesized void `<clinit>()V`, which is outside the
evaluator's i32-return subset. There was no fallback for `mix(II)I`: its
generated function had an `ir_method_data` array followed by a call to
`native_jvm::ir_eval::evaluate_i32`, with neither a legacy `cstack` body nor a
direct-IR straight-line body.

## Published output

The stripped library and transformed plain-loader jar were copied byte-for-byte
to `published.so` and `published.jar`. The committed bytes are validated in
`liveness.md`; `published.so` is temporarily named `libirkernel.so` only while
running the jar because `--plain-lib-name irkernel` uses
`System.loadLibrary("irkernel")`.

```text
35ae8ebf14bd843d17b19140d788117e3ce6c254a8fe09711af6ddebdf6e2791  published.so
5bbce6ef87944adea74a2e9490a407d7756ae6582a4b37798f9d3ddbec3b63e6  published.jar
```

`jar tf published.jar` listed only the manifest, the two fixture classes, and
`native0/Loader.class`; it contained no `.so`, confirming that no packing was
performed.

## Runtime comparison

The gate was run against the published files:

```bash
mkdir -p /tmp/ir-eval-lower-6d81/runlib
cp docs/eval/ir-eval-lower/published.so \
  /tmp/ir-eval-lower-6d81/runlib/libirkernel.so

LD_LIBRARY_PATH=/tmp/ir-eval-lower-6d81/runlib \
  java -jar docs/eval/ir-eval-lower/published.jar

cmp \
  <(java -jar /tmp/ir-eval-lower-6d81/input/irkernel.jar) \
  <(LD_LIBRARY_PATH=/tmp/ir-eval-lower-6d81/runlib \
    java -jar docs/eval/ir-eval-lower/published.jar)
```

`cmp` exited **0**. Both runs printed:

```text
add=42
sumTo=45
subMul=39
mix(0,0)=-385
mix(1,2)=2028
mix(2,1)=1500
mix(-3,5)=-593
mix(7,-4)=3060
mix(123,456)=409744
```

The six `mix` cases have six distinct outputs and include nonzero values.
