#!/usr/bin/env python3
"""Produce a deterministic static audit for an APK and decoded manifest."""

from __future__ import annotations

import argparse
import hashlib
import json
import struct
import xml.etree.ElementTree as ET
import zipfile
from collections import defaultdict
from pathlib import Path
from typing import Any, Iterable


ANDROID_URI = "http://schemas.android.com/apk/res/android"
ANDROID = f"{{{ANDROID_URI}}}"


def file_sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for block in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def dex_summary(data: bytes) -> dict[str, Any]:
    summary: dict[str, Any] = {"size_bytes": len(data), "valid_magic": False}
    if len(data) < 0x70 or not data.startswith(b"dex\n"):
        return summary

    summary["valid_magic"] = True
    summary["version"] = data[4:7].decode("ascii", errors="replace")
    fields = {
        "declared_file_size": 0x20,
        "string_ids_size": 0x38,
        "type_ids_size": 0x40,
        "proto_ids_size": 0x48,
        "field_ids_size": 0x50,
        "method_ids_size": 0x58,
        "class_defs_size": 0x60,
        "data_size": 0x68,
    }
    for name, offset in fields.items():
        summary[name] = struct.unpack_from("<I", data, offset)[0]

    class_count = int(summary["class_defs_size"])
    summary["suspiciously_packed"] = len(data) > 10_000_000 and class_count < 100
    return summary


def load_signatures(path: Path | None) -> dict[str, list[str]]:
    if path is None:
        return {}
    raw = json.loads(path.read_text(encoding="utf-8"))
    return {
        str(category): sorted({str(value).lower() for value in values})
        for category, values in raw.items()
    }


def match_signatures(
    values: Iterable[str], signatures: dict[str, list[str]]
) -> dict[str, list[str]]:
    normalized = sorted({value.lower() for value in values if value})
    matches: dict[str, set[str]] = defaultdict(set)
    for category, needles in signatures.items():
        for needle in needles:
            if any(needle in value for value in normalized):
                matches[category].add(needle)
    return {
        category: sorted(found)
        for category, found in sorted(matches.items())
        if found
    }


def manifest_summary(
    manifest_path: Path, signatures: dict[str, list[str]]
) -> dict[str, Any]:
    root = ET.parse(manifest_path).getroot()
    application = root.find("application")
    if application is None:
        raise ValueError("decoded manifest has no application element")

    permissions = sorted(
        {
            element.get(f"{ANDROID}name", "")
            for element in root.findall("uses-permission")
            if element.get(f"{ANDROID}name")
        }
    )

    components: list[dict[str, str]] = []
    searchable: list[str] = list(permissions)
    for kind in ("activity", "activity-alias", "service", "receiver", "provider"):
        for element in application.findall(kind):
            item = {
                "kind": kind,
                "name": element.get(f"{ANDROID}name", ""),
                "exported": element.get(f"{ANDROID}exported", ""),
                "process": element.get(f"{ANDROID}process", ""),
                "authorities": element.get(f"{ANDROID}authorities", ""),
            }
            components.append(item)
            searchable.extend(item.values())

    metadata: list[dict[str, str]] = []
    for parent in (root, application, *list(application)):
        for element in parent.findall("meta-data"):
            item = {
                "name": element.get(f"{ANDROID}name", ""),
                "value": element.get(f"{ANDROID}value", ""),
                "resource": element.get(f"{ANDROID}resource", ""),
            }
            metadata.append(item)
            searchable.extend(item.values())

    hosts = sorted(
        {
            element.get(f"{ANDROID}host", "")
            for element in root.iter("data")
            if element.get(f"{ANDROID}host")
        }
    )
    searchable.extend(hosts)

    app_attributes = {
        "name": application.get(f"{ANDROID}name", ""),
        "app_component_factory": application.get(
            f"{ANDROID}appComponentFactory", ""
        ),
        "allow_backup": application.get(f"{ANDROID}allowBackup", ""),
        "uses_cleartext_traffic": application.get(
            f"{ANDROID}usesCleartextTraffic", ""
        ),
        "network_security_config": application.get(
            f"{ANDROID}networkSecurityConfig", ""
        ),
    }
    searchable.extend(app_attributes.values())

    components.sort(key=lambda item: (item["kind"], item["name"]))
    metadata.sort(key=lambda item: (item["name"], item["value"], item["resource"]))
    return {
        "package": root.get("package", ""),
        "permissions": permissions,
        "permission_count": len(permissions),
        "application": app_attributes,
        "components": components,
        "component_count": len(components),
        "metadata": metadata,
        "deep_link_hosts": hosts,
        "signature_matches": match_signatures(searchable, signatures),
    }


def apk_summary(apk_path: Path, signatures: dict[str, list[str]]) -> dict[str, Any]:
    dex_files: dict[str, dict[str, Any]] = {}
    native_sizes: dict[str, int] = defaultdict(int)
    searchable: list[str] = []
    top_level_sizes: dict[str, int] = defaultdict(int)

    with zipfile.ZipFile(apk_path) as archive:
        entries = sorted(archive.infolist(), key=lambda item: item.filename)
        for entry in entries:
            searchable.append(entry.filename)
            top_level = entry.filename.split("/", 1)[0]
            top_level_sizes[top_level] += entry.file_size

            parts = entry.filename.split("/")
            if len(parts) >= 3 and parts[0] == "lib":
                native_sizes[parts[1]] += entry.file_size

            name = Path(entry.filename).name
            if name == "classes.dex" or (
                name.startswith("classes") and name.endswith(".dex")
            ):
                dex_files[entry.filename] = dex_summary(archive.read(entry))

        return {
            "path": apk_path.name,
            "size_bytes": apk_path.stat().st_size,
            "sha256": file_sha256(apk_path),
            "zip_entry_count": len(entries),
            "uncompressed_bytes_by_top_level": dict(sorted(top_level_sizes.items())),
            "native_uncompressed_bytes_by_abi": dict(sorted(native_sizes.items())),
            "dex_files": dex_files,
            "signature_matches_from_paths": match_signatures(searchable, signatures),
        }


def build_audit(
    apk_path: Path,
    manifest_path: Path | None,
    signatures_path: Path | None,
) -> dict[str, Any]:
    signatures = load_signatures(signatures_path)
    result: dict[str, Any] = {
        "schema_version": 1,
        "apk": apk_summary(apk_path, signatures),
    }
    if manifest_path is not None:
        result["manifest"] = manifest_summary(manifest_path, signatures)
    return result


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--apk", required=True, type=Path)
    parser.add_argument("--manifest", type=Path)
    parser.add_argument("--signatures", type=Path)
    parser.add_argument("--output", type=Path)
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    audit = build_audit(args.apk, args.manifest, args.signatures)
    rendered = json.dumps(audit, indent=2, sort_keys=True) + "\n"
    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(rendered, encoding="utf-8")
    else:
        print(rendered, end="")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
