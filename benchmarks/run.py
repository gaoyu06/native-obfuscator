#!/usr/bin/env python3
"""Build and run plain-JVM and IR-JNI benchmarks."""

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
LOG_DIR = ROOT / "build" / "benchmarks" / "logs"
WARMUP = int(os.environ.get("BENCH_WARMUP", "5"))
ITERATIONS = int(os.environ.get("BENCH_ITERATIONS", "10"))
CODEGENS = ("ir",)
KERNEL_METHODS = (
    ("integer-loop", "benchmarks/kernels/IntegerLoopKernel", "run", "(I)J"),
    ("string-concat-hash", "benchmarks/kernels/StringConcatHashKernel", "run", "(I)I"),
    ("recursion", "benchmarks/kernels/RecursionKernel", "run", "(II)J"),
    ("recursion", "benchmarks/kernels/RecursionKernel", "recurse", "(IJ)J"),
)


class HarnessFailure(RuntimeError):
    def __init__(self, stage, message, command=None):
        super().__init__(message)
        self.stage = stage
        self.command = command


def printable(command):
    return " ".join(shlex.quote(str(part)) for part in command)


def execute(report, stage, command, cwd=ROOT, timeout=300, env=None, log_path=None):
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
        raise HarnessFailure(stage, str(error), entry["command"])

    entry["exitCode"] = completed.returncode
    entry["status"] = "PASS" if completed.returncode == 0 else "FAIL"
    if log_path is not None:
        log_path.parent.mkdir(parents=True, exist_ok=True)
        log_path.write_text(
            "stdout:\n{}\nstderr:\n{}".format(completed.stdout, completed.stderr),
            encoding="utf-8",
        )
        entry["log"] = str(log_path.relative_to(ROOT))
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
            entry["command"],
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


def compiler(environment_name, names):
    candidates = [os.environ.get(environment_name)] + list(names)
    for candidate in candidates:
        if candidate:
            resolved = shutil.which(candidate)
            if resolved:
                return str(Path(resolved).resolve())
            path = Path(candidate)
            if path.exists():
                return str(path.resolve())
    return names[-1]


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


def find_native_library(output, stage):
    names = {"libnative_library.so", "libnative_library.dylib", "native_library.dll"}
    matches = sorted(path for path in output.rglob("*") if path.name in names)
    if not matches:
        raise HarnessFailure(
            stage,
            "native build succeeded but produced none of: {}".format(
                ", ".join(sorted(names))
            ),
        )
    return matches[0]


def assert_equivalent(plain, native, stage):
    plain_kernels = {item["name"]: item for item in plain["kernels"]}
    native_kernels = {item["name"]: item for item in native["kernels"]}
    if set(plain_kernels) != set(native_kernels):
        raise HarnessFailure(
            stage,
            "plain/native kernel sets differ: {} vs {}".format(
                sorted(plain_kernels), sorted(native_kernels)
            ),
        )
    for name in sorted(plain_kernels):
        expected = plain_kernels[name]["checksum"]
        actual = native_kernels[name]["checksum"]
        if expected != actual:
            raise HarnessFailure(
                stage,
                "{} checksum differs: plain={}, native={}".format(
                    name, expected, actual
                ),
            )


def classify_method_paths(output, transpile_output, codegen, plain):
    measured_kernels = [item["name"] for item in plain["kernels"]]
    configured_kernels = {item[0] for item in KERNEL_METHODS}
    if set(measured_kernels) != configured_kernels:
        raise HarnessFailure(
            "{}:method-path-check".format(codegen),
            "measured/configured kernel sets differ: {} vs {}".format(
                sorted(measured_kernels), sorted(configured_kernels)
            ),
        )

    fallback_logs = [
        line.strip()
        for line in transpile_output.splitlines()
        if "IR codegen unsupported for " in line
    ]
    generated = ""
    if codegen == "ir":
        generated = "\n".join(
            path.read_text(encoding="utf-8", errors="replace")
            for path in sorted((output / "cpp").rglob("*"))
            if path.suffix in {".cpp", ".hpp"}
        )

    method_paths = []
    for kernel, owner, name, descriptor in KERNEL_METHODS:
        method = "{}.{}{}".format(owner, name, descriptor)
        marker = "IR codegen: {}".format(method)
        fallback_prefix = "IR codegen unsupported for {}#{}{}".format(
            owner, name, descriptor
        )
        fallback_matches = [
            line for line in fallback_logs if fallback_prefix in line
        ]
        if fallback_matches:
            path = "restored-bytecode"
            evidence = fallback_matches[0]
        elif marker in generated:
            path = "ir"
            evidence = "// {}".format(marker)
        else:
            raise HarnessFailure(
                "{}:method-path-check".format(codegen),
                "no IR marker or restore log found for {}".format(method),
            )
        method_paths.append(
            {
                "kernel": kernel,
                "method": method,
                "path": path,
                "evidence": evidence,
            }
        )

    kernel_paths = []
    for kernel in measured_kernels:
        methods = [item for item in method_paths if item["kernel"] == kernel]
        paths = {item["path"] for item in methods}
        if paths == {"ir"}:
            kernel_path = "ir"
            stayed_on_ir = True
        elif "restored-bytecode" in paths:
            kernel_path = "restored-bytecode"
            stayed_on_ir = False
        else:
            raise HarnessFailure(
                "{}:method-path-check".format(codegen),
                "unrecognized paths for {}: {}".format(kernel, sorted(paths)),
            )
        kernel_paths.append(
            {
                "kernel": kernel,
                "path": kernel_path,
                "stayedOnIr": stayed_on_ir,
                "methods": [item["method"] for item in methods],
            }
        )
    return method_paths, kernel_paths, fallback_logs


def failure_record(error, default_stage):
    if isinstance(error, HarnessFailure):
        record = {"stage": error.stage, "reason": str(error)}
        if error.command is not None:
            record["command"] = error.command
        return record
    return {"stage": default_stage, "reason": str(error)}


def run_native(report, codegen, input_jar, obfuscator_jar, plain, cc, cxx):
    native_report = report["native"][codegen]
    mode_work = WORK / codegen
    output = mode_work / "transpiled"
    output.mkdir(parents=True)

    transpile_command = [
        "java",
        "-jar",
        obfuscator_jar,
        "--white-list={}".format(ROOT / "benchmarks" / "whitelist.txt"),
        "--plain-lib-name=native_library",
        "--codegen={}".format(codegen),
        input_jar,
        output,
    ]
    transpile_completed = execute(
        report,
        "{}:transpile".format(codegen),
        transpile_command,
        timeout=180,
        log_path=LOG_DIR / "transpile-{}.log".format(codegen),
    )
    transpile_output = transpile_completed.stdout + transpile_completed.stderr
    method_paths, kernel_paths, fallback_logs = classify_method_paths(
        output, transpile_output, codegen, plain
    )
    native_report["methodPaths"] = method_paths
    native_report["kernelPaths"] = kernel_paths
    native_report["fallbackLogs"] = fallback_logs

    cpp = output / "cpp"
    cmake_build = cpp / "cmake-build"
    build_env = os.environ.copy()
    detected_java_home = java_home()
    if detected_java_home:
        build_env["JAVA_HOME"] = detected_java_home
    native_report["nativeBuildSkipped"] = False
    execute(
        report,
        "{}:cmake-configure".format(codegen),
        [
            "cmake",
            "-S",
            cpp,
            "-B",
            cmake_build,
            "-DCMAKE_BUILD_TYPE=Release",
            "-DCMAKE_C_COMPILER={}".format(cc),
            "-DCMAKE_CXX_COMPILER={}".format(cxx),
        ],
        timeout=180,
        env=build_env,
    )
    execute(
        report,
        "{}:native-build".format(codegen),
        ["cmake", "--build", cmake_build, "--config", "Release", "--parallel"],
        timeout=300,
        env=build_env,
    )

    native_library = find_native_library(
        cmake_build, "{}:locate-native-library".format(codegen)
    )
    shutil.copy2(native_library, output / native_library.name)
    transpiled_jar = output / input_jar.name
    native_command = [
        "java",
        "-Djava.library.path={}".format(output),
        "-jar",
        transpiled_jar,
        "--mode=transpiled-jni-{}".format(codegen),
        "--warmup={}".format(WARMUP),
        "--iterations={}".format(ITERATIONS),
    ]
    native_completed = execute(
        report,
        "{}:transpiled-jni".format(codegen),
        native_command,
        cwd=output,
        timeout=300,
    )
    native = parse_benchmark(
        "{}:transpiled-jni".format(codegen), native_completed
    )
    assert_equivalent(plain, native, "{}:correctness-check".format(codegen))
    native_report.update(
        {
            "status": "PASS",
            "command": printable(native_command),
            "library": str(native_library.relative_to(ROOT)),
            "result": native,
        }
    )


def write_report(report):
    RESULT.parent.mkdir(parents=True, exist_ok=True)
    rendered = json.dumps(report, indent=2, sort_keys=True)
    RESULT.write_text(rendered + "\n", encoding="utf-8")
    print(rendered)


def main():
    cxx = compiler("CXX", ("g++", "clang++", "c++"))
    cc = compiler("CC", ("gcc", "clang", "cc"))
    report = {
        "schemaVersion": 2,
        "status": "FAIL",
        "warmup": WARMUP,
        "iterations": ITERATIONS,
        "environment": {
            "uname": version(["uname", "-a"]),
            "jdk": version(["java", "-version"]),
            "os": platform.platform(),
            "cCompiler": {
                "command": cc,
                "version": version([cc, "--version"]),
            },
            "cxxCompiler": {
                "command": cxx,
                "version": version([cxx, "--version"]),
            },
            "cmake": version(["cmake", "--version"]),
        },
        "commands": [],
        "plainJvm": {"status": "NOT_RUN"},
        "native": {
            codegen: {
                "status": "NOT_RUN",
                "codegen": codegen,
                "nativeBuildSkipped": True,
            }
            for codegen in CODEGENS
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
        WORK.mkdir(parents=True)

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
    except (HarnessFailure, KeyError, OSError, ValueError) as error:
        report["failure"] = failure_record(error, "configuration")
        for codegen in CODEGENS:
            report["native"][codegen]["status"] = "NOT_RUN"
            report["native"][codegen]["reason"] = (
                "plain-JVM/configuration prerequisite failed"
            )
        write_report(report)
        return 1

    failed_modes = []
    for codegen in CODEGENS:
        try:
            run_native(
                report, codegen, input_jar, obfuscator_jar, plain, cc, cxx
            )
        except (HarnessFailure, KeyError, OSError, ValueError) as error:
            native_report = report["native"][codegen]
            native_report["status"] = "FAIL"
            native_report["failure"] = failure_record(
                error, "{}:harness".format(codegen)
            )
            failed_modes.append(codegen)

    if failed_modes:
        report["failure"] = {
            "stage": "native-modes",
            "reason": "failed modes: {}".format(", ".join(failed_modes)),
        }
    else:
        report["status"] = "PASS"

    write_report(report)
    return 0 if report["status"] == "PASS" else 1


if __name__ == "__main__":
    sys.exit(main())
