#!/usr/bin/env python3
"""Rename LeiAndroidTemplate into a new Android app.

Example:
  python scripts/init_template.py --name 123PanX --package io.github.bileizhen.pan123x
"""
from __future__ import annotations
import argparse
import os
from pathlib import Path
import re
import shutil

ROOT = Path(__file__).resolve().parents[1]
OLD_PACKAGE = "io.github.bileizhen.leitemplate"
OLD_NAME = "Lei Android Template"
OLD_ROOT_NAME = "LeiAndroidTemplate"
OLD_APP_CLASS = "LeiTemplateApplication"

def kotlin_class_base(name: str) -> str:
    value = re.sub(r"[^A-Za-z0-9_]", "", name) or "App"
    if value[0].isdigit():
        value = "App" + value
    return value

def main() -> None:
    p = argparse.ArgumentParser()
    p.add_argument("--name", required=True, help="Display/project name, e.g. 123PanX")
    p.add_argument("--package", required=True, help="Package/applicationId, e.g. io.github.bileizhen.pan123x")
    args = p.parse_args()
    if not re.fullmatch(r"[A-Za-z_][A-Za-z0-9_]*(\.[A-Za-z_][A-Za-z0-9_]*)+", args.package):
        raise SystemExit("Invalid Java/Kotlin package name")

    app_class = kotlin_class_base(args.name) + "Application"
    replacements = {
        OLD_PACKAGE: args.package,
        OLD_NAME: args.name,
        OLD_ROOT_NAME: args.name,
        OLD_APP_CLASS: app_class,
    }
    text_ext = {".kt", ".kts", ".xml", ".md", ".properties", ".yml", ".yaml", ".txt"}
    ignored_dirs = {".git", ".gradle", ".idea", "build", "__pycache__"}
    for directory, dirs, files in os.walk(ROOT):
        dirs[:] = [name for name in dirs if name not in ignored_dirs]
        for name in files:
            path = Path(directory) / name
            if path.name == "local.properties":
                continue
            if path.suffix in text_ext or path.name in {"gradlew", ".gitignore"}:
                try:
                    text = path.read_text(encoding="utf-8")
                except UnicodeDecodeError:
                    continue
                new = text
                for old, value in replacements.items():
                    new = new.replace(old, value)
                if new != text:
                    path.write_text(new, encoding="utf-8")

    new_dir = ROOT / "app/src/main/java" / Path(*args.package.split("."))
    for source_set in (ROOT / "app/src").iterdir():
        for language in ("java", "kotlin"):
            java_root = source_set / language
            old_dir = java_root / Path(*OLD_PACKAGE.split("."))
            destination = java_root / Path(*args.package.split("."))
            if old_dir.exists() and old_dir != destination:
                destination.parent.mkdir(parents=True, exist_ok=True)
                # Move through a sibling directory so nested package names also work.
                staging = Path(shutil.move(str(old_dir), str(java_root / "_template_init_staging")))
                destination.parent.mkdir(parents=True, exist_ok=True)
                shutil.move(str(staging), str(destination))
                pth = old_dir.parent
                while pth != java_root and pth.exists() and not any(pth.iterdir()):
                    pth.rmdir(); pth = pth.parent

    old_app_file = new_dir / f"{OLD_APP_CLASS}.kt"
    if old_app_file.exists():
        old_app_file.rename(new_dir / f"{app_class}.kt")

    print(f"Initialized {args.name} ({args.package})")
    print(f"Application class: {app_class}")
    print("Next: replace launcher icons, update README, then run ./gradlew assembleDebug")

if __name__ == "__main__":
    main()
