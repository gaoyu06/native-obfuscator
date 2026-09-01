#!/usr/bin/env python3
"""Measure in-tree IR admission leftovers and generate the inventory report."""

from __future__ import annotations

import argparse
import collections
import csv
from dataclasses import dataclass
from pathlib import Path
import re
import shlex
import shutil
import subprocess
import sys


CLASSIC_FIXTURES = [
    "clinit/TestClInitStacktrace",
    "empty/EmptyTest1",
    "indy/IndyTest1",
    "interface/InterfaceDefault",
    "interface/InterfaceDefaultStacktrace",
    "issues/Issue52",
    "java-obfuscator-test/JavaObfuscatorTest",
    "pull-requests/PullRequest72",
]
JDK_RELEASES = (17, 21, 25)
FALLBACK_RE = re.compile(
    r"IR codegen unsupported for (.+)#([^(]+)(\(.*\).+?): (.*); "
    r"(falling back to legacy for this method|leaving constructor bytecode unchanged|leaving method bytecode unchanged)"
)
MARKER_RE = re.compile(r"// IR codegen: (.+)\.([^.()]+)(\(.*\).+)$")
REASON_RE = re.compile(
    r"^(.*) at bytecode instruction (\d+) \(opcode (\d+)\)$"
)


@dataclass(frozen=True)
class Toolchain:
    javac: str
    javap: str
    version: str


class Recorder:
    def __init__(self, work: Path) -> None:
        self.stream = (work / "commands.log").open("w", encoding="utf-8")

    def close(self) -> None:
        self.stream.close()

    def run(
        self,
        command: list[str],
        *,
        cwd: Path,
        check: bool = True,
        output: Path | None = None,
        display_output: bool = True,
    ) -> subprocess.CompletedProcess[str]:
        rendered = f"(cd {shlex.quote(str(cwd))} && {shlex.join(command)})"
        print(f"$ {rendered}")
        self.stream.write(f"$ {rendered}\n")
        self.stream.flush()
        result = subprocess.run(
            command,
            cwd=cwd,
            text=True,
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
        )
        if output is not None:
            output.write_text(result.stdout, encoding="utf-8")
        if display_output and result.stdout:
            print(result.stdout, end="")
        if check and result.returncode:
            raise subprocess.CalledProcessError(
                result.returncode, command, output=result.stdout
            )
        return result


def sibling_tool(javac: str, name: str) -> str:
    compiler = Path(javac)
    if compiler.parent.name == "bin":
        candidate = compiler.parent / name
        if candidate.is_file():
            return str(candidate)
    return name


def toolchain(recorder: Recorder, repo: Path, javac: str) -> Toolchain:
    result = recorder.run([javac, "-version"], cwd=repo)
    return Toolchain(javac, sibling_tool(javac, "javap"), result.stdout.strip())


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
    declaration: str | None = None
    descriptor: str | None = None
    methods: list[tuple[str, str, str]] = []
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
                methods.append((owner, name, descriptor))
            declaration = None
            descriptor = None
    return methods


def inventory_jar(
    recorder: Recorder,
    repo: Path,
    fixture_dir: Path,
    input_jar: Path,
    javap: str,
) -> list[tuple[str, str, str]]:
    listing = recorder.run(
        ["jar", "tf", str(input_jar)],
        cwd=repo,
        output=fixture_dir / "jar-list.txt",
        display_output=False,
    ).stdout
    classes = sorted(
        line.removesuffix(".class")
        for line in listing.splitlines()
        if line.endswith(".class")
        and not line.startswith("META-INF/versions/")
        and line != "module-info.class"
    )
    methods: list[tuple[str, str, str]] = []
    with (fixture_dir / "javap.txt").open("w", encoding="utf-8") as combined:
        for owner in classes:
            result = recorder.run(
                [
                    javap,
                    "-p",
                    "-s",
                    "-c",
                    "-classpath",
                    str(input_jar),
                    owner.replace("/", "."),
                ],
                cwd=repo,
                display_output=False,
            )
            combined.write(f"===== {owner} =====\n")
            combined.write(result.stdout)
            methods.extend(parse_javap(owner, result.stdout))
    return methods


def collect_markers(output_dir: Path) -> set[tuple[str, str, str]]:
    markers: set[tuple[str, str, str]] = set()
    cpp_root = output_dir / "cpp" / "output"
    for path in cpp_root.rglob("*"):
        if not path.is_file():
            continue
        for line in path.read_text(encoding="utf-8", errors="replace").splitlines():
            marker_index = line.find("// IR codegen: ")
            if marker_index < 0:
                continue
            marker = line[marker_index:]
            match = MARKER_RE.fullmatch(marker)
            if match is None:
                raise ValueError(f"unparsed IR marker in {path}: {marker}")
            markers.add(match.groups())
    return markers


def collect_fallbacks(
    log: str,
) -> dict[tuple[str, str, str], tuple[str, str]]:
    fallbacks: dict[tuple[str, str, str], tuple[str, str]] = {}
    for line in log.splitlines():
        match = FALLBACK_RE.search(line)
        if match is None:
            continue
        owner, name, descriptor, reason, action = match.groups()
        category = (
            "legacy-fallback"
            if action.startswith("falling back")
            else "constructor-left-java"
        )
        # Preserve the first unsupported message for each exact input method.
        fallbacks.setdefault((owner, name, descriptor), (category, reason))
    return fallbacks


def measure_fixture(
    recorder: Recorder,
    repo: Path,
    work: Path,
    corpus: str,
    fixture: str,
    source_dir: Path,
    release: int,
    tools: Toolchain,
    obfuscator_jar: Path,
) -> tuple[list[dict[str, str]], dict[str, str | int]]:
    safe_name = fixture.replace("/", "__")
    fixture_dir = work / "fixtures" / corpus / safe_name
    classes_dir = fixture_dir / "classes"
    output_dir = fixture_dir / "output"
    classes_dir.mkdir(parents=True)
    output_dir.mkdir()
    java_files = sorted(source_dir.rglob("*.java"))
    if not java_files:
        raise RuntimeError(f"fixture has no Java source: {source_dir}")

    recorder.run(
        [
            tools.javac,
            "--release",
            str(release),
            "-g",
            "-d",
            str(classes_dir),
            *map(str, java_files),
        ],
        cwd=repo,
        output=fixture_dir / "javac.log",
    )
    input_jar = fixture_dir / "input.jar"
    recorder.run(
        ["jar", "--create", "--file", str(input_jar), "-C", str(classes_dir), "."],
        cwd=repo,
        display_output=False,
    )
    inventory = inventory_jar(
        recorder, repo, fixture_dir, input_jar, tools.javap
    )
    inventory_set = set(inventory)
    process = recorder.run(
        [
            "java",
            "-jar",
            str(obfuscator_jar),
            str(input_jar),
            str(output_dir),
            "--codegen=ir",
        ],
        cwd=repo,
        output=fixture_dir / "obfuscator.log",
    )
    markers = collect_markers(output_dir)
    fallbacks = collect_fallbacks(process.stdout)

    rows: list[dict[str, str]] = []
    for owner, name, descriptor in sorted(inventory):
        key = (owner, name, descriptor)
        if key in markers:
            category = "IR"
            reason = ""
        elif key in fallbacks:
            category, reason = fallbacks[key]
        else:
            category = "missing"
            reason = "no exact input-method marker or fallback log"
        rows.append(
            {
                "corpus": corpus,
                "fixture": fixture,
                "class": owner,
                "method": name,
                "descriptor": descriptor,
                "category": category,
                "reason": reason,
            }
        )

    raw_fallback = sum(
        category == "legacy-fallback" for category, _ in fallbacks.values()
    )
    raw_left = sum(
        category == "constructor-left-java" for category, _ in fallbacks.values()
    )
    metadata: dict[str, str | int] = {
        "corpus": corpus,
        "fixture": fixture,
        "release": release,
        "java_sources": len(java_files),
        "inventory": len(inventory),
        "raw_ir_markers": len(markers),
        "raw_fallback_logs": raw_fallback,
        "raw_left_logs": raw_left,
        "excluded_ir_markers": len(markers - inventory_set),
        "excluded_fallback_logs": len(set(fallbacks) - inventory_set),
    }
    return rows, metadata


def write_evidence(
    work: Path,
    rows: list[dict[str, str]],
    metadata: list[dict[str, str | int]],
) -> None:
    with (work / "methods.tsv").open("w", encoding="utf-8", newline="") as stream:
        writer = csv.DictWriter(
            stream,
            fieldnames=[
                "corpus",
                "fixture",
                "class",
                "method",
                "descriptor",
                "category",
                "reason",
            ],
            delimiter="\t",
            lineterminator="\n",
        )
        writer.writeheader()
        writer.writerows(rows)
    with (work / "fixtures.tsv").open("w", encoding="utf-8", newline="") as stream:
        columns = [
            "corpus",
            "fixture",
            "release",
            "java_sources",
            "inventory",
            "raw_ir_markers",
            "raw_fallback_logs",
            "raw_left_logs",
            "excluded_ir_markers",
            "excluded_fallback_logs",
        ]
        writer = csv.DictWriter(
            stream, fieldnames=columns, delimiter="\t", lineterminator="\n"
        )
        writer.writeheader()
        writer.writerows(metadata)


def markdown_cell(value: str) -> str:
    return value.replace("|", r"\|").replace("\n", " ")


def reason_family(reason: str) -> tuple[str, str]:
    match = REASON_RE.fullmatch(reason)
    if match is None:
        return reason, "n/a"
    message, _, opcode = match.groups()
    return message, opcode


def render_report(
    report: Path,
    repo: Path,
    work: Path,
    command: list[str],
    rows: list[dict[str, str]],
    metadata: list[dict[str, str | int]],
    tools: dict[int, Toolchain],
    missing_trees: list[int],
    compiler_base: str,
    measured_commit: str,
    uname: str,
    java_version: str,
) -> None:
    counts: dict[str, collections.Counter[str]] = collections.defaultdict(
        collections.Counter
    )
    for row in rows:
        counts[row["corpus"]]["inventory"] += 1
        counts[row["corpus"]][row["category"]] += 1
    leftovers = [
        row
        for row in rows
        if row["category"] in ("legacy-fallback", "constructor-left-java")
    ]
    corpus_order = ["ClassicTest", "jdk17", "jdk21", "jdk25"]
    families: collections.Counter[tuple[str, str, str]] = collections.Counter()
    for row in leftovers:
        message, opcode = reason_family(row["reason"])
        families[(row["category"], opcode, message)] += 1

    lines = [
        "# IR leftover inventory on current master",
        "",
        "## Scope and interpretation",
        "",
        f"- Measured compiler base (merge-base with `origin/master`): `{compiler_base}`",
        f"- Measurement commit: `{measured_commit}`",
        "- This is an admission measurement of checked-in fixtures with explicit "
        "`--codegen=ir`.",
        "- This is **not a JDK support badge** or a behavioral/native E2E claim.",
        "- This run changes no compiler/runtime source or defaults: "
        "`--codegen=ir`, `--ir-lower=direct`, and `--backend=cpp` remain "
        "the defaults.",
        "- Master already contains #163 constructor prefix extra locals, #164 "
        "gapped extras, #165 shared-suffix multi-super diamonds, #166 "
        "prefix-only constructor try/catch, #167 primitive `Class` LDC, and "
        "#168 interface-hosted proven `ConstantDynamic`; this report measures "
        "the post-#168 tree rather than restoring the pre-#163 leftover list.",
        "- Inventory means `javap -p -s -c` methods with a `Code:` body. Results "
        "are joined by exact `class + method + descriptor`.",
        "- `// IR codegen:` means IR; `falling back to legacy for this method` "
        "means `legacy-fallback`; `leaving constructor bytecode unchanged` means "
        "`constructor-left-java`.",
        "",
        "## Host",
        "",
        "`uname -a` (verbatim):",
        "",
        "```text",
        uname.rstrip(),
        "```",
        "",
        "`java -version` (verbatim):",
        "",
        "```text",
        java_version.rstrip(),
        "```",
        "",
        "Compilers used:",
        "",
        "| Release | Compiler | Version output |",
        "| ---: | --- | --- |",
    ]
    for release in (8, 17, 21, 25):
        if release not in tools:
            continue
        lines.append(
            f"| {release} | `{markdown_cell(tools[release].javac)}` | "
            f"`{markdown_cell(tools[release].version)}` |"
        )
    if missing_trees:
        lines.extend(
            [
                "",
                "Missing in-tree fixture directories were skipped as required: "
                + ", ".join(f"`jdk{release}`" for release in missing_trees)
                + ".",
            ]
        )

    lines.extend(
        [
            "",
            "## Commands actually run",
            "",
            "Top-level command:",
            "",
            "```bash",
            shlex.join(command),
            "```",
            "",
            "The helper recorded every expanded per-fixture command in "
            f"`{work / 'commands.log'}`. The key commands were:",
            "",
            "```bash",
            "uname -a",
            "java -version",
            "./gradlew :obfuscator:shadowJar",
            "# For every present fixture, with its matching N and compiler:",
            "javac --release N -g -d <classes> <fixture Java sources>",
            "jar --create --file <input.jar> -C <classes> .",
            "javap -p -s -c -classpath <input.jar> <each input class>",
            "java -jar obfuscator/build/libs/obfuscator.jar "
            "<input.jar> <output> --codegen=ir",
            "```",
            "",
            "No historical fixture branch was fetched. All source trees came from "
            "`obfuscator/test_data/tests/` on the measured checkout. "
            "`pull-requests/PullRequest72/TestStringConcatFactory.j` was not "
            "assembled; this measurement uses that fixture's Java source only, "
            "matching the 108-method Java ClassicTest corpus.",
            "",
            "## Joined totals",
            "",
            "| Corpus | Inventory | IR | Legacy fallback | Constructor left in Java | Missing |",
            "| --- | ---: | ---: | ---: | ---: | ---: |",
        ]
    )
    for corpus in corpus_order:
        if corpus not in counts:
            continue
        count = counts[corpus]
        lines.append(
            f"| `{corpus}` | {count['inventory']} | {count['IR']} | "
            f"{count['legacy-fallback']} | {count['constructor-left-java']} | "
            f"{count['missing']} |"
        )
    classic = counts.get("ClassicTest")
    if classic:
        lines.extend(
            [
                "",
                "ClassicTest result from this run: "
                f"**{classic['IR']}/{classic['inventory']} IR**, "
                f"{classic['legacy-fallback']} legacy fallback, "
                f"{classic['constructor-left-java']} constructor left in Java, "
                f"and {classic['missing']} missing.",
            ]
        )

    for corpus in corpus_order:
        if corpus not in counts:
            continue
        corpus_leftovers = [row for row in leftovers if row["corpus"] == corpus]
        lines.extend(
            [
                "",
                f"## {corpus} measured leftovers only",
                "",
                "| Fixture | Class | Method | Descriptor | Category | Exact first reject/log reason |",
                "| --- | --- | --- | --- | --- | --- |",
            ]
        )
        if not corpus_leftovers:
            lines.append("| _None_ | — | — | — | — | — |")
        else:
            for row in corpus_leftovers:
                lines.append(
                    "| `{fixture}` | `{class_name}` | `{method}` | `{descriptor}` | "
                    "`{category}` | {reason} |".format(
                        fixture=markdown_cell(row["fixture"]),
                        class_name=markdown_cell(row["class"]),
                        method=markdown_cell(row["method"]),
                        descriptor=markdown_cell(row["descriptor"]),
                        category=row["category"],
                        reason=markdown_cell(row["reason"]),
                    )
                )

    lines.extend(
        [
            "",
            "## Code-path leftovers (static source audit)",
            "",
            "These are source-visible rejection paths, kept separate from the "
            "measured fixture leftovers. A zero measured count does not remove "
            "the code path.",
            "",
            "| Leftover class | Source path | Static reject behavior |",
            "| --- | --- | --- |",
            "| Constructor retained-prefix non-identity `ASTORE 0` | "
            "`ConstructorSpecialMethodProcessor` | "
            "`Constructor prefix changes local 0 before the bridge` "
            "(opcode 58 / `ASTORE`). |",
            "| Constructor prefix → suffix branch other than an admitted "
            "shared-suffix join `GOTO` | "
            "`ConstructorSpecialMethodProcessor` | "
            "`Constructor prefix branches across the this/super call` "
            "(jump/table/lookup-switch target outside retained prefix). |",
            "| Mixed constructor prefix/suffix try/catch | "
            "`ConstructorSpecialMethodProcessor` | "
            "`Constructor exception regions may not cross the this/super split`. |",
            "| Non-diamond multi-super constructor | "
            "`ConstructorSpecialMethodProcessor` | "
            "`Constructor chain calls do not share one suffix join` or the "
            "exactly-one-chain-call control-flow diagnostics. |",
            "| Conditionally assigned constructor-prefix extra | "
            "`ConstructorSpecialMethodProcessor` | "
            "`Constructor prefix extra local <n> is not definitely assigned on "
            "every path reaching the this/super call`. |",
            "| Non-static `ConstantDynamic` bootstrap | "
            "`DynamicConstantSupport` | "
            "`ConstantDynamic bootstrap is not REF_invokeStatic`. |",
            "| Varargs `ConstantDynamic` bootstrap shape | "
            "`DynamicConstantSupport` | "
            "`ConstantDynamic bootstrap must take Lookup, String, Class, then "
            "one exact parameter per static argument`. |",
            "| Malformed `ConstantDynamic` | "
            "`DynamicConstantSupport` | "
            "`Malformed ConstantDynamic descriptor` and related descriptor/type "
            "shape checks reject before resolver installation. |",
            "| Cyclic `ConstantDynamic` | "
            "`DynamicConstantSupport` | Rejected before resolver installation; "
            "`Cyclic ConstantDynamic bootstrap arguments`. |",
            "| Legacy subroutine bytecode (`jsr`/`ret`) | `AsmToIr` | "
            "`Unsupported instruction for phase-two IR` with opcode 168 (`JSR`) "
            "or 169 (`RET`). |",
            "",
            "Additional exact constructor-split rejection messages found in "
            "`ConstructorSpecialMethodProcessor` are "
            "`A constructor IR body must be an instance method returning V`, "
            "`Constructor has no direct this/super constructor call`, "
            "`Constructor suffix jumps into its bytecode prefix`, and "
            "`Constructor switch targets its bytecode prefix`.",
            "",
            "Other exact `DynamicConstantSupport` rejection messages found on "
            "this checkout remain conservative shape/placement checks:",
            "",
            "- `MethodHandle/MethodType LDC is not lowerable by the IR frontend: <cause>`",
            "- `ConstantDynamic interface companion cannot be placed safely: <cause>`",
            "- `ConstantDynamic interface companion has no hidden-method pool`",
            "- `ConstantDynamic interface companion requires a public interface`",
            "- `ConstantDynamic interface companion is not supported for annotations`",
            "- `ConstantDynamic interface companion requires class-file version 55`",
            "- `ConstantDynamic interface resolver installation is incomplete`",
            "- `ConstantDynamic bootstrap bridge name collides with an existing interface member`",
            "- `Cyclic ConstantDynamic bootstrap arguments`",
            "- `Malformed ConstantDynamic descriptor`",
            "- `ConstantDynamic result is not a scalar or reference`",
            "- `ConstantDynamic bootstrap is not REF_invokeStatic`",
            "- `ConstantDynamic bootstrap must take Lookup, String, Class, then one exact parameter per static argument`",
            "- `ConstantDynamic bootstrap return does not match its constant type`",
            "- `Primitive Type is not a loadable bootstrap argument`",
            "- `Unsupported ConstantDynamic bootstrap argument`",
            "- `ConstantDynamic bootstrap argument does not match parameter <index>`",
            "- `ConstantDynamic resolver name collides with an existing class member`",
            "- `Malformed MethodType LDC`",
            "- `Unsupported MethodHandle LDC`",
            "",
            "## Next increment candidates from measured counts",
            "",
        ]
    )
    if not families:
        lines.append(
            "No measured leftover exists, so this corpus does not support an "
            "evidence-based ordering of next increments."
        )
    else:
        lines.extend(
            [
                "Ordered only by the number of measured methods sharing the exact "
                "first-failure family; equal counts are ties:",
                "",
            ]
        )
        for (category, opcode, message), count in sorted(
            families.items(),
            key=lambda item: (-item[1], item[0][1], item[0][2], item[0][0]),
        ):
            opcode_text = f"opcode {opcode}, " if opcode != "n/a" else ""
            lines.append(
                f"1. **{count} method{'s' if count != 1 else ''}** — "
                f"{opcode_text}`{markdown_cell(message)}` "
                f"(`{category}`)."
            )
    lines.extend(
        [
            "",
            "This ordering is an admission-count observation for these fixtures "
            "only. It is not evidence of broader bytecode/JDK coverage, and it "
            "does not justify changing the `legacy` default.",
            "",
            "## Raw evidence",
            "",
            f"- Exact method ledger: `{work / 'methods.tsv'}`",
            f"- Per-fixture raw marker/log counts: `{work / 'fixtures.tsv'}`",
            f"- Expanded command log: `{work / 'commands.log'}`",
            "",
            "Generated by `docs/measurement/ir-leftover-inventory/measure.py`; "
            "do not edit measured counts by hand.",
            "",
        ]
    )
    report.write_text("\n".join(lines), encoding="utf-8")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--work-dir",
        type=Path,
        default=Path("/tmp/native-obfuscator-ir-leftover-inventory"),
    )
    parser.add_argument("--javac-25", help="JDK 25 javac used with --release 25")
    parser.add_argument(
        "--report",
        type=Path,
        help="generated report path (defaults to docs/benchmarks inventory)",
    )
    args = parser.parse_args()

    repo = Path(__file__).resolve().parents[3]
    work = args.work_dir.resolve()
    report = (
        args.report.resolve()
        if args.report
        else repo / "docs/benchmarks/ir-leftover-inventory.md"
    )
    if work.exists():
        shutil.rmtree(work)
    work.mkdir(parents=True)
    recorder = Recorder(work)
    try:
        uname = recorder.run(
            ["uname", "-a"], cwd=repo, output=work / "uname.txt"
        ).stdout
        java_version = recorder.run(
            ["java", "-version"], cwd=repo, output=work / "java-version.txt"
        ).stdout
        compiler_base = recorder.run(
            ["git", "merge-base", "HEAD", "origin/master"], cwd=repo
        ).stdout.strip()
        measured_commit = recorder.run(
            ["git", "rev-parse", "HEAD"], cwd=repo
        ).stdout.strip()
        recorder.run(["./gradlew", ":obfuscator:shadowJar"], cwd=repo)

        javac25 = args.javac_25 or shutil.which("javac25")
        tools: dict[int, Toolchain] = {}
        tools[8] = toolchain(recorder, repo, "javac")
        tools[17] = toolchain(recorder, repo, "javac")
        tools[21] = toolchain(recorder, repo, "javac")

        tests = repo / "obfuscator/test_data/tests"
        missing_trees: list[int] = []
        jobs: list[tuple[str, str, Path, int]] = [
            ("ClassicTest", fixture, tests / fixture, 8)
            for fixture in CLASSIC_FIXTURES
        ]
        for release in JDK_RELEASES:
            root = tests / f"jdk{release}"
            if not root.is_dir():
                missing_trees.append(release)
                continue
            fixtures = [
                path
                for path in sorted(root.iterdir())
                if path.is_dir() and any(path.rglob("*.java"))
            ]
            if release == 25:
                if fixtures and javac25 is None:
                    raise RuntimeError(
                        "jdk25 sources are present; pass --javac-25 with a JDK 25 compiler"
                    )
                if fixtures:
                    tools[25] = toolchain(recorder, repo, javac25)
            jobs.extend(
                (f"jdk{release}", path.name, path, release) for path in fixtures
            )

        obfuscator_jar = repo / "obfuscator/build/libs/obfuscator.jar"
        rows: list[dict[str, str]] = []
        metadata: list[dict[str, str | int]] = []
        for corpus, fixture, source_dir, release in jobs:
            print(f"\n### Measuring {corpus}: {fixture}")
            fixture_rows, fixture_metadata = measure_fixture(
                recorder,
                repo,
                work,
                corpus,
                fixture,
                source_dir,
                release,
                tools[release],
                obfuscator_jar,
            )
            rows.extend(fixture_rows)
            metadata.append(fixture_metadata)

        write_evidence(work, rows, metadata)
        missing = [row for row in rows if row["category"] == "missing"]
        if missing:
            raise RuntimeError(
                f"{len(missing)} inventory methods lacked an exact marker/log join"
            )
        command = ["python3", str(Path(__file__).relative_to(repo))]
        if args.javac_25:
            command.extend(["--javac-25", args.javac_25])
        if args.work_dir != parser.get_default("work_dir"):
            command.extend(["--work-dir", str(args.work_dir)])
        if args.report:
            command.extend(["--report", str(args.report)])
        render_report(
            report,
            repo,
            work,
            command,
            rows,
            metadata,
            tools,
            missing_trees,
            compiler_base,
            measured_commit,
            uname,
            java_version,
        )
        totals: dict[str, collections.Counter[str]] = collections.defaultdict(
            collections.Counter
        )
        for row in rows:
            totals[row["corpus"]]["inventory"] += 1
            totals[row["corpus"]][row["category"]] += 1
        print("\ncorpus\tinventory\tIR\tlegacy-fallback\tconstructor-left-java")
        for corpus in ("ClassicTest", "jdk17", "jdk21", "jdk25"):
            if corpus in totals:
                count = totals[corpus]
                print(
                    corpus,
                    count["inventory"],
                    count["IR"],
                    count["legacy-fallback"],
                    count["constructor-left-java"],
                    sep="\t",
                )
        print(f"\nGenerated {report}")
        return 0
    finally:
        recorder.close()


if __name__ == "__main__":
    sys.exit(main())
