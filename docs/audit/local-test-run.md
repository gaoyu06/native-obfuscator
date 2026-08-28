# Local test-run evidence

Repository revision inspected before documentation changes:

```text
e7ca4c87deca403f692698fd74652d856f3c162f
2026-05-14
Add Zig toolchain support: install-zig command and --use-zig build option
```

No asynchronous environment-install status/log existed under `/tmp/cursor/async-install/`; there was no setup process to wait for.

## Tool preflight (raw output)

Command: `java -version` — exit 0

```text
openjdk version "21.0.10" 2026-01-20
OpenJDK Runtime Environment (build 21.0.10+7-Ubuntu-124.04)
OpenJDK 64-Bit Server VM (build 21.0.10+7-Ubuntu-124.04, mixed mode, sharing)
```

Command: `javac -version` — exit 0

```text
javac 21.0.10
```

Command: `cmake --version` — exit 0

```text
cmake version 3.28.3

CMake suite maintained and supported by Kitware (kitware.com/cmake).
```

Command: `g++ --version` — exit 0

```text
g++ (Ubuntu 13.3.0-6ubuntu2~24.04.1) 13.3.0
Copyright (C) 2023 Free Software Foundation, Inc.
This is free software; see the source for copying conditions.  There is NO
warranty; not even for MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
```

Command: `krak2 -V` — exit 127

```text
--: line 1: krak2: command not found
```

Command: `zig version` — exit 127

```text
--: line 1: zig: command not found
```

Command: `./gradlew --version` — exit 0

```text
Downloading https://services.gradle.org/distributions/gradle-9.3.1-all.zip
................................................................................................................................................................................................................................

Welcome to Gradle 9.3.1!

Here are the highlights of this release:
 - Test reporting improvements
 - Error and warning improvements
 - Build authoring improvements

For more details see https://docs.gradle.org/9.3.1/release-notes.html

------------------------------------------------------------
Gradle 9.3.1
------------------------------------------------------------

Build time:    2026-01-29 14:15:01 UTC
Revision:      44f4e8d3122ee6e7cbf5a248d7e20b4ca666bda3

Kotlin:        2.2.21
Groovy:        4.0.29
Ant:           Apache Ant(TM) version 1.10.15 compiled on August 25 2024
Launcher JVM:  21.0.10 (Ubuntu 21.0.10+7-Ubuntu-124.04)
Daemon JVM:    /usr/lib/jvm/java-21-openjdk-amd64 (no Daemon JVM specified, using current Java home)
OS:            Linux 6.12.94+ amd64
```

JDK, CMake, and g++ are installed. Required Krakatau command `krak2` is missing. Optional Zig is also missing; the Gradle E2E suite does not invoke Zig.

## Gradle test attempt

The exact `./gradlew test` result is added below after the documentation checkpoint is committed and pushed, as required by this cloud run's branch workflow.
