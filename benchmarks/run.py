#!/usr/bin/env python3
"""Build and run the plain-JVM/current-transpiler benchmark pair."""

import json
import os
import platform
import re
import shlex
import shutil
import subprocess
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
RESULT = ROOT / "build" / "benchmarks" / "results.json"
WORK = ROOT / "build" / "benchmarks" / "work"
WARMUP = int(os.environ.get("BENCH_WARMUP", "5"))
ITERATIONS = int(os.environ.get("BENCH_ITERATIONS", "10"))


class HarnessFailure(RuntimeError):
    def __init__(self, stage, message):
        super().__init__(message)
        self.stage = stage


def printable(command):
    return " ".join(shlex.quote(str(part)) for part in command)


def execute(report, stage, command, cwd=ROOT, timeout=300, env=None):
    command = [str(part) for part in command]
    entry = {"stage": stage, "command": printable(command)}
    report["commands"].append(entry)
    try:
        completed = subprocess.run(
            command,
            cwd=str(cwd),
            env=env,
            text=True,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            timeout=timeout,
            check=False,
        )
    except (OSError, subprocess.TimeoutExpired) as error:
        entry["status"] = "FAIL"
        entry["failure"] = str(error)
        raise HarnessFailure(stage, str(error))

    entry["exitCode"] = completed.returncode
    entry["status"] = "PASS" if completed.returncode == 0 else "FAIL"
    if completed.returncode != 0:
        entry["stdout"] = completed.stdout
        entry["stderr"] = completed.stderr
        raise HarnessFailure(
            stage,
            "command exited {}: {}\nstdout:\n{}\nstderr:\n{}".format(
                completed.returncode,
                entry["command"],
                completed.stdout,
                completed.stderr,
            ),
        )
    return completed


def version(command):
    try:
        completed = subprocess.run(
            command,
            cwd=str(ROOT),
            text=True,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            timeout=20,
            check=False,
        )
        text = (completed.stdout + completed.stderr).strip()
        return text if text else "unknown"
    except (OSError, subprocess.TimeoutExpired) as error:
        return "unavailable: {}".format(error)


def cxx_compiler():
    candidates = [
        os.environ.get("CXX"),
        shutil.which("g++"),
        shutil.which("clang++"),
        shutil.which("c++"),
    ]
    for candidate in candidates:
        if candidate:
            return str(Path(candidate).resolve())
    return "c++"


def java_home():
    configured = os.environ.get("JAVA_HOME")
    if configured:
        return configured
    settings = version(["java", "-XshowSettings:properties", "-version"])
    match = re.search(r"^\s*java\.home\s*=\s*(.+)$", settings, re.MULTILINE)
    return match.group(1).strip() if match else None


def parse_benchmark(stage, completed):
    lines = [line for line in completed.stdout.splitlines() if line.strip()]
    if not lines:
        raise HarnessFailure(stage, "benchmark process produced no JSON")
    try:
        return json.loads(lines[-1])
    except json.JSONDecodeError as error:
        raise HarnessFailure(
            stage,
            "benchmark process produced invalid JSON: {}\nstdout:\n{}".format(
                error, completed.stdout
            ),
        )


def find_native_library(output):
    names = {"libnative_library.so", "libnative_library.dylib", "native_library.dll"}
    matches = sorted(path for path in output.rglob("*") if path.name in names)
    if not matches:
        raise HarnessFailure(
            "locate-native-library",
            "native build succeeded but produced none of: {}".format(
                ", ".join(sorted(names))
            ),
        )
    return matches[0]


def assert_equivalent(plain, native):
    plain_kernels = {item["name"]: item for item in plain["kernels"]}
    native_kernels = {item["name"]: item for item in native["kernels"]}
    if set(plain_kernels) != set(native_kernels):
        raise HarnessFailure(
            "correctness-check",
            "plain/native kernel sets differ: {} vs {}".format(
                sorted(plain_kernels), sorted(native_kernels)
            ),
        )
    for name in sorted(plain_kernels):
        expected = plain_kernels[name]["checksum"]
        actual = native_kernels[name]["checksum"]
        if expected != actual:
            raise HarnessFailure(
                "correctness-check",
                "{} checksum differs: plain={}, native={}".format(
                    name, expected, actual
                ),
            )


def write_report(report):
    RESULT.parent.mkdir(parents=True, exist_ok=True)
    rendered = json.dumps(report, indent=2, sort_keys=True)
    RESULT.write_text(rendered + "\n", encoding="utf-8")
    print(rendered)
    print("Raw result: {}".format(RESULT), file=sys.stderr)


def main():
    compiler = cxx_compiler()
    report = {
        "schemaVersion": 1,
        "status": "FAIL",
        "warmup": WARMUP,
        "iterations": ITERATIONS,
        "environment": {
            "jdk": version(["java", "-version"]),
            "os": platform.platform(),
            "compiler": {
                "command": compiler,
                "version": version([compiler, "--version"]),
            },
            "cmake": version(["cmake", "--version"]),
        },
        "commands": [],
        "plainJvm": {"status": "NOT_RUN"},
        "transpiledNative": {
            "status": "NOT_RUN",
            "nativeBuildSkipped": True,
        },
    }

    try:
        if WARMUP < 1 or ITERATIONS < 2:
            raise HarnessFailure(
                "configuration",
                "BENCH_WARMUP must be >= 1 and BENCH_ITERATIONS must be >= 2",
            )

        input_jar = Path(os.environ["BENCH_INPUT_JAR"]).resolve()
        obfuscator_jar = Path(os.environ["BENCH_OBFUSCATOR_JAR"]).resolve()
        if not input_jar.is_file() or not obfuscator_jar.is_file():
            raise HarnessFailure(
                "configuration",
                "Gradle did not provide existing benchmark and obfuscator JARs",
            )

        if WORK.exists():
            shutil.rmtree(WORK)
        output = WORK / "transpiled"
        output.mkdir(parents=True)

        plain_command = [
            "java",
            "-jar",
            input_jar,
            "--mode=plain-jvm",
            "--warmup={}".format(WARMUP),
            "--iterations={}".format(ITERATIONS),
        ]
        plain_completed = execute(
            report, "plain-jvm", plain_command, timeout=300
        )
        plain = parse_benchmark("plain-jvm", plain_completed)
        report["plainJvm"] = {
            "status": "PASS",
            "command": printable(plain_command),
            "result": plain,
        }

        transpile_command = [
            "java",
            "-jar",
            obfuscator_jar,
            "--white-list={}".format(ROOT / "benchmarks" / "whitelist.txt"),
            "--plain-lib-name=native_library",
            input_jar,
            output,
        ]
        execute(report, "transpile", transpile_command, timeout=180)

        cpp = output / "cpp"
        cmake_build = cpp / "cmake-build"
        build_env = os.environ.copy()
        detected_java_home = java_home()
        if detected_java_home:
            build_env["JAVA_HOME"] = detected_java_home
        execute(
            report,
            "cmake-configure",
            [
                "cmake",
                "-S",
                cpp,
                "-B",
                cmake_build,
                "-DCMAKE_BUILD_TYPE=Release",
                "-DCMAKE_CXX_COMPILER={}".format(compiler),
            ],
            timeout=180,
            env=build_env,
        )
        report["transpiledNative"]["nativeBuildSkipped"] = False
        execute(
            report,
            "native-build",
            ["cmake", "--build", cmake_build, "--config", "Release", "--parallel"],
            timeout=300,
            env=build_env,
        )

        native_library = find_native_library(cmake_build)
        shutil.copy2(native_library, output / native_library.name)
        transpiled_jar = output / input_jar.name
        native_command = [
            "java",
            "-Djava.library.path={}".format(output),
            "-jar",
            transpiled_jar,
            "--mode=transpiled-jni",
            "--warmup={}".format(WARMUP),
            "--iterations={}".format(ITERATIONS),
        ]
        native_completed = execute(
            report, "transpiled-jni", native_command, cwd=output, timeout=300
        )
        native = parse_benchmark("transpiled-jni", native_completed)
        assert_equivalent(plain, native)
        report["transpiledNative"] = {
            "status": "PASS",
            "nativeBuildSkipped": False,
            "command": printable(native_command),
            "library": str(native_library.relative_to(ROOT)),
            "result": native,
        }
        report["status"] = "PASS"
    except (HarnessFailure, KeyError, ValueError) as error:
        if isinstance(error, HarnessFailure):
            stage = error.stage
        else:
            stage = "configuration"
        report["failure"] = {"stage": stage, "reason": str(error)}
        if report["transpiledNative"]["status"] != "PASS":
            report["transpiledNative"]["status"] = "FAIL"
            report["transpiledNative"]["failure"] = str(error)

    write_report(report)
    return 0 if report["status"] == "PASS" else 1


if __name__ == "__main__":
    sys.exit(main())
