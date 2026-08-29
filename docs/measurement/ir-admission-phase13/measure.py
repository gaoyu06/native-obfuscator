#!/usr/bin/env python3
"""Reproduce phase-13 IR admission measurements without changing compiler code."""

from __future__ import annotations

import argparse
import collections
import csv
from pathlib import Path
import re
import shlex
import shutil
import subprocess
import sys


REQUIRED_TIP = "b5a403fd398961870eb6aadafb50b882bc17f273"
JDK17_REF = "origin/cursor/test-jdk17-e2e-harness-6d81"
JDK21_REF = "origin/cursor/jdk21-25-e2e-6d81"
FETCH_BRANCHES = [
    "cursor/test-jdk17-e2e-harness-6d81",
    "cursor/jdk17-classfile-metadata-6d81",
    "cursor/jdk21-25-e2e-6d81",
    "cursor/jdk25-e2e-6d81",
]
CORPUS_A = [
    "clinit/TestClInitStacktrace",
    "empty/EmptyTest1",
    "indy/IndyTest1",
    "interface/InterfaceDefault",
    "interface/InterfaceDefaultStacktrace",
    "issues/Issue52",
    "java-obfuscator-test/JavaObfuscatorTest",
    "pull-requests/PullRequest72",
]
FALLBACK_RE = re.compile(
    r"IR codegen unsupported for (.+)#([^(]+)(\(.*\).+?): (.*); "
    r"(falling back to legacy for this method|leaving constructor bytecode unchanged)"
)
MARKER_RE = re.compile(r"// IR codegen: (.+)\.([^.()]+)(\(.*\).+)$")


class Recorder:
    def __init__(self, root: Path) -> None:
        self.commands = (root / "commands.log").open("w", encoding="utf-8")

    def close(self) -> None:
        self.commands.close()

    def run(
        self,
        command: list[str],
        *,
        cwd: Path,
        check: bool = True,
        output: Path | None = None,
    ) -> subprocess.CompletedProcess[str]:
        rendered = f"(cd {shlex.quote(str(cwd))} && {shlex.join(command)})"
        print(f"$ {rendered}")
        self.commands.write(f"$ {rendered}\n")
        self.commands.flush()
        result = subprocess.run(
            command,
            cwd=cwd,
            text=True,
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
        )
        if output is not None:
            output.write_text(result.stdout, encoding="utf-8")
        if result.stdout:
            print(result.stdout, end="")
        if check and result.returncode:
            raise subprocess.CalledProcessError(
                result.returncode, command, output=result.stdout
            )
        return result


def archive_path(
    recorder: Recorder, repo: Path, ref: str, source_path: str, destination: Path
) -> None:
    destination.mkdir(parents=True, exist_ok=True)
    git_command = ["git", "archive", ref, source_path]
    tar_command = ["tar", "-x", "-C", str(destination)]
    rendered = (
        f"(cd {shlex.quote(str(repo))} && {shlex.join(git_command)}"
        f" | {shlex.join(tar_command)})"
    )
    print(f"$ {rendered}")
    recorder.commands.write(f"$ {rendered}\n")
    recorder.commands.flush()
    git = subprocess.Popen(git_command, cwd=repo, stdout=subprocess.PIPE)
    assert git.stdout is not None
    tar = subprocess.run(tar_command, cwd=repo, stdin=git.stdout)
    git.stdout.close()
    git_status = git.wait()
    if git_status or tar.returncode:
        raise RuntimeError(
            f"archive failed: git={git_status}, tar={tar.returncode}, path={source_path}"
        )


def method_name(owner: str, declaration: str) -> str | None:
    if declaration == "static {};":
        return "<clinit>"
    if "(" not in declaration:
        return None
    candidate = declaration.split("(", 1)[0].split()[-1]
    if candidate == owner.replace("/", "."):
        return "<init>"
    return candidate


def parse_javap(owner: str, text: str) -> list[tuple[str, str, str]]:
    """Return code-bearing input methods as (owner, name, descriptor)."""
    declaration: str | None = None
    descriptor: str | None = None
    result: list[tuple[str, str, str]] = []
    for line in text.splitlines():
        stripped = line.strip()
        if (
            line.startswith("  ")
            and not line.startswith("    ")
            and (stripped.endswith(";") or stripped == "static {};")
        ):
            declaration = stripped
            descriptor = None
            continue
        if declaration is not None and stripped.startswith("descriptor: "):
            descriptor = stripped.removeprefix("descriptor: ")
            continue
        if declaration is not None and descriptor is not None and stripped == "Code:":
            name = method_name(owner, declaration)
            if name is not None:
                result.append((owner, name, descriptor))
            declaration = None
            descriptor = None
    return result


def inventory_jar(
    recorder: Recorder, repo: Path, fixture_dir: Path, input_jar: Path
) -> list[tuple[str, str, str]]:
    listing = recorder.run(
        ["jar", "tf", str(input_jar)],
        cwd=repo,
        output=fixture_dir / "jar-list.txt",
    ).stdout
    classes = sorted(
        line.removesuffix(".class")
        for line in listing.splitlines()
        if line.endswith(".class")
        and not line.startswith("META-INF/versions/")
        and line != "module-info.class"
    )
    inventory: list[tuple[str, str, str]] = []
    javap_output = fixture_dir / "javap.txt"
    with javap_output.open("w", encoding="utf-8") as combined:
        for owner in classes:
            class_name = owner.replace("/", ".")
            result = recorder.run(
                [
                    "javap",
                    "-p",
                    "-s",
                    "-c",
                    "-classpath",
                    str(input_jar),
                    class_name,
                ],
                cwd=repo,
            )
            combined.write(f"===== {owner} =====\n")
            combined.write(result.stdout)
            inventory.extend(parse_javap(owner, result.stdout))
    return inventory


def collect_markers(
    recorder: Recorder, repo: Path, fixture_dir: Path, output_dir: Path
) -> list[tuple[str, str, str]]:
    cpp_root = output_dir / "cpp" / "output"
    command = [
        "rg",
        "--no-filename",
        "-o",
        r"// IR codegen: .+",
        str(cpp_root),
    ]
    result = recorder.run(
        command,
        cwd=repo,
        check=False,
        output=fixture_dir / "markers.raw",
    )
    if result.returncode not in (0, 1):
        raise subprocess.CalledProcessError(
            result.returncode, command, output=result.stdout
        )
    markers: list[tuple[str, str, str]] = []
    for line in result.stdout.splitlines():
        match = MARKER_RE.fullmatch(line)
        if not match:
            raise ValueError(f"unparsed IR marker: {line}")
        markers.append(match.groups())
    return markers


def collect_fallbacks(
    log: str,
) -> dict[tuple[str, str, str], tuple[str, str]]:
    fallbacks: dict[tuple[str, str, str], tuple[str, str]] = {}
    for line in log.splitlines():
        match = FALLBACK_RE.search(line)
        if not match:
            continue
        owner, name, descriptor, reason, action = match.groups()
        result = (
            "legacy-fallback"
            if action.startswith("falling back")
            else "constructor-left-java"
        )
        fallbacks[(owner, name, descriptor)] = (result, reason)
    return fallbacks


def measure_fixture(
    recorder: Recorder,
    repo: Path,
    work: Path,
    corpus: str,
    fixture: str,
    source_dir: Path,
    release: int,
    obfuscator_jar: Path,
) -> tuple[list[dict[str, str]], dict[str, str | int]]:
    safe_name = fixture.replace("/", "__")
    fixture_dir = work / "fixtures" / corpus / safe_name
    classes_dir = fixture_dir / "classes"
    output_dir = fixture_dir / "output"
    classes_dir.mkdir(parents=True)
    output_dir.mkdir()
    java_files = sorted(source_dir.rglob("*.java"))
    krakatau_files = sorted(source_dir.rglob("*.j"))
    metadata: dict[str, str | int] = {
        "corpus": corpus,
        "fixture": fixture,
        "release": release,
        "java_sources": len(java_files),
        "krakatau_sources": len(krakatau_files),
        "compile_status": "ok",
        "obfuscator_status": "not-run",
    }
    if not java_files:
        metadata["compile_status"] = "no-java-sources"
        return [], metadata

    compile_command = [
        "javac",
        "--release",
        str(release),
        "-g",
        "-d",
        str(classes_dir),
        *map(str, java_files),
    ]
    compile_result = recorder.run(
        compile_command,
        cwd=repo,
        check=False,
        output=fixture_dir / "javac.log",
    )
    if compile_result.returncode:
        metadata["compile_status"] = f"failed ({compile_result.returncode})"
        return [], metadata

    input_jar = fixture_dir / "input.jar"
    recorder.run(
        ["jar", "--create", "--file", str(input_jar), "-C", str(classes_dir), "."],
        cwd=repo,
    )
    inventory = inventory_jar(recorder, repo, fixture_dir, input_jar)
    inventory_set = set(inventory)

    process_result = recorder.run(
        [
            "java",
            "-jar",
            str(obfuscator_jar),
            str(input_jar),
            str(output_dir),
            "--codegen=ir",
        ],
        cwd=repo,
        check=False,
        output=fixture_dir / "obfuscator.log",
    )
    metadata["obfuscator_status"] = (
        "ok"
        if process_result.returncode == 0
        else f"failed ({process_result.returncode})"
    )
    if process_result.returncode:
        metadata["inventory"] = len(inventory)
        return [], metadata

    markers = collect_markers(recorder, repo, fixture_dir, output_dir)
    marker_set = set(markers)
    fallbacks = collect_fallbacks(process_result.stdout)
    rows: list[dict[str, str]] = []
    for owner, name, descriptor in sorted(inventory):
        key = (owner, name, descriptor)
        if key in marker_set:
            result = "IR"
            reason = ""
        elif key in fallbacks:
            result, reason = fallbacks[key]
        else:
            result = "missing"
            reason = "no matching input-method marker or fallback log"
        rows.append(
            {
                "corpus": corpus,
                "fixture": fixture,
                "class": owner,
                "method": name,
                "descriptor": descriptor,
                "result": result,
                "reason": reason,
            }
        )

    raw_fallback = sum(1 for result, _ in fallbacks.values() if result == "legacy-fallback")
    raw_left = sum(
        1 for result, _ in fallbacks.values() if result == "constructor-left-java"
    )
    metadata.update(
        {
            "inventory": len(inventory),
            "raw_ir_markers": len(markers),
            "raw_fallback_logs": raw_fallback,
            "raw_left_logs": raw_left,
            "excluded_ir_markers": len(marker_set - inventory_set),
            "excluded_fallback_logs": len(set(fallbacks) - inventory_set),
        }
    )
    excluded = sorted((marker_set | set(fallbacks)) - inventory_set)
    with (fixture_dir / "excluded-generated-methods.tsv").open(
        "w", encoding="utf-8", newline=""
    ) as stream:
        writer = csv.writer(stream, delimiter="\t", lineterminator="\n")
        writer.writerow(["class", "method", "descriptor"])
        writer.writerows(excluded)
    return rows, metadata


def write_results(
    work: Path, rows: list[dict[str, str]], fixture_metadata: list[dict[str, str | int]]
) -> None:
    columns = [
        "corpus",
        "fixture",
        "class",
        "method",
        "descriptor",
        "result",
        "reason",
    ]
    with (work / "methods.tsv").open("w", encoding="utf-8", newline="") as stream:
        writer = csv.DictWriter(
            stream, fieldnames=columns, delimiter="\t", lineterminator="\n"
        )
        writer.writeheader()
        writer.writerows(rows)
    metadata_columns = sorted({key for item in fixture_metadata for key in item})
    with (work / "fixtures.tsv").open(
        "w", encoding="utf-8", newline=""
    ) as stream:
        writer = csv.DictWriter(
            stream,
            fieldnames=metadata_columns,
            delimiter="\t",
            lineterminator="\n",
        )
        writer.writeheader()
        writer.writerows(fixture_metadata)
    raw_marker_counts: dict[str, collections.Counter[str]] = collections.defaultdict(
        collections.Counter
    )
    for metadata in fixture_metadata:
        if metadata["obfuscator_status"] != "ok":
            continue
        corpus = str(metadata["corpus"])
        for key in (
            "inventory",
            "raw_ir_markers",
            "raw_fallback_logs",
            "raw_left_logs",
            "excluded_ir_markers",
            "excluded_fallback_logs",
        ):
            raw_marker_counts[corpus][key] += int(metadata[key])
    with (work / "raw-marker-counts.tsv").open(
        "w", encoding="utf-8", newline=""
    ) as stream:
        writer = csv.writer(stream, delimiter="\t", lineterminator="\n")
        writer.writerow(
            [
                "corpus",
                "input_inventory",
                "raw_ir_markers",
                "excluded_ir_markers",
                "matched_ir_markers",
                "raw_fallback_logs",
                "raw_left_logs",
                "excluded_fallback_logs",
            ]
        )
        for corpus, count in sorted(raw_marker_counts.items()):
            writer.writerow(
                [
                    corpus,
                    count["inventory"],
                    count["raw_ir_markers"],
                    count["excluded_ir_markers"],
                    count["raw_ir_markers"] - count["excluded_ir_markers"],
                    count["raw_fallback_logs"],
                    count["raw_left_logs"],
                    count["excluded_fallback_logs"],
                ]
            )

    counts: dict[str, collections.Counter[str]] = collections.defaultdict(
        collections.Counter
    )
    fixture_counts: dict[
        tuple[str, str], collections.Counter[str]
    ] = collections.defaultdict(collections.Counter)
    reasons: dict[str, collections.Counter[str]] = collections.defaultdict(
        collections.Counter
    )
    reason_families: collections.Counter[tuple[str, str, str, str]] = (
        collections.Counter()
    )
    for row in rows:
        counts[row["corpus"]]["inventory"] += 1
        counts[row["corpus"]][row["result"]] += 1
        fixture_key = (row["corpus"], row["fixture"])
        fixture_counts[fixture_key]["inventory"] += 1
        fixture_counts[fixture_key][row["result"]] += 1
        if row["reason"]:
            reasons[row["corpus"]][row["reason"]] += 1
            match = re.fullmatch(
                r"(.*) at bytecode instruction \d+ \(opcode (\d+)\)",
                row["reason"],
            )
            if match:
                message, opcode = match.groups()
            else:
                message, opcode = row["reason"], "n/a"
            reason_families[
                (row["corpus"], row["result"], opcode, message)
            ] += 1
    with (work / "raw-counts.txt").open("w", encoding="utf-8") as stream:
        print("corpus\tinventory\tIR\tlegacy-fallback\tconstructor-left-java\tmissing", file=stream)
        for corpus in sorted(counts):
            count = counts[corpus]
            print(
                corpus,
                count["inventory"],
                count["IR"],
                count["legacy-fallback"],
                count["constructor-left-java"],
                count["missing"],
                sep="\t",
                file=stream,
            )
    with (work / "fixture-counts.tsv").open(
        "w", encoding="utf-8", newline=""
    ) as stream:
        writer = csv.writer(stream, delimiter="\t", lineterminator="\n")
        writer.writerow(
            [
                "corpus",
                "fixture",
                "inventory",
                "IR",
                "legacy-fallback",
                "constructor-left-java",
                "missing",
            ]
        )
        for (corpus, fixture), count in sorted(fixture_counts.items()):
            writer.writerow(
                [
                    corpus,
                    fixture,
                    count["inventory"],
                    count["IR"],
                    count["legacy-fallback"],
                    count["constructor-left-java"],
                    count["missing"],
                ]
            )
    with (work / "fallback-histogram.tsv").open(
        "w", encoding="utf-8", newline=""
    ) as stream:
        writer = csv.writer(stream, delimiter="\t", lineterminator="\n")
        writer.writerow(["corpus", "count", "reason"])
        for corpus in sorted(reasons):
            for reason, count in sorted(
                reasons[corpus].items(), key=lambda item: (-item[1], item[0])
            ):
                writer.writerow([corpus, count, reason])
    with (work / "fallback-family-histogram.tsv").open(
        "w", encoding="utf-8", newline=""
    ) as stream:
        writer = csv.writer(stream, delimiter="\t", lineterminator="\n")
        writer.writerow(["corpus", "result", "count", "opcode", "message"])
        for (corpus, result, opcode, message), count in sorted(
            reason_families.items(),
            key=lambda item: (
                item[0][0],
                item[0][1],
                -item[1],
                item[0][2],
                item[0][3],
            ),
        ):
            writer.writerow([corpus, result, count, opcode, message])


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--work-dir",
        type=Path,
        default=Path("/tmp/native-obfuscator-ir-admission-phase13"),
    )
    parser.add_argument(
        "--include-jdk21",
        action="store_true",
        help="also measure fetched JDK 21 fixtures as a separate extra corpus",
    )
    args = parser.parse_args()
    repo = Path(__file__).resolve().parents[3]
    work = args.work_dir.resolve()
    if work.exists():
        shutil.rmtree(work)
    work.mkdir(parents=True)
    recorder = Recorder(work)
    try:
        base = recorder.run(
            ["git", "merge-base", "HEAD", REQUIRED_TIP], cwd=repo
        ).stdout.strip()
        if base != REQUIRED_TIP:
            raise RuntimeError(
                f"expected phase-13 base {REQUIRED_TIP}, found merge-base {base}"
            )
        changed = recorder.run(
            ["git", "diff", "--name-only", f"{REQUIRED_TIP}..HEAD"], cwd=repo
        ).stdout.splitlines()
        non_docs = [path for path in changed if not path.startswith("docs/")]
        if non_docs:
            raise RuntimeError(
                "measurement branch contains non-docs changes: " + ", ".join(non_docs)
            )
        recorder.run(["uname", "-a"], cwd=repo, output=work / "uname.txt")
        recorder.run(["java", "-version"], cwd=repo, output=work / "java-version.txt")
        recorder.run(["javac", "-version"], cwd=repo, output=work / "javac-version.txt")
        if shutil.which("krak2"):
            krakatau = recorder.run(["krak2", "-V"], cwd=repo, check=False)
            krakatau_status = f"exit={krakatau.returncode}\n{krakatau.stdout}"
        else:
            rendered = f"(cd {shlex.quote(str(repo))} && krak2 -V)"
            print(f"$ {rendered}\nkrak2: command not found")
            recorder.commands.write(f"$ {rendered}\n")
            recorder.commands.flush()
            krakatau_status = "exit=127\nkrak2: command not found\n"
        (work / "krakatau-status.txt").write_text(
            krakatau_status, encoding="utf-8"
        )
        recorder.run(
            ["./gradlew", ":obfuscator:shadowJar", "--rerun-tasks"], cwd=repo
        )
        recorder.run(
            ["git", "fetch", "origin", *FETCH_BRANCHES],
            cwd=repo,
        )
        jdk17_sha = recorder.run(["git", "rev-parse", JDK17_REF], cwd=repo).stdout
        (work / "jdk17-source-sha.txt").write_text(jdk17_sha, encoding="utf-8")

        extracted = work / "fetched-sources"
        archive_path(
            recorder,
            repo,
            JDK17_REF,
            "obfuscator/test_data/tests/jdk17",
            extracted,
        )
        if args.include_jdk21:
            jdk21_sha = recorder.run(["git", "rev-parse", JDK21_REF], cwd=repo).stdout
            (work / "jdk21-source-sha.txt").write_text(jdk21_sha, encoding="utf-8")
            archive_path(
                recorder,
                repo,
                JDK21_REF,
                "obfuscator/test_data/tests/jdk21",
                extracted,
            )

        obfuscator_jar = repo / "obfuscator/build/libs/obfuscator.jar"
        jobs: list[tuple[str, str, Path, int]] = []
        corpus_a_root = repo / "obfuscator/test_data/tests"
        jobs.extend(
            ("A", fixture, corpus_a_root / fixture, 8) for fixture in CORPUS_A
        )
        jdk17_root = extracted / "obfuscator/test_data/tests/jdk17"
        jobs.extend(
            ("B-jdk17", path.name, path, 17)
            for path in sorted(jdk17_root.iterdir())
            if path.is_dir()
        )
        if args.include_jdk21:
            jdk21_root = extracted / "obfuscator/test_data/tests/jdk21"
            jobs.extend(
                ("C-jdk21-extra", path.name, path, 21)
                for path in sorted(jdk21_root.iterdir())
                if path.is_dir()
            )

        rows: list[dict[str, str]] = []
        fixture_metadata: list[dict[str, str | int]] = []
        for corpus, fixture, source_dir, release in jobs:
            print(f"\n### Measuring {corpus}: {fixture}")
            fixture_rows, metadata = measure_fixture(
                recorder,
                repo,
                work,
                corpus,
                fixture,
                source_dir,
                release,
                obfuscator_jar,
            )
            rows.extend(fixture_rows)
            fixture_metadata.append(metadata)
        write_results(work, rows, fixture_metadata)
        print("\nRaw counts:")
        print((work / "raw-counts.txt").read_text(encoding="utf-8"), end="")
        print("\nRaw marker and log counts:")
        print((work / "raw-marker-counts.tsv").read_text(encoding="utf-8"), end="")
        print("\nFallback histogram:")
        print((work / "fallback-histogram.tsv").read_text(encoding="utf-8"), end="")
        print("\nFallback family histogram:")
        print(
            (work / "fallback-family-histogram.tsv").read_text(encoding="utf-8"),
            end="",
        )
        return 0
    finally:
        recorder.close()


if __name__ == "__main__":
    sys.exit(main())
