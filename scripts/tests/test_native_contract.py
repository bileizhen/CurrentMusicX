"""Verify branch product contract without compiling or contacting a server."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]

class NativeContractTest(unittest.TestCase):
    def test_native_sources_have_no_web_bridge_or_placeholder_implementation(self):
        source = ROOT / "app/src/main"
        forbidden = ("WebView", "JavascriptInterface", "evaluateJavascript", "HTMLAudioElement", "TODO()", "NotImplementedError", "fakeData", "LeiTemplateApplication", "LeiTemplateApp", "io.github.bileizhen.leitemplate")
        for p in source.rglob("*"):
            if p.is_file() and p.suffix in (".kt", ".xml", ".js", ".html", ".css"):
                self.assertNotIn(p.suffix, (".js", ".html", ".css"))
                text = p.read_text("utf-8")
                for term in forbidden:
                    self.assertNotIn(term, text, str(p))
        self.assertTrue((source / "java/io/github/currencortex/music/CurrentMusicApplication.kt").exists())
        self.assertIn('rootProject.name = "CurrentMusic"', (ROOT / "settings.gradle.kts").read_text("utf-8"))
