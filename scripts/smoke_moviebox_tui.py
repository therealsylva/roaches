#!/usr/bin/env python3
"""Drive the released MovieBox TUI through a real search in a pseudo-terminal."""

from __future__ import annotations

import fcntl
import os
import pty
import re
import select
import signal
import struct
import sys
import tempfile
import termios
import time


SEARCH_QUERY = "Dune"
SUCCESS_MARKERS = (
    "Dune: Part Two",
    "Dune Part Two",
    "Dune (2021)",
    "Dune (1984)",
    "Dune: Prophecy",
)
FAILURE_MARKERS = (
    "All hosts exhausted",
    "Search failed",
    "No results found",
    "Network error",
)


def sanitized_screen(raw: bytes) -> str:
    text = raw.decode("utf-8", "replace")
    text = re.sub(r"\x1b\[[0-?]*[ -/]*[@-~]", "\n", text)
    text = re.sub(r"\x1b\][^\x07]*(?:\x07|\x1b\\)", "", text)
    text = "".join(ch if ch in "\n\t" or ord(ch) >= 32 else " " for ch in text)
    lines = [line.strip() for line in text.splitlines() if line.strip()]
    return "\n".join(lines[-300:])


def main() -> int:
    if len(sys.argv) != 2:
        print("usage: smoke_moviebox_tui.py /path/to/moviebox-tui", file=sys.stderr)
        return 2

    binary = os.path.abspath(sys.argv[1])
    runtime = tempfile.TemporaryDirectory(prefix="moviebox-tui-smoke-")
    env = os.environ.copy()
    env.update(
        {
            "TERM": "xterm-256color",
            "XDG_CONFIG_HOME": os.path.join(runtime.name, "config"),
            "XDG_CACHE_HOME": os.path.join(runtime.name, "cache"),
            "XDG_DATA_HOME": os.path.join(runtime.name, "data"),
            "MOVIEBOX_NO_IMAGE": "1",
            "MOVIEBOX_LOG": "debug",
        }
    )

    pid, fd = pty.fork()
    if pid == 0:
        os.execve(binary, [binary], env)

    fcntl.ioctl(fd, termios.TIOCSWINSZ, struct.pack("HHHH", 42, 140, 0, 0))
    raw = bytearray()
    started = False
    query_sent = False
    deadline = time.monotonic() + 90
    query_at = 0.0
    result_seen_at = 0.0
    moved_to_movie = False
    details_opened = False
    audio_selected = False

    try:
        while time.monotonic() < deadline:
            ready, _, _ = select.select([fd], [], [], 0.25)
            if ready:
                try:
                    chunk = os.read(fd, 65536)
                except OSError:
                    break
                if not chunk:
                    break
                raw.extend(chunk)

            current = raw.decode("utf-8", "replace")
            if "Search movies and series" in current:
                started = True
            if started and not query_sent:
                os.write(fd, SEARCH_QUERY.encode() + b"\r")
                query_sent = True
                query_at = time.monotonic()

            if query_sent and time.monotonic() - query_at > 1:
                success = next((marker for marker in SUCCESS_MARKERS if marker in current), None)
                if success and not result_seen_at:
                    print(f"LIVE_SEARCH_OK marker={success!r}")
                    result_seen_at = time.monotonic()
                failure = next((marker for marker in FAILURE_MARKERS if marker in current), None)
                if failure:
                    print(f"LIVE_SEARCH_FAILED marker={failure!r}", file=sys.stderr)
                    return 1

            if result_seen_at and not moved_to_movie:
                os.write(fd, b"\x1b[B")
                moved_to_movie = True
            elif moved_to_movie and not details_opened and time.monotonic() - result_seen_at > 0.75:
                os.write(fd, b"\r")
                details_opened = True

            if details_opened:
                screen = sanitized_screen(bytes(raw))
                if not audio_selected and "Choose an audio track to load streams." in screen:
                    os.write(fd, b"\r")
                    audio_selected = True
                stream_match = re.search(r"Streams\s*·\s*([1-9][0-9]*) available", screen)
                if stream_match:
                    print(f"LIVE_RESOURCE_OK streams={stream_match.group(1)}")
                    return 0

        print("LIVE_RESOURCE_FAILED no verified stream list before timeout", file=sys.stderr)
        return 1
    finally:
        print("--- sanitized TUI capture ---")
        print(sanitized_screen(bytes(raw)))
        try:
            os.kill(pid, signal.SIGTERM)
        except ProcessLookupError:
            pass
        try:
            os.waitpid(pid, 0)
        except ChildProcessError:
            pass
        os.close(fd)
        runtime.cleanup()


if __name__ == "__main__":
    raise SystemExit(main())
