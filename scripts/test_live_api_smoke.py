import base64
import unittest
from unittest.mock import patch

import live_api_smoke as smoke


class RecoverySmokeTest(unittest.TestCase):
    def test_normalizes_all_supported_envelopes_and_rejects_malformed_lists(self):
        row = {"resourceId": "one"}
        for payload in ([row], {"list": [row]}, {"data": {"list": [row]}}, {"data": [row]}):
            self.assertEqual(smoke.resource_rows(payload), [row])
        self.assertEqual(smoke.resource_rows({"list": "broken"}), [])

    def test_hls_policy_does_not_block_recovery_with_a_fake_mpd(self):
        encoded = base64.urlsafe_b64encode(b"https://cdn.example.com/hls/title/").decode()
        self.assertIsNone(smoke.resolve_dash_manifest(f"Edge-Cache-Cookie=urlprefix={encoded}:sign=test"))

    def test_signs_decoded_nested_query_like_android_and_upstream(self):
        captured = []
        real_hmac = smoke.hmac.new

        def capture(key, message, digest):
            captured.append(message.decode())
            return real_hmac(key, message, digest)

        with patch.object(smoke.hmac, "new", side_effect=capture):
            smoke.signed_headers("GET", "https://api.example.com/path?failUrl=https%3A%2F%2Fcdn.example.com%2Fv%3Fx%3Da%2Bb%26t%3D1&resourceId=9", None, None)
        self.assertTrue(captured[0].endswith("/path?failUrl=https://cdn.example.com/v?x=a+b&t=1&resourceId=9"))

    def test_validates_target_before_accepting_recovery_and_uses_array_fallback(self):
        notice = "https://macdn.aoneroom.com/other/upgrade.mp4"
        original = {"resourceId": "one", "resourceLink": notice, "se": 2, "ep": 3}
        wrong = {**original, "ep": 1, "resourceLink": "https://cdn.example.com/wrong.mp4"}
        good = {**original, "resourceLink": "https://cdn.example.com/good.mp4"}
        responses = [({}, "token"), ({"list": []}, "token"), ([original], "token"), ({"data": {"list": [wrong, good]}}, "token")]
        with patch.object(smoke, "request", side_effect=responses), patch.object(smoke, "validate_stream_delivery") as verify:
            streams = smoke.resolve_playable_streams("show", 2, 3, "token")
        self.assertEqual(streams[0]["url"], good["resourceLink"])
        self.assertEqual(verify.call_count, 1)


if __name__ == "__main__":
    unittest.main()
