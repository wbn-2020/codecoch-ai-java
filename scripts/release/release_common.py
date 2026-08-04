from __future__ import annotations

import hashlib
import json
import pathlib
import re
import tarfile
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
RUNTIME_IMAGE_REPOSITORY = "codecoachai/runtime-base"
RUNTIME_IMAGE_NAME = "codecoachai-runtime-base.tar"
MAX_DOCKER_MANIFEST_BYTES = 1024 * 1024
RELEASE_FORMAT_VERSION = 2


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


def validate_runtime_image_archive(
    path: pathlib.Path,
    expected_tag: str,
) -> pathlib.Path:
    candidate = path.resolve(strict=True)
    if not candidate.is_file() or candidate.is_symlink():
        raise ValueError(f"runtime image must be a regular file: {candidate}")

    expected_reference = f"{RUNTIME_IMAGE_REPOSITORY}:{validate_release_id(expected_tag)}"
    try:
        with tarfile.open(candidate, "r:*") as archive:
            members = archive.getmembers()
            member_by_name: dict[str, tarfile.TarInfo] = {}
            for member in members:
                member_path = pathlib.PurePosixPath(member.name)
                if (
                    not member.name
                    or member_path.is_absolute()
                    or ".." in member_path.parts
                    or "." in member_path.parts
                    or "\\" in member.name
                ):
                    raise ValueError(
                        f"runtime image contains an unsafe archive path: {member.name}"
                    )
                if member.issym() or member.islnk() or member.isdev():
                    raise ValueError(
                        f"runtime image contains an unsupported archive entry: {member.name}"
                    )
                if member.name in member_by_name:
                    raise ValueError(
                        f"runtime image contains a duplicate archive path: {member.name}"
                    )
                member_by_name[member.name] = member

            manifest_member = member_by_name.get("manifest.json")
            if manifest_member is None or not manifest_member.isfile():
                raise ValueError("runtime image Docker manifest.json is missing")
            if manifest_member.size > MAX_DOCKER_MANIFEST_BYTES:
                raise ValueError("runtime image Docker manifest.json exceeds 1 MiB")
            manifest_stream = archive.extractfile(manifest_member)
            if manifest_stream is None:
                raise ValueError("runtime image Docker manifest.json cannot be read")
            try:
                manifest = json.loads(manifest_stream.read().decode("utf-8"))
            except (UnicodeDecodeError, json.JSONDecodeError) as exception:
                raise ValueError(
                    "runtime image Docker manifest.json is invalid"
                ) from exception

            if not isinstance(manifest, list) or len(manifest) != 1:
                raise ValueError("runtime image must contain exactly one Docker image")
            image = manifest[0]
            if not isinstance(image, dict):
                raise ValueError("runtime image Docker manifest entry is invalid")
            if image.get("RepoTags") != [expected_reference]:
                raise ValueError(
                    f"runtime image must contain exactly tag {expected_reference}"
                )
            config = image.get("Config")
            layers = image.get("Layers")
            if not isinstance(config, str) or not config:
                raise ValueError("runtime image Docker config path is invalid")
            if not isinstance(layers, list) or not layers or not all(
                isinstance(layer, str) and layer for layer in layers
            ):
                raise ValueError("runtime image Docker layer list is invalid")
            for referenced_path in [config, *layers]:
                referenced = member_by_name.get(referenced_path)
                if referenced is None or not referenced.isfile():
                    raise ValueError(
                        "runtime image Docker manifest references a missing file: "
                        f"{referenced_path}"
                    )
    except tarfile.TarError as exception:
        raise ValueError(f"runtime image is not a valid Docker save tar: {candidate}") from exception
    return candidate


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
    if metadata.get("formatVersion") != RELEASE_FORMAT_VERSION:
        raise ValueError(
            f"release metadata formatVersion must be {RELEASE_FORMAT_VERSION}"
        )
    release_id = validate_release_id(str(metadata.get("releaseId", "")))
    if root.name != release_id:
        raise ValueError(
            "release directory name must match release.json releaseId"
        )
    runtime_image = metadata.get("runtimeImage")
    if not isinstance(runtime_image, dict):
        raise ValueError("release metadata runtimeImage must be an object")
    expected_runtime_path = f"runtime/{RUNTIME_IMAGE_NAME}"
    if runtime_image.get("path") != expected_runtime_path:
        raise ValueError("release metadata runtime image path is invalid")
    if runtime_image.get("repository") != RUNTIME_IMAGE_REPOSITORY:
        raise ValueError("release metadata runtime image repository is invalid")
    if runtime_image.get("tag") != release_id:
        raise ValueError("release metadata runtime image tag must match release ID")
    if expected_runtime_path not in expected_paths:
        raise ValueError("release runtime image is missing from the manifest")
    runtime_image_path = root.joinpath(
        *pathlib.PurePosixPath(expected_runtime_path).parts
    )
    if runtime_image.get("bytes") != runtime_image_path.stat().st_size:
        raise ValueError("release metadata runtime image size is invalid")
    validate_runtime_image_archive(runtime_image_path, release_id)

    control_bundle = metadata.get("controlBundle")
    if not isinstance(control_bundle, dict) or control_bundle.get("path") != "control":
        raise ValueError("release metadata controlBundle is invalid")
    required_control_paths = {
        "control/docker-compose.yml",
        "control/docker-compose.release.yml",
        "control/Dockerfile",
        "control/docs/operations/release-engineering-runbook.md",
        "control/scripts/docker/nacos-config-init.sh",
        "control/scripts/docker/entrypoint.sh",
        "control/scripts/docker/HealthProbe.java",
        "control/scripts/nacos/nacos_config_guard.py",
        "control/scripts/release/check_health.py",
        "control/scripts/release/flyway-pom.xml",
        "control/scripts/release/release.env.example",
        "control/docs/nacos/codecoachai-common-dev.yml",
        "control/docs/nacos/codecoachai-redis-dev.yml",
        "control/docs/nacos/codecoachai-gateway-dev.yml",
        "control/docs/nacos/codecoachai-core-dev.yml",
        "control/docs/nacos/codecoachai-ai-dev.yml",
        "control/docs/nacos/codecoachai-search-dev.yml",
    }
    missing_control_paths = sorted(required_control_paths - expected_paths)
    if missing_control_paths:
        raise ValueError(
            f"release control bundle is incomplete: {missing_control_paths}"
        )
    migration_paths = {
        path
        for path in expected_paths
        if path.startswith("control/sql/migration/") and path.endswith(".sql")
    }
    if not migration_paths:
        raise ValueError("release control bundle contains no SQL migrations")
    backend_source = metadata.get("backendSource")
    if (
        not isinstance(backend_source, dict)
        or control_bundle.get("backendSourceSha") != backend_source.get("sha")
    ):
        raise ValueError("release control bundle source SHA is invalid")
    return entries
