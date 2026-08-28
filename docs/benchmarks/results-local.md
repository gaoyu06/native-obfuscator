# Local benchmark result

This is one raw run from the Cursor Cloud environment on 2026-08-28. It is
evidence that both paths executed and agreed on checksums, not a general
performance claim. Times are nanoseconds per exact sample workload.

| Kernel | Plain median | Plain mean | Transpiled median | Transpiled mean |
| --- | ---: | ---: | ---: | ---: |
| integer-loop | 10,081,582 | 10,073,444.5 | 182,639,976 | 182,555,553.1 |
| string-concat-hash | 572,045 | 808,555.1 | 13,222,507.5 | 12,980,321.1 |
| recursion | 63,582 | 63,593.5 | 12,632,841.5 | 12,646,276.3 |

Raw `build/benchmarks/results.json`:

```json
{
  "commands": [
    {
      "command": "java -jar /workspace/obfuscator/build/benchmarks/transpiler-benchmarks.jar --mode=plain-jvm --warmup=5 --iterations=10",
      "exitCode": 0,
      "stage": "plain-jvm",
      "status": "PASS"
    },
    {
      "command": "java -jar /workspace/obfuscator/build/libs/obfuscator.jar --white-list=/workspace/benchmarks/whitelist.txt --plain-lib-name=native_library /workspace/obfuscator/build/benchmarks/transpiler-benchmarks.jar /workspace/build/benchmarks/work/transpiled",
      "exitCode": 0,
      "stage": "transpile",
      "status": "PASS"
    },
    {
      "command": "cmake -S /workspace/build/benchmarks/work/transpiled/cpp -B /workspace/build/benchmarks/work/transpiled/cpp/cmake-build -DCMAKE_BUILD_TYPE=Release -DCMAKE_CXX_COMPILER=/usr/bin/x86_64-linux-gnu-g++-13",
      "exitCode": 0,
      "stage": "cmake-configure",
      "status": "PASS"
    },
    {
      "command": "cmake --build /workspace/build/benchmarks/work/transpiled/cpp/cmake-build --config Release --parallel",
      "exitCode": 0,
      "stage": "native-build",
      "status": "PASS"
    },
    {
      "command": "java -Djava.library.path=/workspace/build/benchmarks/work/transpiled -jar /workspace/build/benchmarks/work/transpiled/transpiler-benchmarks.jar --mode=transpiled-jni --warmup=5 --iterations=10",
      "exitCode": 0,
      "stage": "transpiled-jni",
      "status": "PASS"
    }
  ],
  "environment": {
    "cmake": "cmake version 3.28.3\n\nCMake suite maintained and supported by Kitware (kitware.com).",
    "compiler": {
      "command": "/usr/bin/x86_64-linux-gnu-g++-13",
      "version": "x86_64-linux-gnu-g++-13 (Ubuntu 13.3.0-6ubuntu2~24.04.1) 13.3.0\nCopyright (C) 2023 Free Software Foundation, Inc.\nThis is free software; see the source for copying conditions.  There is NO\nwarranty; not even for MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE."
    },
    "jdk": "openjdk version \"21.0.10\" 2026-01-20\nOpenJDK Runtime Environment (build 21.0.10+7-Ubuntu-124.04)\nOpenJDK 64-Bit Server VM (build 21.0.10+7-Ubuntu-124.04, mixed mode, sharing)",
    "os": "Linux-6.12.94+-x86_64-with-glibc2.39"
  },
  "iterations": 10,
  "plainJvm": {
    "command": "java -jar /workspace/obfuscator/build/benchmarks/transpiler-benchmarks.jar --mode=plain-jvm --warmup=5 --iterations=10",
    "result": {
      "iterations": 10,
      "kernels": [
        {
          "checksum": -5663617524014644874,
          "mean": 10073444.5,
          "median": 10081582.0,
          "name": "integer-loop",
          "samples": [
            10093150,
            10087704,
            10092963,
            10081669,
            10022077,
            10076559,
            10081495,
            10017951,
            10099694,
            10081183
          ],
          "unit": "ns/sample",
          "workload": "5,000,000 loop iterations"
        },
        {
          "checksum": -124029673400,
          "mean": 808555.1,
          "median": 572045.0,
          "name": "string-concat-hash",
          "samples": [
            586698,
            557392,
            724351,
            1357233,
            1687179,
            1194641,
            475418,
            487753,
            529597,
            485289
          ],
          "unit": "ns/sample",
          "workload": "200 calls x 96 concatenations"
        },
        {
          "checksum": 3055512,
          "mean": 63593.5,
          "median": 63582.0,
          "name": "recursion",
          "samples": [
            63848,
            63636,
            63498,
            63583,
            63581,
            63535,
            63541,
            63541,
            63587,
            63585
          ],
          "unit": "ns/sample",
          "workload": "2,000 traversals x depth 32"
        }
      ],
      "mode": "plain-jvm",
      "timer": "System.nanoTime",
      "warmup": 5
    },
    "status": "PASS"
  },
  "schemaVersion": 1,
  "status": "PASS",
  "transpiledNative": {
    "command": "java -Djava.library.path=/workspace/build/benchmarks/work/transpiled -jar /workspace/build/benchmarks/work/transpiled/transpiler-benchmarks.jar --mode=transpiled-jni --warmup=5 --iterations=10",
    "library": "build/benchmarks/work/transpiled/cpp/cmake-build/build/lib/libnative_library.so",
    "nativeBuildSkipped": false,
    "result": {
      "iterations": 10,
      "kernels": [
        {
          "checksum": -5663617524014644874,
          "mean": 182555553.1,
          "median": 182639976.0,
          "name": "integer-loop",
          "samples": [
            182047881,
            182691469,
            182688015,
            182657385,
            182596715,
            182475924,
            182717784,
            182622567,
            182711597,
            182346194
          ],
          "unit": "ns/sample",
          "workload": "5,000,000 loop iterations"
        },
        {
          "checksum": -124029673400,
          "mean": 12980321.1,
          "median": 13222507.5,
          "name": "string-concat-hash",
          "samples": [
            12230247,
            12230235,
            12517613,
            13276268,
            13295886,
            13281632,
            13220519,
            13224496,
            13328997,
            13197318
          ],
          "unit": "ns/sample",
          "workload": "200 calls x 96 concatenations"
        },
        {
          "checksum": 3055512,
          "mean": 12646276.3,
          "median": 12632841.5,
          "name": "recursion",
          "samples": [
            12716067,
            12596918,
            12601770,
            12615378,
            12661575,
            12625908,
            12750692,
            12678790,
            12575890,
            12639775
          ],
          "unit": "ns/sample",
          "workload": "2,000 traversals x depth 32"
        }
      ],
      "mode": "transpiled-jni",
      "timer": "System.nanoTime",
      "warmup": 5
    },
    "status": "PASS"
  },
  "warmup": 5
}
```
