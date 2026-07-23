from __future__ import annotations

import contextlib
import hashlib
import importlib.util
import io
import json
import pathlib
import sys
import tempfile
import threading
import unittest
import urllib.parse
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer


GUARD_PATH = pathlib.Path(__file__).resolve().parents[1] / "nacos_config_guard.py"
SPEC = importlib.util.spec_from_file_location("nacos_config_guard", GUARD_PATH)
assert SPEC and SPEC.loader
guard = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = guard
SPEC.loader.exec_module(guard)


class FakeNacosState:
    def __init__(self) -> None:
        self.namespaces = [
            {"namespace": "", "namespaceShowName": "public", "type": 0},
            {"namespace": "public", "namespaceShowName": "public", "type": 2},
        ]
        self.configs: dict[tuple[str, str, str], dict[str, str]] = {}
        self.requests: list[dict[str, object]] = []
        self.force_cas_failure = False


class FakeNacosHandler(BaseHTTPRequestHandler):
    server: "FakeNacosServer"

    def do_GET(self) -> None:
        parsed = urllib.parse.urlparse(self.path)
        query = urllib.parse.parse_qs(parsed.query, keep_blank_values=True)
        self.server.state.requests.append(
            {"method": "GET", "path": parsed.path, "query": query}
        )
        if parsed.path == "/nacos/v1/console/namespaces":
            self._json({"code": 200, "data": self.server.state.namespaces})
            return
        if parsed.path == "/nacos/v1/cs/configs" and query.get("search") == ["accurate"]:
            tenant = query.get("tenant", [""])[0]
            group = query.get("group", [""])[0]
            data_id = query.get("dataId", [""])[0]
            record = self.server.state.configs.get((tenant, group, data_id))
            page_items = []
            if record:
                page_items.append(
                    {
                        "dataId": data_id,
                        "group": group,
                        "tenant": tenant,
                        "content": record["content"],
                        "type": record["type"],
                    }
                )
            self._json({"totalCount": len(page_items), "pageItems": page_items})
            return
        self.send_error(404)

    def do_POST(self) -> None:
        parsed = urllib.parse.urlparse(self.path)
        length = int(self.headers.get("Content-Length", "0"))
        form = urllib.parse.parse_qs(
            self.rfile.read(length).decode("utf-8"),
            keep_blank_values=True,
        )
        flat_form = {key: values[0] for key, values in form.items()}
        cas_md5 = self.headers.get("casMd5")
        self.server.state.requests.append(
            {
                "method": "POST",
                "path": parsed.path,
                "form": flat_form,
                "casMd5Header": cas_md5,
            }
        )
        if parsed.path == "/nacos/v1/auth/users/login":
            self._json({"accessToken": "test-token"})
            return
        if parsed.path == "/nacos/v1/cs/configs":
            tenant = flat_form.get("tenant", "")
            group = flat_form.get("group", "")
            data_id = flat_form.get("dataId", "")
            key = (tenant, group, data_id)
            before = self.server.state.configs.get(key)
            expected_cas = (
                hashlib.md5(before["content"].encode("utf-8")).hexdigest()
                if before
                else None
            )
            supplied_cas = cas_md5
            if self.server.state.force_cas_failure or (
                expected_cas and supplied_cas != expected_cas
            ):
                self._text("false")
                return
            self.server.state.configs[key] = {
                "content": flat_form.get("content", ""),
                "type": flat_form.get("type", ""),
            }
            self._text("true")
            return
        self.send_error(404)

    def do_DELETE(self) -> None:
        self.server.state.requests.append({"method": "DELETE", "path": self.path})
        self.send_error(405)

    def log_message(self, format: str, *args: object) -> None:
        return

    def _json(self, value: object) -> None:
        content = json.dumps(value).encode("utf-8")
        self.send_response(200)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(content)))
        self.end_headers()
        self.wfile.write(content)

    def _text(self, value: str) -> None:
        content = value.encode("utf-8")
        self.send_response(200)
        self.send_header("Content-Type", "text/plain")
        self.send_header("Content-Length", str(len(content)))
        self.end_headers()
        self.wfile.write(content)


class FakeNacosServer(ThreadingHTTPServer):
    def __init__(self, state: FakeNacosState) -> None:
        super().__init__(("127.0.0.1", 0), FakeNacosHandler)
        self.state = state


class RunningFakeNacos:
    def __init__(self, state: FakeNacosState) -> None:
        self.server = FakeNacosServer(state)
        self.thread = threading.Thread(target=self.server.serve_forever, daemon=True)

    def __enter__(self) -> str:
        self.thread.start()
        host, port = self.server.server_address
        return f"http://{host}:{port}"

    def __exit__(self, exc_type, exc, traceback) -> None:
        self.server.shutdown()
        self.server.server_close()
        self.thread.join(timeout=5)


class NacosConfigGuardTest(unittest.TestCase):
    group = "DEFAULT_GROUP"
    data_id = "codecoachai-gateway-dev.yml"

    def run_guard(self, arguments: list[str]) -> tuple[int, str, str]:
        stdout = io.StringIO()
        stderr = io.StringIO()
        with contextlib.redirect_stdout(stdout), contextlib.redirect_stderr(stderr):
            code = guard.main(arguments)
        return code, stdout.getvalue(), stderr.getvalue()

    def create_config_dir(self, root: pathlib.Path, content: str) -> pathlib.Path:
        config_dir = root / "config"
        config_dir.mkdir()
        (config_dir / self.data_id).write_bytes(content.encode("utf-8"))
        return config_dir

    def test_auto_target_blocks_duplicate_public_without_writes(self) -> None:
        state = FakeNacosState()
        with tempfile.TemporaryDirectory() as temporary:
            root = pathlib.Path(temporary)
            config_dir = self.create_config_dir(root, "server:\n  port: 8080\n")
            with RunningFakeNacos(state) as address:
                code, _, stderr = self.run_guard(
                    [
                        "audit",
                        "--nacos-addr",
                        address,
                        "--config-dir",
                        str(config_dir),
                        "--target",
                        "auto",
                    ]
                )

        self.assertEqual(guard.EXIT_ERROR, code)
        self.assertIn("Ambiguous Nacos public namespace", stderr)
        self.assertFalse(
            any(request["method"] == "POST" for request in state.requests)
        )

    def test_mirror_public_audit_reads_each_literal_tenant(self) -> None:
        state = FakeNacosState()
        state.configs[("", self.group, self.data_id)] = {
            "content": "new-content\n",
            "type": "yaml",
        }
        state.configs[("public", self.group, self.data_id)] = {
            "content": "old-content\n",
            "type": "text",
        }
        with tempfile.TemporaryDirectory() as temporary:
            root = pathlib.Path(temporary)
            config_dir = self.create_config_dir(root, "new-content\n")
            audit_dir = root / "audit"
            with RunningFakeNacos(state) as address:
                code, stdout, _ = self.run_guard(
                    [
                        "audit",
                        "--nacos-addr",
                        address,
                        "--config-dir",
                        str(config_dir),
                        "--target",
                        "mirror-public",
                        "--audit-dir",
                        str(audit_dir),
                    ]
                )
            manifest = json.loads((audit_dir / "manifest.json").read_text())

        self.assertEqual(guard.EXIT_DRIFT, code)
        self.assertIn("MATCH   builtin-public", stdout)
        self.assertIn("DRIFT   literal-public", stdout)
        self.assertEqual("DRIFT", manifest["result"])
        tenants = {
            request["query"]["tenant"][0]
            for request in state.requests
            if request["path"] == "/nacos/v1/cs/configs"
        }
        self.assertEqual({"", "public"}, tenants)

    def test_publish_mirrors_both_public_namespaces_with_cas_and_backups(self) -> None:
        state = FakeNacosState()
        for tenant in ("", "public"):
            state.configs[(tenant, self.group, self.data_id)] = {
                "content": f"old-{tenant or 'builtin'}\r\n",
                "type": "text",
            }
        expected = "new-content\r\nsecond-line\r\n"
        with tempfile.TemporaryDirectory() as temporary:
            root = pathlib.Path(temporary)
            config_dir = self.create_config_dir(root, expected)
            audit_dir = root / "audit"
            with RunningFakeNacos(state) as address:
                code, _, stderr = self.run_guard(
                    [
                        "publish",
                        "--nacos-addr",
                        address,
                        "--config-dir",
                        str(config_dir),
                        "--target",
                        "mirror-public",
                        "--confirm-write",
                        "--audit-dir",
                        str(audit_dir),
                    ]
                )
            manifest = json.loads((audit_dir / "manifest.json").read_text())
            before_builtin = (
                audit_dir
                / "before"
                / "builtin-public"
                / self.group
                / self.data_id
            ).read_bytes()
            before_literal = (
                audit_dir
                / "before"
                / "literal-public"
                / self.group
                / self.data_id
            ).read_bytes()

        self.assertEqual("", stderr)
        self.assertEqual(guard.EXIT_OK, code)
        self.assertEqual("VERIFIED", manifest["result"])
        self.assertEqual(b"old-builtin\r\n", before_builtin)
        self.assertEqual(b"old-public\r\n", before_literal)
        self.assertEqual(
            expected,
            state.configs[("", self.group, self.data_id)]["content"],
        )
        self.assertEqual(
            expected,
            state.configs[("public", self.group, self.data_id)]["content"],
        )
        publish_requests = [
            request
            for request in state.requests
            if request["method"] == "POST"
            and request["path"] == "/nacos/v1/cs/configs"
        ]
        self.assertEqual(2, len(publish_requests))
        self.assertTrue(
            all(request.get("casMd5Header") for request in publish_requests)
        )
        self.assertTrue(
            all("casMd5" not in request["form"] for request in publish_requests)
        )
        self.assertFalse(
            any(request["method"] == "DELETE" for request in state.requests)
        )

    def test_publish_stops_when_cas_is_rejected(self) -> None:
        state = FakeNacosState()
        state.configs[("", self.group, self.data_id)] = {
            "content": "before\n",
            "type": "yaml",
        }
        state.namespaces = [
            {"namespace": "", "namespaceShowName": "public", "type": 0}
        ]
        state.force_cas_failure = True
        with tempfile.TemporaryDirectory() as temporary:
            root = pathlib.Path(temporary)
            config_dir = self.create_config_dir(root, "after\n")
            audit_dir = root / "audit"
            with RunningFakeNacos(state) as address:
                code, _, stderr = self.run_guard(
                    [
                        "publish",
                        "--nacos-addr",
                        address,
                        "--config-dir",
                        str(config_dir),
                        "--target",
                        "builtin-public",
                        "--confirm-write",
                        "--audit-dir",
                        str(audit_dir),
                    ]
                )
            manifest = json.loads((audit_dir / "manifest.json").read_text())

        self.assertEqual(guard.EXIT_ERROR, code)
        self.assertIn("changed concurrently", stderr)
        self.assertEqual("ERROR", manifest["result"])
        self.assertEqual(
            "before\n",
            state.configs[("", self.group, self.data_id)]["content"],
        )

    def test_missing_config_requires_explicit_creation_permission(self) -> None:
        state = FakeNacosState()
        state.namespaces = [
            {"namespace": "", "namespaceShowName": "public", "type": 0}
        ]
        with tempfile.TemporaryDirectory() as temporary:
            root = pathlib.Path(temporary)
            config_dir = self.create_config_dir(root, "new\n")
            audit_dir = root / "audit"
            with RunningFakeNacos(state) as address:
                code, _, stderr = self.run_guard(
                    [
                        "publish",
                        "--nacos-addr",
                        address,
                        "--config-dir",
                        str(config_dir),
                        "--target",
                        "builtin-public",
                        "--confirm-write",
                        "--audit-dir",
                        str(audit_dir),
                    ]
                )

        self.assertEqual(guard.EXIT_ERROR, code)
        self.assertIn("--allow-create-config", stderr)
        self.assertFalse(
            any(
                request["method"] == "POST"
                and request["path"] == "/nacos/v1/cs/configs"
                for request in state.requests
            )
        )

    def test_data_id_cannot_escape_config_directory(self) -> None:
        state = FakeNacosState()
        state.namespaces = [
            {"namespace": "", "namespaceShowName": "public", "type": 0}
        ]
        with tempfile.TemporaryDirectory() as temporary:
            root = pathlib.Path(temporary)
            config_dir = self.create_config_dir(root, "safe\n")
            (root / "outside.yml").write_text("outside\n", encoding="utf-8")
            with RunningFakeNacos(state) as address:
                code, _, stderr = self.run_guard(
                    [
                        "audit",
                        "--nacos-addr",
                        address,
                        "--config-dir",
                        str(config_dir),
                        "--target",
                        "builtin-public",
                        "--data-id",
                        "../outside.yml",
                    ]
                )

        self.assertEqual(guard.EXIT_ERROR, code)
        self.assertIn("must be file names", stderr)
        self.assertFalse(
            any(request["method"] == "POST" for request in state.requests)
        )


if __name__ == "__main__":
    unittest.main()
