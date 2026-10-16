"""Verify branch product contract without compiling or contacting a server."""
from pathlib import Path
import hashlib
import unittest

ROOT = Path(__file__).resolve().parents[2]

class NativeContractTest(unittest.TestCase):
    def test_native_sources_have_no_web_bridge_or_placeholder_implementation(self):
        source = ROOT / "app/src/main"
        # Local, hash-pinned audio parameter algorithms run in Rhino, never a UI/browser.
        codecs = {
            "assets/netease-ai-master-codec.js": "0d2cbcdf9646422c62dbb1eedc7e146d19e1a0312c4f24c83d1df6c96ccc2137",
            "assets/netease-original/mood-codec.js": "bb756e69dffae120e355b00e3242d656922a0f2c82c7555e7abeb3a975a8059a",
        }
        forbidden = ("WebView", "JavascriptInterface", "evaluateJavascript", "HTMLAudioElement", "TODO()", "NotImplementedError", "fakeData", "LeiTemplateApplication", "LeiTemplateApp", "io.github.bileizhen.leitemplate")
        for p in source.rglob("*"):
            if p.is_file() and p.suffix in (".kt", ".xml", ".js", ".html", ".css"):
                if p.suffix == ".js":
                    self.assertIn(p.relative_to(source).as_posix(), codecs)
                    self.assertEqual(hashlib.sha256(p.read_bytes()).hexdigest(), codecs[p.relative_to(source).as_posix()])
                else:
                    self.assertNotIn(p.suffix, (".html", ".css"))
                text = p.read_text("utf-8")
                for term in forbidden:
                    self.assertNotIn(term, text, str(p))
        self.assertTrue((source / "java/io/github/currencortex/music/CurrentMusicApplication.kt").exists())
        self.assertIn('rootProject.name = "CurrentMusic"', (ROOT / "settings.gradle.kts").read_text("utf-8"))
