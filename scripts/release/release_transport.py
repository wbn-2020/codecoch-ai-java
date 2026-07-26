from __future__ import annotations

import argparse
import base64
import contextlib
import dataclasses
import hashlib
import hmac
import ipaddress
import json
import os
import pathlib
import posixpath
import re
import stat
import sys
import uuid
from typing import Any, Iterator

from release_common import (
    MANIFEST_NAME,
    RELEASE_METADATA_NAME,
    ManifestEntry,
    parse_manifest_text,
    sha256_file,
    validate_release_id,
    verify_release,
)


HOST_LABEL_PATTERN = re.compile(r"^[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?$")
USER_PATTERN = re.compile(r"^[A-Za-z_][A-Za-z0-9._-]{0,31}$")
REMOTE_COMPONENT_PATTERN = re.compile(r"^[A-Za-z0-9._-]+$")
FINGERPRINT_PATTERN = re.compile(r"^SHA256:[A-Za-z0-9+/]{43}=?$")


def required_env(environment: dict[str, str], name: str) -> str:
    value = environment.get(name, "")
    if not value:
        raise ValueError(f"{name} is required")
    return value


def validate_host(value: str) -> str:
    if any(character.isspace() for character in value):
        raise ValueError("deployment host cannot contain whitespace")
    try:
        ipaddress.ip_address(value)
        return value
    except ValueError:
        pass

    if len(value) > 253:
        raise ValueError("deployment hostname is too long")
    labels = value.rstrip(".").split(".")
    if not labels or any(not HOST_LABEL_PATTERN.fullmatch(label) for label in labels):
        raise ValueError("deployment host is not a valid IP address or hostname")
    return value.rstrip(".")


def validate_remote_root(value: str) -> str:
    path = pathlib.PurePosixPath(value)
    if not path.is_absolute() or str(path) == "/":
        raise ValueError("remote root must be an absolute non-root POSIX path")
    if str(path) != value:
        raise ValueError("remote root must be normalized and cannot end with slash")
    for component in path.parts[1:]:
        if component in {".", ".."} or not REMOTE_COMPONENT_PATTERN.fullmatch(component):
            raise ValueError(f"unsafe remote path component: {component}")
    return str(path)


@dataclasses.dataclass(frozen=True)
class DeploymentConfig:
    host: str
    port: int
    username: str
    remote_root: str
    known_hosts: pathlib.Path
    expected_fingerprint: str
    password: str | None
    identity_file: pathlib.Path | None
    key_passphrase: str | None

    @classmethod
    def from_env(
        cls,
        environment: dict[str, str] | None = None,
    ) -> "DeploymentConfig":
        environment = dict(os.environ if environment is None else environment)
        host = validate_host(required_env(environment, "CODECOACHAI_DEPLOY_HOST"))
        raw_port = required_env(environment, "CODECOACHAI_DEPLOY_PORT")
        try:
            port = int(raw_port)
        except ValueError as exception:
            raise ValueError("CODECOACHAI_DEPLOY_PORT must be an integer") from exception
        if port < 1 or port > 65535:
            raise ValueError("CODECOACHAI_DEPLOY_PORT must be between 1 and 65535")

        username = required_env(environment, "CODECOACHAI_DEPLOY_USER")
        if not USER_PATTERN.fullmatch(username):
            raise ValueError("CODECOACHAI_DEPLOY_USER contains unsafe characters")

        remote_root = validate_remote_root(
            required_env(environment, "CODECOACHAI_REMOTE_ROOT")
        )
        known_hosts_input = pathlib.Path(
            required_env(environment, "CODECOACHAI_KNOWN_HOSTS")
        ).expanduser()
        if known_hosts_input.is_symlink():
            raise ValueError("CODECOACHAI_KNOWN_HOSTS must be a regular file")
        known_hosts = known_hosts_input.resolve(strict=True)
        if not known_hosts.is_file():
            raise ValueError("CODECOACHAI_KNOWN_HOSTS must be a regular file")

        fingerprint = required_env(
            environment,
            "CODECOACHAI_DEPLOY_HOST_FINGERPRINT",
        )
        if not FINGERPRINT_PATTERN.fullmatch(fingerprint):
            raise ValueError(
                "CODECOACHAI_DEPLOY_HOST_FINGERPRINT must use SHA256:<base64>"
            )

        password = environment.get("CODECOACHAI_DEPLOY_PASSWORD") or None
        identity_raw = environment.get("CODECOACHAI_DEPLOY_IDENTITY_FILE") or None
        if bool(password) == bool(identity_raw):
            raise ValueError(
                "set exactly one of CODECOACHAI_DEPLOY_PASSWORD or "
                "CODECOACHAI_DEPLOY_IDENTITY_FILE"
            )
        identity_file = None
        if identity_raw:
            identity_input = pathlib.Path(identity_raw).expanduser()
            if identity_input.is_symlink():
                raise ValueError(
                    "CODECOACHAI_DEPLOY_IDENTITY_FILE must be a regular file"
                )
            identity_file = identity_input.resolve(strict=True)
            if not identity_file.is_file():
                raise ValueError(
                    "CODECOACHAI_DEPLOY_IDENTITY_FILE must be a regular file"
                )

        return cls(
            host=host,
            port=port,
            username=username,
            remote_root=remote_root,
            known_hosts=known_hosts,
            expected_fingerprint=fingerprint,
            password=password,
            identity_file=identity_file,
            key_passphrase=environment.get("CODECOACHAI_DEPLOY_KEY_PASSPHRASE") or None,
        )

    def public_summary(self) -> dict[str, object]:
        return {
            "host": self.host,
            "port": self.port,
            "username": self.username,
            "remoteRoot": self.remote_root,
            "knownHosts": str(self.known_hosts),
            "expectedFingerprint": self.expected_fingerprint,
            "authentication": "identity-file" if self.identity_file else "password-env",
        }


def key_fingerprint(key: Any) -> str:
    digest = hashlib.sha256(key.asbytes()).digest()
    encoded = base64.b64encode(digest).decode("ascii").rstrip("=")
    return f"SHA256:{encoded}"


@contextlib.contextmanager
def connect(config: DeploymentConfig) -> Iterator[Any]:
    try:
        import paramiko
    except ImportError as exception:
        raise RuntimeError(
            "Paramiko is required; install scripts/release/requirements.txt"
        ) from exception

    client = paramiko.SSHClient()
    client.load_host_keys(str(config.known_hosts))
    client.set_missing_host_key_policy(paramiko.RejectPolicy())
    connect_arguments: dict[str, object] = {
        "hostname": config.host,
        "port": config.port,
        "username": config.username,
        "allow_agent": False,
        "look_for_keys": False,
        "timeout": 30,
        "banner_timeout": 30,
        "auth_timeout": 30,
    }
    if config.identity_file:
        connect_arguments["key_filename"] = str(config.identity_file)
        connect_arguments["passphrase"] = config.key_passphrase
    else:
        connect_arguments["password"] = config.password

    try:
        client.connect(**connect_arguments)
        transport = client.get_transport()
        if transport is None or not transport.is_active():
            raise RuntimeError("SSH transport is not active")
        actual_fingerprint = key_fingerprint(transport.get_remote_server_key())
        if not hmac.compare_digest(
            actual_fingerprint,
            config.expected_fingerprint,
        ):
            raise RuntimeError(
                "SSH host-key fingerprint mismatch: "
                f"expected {config.expected_fingerprint}, got {actual_fingerprint}"
            )
        yield client
    finally:
        client.close()


class RemoteReleaseManager:
    def __init__(self, sftp: Any, remote_root: str):
        self.sftp = sftp
        self.remote_root = validate_remote_root(remote_root)

    def _path(self, *parts: str) -> str:
        for part in parts:
            if not REMOTE_COMPONENT_PATTERN.fullmatch(part):
                raise ValueError(f"unsafe remote path component: {part}")
        return posixpath.join(self.remote_root, *parts)

    def _missing(self, path: str) -> bool:
        try:
            self.sftp.lstat(path)
            return False
        except OSError as exception:
            if getattr(exception, "errno", None) == 2:
                return True
            raise

    def _ensure_directory(self, path: str) -> None:
        pure_path = pathlib.PurePosixPath(path)
        current = "/"
        for component in pure_path.parts[1:]:
            current = posixpath.join(current, component)
            try:
                attributes = self.sftp.lstat(current)
            except OSError as exception:
                if getattr(exception, "errno", None) != 2:
                    raise
                self.sftp.mkdir(current, mode=0o755)
                continue
            if stat.S_ISLNK(attributes.st_mode) or not stat.S_ISDIR(attributes.st_mode):
                raise RuntimeError(f"remote path is not a real directory: {current}")

    def _remote_sha256(self, path: str) -> str:
        digest = hashlib.sha256()
        with self.sftp.open(path, "rb") as stream:
            for block in iter(lambda: stream.read(1024 * 1024), b""):
                digest.update(block)
        return digest.hexdigest()

    def _remove_tree(self, path: str) -> None:
        if self._missing(path):
            return
        attributes = self.sftp.lstat(path)
        if stat.S_ISDIR(attributes.st_mode) and not stat.S_ISLNK(attributes.st_mode):
            for child in self.sftp.listdir(path):
                self._remove_tree(posixpath.join(path, child))
            self.sftp.rmdir(path)
        else:
            self.sftp.remove(path)

    def _walk_files(self, root: str) -> set[str]:
        files: set[str] = set()

        def walk(path: str, relative: pathlib.PurePosixPath) -> None:
            for attributes in self.sftp.listdir_attr(path):
                child_path = posixpath.join(path, attributes.filename)
                child_relative = relative / attributes.filename
                if stat.S_ISLNK(attributes.st_mode):
                    raise RuntimeError(
                        f"remote release contains a symlink: {child_relative}"
                    )
                if stat.S_ISDIR(attributes.st_mode):
                    walk(child_path, child_relative)
                elif stat.S_ISREG(attributes.st_mode):
                    files.add(child_relative.as_posix())
                else:
                    raise RuntimeError(
                        f"remote release contains an unsupported entry: {child_relative}"
                    )

        walk(root, pathlib.PurePosixPath())
        return files

    def _remote_manifest(self, release_id: str) -> list[ManifestEntry]:
        release_path = self._path("releases", validate_release_id(release_id))
        manifest_path = posixpath.join(release_path, MANIFEST_NAME)
        with self.sftp.open(manifest_path, "rb") as stream:
            content = stream.read(1024 * 1024 + 1)
        if len(content) > 1024 * 1024:
            raise RuntimeError("remote release manifest exceeds 1 MiB")
        return parse_manifest_text(content.decode("ascii"))

    @contextlib.contextmanager
    def _pointer_lock(self) -> Iterator[None]:
        self._ensure_directory(self.remote_root)
        lock_path = self._path(".release-pointer.lock")
        try:
            self.sftp.mkdir(lock_path, mode=0o700)
        except OSError as exception:
            raise RuntimeError(
                "release pointer lock is already held; investigate a concurrent "
                "or interrupted activation before retrying"
            ) from exception
        try:
            yield
        finally:
            self.sftp.rmdir(lock_path)

    def verify_remote_release(self, release_id: str) -> list[ManifestEntry]:
        release_id = validate_release_id(release_id)
        release_path = self._path("releases", release_id)
        entries = self._remote_manifest(release_id)
        expected = {entry.relative_path for entry in entries} | {MANIFEST_NAME}
        actual = self._walk_files(release_path)
        if actual != expected:
            raise RuntimeError(
                "remote release file set mismatch; "
                f"missing={sorted(expected - actual)}, extra={sorted(actual - expected)}"
            )
        for entry in entries:
            remote_path = posixpath.join(release_path, entry.relative_path)
            if self._remote_sha256(remote_path) != entry.digest:
                raise RuntimeError(
                    f"remote SHA-256 mismatch: {entry.relative_path}"
                )
        metadata_path = posixpath.join(release_path, RELEASE_METADATA_NAME)
        with self.sftp.open(metadata_path, "rb") as stream:
            metadata_content = stream.read(1024 * 1024 + 1)
        if len(metadata_content) > 1024 * 1024:
            raise RuntimeError("remote release metadata exceeds 1 MiB")
        metadata = json.loads(metadata_content.decode("ascii"))
        if not isinstance(metadata, dict) or metadata.get("releaseId") != release_id:
            raise RuntimeError(
                "remote release directory does not match release.json releaseId"
            )
        return entries

    def upload(self, local_release: pathlib.Path) -> str:
        local_release = local_release.resolve(strict=True)
        entries = verify_release(local_release)
        metadata = json.loads(
            (local_release / RELEASE_METADATA_NAME).read_text(encoding="ascii")
        )
        release_id = validate_release_id(str(metadata.get("releaseId", "")))
        if local_release.name != release_id:
            raise ValueError(
                "local release directory name must match release.json releaseId"
            )

        self._ensure_directory(self.remote_root)
        incoming_root = self._path(".incoming")
        releases_root = self._path("releases")
        self._ensure_directory(incoming_root)
        self._ensure_directory(releases_root)
        final_path = self._path("releases", release_id)
        if not self._missing(final_path):
            remote_entries = self.verify_remote_release(release_id)
            if remote_entries != entries:
                raise RuntimeError(
                    "remote release ID already exists with different artifacts"
                )
            return release_id

        stage_name = f"{release_id}.{uuid.uuid4().hex}"
        stage_path = self._path(".incoming", stage_name)
        self.sftp.mkdir(stage_path, mode=0o755)
        local_files = sorted(
            (
                path
                for path in local_release.rglob("*")
                if path.is_file() and not path.is_symlink()
            ),
            key=lambda path: path.relative_to(local_release).as_posix(),
        )
        try:
            created_directories = {stage_path}
            for local_path in local_files:
                relative = local_path.relative_to(local_release)
                remote_parent = stage_path
                for component in relative.parts[:-1]:
                    remote_parent = posixpath.join(remote_parent, component)
                    if remote_parent not in created_directories:
                        self.sftp.mkdir(remote_parent, mode=0o755)
                        created_directories.add(remote_parent)
                remote_path = posixpath.join(stage_path, *relative.parts)
                temporary_path = f"{remote_path}.part-{uuid.uuid4().hex}"
                self.sftp.put(str(local_path), temporary_path, confirm=True)
                self.sftp.chmod(
                    temporary_path,
                    0o750 if local_path.suffix == ".sh" else 0o644,
                )
                self.sftp.rename(temporary_path, remote_path)
                if self._remote_sha256(remote_path) != sha256_file(local_path):
                    raise RuntimeError(
                        f"remote SHA-256 mismatch during upload: {relative.as_posix()}"
                    )

            manifest_entries = {
                entry.relative_path: entry.digest for entry in entries
            }
            for relative_path, expected_digest in manifest_entries.items():
                if self._remote_sha256(
                    posixpath.join(stage_path, relative_path)
                ) != expected_digest:
                    raise RuntimeError(
                        f"staged release SHA-256 mismatch: {relative_path}"
                    )
            self.sftp.rename(stage_path, final_path)
        except BaseException:
            self._remove_tree(stage_path)
            raise
        return release_id

    def _read_link(self, name: str) -> str | None:
        path = self._path(name)
        try:
            attributes = self.sftp.lstat(path)
        except OSError as exception:
            if getattr(exception, "errno", None) == 2:
                return None
            raise
        if not stat.S_ISLNK(attributes.st_mode):
            raise RuntimeError(f"remote release pointer is not a symlink: {path}")
        target = self.sftp.readlink(path)
        parts = pathlib.PurePosixPath(target).parts
        if len(parts) != 2 or parts[0] != "releases":
            raise RuntimeError(f"remote release pointer has an unsafe target: {target}")
        validate_release_id(parts[1])
        return target

    def _replace_link(self, name: str, target: str) -> None:
        parts = pathlib.PurePosixPath(target).parts
        if len(parts) != 2 or parts[0] != "releases":
            raise ValueError("release link target must be releases/<release-id>")
        validate_release_id(parts[1])
        link_path = self._path(name)
        temporary_path = self._path(f".{name}.{uuid.uuid4().hex}.tmp")
        self.sftp.symlink(target, temporary_path)
        try:
            self.sftp.posix_rename(temporary_path, link_path)
        except BaseException as exception:
            if not self._missing(temporary_path):
                self.sftp.remove(temporary_path)
            raise RuntimeError(
                "server SFTP must support the POSIX rename extension for atomic "
                "release pointer updates"
            ) from exception

    def activate(self, release_id: str) -> dict[str, str | None]:
        release_id = validate_release_id(release_id)
        with self._pointer_lock():
            self.verify_remote_release(release_id)
            new_target = f"releases/{release_id}"
            current_target = self._read_link("current")
            if current_target and current_target != new_target:
                self._replace_link("previous", current_target)
            self._replace_link("current", new_target)
            return {
                "current": new_target,
                "previous": self._read_link("previous"),
            }

    def rollback(self) -> dict[str, str | None]:
        with self._pointer_lock():
            current_target = self._read_link("current")
            previous_target = self._read_link("previous")
            if previous_target is None:
                raise RuntimeError("no previous release is available for rollback")
            previous_id = pathlib.PurePosixPath(previous_target).parts[1]
            self.verify_remote_release(previous_id)
            self._replace_link("current", previous_target)
            if current_target:
                self._replace_link("previous", current_target)
            return {
                "current": previous_target,
                "previous": current_target,
            }

    def status(self) -> dict[str, object]:
        releases_root = self._path("releases")
        releases: list[str] = []
        if not self._missing(releases_root):
            for name in self.sftp.listdir(releases_root):
                try:
                    releases.append(validate_release_id(name))
                except ValueError:
                    continue
        return {
            "current": self._read_link("current"),
            "previous": self._read_link("previous"),
            "releases": sorted(releases),
        }


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        description=(
            "Upload and switch immutable CodeCoachAI releases over host-key-pinned "
            "SFTP. No remote shell or SCP command is used."
        )
    )
    subparsers = parser.add_subparsers(dest="command", required=True)

    upload_parser = subparsers.add_parser("upload")
    upload_parser.add_argument("--release-dir", required=True, type=pathlib.Path)
    upload_parser.add_argument("--execute", action="store_true")
    upload_parser.add_argument("--confirm-release-id")

    activate_parser = subparsers.add_parser("activate")
    activate_parser.add_argument("--release-id", required=True)
    activate_parser.add_argument("--execute", action="store_true")
    activate_parser.add_argument("--confirm-release-id")

    rollback_parser = subparsers.add_parser("rollback")
    rollback_parser.add_argument("--execute", action="store_true")
    rollback_parser.add_argument("--confirm", choices=["ROLLBACK"])

    status_parser = subparsers.add_parser("status")
    status_parser.add_argument("--execute", action="store_true")
    return parser


def execute_remote(
    config: DeploymentConfig,
    arguments: argparse.Namespace,
) -> object:
    with connect(config) as client:
        with client.open_sftp() as sftp:
            manager = RemoteReleaseManager(sftp, config.remote_root)
            if arguments.command == "upload":
                return {"releaseId": manager.upload(arguments.release_dir)}
            if arguments.command == "activate":
                return manager.activate(arguments.release_id)
            if arguments.command == "rollback":
                return manager.rollback()
            if arguments.command == "status":
                return manager.status()
    raise AssertionError(f"unsupported command: {arguments.command}")


def main(argv: list[str] | None = None) -> int:
    parser = build_parser()
    arguments = parser.parse_args(argv)
    try:
        config = DeploymentConfig.from_env()
        plan: dict[str, object] = {
            "execute": arguments.execute,
            "command": arguments.command,
            "target": config.public_summary(),
        }
        if arguments.command == "upload":
            entries = verify_release(arguments.release_dir)
            metadata = json.loads(
                (arguments.release_dir / RELEASE_METADATA_NAME).read_text(
                    encoding="ascii"
                )
            )
            release_id = validate_release_id(str(metadata.get("releaseId", "")))
            plan.update({"releaseId": release_id, "fileCount": len(entries) + 1})
            if arguments.execute and arguments.confirm_release_id != release_id:
                raise ValueError(
                    "--confirm-release-id must exactly match the uploaded release ID"
                )
        elif arguments.command == "activate":
            release_id = validate_release_id(arguments.release_id)
            plan["releaseId"] = release_id
            if arguments.execute and arguments.confirm_release_id != release_id:
                raise ValueError(
                    "--confirm-release-id must exactly match the activated release ID"
                )
        elif arguments.command == "rollback" and arguments.execute:
            if arguments.confirm != "ROLLBACK":
                raise ValueError("--confirm ROLLBACK is required")

        if not arguments.execute:
            print(json.dumps(plan, indent=2, sort_keys=True))
            return 0

        result = execute_remote(config, arguments)
        print(json.dumps(result, indent=2, sort_keys=True))
        return 0
    except (OSError, RuntimeError, ValueError, json.JSONDecodeError) as exception:
        print(f"Release transport failed: {exception}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
