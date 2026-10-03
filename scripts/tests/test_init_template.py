"""Exercise initialization in a disposable copy of the complete template."""
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]


class InitializationTest(unittest.TestCase):
    def test_source_sets_metadata_and_generated_files(self):
        for package in ("io.github.example.pan123x", "io.github.bileizhen.leitemplate.demo"):
            with self.subTest(package=package), tempfile.TemporaryDirectory() as directory:
                project = Path(directory) / "template"
                shutil.copytree(ROOT, project, ignore=shutil.ignore_patterns(
                    ".git", ".gradle", "build", "__pycache__", "local.properties"))
                generated = project / "app/build/generated/keep.txt"
                generated.parent.mkdir(parents=True)
                generated.write_text("LeiAndroidTemplate", encoding="utf-8")
                subprocess.run([sys.executable, str(project / "scripts/init_template.py"),
                                "--name", "123PanX", "--package", package], check=True)
                new_path = Path(*package.split("."))
                main = project / "app/src/main/java" / new_path
                test = project / "app/src/test/java" / new_path / "core/update/VersionTest.kt"
                self.assertTrue((main / "App123PanXApplication.kt").is_file())
                self.assertTrue(test.is_file())
                self.assertIn(f"package {package}.core.update", test.read_text("utf-8"))
                self.assertIn(f'applicationId = "{package}"',
                              (project / "app/build.gradle.kts").read_text("utf-8"))
                self.assertIn('GITHUB_REPO = "123PanX"',
                              (main / "core/config/AppMetadata.kt").read_text("utf-8"))
                self.assertIn('.App123PanXApplication',
                              (project / "app/src/main/AndroidManifest.xml").read_text("utf-8"))
                self.assertEqual("LeiAndroidTemplate", generated.read_text("utf-8"))
                self.assertTrue((project / "gradle/wrapper/gradle-wrapper.jar").is_file())


if __name__ == "__main__":
    unittest.main()
