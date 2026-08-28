# Shared-library evaluation run

## Scope and provenance

- Evaluation branch: `cursor/eval-shared-lib-reader-6d81`
- Compiler baseline: `origin/cursor/link-only-native-output-6d81-a2de`
  (`38a3991`)
- Host artifact: Linux x86-64 shared library
- Fixture: the N=1 `DemoKernel` / `Runner` fixture was materialized from
  `origin/cursor/eval-automated-reader-6d81` and compiled without viewing its
  Java contents.
- Reader input: only the published shared library, copied to
  `docs/eval/shared-lib/published.so`

## Build and publication

The compiler executable was built with:

```text
./gradlew :obfuscator:shadowJar --console=plain
```

The untransformed fixture was compiled to an executable `fixture.jar`. The
linked publication command was:

```text
CC=gcc CXX=g++ java -jar obfuscator/build/libs/obfuscator.jar \
  --backend=interpreter \
  --publish-native-lib \
  --opcode-seed=925 \
  --white-list=/tmp/shared-lib-eval-925a/fixture-src/whitelist.txt \
  /tmp/shared-lib-eval-925a/fixture.jar \
  /tmp/shared-lib-eval-925a/published
```

The recorded published-directory listing was:

```text
total 64
drwxr-xr-x 2 ubuntu ubuntu  4096 Aug 28 21:31 .
drwxr-xr-x 5 ubuntu ubuntu  4096 Aug 28 21:31 ..
-rw-r--r-- 1 ubuntu ubuntu 16322 Aug 28 21:31 fixture.jar
-rwxr-xr-x 1 ubuntu ubuntu 39512 Aug 28 21:31 x64-linux.so
NO_CPP_FILES
```

`NO_CPP_FILES` was printed only after this assertion succeeded:

```text
test -z "$(compgen -G '/tmp/shared-lib-eval-925a/published/*.cpp')"
```

## Runtime comparison

Untransformed oracle:

```text
add(7,5)=12
sumTo(0)=0
sumTo(1)=0
sumTo(10)=45
sumTo(100)=4950
mix(0,0)=-1640531527
mix(0,1)=389078419
mix(1,4)=-1363050107
mix(MIN,3)=-2129079926
mix(0x12345678,16)=567676480
```

Published jar run with the published library directory:

```text
java -Djava.library.path=/tmp/shared-lib-eval-925a/published \
  -jar /tmp/shared-lib-eval-925a/published/fixture.jar
```

Published stdout:

```text
add(7,5)=12
sumTo(0)=0
sumTo(1)=0
sumTo(10)=45
sumTo(100)=4950
mix(0,0)=-1640531527
mix(0,1)=389078419
mix(1,4)=-1363050107
mix(MIN,3)=-2129079926
mix(0x12345678,16)=567676480
```

The two stdout records are identical.

## Reader sequence

Before opening Java source, the following tool families were used against
`published.so` only:

```text
nm -D -C --defined-only published.so
readelf -Ws published.so
readelf -h -S -d -r published.so
readelf -x .data -x .data.rel.ro -x .rodata published.so
strings -a -t x published.so
objdump -d -C -M intel published.so
```

The method registration table mapped three wrappers to `add`, `sumTo`, and
`mix`. Their descriptors referenced streams at `0x7380`, `0x7340`, and
`0x72c0`, respectively. Disassembly of the common integer interpreter supplied
the operation meanings needed to decode those streams.

`recovery.md` was then written with the complete recovered pseudocode. Only
after that file existed were `DemoKernel.java` and `Runner.java` opened for the
source comparison recorded in `scores.md`.
