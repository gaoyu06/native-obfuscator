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

Command: `./gradlew test --console=plain` — exit 1

```text
To honour the JVM settings for this build a single-use Daemon process will be forked. For more on this, please refer to https://docs.gradle.org/9.3.1/userguide/gradle_daemon.html#sec:disabling_the_daemon in the Gradle documentation.
Daemon will be stopped at the end of the build
Configuration on demand is an incubating feature.
> Task :obfuscator:processResources
> Task :obfuscator:processTestResources

> Task :annotations:compileJava
warning: [options] source value 8 is obsolete and will be removed in a future release
warning: [options] target value 8 is obsolete and will be removed in a future release
warning: [options] To suppress warnings about obsolete options, use -Xlint:-options.
3 warnings

> Task :annotations:processResources NO-SOURCE
> Task :annotations:classes
> Task :annotations:jar
> Task :annotations:compileTestJava NO-SOURCE
> Task :annotations:processTestResources NO-SOURCE
> Task :annotations:testClasses UP-TO-DATE
> Task :annotations:test NO-SOURCE

> Task :obfuscator:compileJava
warning: [options] source value 8 is obsolete and will be removed in a future release
warning: [options] target value 8 is obsolete and will be removed in a future release
warning: [options] To suppress warnings about obsolete options, use -Xlint:-options.
Note: /workspace/obfuscator/src/main/java/by/radioegor146/zig/ZigInstaller.java uses or overrides a deprecated API.
Note: Recompile with -Xlint:deprecation for details.
3 warnings

> Task :obfuscator:classes

> Task :obfuscator:compileTestJava
warning: [options] source value 8 is obsolete and will be removed in a future release
warning: [options] target value 8 is obsolete and will be removed in a future release
warning: [options] To suppress warnings about obsolete options, use -Xlint:-options.
3 warnings

> Task :obfuscator:testClasses
> Task :obfuscator:test FAILED

[Incubating] Problems report is available at: file:///workspace/build/reports/problems/problems-report.html

FAILURE: Build failed with an exception.

* What went wrong:
Execution failed for task ':obfuscator:test'.
> Test process encountered an unexpected problem.
   > Could not start Gradle Test Executor 2: Failed to load JUnit Platform.  Please ensure that all JUnit Platform dependencies are available on the test's runtime classpath, including the JUnit Platform launcher.
   > Could not start Gradle Test Executor 1: Failed to load JUnit Platform.  Please ensure that all JUnit Platform dependencies are available on the test's runtime classpath, including the JUnit Platform launcher.
   > Could not start Gradle Test Executor 3: Failed to load JUnit Platform.  Please ensure that all JUnit Platform dependencies are available on the test's runtime classpath, including the JUnit Platform launcher.
   > Could not start Gradle Test Executor 4: Failed to load JUnit Platform.  Please ensure that all JUnit Platform dependencies are available on the test's runtime classpath, including the JUnit Platform launcher.

* Try:
> Check common problems https://docs.gradle.org/9.3.1/userguide/java_testing.html#sec:java_testing_troubleshooting.
> Run with --stacktrace option to get the stack trace.
> Run with --info or --debug option to get more log output.
> Run with --scan to get full insights from a Build Scan (powered by Develocity).
> Get more help at https://help.gradle.org.

Deprecated Gradle features were used in this build, making it incompatible with Gradle 10.

You can use '--warning-mode all' to show the individual deprecation warnings and determine if they come from your own scripts or plugins.

For more on this, please refer to https://docs.gradle.org/9.3.1/userguide/command_line_interface.html#sec:command_line_warnings in the Gradle documentation.

BUILD FAILED in 11s
7 actionable tasks: 7 executed
```

Observed conclusion: production and test sources compiled, but **zero JUnit tests actually ran**. Gradle could not launch the JUnit Platform because the test runtime has API/engine dependencies but no launcher (`obfuscator/build.gradle:47-49`). Therefore this command did not reach any dynamic test's `javac`, transpiler, CMake, native compile, or transformed-jar execution. `krak2` is independently missing, but it was not the cause of this run's first failure.
