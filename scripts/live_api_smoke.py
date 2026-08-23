#!/usr/bin/env python3
"""Exercise the same signed provider surface used by the Android client."""

from __future__ import annotations

import base64
import hashlib
import hmac
import json
import time
import urllib.error
import urllib.parse
import urllib.request
import uuid


HOSTS = (
    "https://api6.aoneroom.com",
    "https://api5.aoneroom.com",
    "https://api4.aoneroom.com",
    "https://api4sg.aoneroom.com",
    "https://api3.aoneroom.com",
    "https://api6sg.aoneroom.com",
    "https://api.inmoviebox.com",
)
SECRET = "76iRl07s0xSN9jqmEWAt79EBJZulIQIsV64FZr2O"
RETRYABLE = {403, 406, 407, 429, 500, 502, 503, 504}


def md5_hex(value: bytes) -> str:
    return hashlib.md5(value, usedforsecurity=False).hexdigest()


def signed_headers(method: str, url: str, body: bytes | None, token: str | None) -> dict[str, str]:
    timestamp = int(time.time() * 1000)
    parsed = urllib.parse.urlsplit(url)
    query = urllib.parse.parse_qsl(parsed.query, keep_blank_values=True)
    sorted_query = urllib.parse.urlencode(sorted(query), doseq=True)
    canonical_url = parsed.path + (f"?{sorted_query}" if sorted_query else "")
    limited = (body or b"")[:102_400]
    canonical = "\n".join(
        (
            method,
            "application/json",
            "application/json",
            str(len(body)) if body is not None else "",
            str(timestamp),
            md5_hex(limited) if body is not None else "",
            canonical_url,
        )
    )
    key = base64.b64decode(SECRET + "=" * ((4 - len(SECRET) % 4) % 4))
    signature = base64.b64encode(hmac.new(key, canonical.encode(), hashlib.md5).digest()).decode()
    identity = str(uuid.uuid4())
    headers = {
        "User-Agent": "com.community.oneroom/50020046 (Linux; U; Android 13; en_US; Roaches CI)",
        "Accept": "application/json",
        "Content-Type": "application/json",
        "X-Client-Token": f"{timestamp},{md5_hex(str(timestamp)[::-1].encode())}",
        "X-Tr-Signature": f"{timestamp}|2|{signature}",
        "X-Client-Status": "0",
        "X-Forwarded-For": "103.241.80.40",
        "X-Client-Info": json.dumps(
            {
                "package_name": "com.community.oneroom",
                "version_name": "3.0.03.0529.03",
                "version_code": 50020046,
                "os": "android",
                "os_version": "13",
                "device_id": identity.replace("-", ""),
                "gaid": identity,
                "region": "US",
                "X-Play-Mode": "2",
            },
            separators=(",", ":"),
        ),
    }
    if token:
        headers["Authorization"] = f"Bearer {token}"
    return headers


def request(method: str, path: str, payload: dict | None = None, token: str | None = None):
    body = json.dumps(payload, separators=(",", ":")).encode() if payload is not None else None
    last_error: Exception | None = None
    for host in HOSTS:
        url = host + path
        request = urllib.request.Request(url, data=body, method=method)
        for name, value in signed_headers(method, url, body, token).items():
            request.add_header(name, value)
        try:
            with urllib.request.urlopen(request, timeout=20) as response:
                user = response.headers.get("x-user")
                next_token = json.loads(user).get("token") if user else token
                decoded = json.load(response)
                return decoded.get("data", decoded), next_token
        except urllib.error.HTTPError as error:
            last_error = error
            if error.code not in RETRYABLE:
                raise
        except (OSError, ValueError) as error:
            last_error = error
    raise RuntimeError("all provider hosts failed") from last_error


def main() -> int:
    _, token = request("GET", "/wefeed-mobile-bff/tab-operating?page=1&tabId=0&version=")
    search, token = request(
        "POST",
        "/wefeed-mobile-bff/subject-api/search/v2",
        {"keyword": "Dune", "page": 1, "perPage": 20, "subjectType": "All", "tabId": "All"},
        token,
    )
    subjects = search.get("results", [{}])[0].get("subjects", [])
    dune = next((item for item in subjects if item.get("subjectId") and "dune" in item.get("title", "").lower()), None)
    if dune is None:
        raise RuntimeError("live search returned no Dune title")
    subject_id = urllib.parse.quote(dune["subjectId"])
    details, token = request("GET", f"/wefeed-mobile-bff/subject-api/get?subjectId={subject_id}", token=token)
    resources, _ = request(
        "GET",
        f"/wefeed-mobile-bff/subject-api/resource?subjectId={subject_id}&page=1&perPage=50",
        token=token,
    )
    streams = resources.get("list", resources if isinstance(resources, list) else [])
    if not details.get("title") or not any(item.get("resourceLink") for item in streams):
        raise RuntimeError("details or playable resources missing")
    qualities = sorted({int(item.get("resolution", 0)) for item in streams if int(item.get("resolution", 0)) > 0}, reverse=True)
    print(f"LIVE_ANDROID_API_OK title={details['title']!r} streams={len(streams)} qualities={qualities}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
