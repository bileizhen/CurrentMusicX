"""Ensure shipped offline notices match repository source distribution."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]


class LegalAssetsTest(unittest.TestCase):
    def test_license_and_notices_are_synchronized(self):
        assets = ROOT / "app/src/main/assets/legal"
        self.assertEqual((ROOT / "LICENSE").read_text("utf-8"),
                         (assets / "GPL-3.0.txt").read_text("utf-8"))
        self.assertEqual((ROOT / "THIRD_PARTY_NOTICES.md").read_text("utf-8"),
                         (assets / "NOTICES.md").read_text("utf-8"))
        self.assertIn("Apache License", (assets / "APACHE-2.0.txt").read_text("utf-8"))
        self.assertIn("GNU LESSER GENERAL PUBLIC LICENSE", (assets / "LGPL-3.0.txt").read_text("utf-8"))
        self.assertIn("Jaudiotagger 3.0.1", (assets / "NOTICES.md").read_text("utf-8"))
        self.assertEqual((ROOT / "PRIVACY.md").read_text("utf-8"), (assets / "PRIVACY.md").read_text("utf-8"))
        self.assertIn("Copyright (c) 2026 Rcst20", (assets / "NOTICES.md").read_text("utf-8"))
