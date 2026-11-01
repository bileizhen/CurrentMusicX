"""Verify branch product contract without compiling or contacting a server."""
from pathlib import Path
import hashlib
import unittest

ROOT = Path(__file__).resolve().parents[2]

class NativeContractTest(unittest.TestCase):
    def test_native_ui_and_pinned_local_algorithm_boundaries(self):
        source = ROOT / "app/src/main"
        # Hash-pinned local algorithms: Rhino transforms and the network-blocked AFP runtime.
        codecs = {
            "assets/netease-ai-master-codec.js": "0d2cbcdf9646422c62dbb1eedc7e146d19e1a0312c4f24c83d1df6c96ccc2137",
            "assets/recognition/afp.js": "3bdfca17680a7d2d49640322fe050a59fba5a59a32e430db021efccdd3034ba3",
            "assets/recognition/afp.wasm.js": "4926a6d69527a1afbc7f7d120b9c21947e677d80c484c8b65572fa7d4ce3b99f",
            "resources/lyrics-match/opencc-1.4.2-t2cn.js": "321fc24f32a4e250d70a874820de5fb2011400d7e265841782a0661f476c3765",
            "resources/lyrics-match/wanakana-5.3.1.min.js": "8e4442ae09b6e1ec4e3ca4eb483ba5eb46ed9d5cbb0bf7aac987ad5466d3c62a",
            "assets/netease-original/mood-codec.js": "bb756e69dffae120e355b00e3242d656922a0f2c82c7555e7abeb3a975a8059a",
        }
        web_terms = ("WebView", "JavascriptInterface", "evaluateJavascript", "HTMLAudioElement")
        fingerprint = "java/io/github/currencortex/music/feature/recognition/NeteaseFingerprint.kt"
        forbidden = ( "TODO()", "NotImplementedError", "fakeData", "LeiTemplateApplication", "LeiTemplateApp", "io.github.bileizhen.leitemplate")
        for p in source.rglob("*"):
            if p.is_file() and p.suffix in (".kt", ".xml", ".js", ".html", ".css"):
                if p.suffix == ".js":
                    self.assertIn(p.relative_to(source).as_posix(), codecs)
                    self.assertEqual(hashlib.sha256(p.read_bytes()).hexdigest(), codecs[p.relative_to(source).as_posix()])
                else:
                    self.assertNotIn(p.suffix, (".html", ".css"))
                text = p.read_text("utf-8")
                if p.suffix != ".js" and p.relative_to(source).as_posix() != fingerprint:
                    for term in web_terms:
                        self.assertNotIn(term, text, str(p))
                for term in forbidden:
                    self.assertNotIn(term, text, str(p))
        runtime = (source / fingerprint).read_text("utf-8")
        for guard in ("blockNetworkLoads = true", "allowFileAccess = false", "allowContentAccess = false",
                      "shouldOverrideUrlLoading", "shouldInterceptRequest", "https://currentmusic.invalid/"):
            self.assertIn(guard, runtime)
        self.assertTrue((source / "java/io/github/currencortex/music/CurrentMusicApplication.kt").exists())
        self.assertIn('rootProject.name = "CurrentMusic"', (ROOT / "settings.gradle.kts").read_text("utf-8"))
