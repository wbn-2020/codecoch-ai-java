#!/usr/bin/env python3
"""Audit and safely publish Nacos configuration without deleting remote data."""

from __future__ import annotations

import argparse
import dataclasses
import datetime as dt
import hashlib
import json
import os
import pathlib
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
from typing import Any, Iterable


EXIT_OK = 0
EXIT_ERROR = 1
EXIT_DRIFT = 2
PUBLIC_NAMESPACE = "public"


class GuardError(RuntimeError):
    """Raised when a safety contract or Nacos API contract is violated."""


@dataclasses.dataclass(frozen=True)
class NamespaceTarget:
    label: str
    tenant: str


@dataclasses.dataclass(frozen=True)
class Digest:
    bytes: int
    md5: str
    sha256: str


@dataclasses.dataclass(frozen=True)
class RemoteConfig:
    data_id: str
    group: str
    tenant: str
    content: str
    config_type: str

    @property
    def digest(self) -> Digest:
        return digest_bytes(self.content.encode("utf-8"))


def utc_now() -> str:
    return dt.datetime.now(dt.timezone.utc).replace(microsecond=0).isoformat()


def digest_bytes(content: bytes) -> Digest:
    return Digest(
        bytes=len(content),
        md5=hashlib.md5(content).hexdigest(),
        sha256=hashlib.sha256(content).hexdigest(),
    )


def digest_dict(value: Digest | None) -> dict[str, Any] | None:
    return dataclasses.asdict(value) if value else None


def safe_component(value: str) -> str:
    return "".join(ch if ch.isalnum() or ch in "._-" else "_" for ch in value)


def set_private_permissions(path: pathlib.Path, directory: bool) -> None:
    try:
        path.chmod(0o700 if directory else 0o600)
    except OSError:
        # Windows ACLs are not represented by POSIX mode bits.
        pass


def write_private_text(path: pathlib.Path, content: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    set_private_permissions(path.parent, directory=True)
    path.write_text(content, encoding="utf-8", newline="")
    set_private_permissions(path, directory=False)


def write_json(path: pathlib.Path, value: dict[str, Any]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    set_private_permissions(path.parent, directory=True)
    temporary = path.with_suffix(path.suffix + ".tmp")
    temporary.write_text(
        json.dumps(value, ensure_ascii=False, indent=2, sort_keys=True) + "\n",
        encoding="utf-8",
        newline="\n",
    )
    set_private_permissions(temporary, directory=False)
    temporary.replace(path)
    set_private_permissions(path, directory=False)


class NacosClient:
    def __init__(
        self,
        address: str,
        timeout: float,
        username: str = "",
        password: str = "",
        access_token: str = "",
    ) -> None:
        normalized = address.rstrip("/")
        self.api_root = normalized if normalized.endswith("/nacos") else normalized + "/nacos"
        self.timeout = timeout
        self.username = username
        self.password = password
        self.access_token = access_token

    def authenticate(self) -> None:
        if self.access_token or not (self.username and self.password):
            return
        payload = self._request_json(
            "POST",
            "/v1/auth/users/login",
            form={"username": self.username, "password": self.password},
            include_token=False,
        )
        token = str(payload.get("accessToken") or "")
        if not token:
            raise GuardError("Nacos login succeeded without an accessToken")
        self.access_token = token

    def list_namespaces(self) -> list[dict[str, Any]]:
        payload = self._request_json("GET", "/v1/console/namespaces")
        data = payload.get("data")
        if not isinstance(data, list):
            raise GuardError("Nacos namespace response does not contain a data list")
        return [item for item in data if isinstance(item, dict)]

    def fetch_exact_config(
        self,
        data_id: str,
        group: str,
        tenant: str,
    ) -> RemoteConfig | None:
        # The ordinary GET endpoint aliases tenant=public to the built-in public
        # namespace on Nacos 2.3.1. The accurate search endpoint preserves the
        # literal tenant and is therefore required for an unambiguous readback.
        payload = self._request_json(
            "GET",
            "/v1/cs/configs",
            query={
                "search": "accurate",
                "dataId": data_id,
                "group": group,
                "tenant": tenant,
                "pageNo": "1",
                "pageSize": "10",
            },
        )
        page_items = payload.get("pageItems") or []
        matches = [
            item
            for item in page_items
            if isinstance(item, dict)
            and str(item.get("dataId") or "") == data_id
            and str(item.get("group") or "") == group
            and str(item.get("tenant") or "") == tenant
        ]
        if not matches:
            return None
        if len(matches) != 1:
            raise GuardError(
                f"Expected one exact Nacos config for {data_id}/{group}/{tenant!r}, "
                f"found {len(matches)}"
            )
        item = matches[0]
        content = item.get("content")
        if not isinstance(content, str):
            raise GuardError(
                f"Nacos exact search omitted content for {data_id}/{group}/{tenant!r}"
            )
        return RemoteConfig(
            data_id=data_id,
            group=group,
            tenant=tenant,
            content=content,
            config_type=str(item.get("type") or ""),
        )

    def publish_config(
        self,
        data_id: str,
        group: str,
        tenant: str,
        content: str,
        config_type: str,
        cas_md5: str | None,
    ) -> None:
        form = {
            "dataId": data_id,
            "group": group,
            "tenant": tenant,
            "content": content,
            "type": config_type,
        }
        headers = {"casMd5": cas_md5} if cas_md5 else None
        result = self._request_text(
            "POST",
            "/v1/cs/configs",
            form=form,
            extra_headers=headers,
        ).strip().lower()
        if result != "true":
            raise GuardError(
                f"Nacos rejected publish for {data_id}/{group}/{tenant!r}; "
                "the config may have changed concurrently"
            )

    def _request_json(
        self,
        method: str,
        path: str,
        query: dict[str, str] | None = None,
        form: dict[str, str] | None = None,
        include_token: bool = True,
        extra_headers: dict[str, str] | None = None,
    ) -> dict[str, Any]:
        text = self._request_text(
            method,
            path,
            query,
            form,
            include_token,
            extra_headers,
        )
        try:
            value = json.loads(text)
        except json.JSONDecodeError as exc:
            raise GuardError(f"Nacos returned invalid JSON for {method} {path}") from exc
        if not isinstance(value, dict):
            raise GuardError(f"Nacos returned non-object JSON for {method} {path}")
        return value

    def _request_text(
        self,
        method: str,
        path: str,
        query: dict[str, str] | None = None,
        form: dict[str, str] | None = None,
        include_token: bool = True,
        extra_headers: dict[str, str] | None = None,
    ) -> str:
        query_values = dict(query or {})
        form_values = None if form is None else dict(form)
        if include_token and self.access_token:
            query_values["accessToken"] = self.access_token
        url = self.api_root + path
        if query_values:
            url += "?" + urllib.parse.urlencode(query_values)
        body = None
        headers = {"Accept": "application/json, text/plain, */*"}
        headers.update(extra_headers or {})
        if form_values is not None:
            body = urllib.parse.urlencode(form_values).encode("utf-8")
            headers["Content-Type"] = "application/x-www-form-urlencoded; charset=UTF-8"
        request = urllib.request.Request(url, data=body, headers=headers, method=method)
        try:
            with urllib.request.urlopen(request, timeout=self.timeout) as response:
                return response.read().decode("utf-8")
        except urllib.error.HTTPError as exc:
            raise GuardError(f"Nacos HTTP {exc.code} for {method} {path}") from exc
        except urllib.error.URLError as exc:
            raise GuardError(f"Nacos request failed for {method} {path}: {exc.reason}") from exc


def namespace_id(item: dict[str, Any]) -> str:
    return str(item.get("namespace") or "")


def namespace_summary(namespaces: Iterable[dict[str, Any]]) -> list[dict[str, Any]]:
    return [
        {
            "id": namespace_id(item),
            "name": str(item.get("namespaceShowName") or ""),
            "type": item.get("type"),
        }
        for item in namespaces
    ]


def resolve_targets(
    selector: str,
    custom_namespace: str,
    namespaces: list[dict[str, Any]],
) -> list[NamespaceTarget]:
    ids = {namespace_id(item) for item in namespaces}
    has_builtin_public = "" in ids
    has_literal_public = PUBLIC_NAMESPACE in ids

    if selector == "auto":
        if has_literal_public:
            raise GuardError(
                "Ambiguous Nacos public namespace: both the built-in namespace "
                "(tenant='') and a literal namespace (tenant='public') exist. "
                "Use --target mirror-public for audited synchronization, or choose "
                "one target explicitly."
            )
        if not has_builtin_public:
            raise GuardError("The built-in public Nacos namespace was not reported")
        return [NamespaceTarget("builtin-public", "")]

    if selector == "builtin-public":
        if not has_builtin_public:
            raise GuardError("The built-in public Nacos namespace was not reported")
        return [NamespaceTarget("builtin-public", "")]

    if selector == "literal-public":
        if not has_literal_public:
            raise GuardError(
                "Literal namespace 'public' does not exist; refusing to create config "
                "under an undeclared namespace"
            )
        return [NamespaceTarget("literal-public", PUBLIC_NAMESPACE)]

    if selector == "mirror-public":
        if not has_builtin_public or not has_literal_public:
            raise GuardError(
                "mirror-public requires both tenant='' and a declared tenant='public'; "
                "refusing to create the duplicate namespace implicitly"
            )
        return [
            NamespaceTarget("builtin-public", ""),
            NamespaceTarget("literal-public", PUBLIC_NAMESPACE),
        ]

    if selector == "namespace":
        if not custom_namespace:
            raise GuardError("--namespace-id is required with --target namespace")
        if custom_namespace in {"", PUBLIC_NAMESPACE}:
            raise GuardError(
                "Use builtin-public or literal-public for the two public namespace forms"
            )
        if custom_namespace not in ids:
            raise GuardError(
                f"Namespace {custom_namespace!r} is not declared; refusing to create "
                "config under an undeclared namespace"
            )
        return [NamespaceTarget(f"namespace-{custom_namespace}", custom_namespace)]

    raise GuardError(f"Unsupported target selector: {selector}")


def discover_files(config_dir: pathlib.Path, data_ids: list[str]) -> list[pathlib.Path]:
    if not config_dir.is_dir():
        raise GuardError(f"Config directory does not exist: {config_dir}")
    if data_ids:
        unique_data_ids = list(dict.fromkeys(data_ids))
        invalid = [
            data_id
            for data_id in unique_data_ids
            if pathlib.Path(data_id).name != data_id
            or "/" in data_id
            or "\\" in data_id
        ]
        if invalid:
            raise GuardError(
                "Data IDs must be file names inside the config directory: "
                + ", ".join(invalid)
            )
        files = [config_dir / data_id for data_id in unique_data_ids]
        missing = [str(path) for path in files if not path.is_file()]
        if missing:
            raise GuardError("Missing config files: " + ", ".join(missing))
    else:
        files = sorted(config_dir.glob("*.yml"))
    resolved_dir = config_dir.resolve()
    escaped = [
        str(path)
        for path in files
        if not path.resolve().is_relative_to(resolved_dir)
    ]
    if escaped:
        raise GuardError(
            "Config files must resolve inside the config directory: "
            + ", ".join(escaped)
        )
    if not files:
        raise GuardError(f"No .yml config files found in {config_dir}")
    return files


def config_type_for(path: pathlib.Path) -> str:
    if path.suffix.lower() in {".yaml", ".yml"}:
        return "yaml"
    return "text"


def backup_path(
    audit_dir: pathlib.Path,
    phase: str,
    target: NamespaceTarget,
    group: str,
    data_id: str,
) -> pathlib.Path:
    return (
        audit_dir
        / phase
        / safe_component(target.label)
        / safe_component(group)
        / safe_component(data_id)
    )


def compare_entry(
    target: NamespaceTarget,
    path: pathlib.Path,
    group: str,
    local_bytes: bytes,
    remote: RemoteConfig | None,
) -> dict[str, Any]:
    local_digest = digest_bytes(local_bytes)
    remote_digest = remote.digest if remote else None
    matches = bool(
        remote
        and remote.content.encode("utf-8") == local_bytes
        and remote_digest
        and remote_digest.md5 == local_digest.md5
        and remote_digest.sha256 == local_digest.sha256
    )
    return {
        "target": target.label,
        "tenant": target.tenant,
        "group": group,
        "dataId": path.name,
        "local": digest_dict(local_digest),
        "remote": digest_dict(remote_digest),
        "remoteType": remote.config_type if remote else None,
        "status": "MATCH" if matches else ("MISSING" if remote is None else "DRIFT"),
    }


def poll_verified(
    client: NacosClient,
    data_id: str,
    group: str,
    target: NamespaceTarget,
    expected: bytes,
    verify_timeout: float,
) -> RemoteConfig:
    deadline = time.monotonic() + verify_timeout
    while True:
        remote = client.fetch_exact_config(data_id, group, target.tenant)
        if remote and remote.content.encode("utf-8") == expected:
            expected_digest = digest_bytes(expected)
            if (
                remote.digest.md5 == expected_digest.md5
                and remote.digest.sha256 == expected_digest.sha256
            ):
                return remote
        if time.monotonic() >= deadline:
            raise GuardError(
                f"Timed out verifying {data_id}/{group}/{target.tenant!r} "
                "through the exact namespace readback"
            )
        time.sleep(0.5)


def execute(args: argparse.Namespace, manifest: dict[str, Any]) -> int:
    client = NacosClient(
        address=args.nacos_addr,
        timeout=args.timeout,
        username=args.username,
        password=args.password,
        access_token=args.access_token,
    )
    client.authenticate()
    namespaces = client.list_namespaces()
    manifest["namespaces"] = namespace_summary(namespaces)
    manifest["duplicateLiteralPublic"] = any(
        namespace_id(item) == PUBLIC_NAMESPACE for item in namespaces
    )
    targets = resolve_targets(args.target, args.namespace_id, namespaces)
    manifest["targets"] = [dataclasses.asdict(target) for target in targets]

    config_dir = pathlib.Path(args.config_dir).resolve()
    files = discover_files(config_dir, args.data_id)
    manifest["configDir"] = str(config_dir)
    manifest["entries"] = []

    audit_dir = pathlib.Path(args.audit_dir).resolve() if args.audit_dir else None
    if args.command == "publish":
        if not args.confirm_write:
            raise GuardError("publish requires --confirm-write")
        if audit_dir is None:
            raise GuardError("publish requires --audit-dir for rollback evidence")
        audit_dir.mkdir(parents=True, exist_ok=True)
        set_private_permissions(audit_dir, directory=True)

    drift = False
    for target in targets:
        for path in files:
            local_bytes = path.read_bytes()
            try:
                local_bytes.decode("utf-8")
            except UnicodeDecodeError as exc:
                raise GuardError(f"Config is not valid UTF-8: {path}") from exc

            before = client.fetch_exact_config(path.name, args.group, target.tenant)
            entry = compare_entry(target, path, args.group, local_bytes, before)
            manifest["entries"].append(entry)
            if entry["status"] == "MATCH":
                entry["action"] = "SKIPPED"
                continue

            drift = True
            if args.command == "audit":
                entry["action"] = "NONE"
                continue

            if before is None and not args.allow_create_config:
                entry["action"] = "BLOCKED"
                raise GuardError(
                    f"{path.name}/{args.group}/{target.tenant!r} is missing; "
                    "use --allow-create-config only after confirming the target namespace"
                )

            if before is not None and audit_dir is not None:
                write_private_text(
                    backup_path(
                        audit_dir,
                        "before",
                        target,
                        args.group,
                        path.name,
                    ),
                    before.content,
                )

            client.publish_config(
                data_id=path.name,
                group=args.group,
                tenant=target.tenant,
                content=local_bytes.decode("utf-8"),
                config_type=config_type_for(path),
                cas_md5=before.digest.md5 if before else None,
            )
            verified = poll_verified(
                client,
                path.name,
                args.group,
                target,
                local_bytes,
                args.verify_timeout,
            )
            entry["action"] = "PUBLISHED"
            entry["verified"] = digest_dict(verified.digest)
            if audit_dir is not None:
                write_private_text(
                    backup_path(
                        audit_dir,
                        "after",
                        target,
                        args.group,
                        path.name,
                    ),
                    verified.content,
                )

    if args.command == "audit" and drift:
        manifest["result"] = "DRIFT"
        return EXIT_DRIFT
    manifest["result"] = "VERIFIED"
    return EXIT_OK


def default_config_dir() -> str:
    return str(pathlib.Path(__file__).resolve().parents[2] / "docs" / "nacos")


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        description=(
            "Audit or publish Nacos config with exact tenant readback, CAS, "
            "MD5/SHA-256 verification, and no delete operations."
        )
    )
    subparsers = parser.add_subparsers(dest="command", required=True)

    def add_common(subparser: argparse.ArgumentParser) -> None:
        subparser.add_argument(
            "--nacos-addr",
            default=os.environ.get("NACOS_ADDR", "http://127.0.0.1:8848"),
        )
        subparser.add_argument(
            "--group",
            default=os.environ.get("NACOS_GROUP", "DEFAULT_GROUP"),
        )
        subparser.add_argument("--config-dir", default=default_config_dir())
        subparser.add_argument(
            "--target",
            choices=[
                "auto",
                "builtin-public",
                "literal-public",
                "mirror-public",
                "namespace",
            ],
            default=os.environ.get("NACOS_TARGET", "auto"),
        )
        subparser.add_argument(
            "--namespace-id",
            default=os.environ.get("NACOS_NAMESPACE", ""),
        )
        subparser.add_argument("--data-id", action="append", default=[])
        subparser.add_argument(
            "--username",
            default=os.environ.get("NACOS_USERNAME", ""),
            help=argparse.SUPPRESS,
        )
        subparser.add_argument(
            "--password",
            default=os.environ.get("NACOS_PASSWORD", ""),
            help=argparse.SUPPRESS,
        )
        subparser.add_argument(
            "--access-token",
            default=os.environ.get("NACOS_ACCESS_TOKEN", ""),
            help=argparse.SUPPRESS,
        )
        subparser.add_argument("--timeout", type=float, default=10.0)
        subparser.add_argument(
            "--audit-dir",
            default=os.environ.get("NACOS_AUDIT_DIR", ""),
        )

    audit = subparsers.add_parser("audit", help="Read-only config comparison")
    add_common(audit)

    publish = subparsers.add_parser(
        "publish",
        help="CAS publish followed by exact tenant readback verification",
    )
    add_common(publish)
    publish.add_argument("--confirm-write", action="store_true")
    publish.add_argument("--allow-create-config", action="store_true")
    publish.add_argument("--verify-timeout", type=float, default=20.0)
    return parser


def main(argv: list[str] | None = None) -> int:
    parser = build_parser()
    args = parser.parse_args(argv)
    manifest: dict[str, Any] = {
        "schemaVersion": 1,
        "startedAt": utc_now(),
        "command": args.command,
        "nacosAddress": args.nacos_addr.rstrip("/"),
        "group": args.group,
        "targetSelector": args.target,
        "namespaceId": args.namespace_id,
    }
    audit_path = (
        pathlib.Path(args.audit_dir).resolve() / "manifest.json"
        if args.audit_dir
        else None
    )
    exit_code = EXIT_ERROR
    try:
        exit_code = execute(args, manifest)
    except GuardError as exc:
        manifest["result"] = "ERROR"
        manifest["error"] = str(exc)
        print(f"ERROR: {exc}", file=sys.stderr)
    finally:
        manifest["finishedAt"] = utc_now()
        if audit_path is not None:
            write_json(audit_path, manifest)

    entries = manifest.get("entries") or []
    for entry in entries:
        print(
            "{status:7} {target:16} {data_id} "
            "local_md5={local_md5} remote_md5={remote_md5} action={action}".format(
                status=entry.get("status", "UNKNOWN"),
                target=entry.get("target", "unknown"),
                data_id=entry.get("dataId", "unknown"),
                local_md5=(entry.get("local") or {}).get("md5", "-"),
                remote_md5=(entry.get("remote") or {}).get("md5", "-"),
                action=entry.get("action", "NONE"),
            )
        )
    print(
        f"RESULT {manifest.get('result', 'ERROR')} "
        f"entries={len(entries)} target={args.target}"
    )
    return exit_code


if __name__ == "__main__":
    raise SystemExit(main())
