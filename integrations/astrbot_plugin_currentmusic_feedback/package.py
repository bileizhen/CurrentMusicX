"""Build an installable ZIP from this repository, without data or credentials."""

import argparse
from pathlib import Path
from zipfile import ZIP_DEFLATED, ZipFile


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("output", type=Path)
    args = parser.parse_args()
    root = Path(__file__).resolve().parent
    files = ("__init__.py", "main.py", "core.py", "github.py", "metadata.yaml",
             "_conf_schema.json", "requirements.txt", "README.md")
    args.output.parent.mkdir(parents=True, exist_ok=True)
    with ZipFile(args.output, "w", ZIP_DEFLATED) as archive:
        for name in files:
            archive.write(root / name, root.name + "/" + name)
        archive.write(root.parents[1] / "LICENSE", root.name + "/LICENSE")
    with ZipFile(args.output) as archive:
        if archive.testzip() is not None:
            raise RuntimeError("ZIP verification failed")
    print(args.output.resolve())


if __name__ == "__main__":
    main()
