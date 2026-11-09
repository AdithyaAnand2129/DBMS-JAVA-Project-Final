#!/usr/bin/env python3
import glob
import os
import shutil
import subprocess
import sys

ROOT = os.path.dirname(os.path.abspath(__file__))
TARGET = os.path.join(ROOT, "target")
CLASSES_DIR = os.path.join(TARGET, "classes")
DEPENDENCY_DIR = os.path.join(TARGET, "dependency")


def find_executable(names):
    for name in names:
        path = shutil.which(name)
        if path:
            return path
    return None


def run_command(command):
    print("->", " ".join(command))
    subprocess.run(command, cwd=ROOT, check=True)


def build_project():
    mvn = find_executable(["mvn", "mvn.cmd"])
    if not mvn:
        raise RuntimeError("Maven is not installed or not on PATH. Install Maven and try again.")

    run_command([mvn, "-q", "-DskipTests", "package", "dependency:copy-dependencies"])


def build_classpath():
    if not os.path.isdir(CLASSES_DIR):
        build_project()

    jars = glob.glob(os.path.join(DEPENDENCY_DIR, "*.jar"))
    return os.pathsep.join([CLASSES_DIR] + jars)


def launch(mode):
    java = find_executable(["java", "java.exe"])
    if not java:
        raise RuntimeError("Java is not installed or not on PATH.")

    cp = build_classpath()

    if mode == "swing":
        cmd = [java, "-cp", cp, "roomwise.Main"]
    elif mode == "console":
        cmd = [java, "-cp", cp, "roomwise.Main", "--console"]
    else:
        raise ValueError(f"Unknown mode: {mode}")

    print(f"Starting RoomWise in {mode} mode...")
    subprocess.run(cmd, cwd=ROOT)


def main():
    mode = "swing"
    if len(sys.argv) > 1:
        mode = sys.argv[1].strip().lower()

    if mode not in {"swing", "console"}:
        print("Usage: python run_roomwise.py [swing|console]")
        print("Examples:")
        print("  python run_roomwise.py swing")
        print("  python run_roomwise.py console")
        sys.exit(1)

    try:
        launch(mode)
    except Exception as exc:
        print(f"Failed to start RoomWise: {exc}")
        print("Try: mvn -q -DskipTests package dependency:copy-dependencies")
        sys.exit(1)


if __name__ == "__main__":
    main()

