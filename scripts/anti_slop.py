#!/usr/bin/env python3
"""Fail on Roaches UI patterns that violate the repository design contract."""

from __future__ import annotations

import pathlib
import re
import sys


ROOT = pathlib.Path(__file__).resolve().parents[1]
SOURCE_ROOT = ROOT / "app" / "src" / "main"

BANNED_COPY = (
    "elevate your",
    "cinematic universe",
    "unlock premium",
    "seamless entertainment",
    "reimagined entertainment",
)

BANNED_DEPENDENCIES = (
    "firebase-analytics",
    "google-mobile-ads",
    "appsflyer",
    "adjust-android",
    "facebook-android-sdk",
    "amplitude",
    "mixpanel",
)

UI_FILE = re.compile(r"app/src/main/.+\.(?:kt|xml)$")
EMOJI = re.compile(
    "[\U0001F300-\U0001FAFF\U00002600-\U000026FF\U00002700-\U000027BF]"
)


def tracked_text_files() -> list[pathlib.Path]:
    roots = [ROOT / "app", ROOT / "build.gradle.kts", ROOT / "README.md"]
    files: list[pathlib.Path] = []
    for candidate in roots:
        if candidate.is_file():
            files.append(candidate)
        elif candidate.exists():
            files.extend(path for path in candidate.rglob("*") if path.is_file())
    return files


def main() -> int:
    failures: list[str] = []
    for path in tracked_text_files():
        try:
            text = path.read_text(encoding="utf-8")
        except (UnicodeDecodeError, OSError):
            continue
        relative = path.relative_to(ROOT).as_posix()
        lower = text.lower()

        for phrase in BANNED_COPY:
            if phrase in lower:
                failures.append(f"{relative}: prohibited filler copy: {phrase!r}")
        for dependency in BANNED_DEPENDENCIES:
            if dependency in lower:
                failures.append(f"{relative}: prohibited tracking/ad dependency: {dependency}")
        if UI_FILE.match(relative) and EMOJI.search(text):
            failures.append(f"{relative}: emoji is prohibited in application UI")
        if relative.endswith("Screen.kt") and "ElevatedCard(" in text:
            failures.append(f"{relative}: ElevatedCard is prohibited in feature screens")
        if relative.endswith("Screen.kt") and "Brush.linearGradient" in text:
            failures.append(f"{relative}: decorative gradient; use ArtworkScrim")

    if failures:
        print("Roaches UI constitution: FAIL")
        for failure in failures:
            print(f" - {failure}")
        return 1

    print("Roaches UI constitution: PASS")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
