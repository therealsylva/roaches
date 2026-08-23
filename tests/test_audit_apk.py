import json
import struct
import tempfile
import unittest
import zipfile
from pathlib import Path

from scripts.audit_apk import build_audit, dex_summary


class DexSummaryTests(unittest.TestCase):
    def test_flags_large_low_class_count_dex_as_packed(self):
        data = bytearray(10_000_001)
        data[0:8] = b"dex\n035\0"
        struct.pack_into("<I", data, 0x20, len(data))
        struct.pack_into("<I", data, 0x60, 4)

        result = dex_summary(bytes(data))

        self.assertTrue(result["valid_magic"])
        self.assertEqual(result["class_defs_size"], 4)
        self.assertTrue(result["suspiciously_packed"])


class AuditIntegrationTests(unittest.TestCase):
    def test_audits_zip_and_decoded_manifest(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            apk = root / "fixture.apk"
            manifest = root / "AndroidManifest.xml"
            signatures = root / "signatures.json"

            dex = bytearray(0x70)
            dex[0:8] = b"dex\n035\0"
            struct.pack_into("<I", dex, 0x20, len(dex))
            struct.pack_into("<I", dex, 0x60, 1)
            with zipfile.ZipFile(apk, "w") as archive:
                archive.writestr("classes.dex", dex)
                archive.writestr("assets/hisavana_config.json", "{}")

            manifest.write_text(
                """<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    package="example.app">
  <uses-permission android:name="com.google.android.gms.permission.AD_ID"/>
  <application android:name="example.App">
    <activity android:name="com.google.android.gms.ads.AdActivity"
        android:exported="false"/>
  </application>
</manifest>
""",
                encoding="utf-8",
            )
            signatures.write_text(
                json.dumps(
                    {
                        "advertising": [
                            "hisavana",
                            "com.google.android.gms.ads",
                            "permission.ad_id",
                        ]
                    }
                ),
                encoding="utf-8",
            )

            result = build_audit(apk, manifest, signatures)

            self.assertEqual(result["manifest"]["package"], "example.app")
            self.assertEqual(result["manifest"]["permission_count"], 1)
            self.assertIn(
                "advertising", result["manifest"]["signature_matches"]
            )
            self.assertIn(
                "advertising", result["apk"]["signature_matches_from_paths"]
            )


if __name__ == "__main__":
    unittest.main()
