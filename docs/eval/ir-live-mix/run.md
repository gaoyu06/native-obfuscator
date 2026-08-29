# Live direct-IR kernel build record

This directory contains a compiler artifact for a later independent native-code
reading. The Java files under `src/` are builder-only reproducibility inputs;
they are not recovery evidence.

## Provenance

- Base branch: `cursor/ir-phase3-fable-review-6d81`
- Base SHA: `ebc6ffc973a91662de7f402a60d392e8f34f1961`
- Artifact branch: `cursor/eval-ir-live-mix-6d81`
- `published.so` SHA-256:
  `61ccf83ddb0e8f580eea015565167883242cfc8aa322fa6934aa35523ce173e9`
- `published.jar` SHA-256:
  `ee483ca7efd5bef6e363e974ba222b95df74a83511572cd042bdb7cab004d6a9`

Toolchain:

```text
openjdk 21.0.10 (javac 21.0.10)
Gradle wrapper 9.3.1
gcc/g++ 13.3.0 (Ubuntu 13.3.0-6ubuntu2~24.04.1)
cmake 3.28.3
GNU strip/objdump 2.42
```

## Commands executed

Compiler and fixture:

```sh
./gradlew :obfuscator:shadowJar --no-daemon

rm -rf "/tmp/ir-live-mix-build"
mkdir -p "/tmp/ir-live-mix-build/classes"
javac --release 8 \
  -d "/tmp/ir-live-mix-build/classes" \
  "docs/eval/ir-live-mix/src/example/Math.java" \
  "docs/eval/ir-live-mix/src/example/Runner.java"
jar --create \
  --file "/tmp/ir-live-mix-build/input.jar" \
  --main-class example.Runner \
  -C "/tmp/ir-live-mix-build/classes" .
printf '%s\n' \
  'example/Math' \
  'example/Math#add!(II)I' \
  'example/Math#sumTo!(I)I' \
  'example/Math#subMul!(II)I' \
  'example/Math#mix!(II)I' \
  'example/Math#<clinit>!()V' \
  > "/tmp/ir-live-mix-build/whitelist.txt"
java -jar "/tmp/ir-live-mix-build/input.jar" \
  > "/tmp/ir-live-mix-build/oracle.stdout"
```

Direct IR transpilation:

```sh
java -jar "obfuscator/build/libs/obfuscator.jar" \
  --codegen=ir \
  --plain-lib-name irkernel \
  --white-list "/tmp/ir-live-mix-build/whitelist.txt" \
  "/tmp/ir-live-mix-build/input.jar" \
  "/tmp/ir-live-mix-build/transpiled"
```

The generated `example_Math_0.cpp` contained all five expected direct-path
markers:

```text
// IR codegen: example/Math.add(II)I
// IR codegen: example/Math.sumTo(I)I
// IR codegen: example/Math.subMul(II)I
// IR codegen: example/Math.mix(II)I
// IR codegen: example/Math.<clinit>()V
```

The transpiler log contained no `IR codegen unsupported` or
`falling back to legacy` message, and the generated class body contained no
legacy `cstack`/`clocal` slots.

GCC Release build, explicit strip, and LoaderPlain runtime name:

```sh
cmake \
  -S "/tmp/ir-live-mix-build/transpiled/cpp" \
  -B "/tmp/ir-live-mix-build/native-build" \
  -DCMAKE_BUILD_TYPE=Release \
  -DCMAKE_C_COMPILER=/usr/bin/gcc \
  -DCMAKE_CXX_COMPILER=/usr/bin/g++
cmake --build "/tmp/ir-live-mix-build/native-build" --config Release
strip --strip-all \
  "/tmp/ir-live-mix-build/native-build/build/lib/libnative_library.so"
mkdir -p "/tmp/ir-live-mix-build/runtime"
cp "/tmp/ir-live-mix-build/native-build/build/lib/libnative_library.so" \
  "/tmp/ir-live-mix-build/runtime/libirkernel.so"
```

Oracle/native comparison and publication:

```sh
java \
  -Djava.library.path="/tmp/ir-live-mix-build/runtime" \
  -jar "/tmp/ir-live-mix-build/transpiled/input.jar" \
  > "/tmp/ir-live-mix-build/native.stdout"
cmp \
  "/tmp/ir-live-mix-build/oracle.stdout" \
  "/tmp/ir-live-mix-build/native.stdout"

cp "/tmp/ir-live-mix-build/native-build/build/lib/libnative_library.so" \
  "docs/eval/ir-live-mix/published.so"
cp "/tmp/ir-live-mix-build/transpiled/input.jar" \
  "docs/eval/ir-live-mix/published.jar"
```

LoaderPlain in `published.jar` performs `System.loadLibrary("irkernel")`.
`cmp` exited `0`; native stderr was empty.

## Observable mix output

The oracle and native runs both printed:

```text
mix#1=0
mix#2=-854579501
mix#3=-317707732
mix#4=-1877933050
mix#5=-259579501
mix#6=1562456856
```

There are six distinct results and five are nonzero.

## Stripped-symbol and disassembly evidence

Commands:

```sh
nm -D --defined-only -C \
  "docs/eval/ir-live-mix/published.so"
objdump -d -C --no-show-raw-insn \
  "docs/eval/ir-live-mix/published.so"
```

The stripped dynamic symbol table contains:

```text
0000000000003550 T native_jvm::classes::__ngen_example_Math_0::__ngen_native_mix4(JNIEnv_*, _jclass*, int, int)
```

Relevant `objdump` excerpt from the normal return path:

```text
0000000000003550 <native_jvm::classes::__ngen_example_Math_0::__ngen_native_mix4(JNIEnv_*, _jclass*, int, int)@@Base>:
    3583: imul   $0x45d9f3b,%ebp,%eax
    358d: imul   $0x119de1f3,%r12d,%ecx
    3595: mov    %eax,%edx
    3597: shr    $0x10,%edx
    359a: xor    %eax,%edx
    359c: mov    %ecx,%eax
    359e: shl    $0x7,%eax
    35a1: xor    %ecx,%eax
    35a3: add    %eax,%edx
    35a5: mov    %ebp,%eax
    35a7: and    %r12d,%ebp
    35aa: shl    $0x5,%eax
    35ad: xor    %eax,%edx
    35af: mov    %edx,%eax
    35b1: shl    $0x5,%eax
    35b4: add    %edx,%eax
    35b6: mov    %r12d,%edx
    35b9: shr    $0x3,%edx
    35bc: add    %edx,%eax
    35be: xor    %ebp,%eax
    35c5: ret
```

This is a live arithmetic path, not a constant-zero return.

No reader, decompilation/recovery, or scoring pass was performed, and no
recovery writeup was produced.
