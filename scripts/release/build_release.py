from __future__ import annotations

import argparse
import datetime as dt
import json
import os
import pathlib
import shutil
import subprocess
import sys
import tempfile
import zipfile

from release_common import (
    MANIFEST_NAME,
    RELEASE_METADATA_NAME,
    SERVICE_MODULES,
    is_forbidden_archive,
    validate_release_id,
    verify_release,
    write_manifest,
)


SHA_PATTERN = r"^[0-9a-fA-F]{40,64}$"


def parse_args(argv: list[str] | None = None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Build an immutable CodeCoachAI release directory."
    )
    parser.add_argument("--backend-artifacts", required=True, type=pathlib.Path)
    parser.add_argument("--frontend-dist", required=True, type=pathlib.Path)
    parser.add_argument("--output-root", required=True, type=pathlib.Path)
    parser.add_argument("--release-id", required=True)
    parser.add_argument("--backend-repo", type=pathlib.Path)
    parser.add_argument("--frontend-repo", type=pathlib.Path)
    parser.add_argument("--backend-source-sha")
    parser.add_argument("--frontend-source-sha")
    parser.add_argument(
        "--allow-dirty",
        action="store_true",
        help="Allow a dirty Git source tree and record that fact in release.json.",
    )
    return parser.parse_args(argv)


def run_git(repo: pathlib.Path, *arguments: str) -> str:
    result = subprocess.run(
        ["git", "-C", str(repo), *arguments],
        check=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        text=True,
        encoding="utf-8",
        errors="replace",
    )
    return result.stdout.strip()


def source_metadata(
    repo: pathlib.Path | None,
    supplied_sha: str | None,
    allow_dirty: bool,
) -> dict[str, object]:
    sha = supplied_sha
    dirty = False
    if repo is not None:
        repo = repo.resolve(strict=True)
        if not (repo / ".git").exists():
            if not sha:
                raise ValueError(f"source SHA is required for non-Git path: {repo}")
        else:
            repository_sha = run_git(repo, "rev-parse", "HEAD")
            if sha and sha.lower() != repository_sha.lower():
                raise ValueError(
                    f"supplied source SHA does not match repository HEAD: {repo}"
                )
            sha = repository_sha
            dirty = bool(run_git(repo, "status", "--porcelain=v1", "--untracked-files=all"))
            if dirty and not allow_dirty:
                raise ValueError(f"source repository is dirty: {repo}")

    if not sha:
        raise ValueError("source SHA is required")
    import re

    if not re.fullmatch(SHA_PATTERN, sha):
        raise ValueError(f"invalid source SHA: {sha}")
    return {"sha": sha.lower(), "dirty": dirty}


def find_service_jar(root: pathlib.Path, service: str) -> pathlib.Path:
    candidates: list[pathlib.Path] = []
    for path in root.rglob("*.jar"):
        lowered = path.name.lower()
        if (
            path.is_symlink()
            or path.parent.name != "target"
            or service not in path.parts
            or lowered.endswith("-sources.jar")
            or lowered.endswith("-javadoc.jar")
            or lowered.endswith("-tests.jar")
            or lowered.startswith("original-")
        ):
            continue
        candidates.append(path)

    if len(candidates) != 1:
        rendered = ", ".join(str(path) for path in sorted(candidates))
        raise ValueError(
            f"expected exactly one deployable JAR for {service}; found "
            f"{len(candidates)}: {rendered}"
        )
    candidate = candidates[0]
    if not zipfile.is_zipfile(candidate):
        raise ValueError(f"service artifact is not a valid JAR/ZIP: {candidate}")
    return candidate


def copy_frontend_dist(source: pathlib.Path, target: pathlib.Path) -> list[str]:
    if not source.is_dir():
        raise ValueError(f"frontend dist directory is missing: {source}")
    index_path = source / "index.html"
    if not index_path.is_file() or index_path.is_symlink():
        raise ValueError("frontend dist must contain a regular index.html")

    excluded: list[str] = []
    for path in sorted(source.rglob("*")):
        if path.is_symlink():
            raise ValueError(f"frontend dist cannot contain symlinks: {path}")
        relative = path.relative_to(source)
        if path.is_dir():
            continue
        if not path.is_file():
            raise ValueError(f"unsupported frontend artifact: {path}")
        if is_forbidden_archive(relative):
            excluded.append(relative.as_posix())
            continue
        destination = target / relative
        destination.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(path, destination)
    return excluded


def release_timestamp() -> str:
    source_date_epoch = os.environ.get("SOURCE_DATE_EPOCH")
    if source_date_epoch:
        try:
            instant = dt.datetime.fromtimestamp(
                int(source_date_epoch),
                tz=dt.timezone.utc,
            )
        except (ValueError, OverflowError) as exception:
            raise ValueError("SOURCE_DATE_EPOCH must be a valid Unix timestamp") from exception
    else:
        instant = dt.datetime.now(tz=dt.timezone.utc)
    return instant.replace(microsecond=0).isoformat().replace("+00:00", "Z")


def build_release(arguments: argparse.Namespace) -> pathlib.Path:
    release_id = validate_release_id(arguments.release_id)
    backend_artifacts = arguments.backend_artifacts.resolve(strict=True)
    frontend_dist = arguments.frontend_dist.resolve(strict=True)
    output_root = arguments.output_root.resolve()
    output_root.mkdir(parents=True, exist_ok=True)
    final_path = output_root / release_id
    if final_path.exists():
        raise ValueError(f"release directory already exists: {final_path}")

    backend_source = source_metadata(
        arguments.backend_repo,
        arguments.backend_source_sha,
        arguments.allow_dirty,
    )
    frontend_source = source_metadata(
        arguments.frontend_repo,
        arguments.frontend_source_sha,
        arguments.allow_dirty,
    )

    stage_parent = pathlib.Path(
        tempfile.mkdtemp(prefix=f".{release_id}.", dir=output_root)
    )
    stage = stage_parent / release_id
    try:
        backend_target = stage / "backend"
        frontend_target = stage / "frontend"
        backend_target.mkdir(parents=True)
        frontend_target.mkdir(parents=True)

        artifacts: list[dict[str, object]] = []
        for service in SERVICE_MODULES:
            source_jar = find_service_jar(backend_artifacts, service)
            target_jar = backend_target / f"{service}.jar"
            shutil.copyfile(source_jar, target_jar)
            artifacts.append(
                {
                    "service": service,
                    "path": target_jar.relative_to(stage).as_posix(),
                    "bytes": target_jar.stat().st_size,
                }
            )

        excluded_frontend_archives = copy_frontend_dist(
            frontend_dist,
            frontend_target,
        )
        metadata = {
            "formatVersion": 1,
            "releaseId": release_id,
            "createdAt": release_timestamp(),
            "backendSource": backend_source,
            "frontendSource": frontend_source,
            "backendArtifacts": artifacts,
            "frontendPath": "frontend",
            "excludedFrontendArchives": excluded_frontend_archives,
            "manifest": MANIFEST_NAME,
            "remoteLayout": {
                "release": f"releases/{release_id}",
                "currentLink": "current",
                "previousLink": "previous",
            },
        }
        (stage / RELEASE_METADATA_NAME).write_text(
            json.dumps(metadata, ensure_ascii=True, indent=2, sort_keys=True) + "\n",
            encoding="ascii",
            newline="\n",
        )
        write_manifest(stage)
        verify_release(stage)
        stage.rename(final_path)
    except BaseException:
        shutil.rmtree(stage_parent, ignore_errors=True)
        raise
    else:
        shutil.rmtree(stage_parent, ignore_errors=True)
    return final_path


def main(argv: list[str] | None = None) -> int:
    try:
        release_path = build_release(parse_args(argv))
    except (OSError, ValueError, subprocess.SubprocessError) as exception:
        print(f"Release build failed: {exception}", file=sys.stderr)
        return 1
    print(release_path)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
