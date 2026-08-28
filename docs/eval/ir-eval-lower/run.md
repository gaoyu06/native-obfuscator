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
`sumTo`, `subMul`, and `mix(II)I` methods; the driver is left as Java while the
four kernel methods are selected by the whitelist. The `mix` source is
intentionally not reproduced here.

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

The transpiler log processed `fixture/IrKernel`, skipped the Java driver, and
contained no fallback message. Generated `mix(II)I` had an
`ir_method_data` array followed by a call to
`native_jvm::ir_eval::evaluate_i32`; it contained neither a legacy `cstack`
body nor a direct-IR straight-line body.

## Candidate output

The stripped library and transformed plain-loader jar were copied byte-for-byte
to `published.so` and `published.jar`. The committed bytes are validated in
`liveness.md`; `published.so` is temporarily named `libirkernel.so` only while
running the jar because `--plain-lib-name irkernel` uses
`System.loadLibrary("irkernel")`.
