"""Build the Phon 4.1.1 Chinese patch using local dependencies only."""
from __future__ import annotations

import argparse
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import zipfile

ROOT = Path(__file__).resolve().parents[1]
TARGET_PHON = "4.1.1"
ZIP_TIME = (2026, 10, 30, 0, 0, 0)


def archive(path: Path, entries: dict[str, bytes]) -> None:
    with zipfile.ZipFile(path, "w", zipfile.ZIP_DEFLATED, compresslevel=9) as output:
        for name, data in sorted(entries.items()):
            info = zipfile.ZipInfo(name, ZIP_TIME)
            info.compress_type = zipfile.ZIP_DEFLATED
            info.create_system = 3
            info.external_attr = 0o100644 << 16
            output.writestr(info, data)


def run(command: list[str]) -> None:
    subprocess.run(command, cwd=ROOT, check=True)


def compiler(phon: Path, ecj: Path | None) -> list[str]:
    if ecj is not None:
        ecj = ecj.resolve(strict=True)
        return [str(phon / "jre/bin/java.exe"), "-jar", str(ecj), "-21"]
    javac = shutil.which("javac")
    if not javac and os.environ.get("JAVA_HOME"):
        candidate = Path(os.environ["JAVA_HOME"]) / "bin/javac.exe"
        if candidate.is_file():
            javac = str(candidate)
    if not javac:
        raise SystemExit("No javac found: install a JDK with Java 21 support, or pass --ecj PATH")
    return [javac, "--release", "21"]


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--phon-dir", type=Path, default=Path(r"C:\Program Files\Phon"))
    parser.add_argument("--ecj", type=Path, help="Path to a locally installed Eclipse ECJ compiler JAR")
    parser.add_argument("--test", action="store_true", help="Run the independent Swing control checks at four scales")
    args = parser.parse_args()
    phon = args.phon_dir.resolve(strict=True)
    java = phon / "jre/bin/java.exe"
    groovy = phon / "lib/groovy-4.0.30.jar"
    metadata = phon / f"lib/phon-metapackage-{TARGET_PHON}.jar"
    for dependency in (java, groovy, metadata):
        if not dependency.is_file():
            raise SystemExit(f"Missing required Phon {TARGET_PHON} dependency: {dependency.name}")
    with zipfile.ZipFile(metadata) as source:
        properties = source.read("phon.build.properties").decode("utf-8")
    if not any(line.startswith(f"phon.app.version={TARGET_PHON}+") or line == f"phon.app.version={TARGET_PHON}"
               for line in properties.splitlines()):
        raise SystemExit("Unsupported Phon version")

    build = ROOT / "build"
    classes = build / "classes"
    if classes.exists():
        if classes.resolve().parent != build.resolve():
            raise SystemExit("Unexpected class output directory")
        shutil.rmtree(classes)
    classes.mkdir(parents=True)
    compile_prefix = compiler(phon, args.ecj)
    run(compile_prefix + ["-encoding", "UTF-8", "-cp", str(groovy), "-d", str(classes)]
        + [str(path) for path in sorted((ROOT / "src").rglob("*.java"))])

    display = build / "phon-zh-display.jar"
    agent = build / "phon-zh-agent.jar"
    archive(display, {"phonzh/ChineseDisplay.class": (classes / "phonzh/ChineseDisplay.class").read_bytes()})
    agent_entries = {
        "META-INF/MANIFEST.MF": (
            "Manifest-Version: 1.0\r\nPremain-Class: phonzh.agent.ChineseAgent\r\n"
            "Can-Retransform-Classes: true\r\n\r\n"
        ).encode("ascii")
    }
    for path in sorted((classes / "phonzh/agent").rglob("*.class")):
        agent_entries[path.relative_to(classes).as_posix()] = path.read_bytes()
    license_entries = {"META-INF/LICENSE", "META-INF/NOTICE", "META-INF/licenses/asm-license.txt"}
    with zipfile.ZipFile(groovy) as source:
        for name in source.namelist():
            if (name.startswith("groovyjarjarasm/asm/") and name.endswith(".class")) or name in license_entries:
                agent_entries[name] = source.read(name)
        if not license_entries.issubset(agent_entries):
            raise SystemExit("Required third-party notices are missing")
    archive(agent, agent_entries)
    (build / "resources").mkdir(exist_ok=True)
    shutil.copyfile(ROOT / "resources/zh_CN.tsv", build / "resources/zh_CN.tsv")

    dictionary = {}
    for line in (ROOT / "resources/zh_CN.tsv").read_text(encoding="utf-8").splitlines():
        if not line.strip() or line.startswith("#"):
            continue
        key, value = line.split("\t", 1)
        if not key or not value or key in dictionary:
            raise SystemExit(f"Empty or duplicate dictionary entry: {key}")
        dictionary[key] = value

    if args.test:
        test_classes = build / "test-classes"
        test_classes.mkdir(exist_ok=True)
        run(compile_prefix + ["-encoding", "UTF-8", "-d", str(test_classes), str(ROOT / "tests/DisplayTest.java")])
        test_output = build / "control-tests"
        test_output.mkdir(exist_ok=True)
        results = []
        for scale in (1, 1.25, 1.5, 2):
            command = [str(java), f"-javaagent:{agent}", f"-Dsun.java2d.uiScale={scale}",
                       f"-Dtest.scale={scale}", "-Dtest.close=true", "-cp", str(test_classes),
                       "DisplayTest", str(test_output / f"controls-{scale}.png")]
            check = subprocess.run(command, cwd=ROOT, capture_output=True, text=True, encoding="utf-8", errors="replace")
            (test_output / f"controls-{scale}.log").write_text(check.stdout + check.stderr, encoding="utf-8")
            results.append({"scale": scale, "exit_code": check.returncode, "checks": check.stdout.splitlines()})
            if check.returncode:
                raise SystemExit(f"Control checks failed at {scale}: see build/control-tests")
        (test_output / "results.json").write_text(json.dumps(results, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        print(f"Independent control checks passed: {sum(len(row['checks']) for row in results)}")

    version = (ROOT / "VERSION").read_text(encoding="ascii").strip()
    dist = ROOT / "dist"
    dist.mkdir(exist_ok=True)
    package = dist / f"phon-zh-cn-{version}-phon{TARGET_PHON}-windows.zip"
    entries = {}
    files = [ROOT / name for name in ("README.md", "LICENSE", "THIRD_PARTY_NOTICES.md", "CHANGELOG.md",
             "VERSION", "Install-PhonChinese.ps1", "install.cmd", "restore-english.cmd")]
    for folder in ("src", "resources", "tools", "tests", "docs", "licenses"):
        files.extend(path for path in (ROOT / folder).rglob("*") if path.is_file() and "__pycache__" not in path.parts)
    for path in files:
        entries["phon-zh-cn/" + path.relative_to(ROOT).as_posix()] = path.read_bytes()
    entries["phon-zh-cn/phon-zh-agent.jar"] = agent.read_bytes()
    entries["phon-zh-cn/phon-zh-display.jar"] = display.read_bytes()
    archive(package, entries)
    with zipfile.ZipFile(package) as check:
        if check.testzip() is not None:
            raise SystemExit("Release archive failed its integrity check")
    digest = hashlib.sha256(package.read_bytes()).hexdigest()
    (dist / "SHA256SUMS.txt").write_text(f"{digest}  {package.name}\n", encoding="ascii")
    print(f"Dictionary entries: {len(dictionary)}")
    print(f"Package: {package.name}")
    print(f"SHA-256: {digest}")


if __name__ == "__main__":
    main()
