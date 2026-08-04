from __future__ import annotations

import hashlib
import json
import pathlib
import re
from dataclasses import dataclass


SERVICE_MODULES = (
    "codecoachai-gateway",
    "codecoachai-core",
    "codecoachai-ai",
    "codecoachai-search",
)

MANIFEST_NAME = "SHA256SUMS"
RELEASE_METADATA_NAME = "release.json"
RELEASE_ID_PATTERN = re.compile(r"^[A-Za-z0-9][A-Za-z0-9._-]{0,63}$")
SHA256_PATTERN = re.compile(r"^[0-9a-f]{64}$")
FORBIDDEN_ARCHIVE_SUFFIXES = (".tar.gz", ".tgz")


@dataclass(frozen=True)
class ManifestEntry:
    digest: str
    relative_path: str


def validate_release_id(value: str) -> str:
    if not RELEASE_ID_PATTERN.fullmatch(value):
        raise ValueError(
            "release ID must use 1-64 ASCII letters, digits, dot, underscore, "
            "or hyphen and must start with a letter or digit"
        )
    if value in {".", ".."}:
        raise ValueError("release ID cannot be a relative path marker")
    return value


def is_forbidden_archive(path: pathlib.PurePath) -> bool:
    lowered = path.name.lower()
    return any(lowered.endswith(suffix) for suffix in FORBIDDEN_ARCHIVE_SUFFIXES)


def sha256_file(path: pathlib.Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def iter_release_files(root: pathlib.Path) -> list[pathlib.Path]:
    root = root.resolve(strict=True)
    files: list[pathlib.Path] = []
    for path in root.rglob("*"):
        if path.is_symlink():
            raise ValueError(f"release directories cannot contain symlinks: {path}")
        if path.is_dir():
            continue
        if not path.is_file():
            raise ValueError(f"unsupported release entry: {path}")
        relative = path.relative_to(root)
        if is_forbidden_archive(relative):
            raise ValueError(f"historical archive is forbidden in a release: {relative}")
        files.append(path)
    return sorted(files, key=lambda item: item.relative_to(root).as_posix())


def write_manifest(root: pathlib.Path) -> list[ManifestEntry]:
    root = root.resolve(strict=True)
    manifest_path = root / MANIFEST_NAME
    if manifest_path.exists():
        manifest_path.unlink()

    entries = [
        ManifestEntry(
            digest=sha256_file(path),
            relative_path=path.relative_to(root).as_posix(),
        )
        for path in iter_release_files(root)
    ]
    if not entries:
        raise ValueError("release directory contains no artifacts")

    content = "".join(
        f"{entry.digest}  {entry.relative_path}\n" for entry in entries
    )
    manifest_path.write_text(content, encoding="ascii", newline="\n")
    return entries


def read_manifest(root: pathlib.Path) -> list[ManifestEntry]:
    root = root.resolve(strict=True)
    manifest_path = root / MANIFEST_NAME
    if not manifest_path.is_file() or manifest_path.is_symlink():
        raise ValueError(f"release manifest is missing: {manifest_path}")

    return parse_manifest_text(manifest_path.read_text(encoding="ascii"))


def parse_manifest_text(content: str) -> list[ManifestEntry]:
    entries: list[ManifestEntry] = []
    seen: set[str] = set()
    for line_number, raw_line in enumerate(content.splitlines(), 1):
        if not raw_line:
            continue
        if len(raw_line) < 67 or raw_line[64:66] != "  ":
            raise ValueError(f"malformed manifest line {line_number}")
        digest = raw_line[:64]
        relative_path = raw_line[66:]
        if not SHA256_PATTERN.fullmatch(digest):
            raise ValueError(f"invalid SHA-256 on manifest line {line_number}")
        path = pathlib.PurePosixPath(relative_path)
        if (
            not relative_path
            or path.is_absolute()
            or ".." in path.parts
            or "." in path.parts
            or "\\" in relative_path
        ):
            raise ValueError(f"unsafe manifest path on line {line_number}")
        if relative_path == MANIFEST_NAME:
            raise ValueError("manifest cannot contain itself")
        if relative_path in seen:
            raise ValueError(f"duplicate manifest path: {relative_path}")
        if is_forbidden_archive(path):
            raise ValueError(f"historical archive is forbidden: {relative_path}")
        seen.add(relative_path)
        entries.append(ManifestEntry(digest, relative_path))

    if not entries:
        raise ValueError("release manifest contains no entries")
    return entries


def verify_release(root: pathlib.Path) -> list[ManifestEntry]:
    root = root.resolve(strict=True)
    entries = read_manifest(root)
    expected_paths = {entry.relative_path for entry in entries}
    actual_paths = {
        path.relative_to(root).as_posix()
        for path in iter_release_files(root)
        if path.relative_to(root).as_posix() != MANIFEST_NAME
    }
    if actual_paths != expected_paths:
        missing = sorted(expected_paths - actual_paths)
        extra = sorted(actual_paths - expected_paths)
        raise ValueError(
            f"release manifest file set mismatch; missing={missing}, extra={extra}"
        )

    for entry in entries:
        path = root.joinpath(*pathlib.PurePosixPath(entry.relative_path).parts)
        if sha256_file(path) != entry.digest:
            raise ValueError(f"SHA-256 mismatch: {entry.relative_path}")

    metadata_path = root / RELEASE_METADATA_NAME
    if RELEASE_METADATA_NAME not in expected_paths or not metadata_path.is_file():
        raise ValueError(f"release metadata is missing: {RELEASE_METADATA_NAME}")
    try:
        metadata = json.loads(metadata_path.read_text(encoding="ascii"))
    except (UnicodeDecodeError, json.JSONDecodeError) as exception:
        raise ValueError("release metadata is not valid ASCII JSON") from exception
    if not isinstance(metadata, dict):
        raise ValueError("release metadata must be a JSON object")
    release_id = validate_release_id(str(metadata.get("releaseId", "")))
    if root.name != release_id:
        raise ValueError(
            "release directory name must match release.json releaseId"
        )
    return entries
