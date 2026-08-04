from __future__ import annotations

import contextlib
import http.server
import importlib.util
import io
import os
import pathlib
import shutil
import subprocess
import sys
import tempfile
import threading
import unittest
from unittest import mock


REPO_ROOT = pathlib.Path(__file__).resolve().parents[3]
HEALTH_PROBE_SOURCE = REPO_ROOT / "scripts" / "docker" / "HealthProbe.java"
ENTRYPOINT = REPO_ROOT / "scripts" / "docker" / "entrypoint.sh"
DOCKERFILE = REPO_ROOT / "Dockerfile"
COMPOSE = REPO_ROOT / "docker-compose.yml"
WORKFLOW = REPO_ROOT / ".github" / "workflows" / "ci.yml"
HEALTH_CHECK_PATH = REPO_ROOT / "scripts" / "release" / "check_health.py"
HEALTH_CHECK_SPEC = importlib.util.spec_from_file_location(
    "release_health_check",
    HEALTH_CHECK_PATH,
)
assert HEALTH_CHECK_SPEC and HEALTH_CHECK_SPEC.loader
health_check = importlib.util.module_from_spec(HEALTH_CHECK_SPEC)
sys.modules[HEALTH_CHECK_SPEC.name] = health_check
HEALTH_CHECK_SPEC.loader.exec_module(health_check)


class HealthHandler(http.server.BaseHTTPRequestHandler):
    status = "UP"

    def do_GET(self) -> None:
        body = f'{{"status":"{self.status}"}}'.encode("ascii")
        self.send_response(200)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def log_message(self, format: str, *args: object) -> None:
        return


class RunningHealthServer:
    def __init__(self, status: str):
        handler = type("ConfiguredHealthHandler", (HealthHandler,), {"status": status})
        self.server = http.server.ThreadingHTTPServer(("127.0.0.1", 0), handler)
        self.thread = threading.Thread(target=self.server.serve_forever, daemon=True)

    def __enter__(self) -> str:
        self.thread.start()
        host, port = self.server.server_address
        return f"http://{host}:{port}/actuator/health"

    def __exit__(self, exc_type, exc, traceback) -> None:
        self.server.shutdown()
        self.server.server_close()
        self.thread.join(timeout=5)


class ContainerContractTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        if not shutil.which("javac") or not shutil.which("java"):
            raise unittest.SkipTest("JDK is required for HealthProbe contract tests")
        cls.temporary = tempfile.TemporaryDirectory()
        cls.classes = pathlib.Path(cls.temporary.name) / "classes"
        cls.classes.mkdir()
        subprocess.run(
            [
                "javac",
                "--release",
                "17",
                "-d",
                str(cls.classes),
                str(HEALTH_PROBE_SOURCE),
            ],
            check=True,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
        )

    @classmethod
    def tearDownClass(cls) -> None:
        cls.temporary.cleanup()

    def run_probe(self, url: str, expected: str = "UP") -> subprocess.CompletedProcess[str]:
        return subprocess.run(
            [
                "java",
                "-cp",
                str(self.classes),
                "HealthProbe",
                url,
                "1000",
                expected,
            ],
            check=False,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            text=True,
        )

    def test_probe_accepts_up_and_rejects_down(self) -> None:
        with RunningHealthServer("UP") as address:
            self.assertEqual(0, self.run_probe(address).returncode)
        with RunningHealthServer("DOWN") as address:
            self.assertNotEqual(0, self.run_probe(address).returncode)

    def test_probe_rejects_non_loopback_targets(self) -> None:
        result = self.run_probe("http://example.com/actuator/health")
        self.assertNotEqual(0, result.returncode)
        self.assertIn("loopback", result.stderr)

    def test_entrypoint_has_valid_shell_syntax(self) -> None:
        shell = shutil.which("sh")
        if not shell:
            self.skipTest("POSIX shell is unavailable")
        result = subprocess.run(
            [shell, "-n", str(ENTRYPOINT)],
            check=False,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            text=True,
        )
        self.assertEqual(0, result.returncode, result.stderr)

    def test_entrypoint_exits_nonzero_when_spring_never_becomes_healthy(self) -> None:
        shell = shutil.which("sh")
        if not shell:
            self.skipTest("POSIX shell is unavailable")

        with tempfile.TemporaryDirectory() as temporary:
            root = pathlib.Path(temporary)
            app_jar = root / "app.jar"
            app_jar.write_bytes(b"placeholder")
            fake_java = root / "fake-java.sh"
            fake_java.write_text(
                "#!/bin/sh\n"
                "if [ \"${1:-}\" = \"-jar\" ]; then\n"
                "  trap 'exit 0' TERM INT HUP\n"
                "  while :; do sleep 1; done\n"
                "fi\n"
                "exit 1\n",
                encoding="ascii",
                newline="\n",
            )
            fake_java.chmod(0o755)

            def shell_path(path: pathlib.Path) -> str:
                if os.name != "nt":
                    return str(path)
                result = subprocess.run(
                    ["cygpath", "-u", str(path)],
                    check=True,
                    stdout=subprocess.PIPE,
                    text=True,
                )
                return result.stdout.strip()

            environment = os.environ.copy()
            environment.update(
                {
                    "APP_JAR": shell_path(app_jar),
                    "JAVA_BIN": shell_path(fake_java),
                    "HEALTH_STARTUP_TIMEOUT_SECONDS": "1",
                    "HEALTH_MONITOR_INTERVAL_SECONDS": "1",
                    "HEALTHCHECK_TIMEOUT_MILLIS": "100",
                    "HEALTH_MONITOR_FAILURE_THRESHOLD": "1",
                }
            )
            result = subprocess.run(
                [shell, shell_path(ENTRYPOINT)],
                check=False,
                stdout=subprocess.PIPE,
                stderr=subprocess.PIPE,
                text=True,
                env=environment,
                timeout=10,
            )
        self.assertEqual(1, result.returncode, result.stderr)
        self.assertIn("startup health timeout", result.stderr)

    def test_runtime_image_contract_is_non_root_and_jre_probed(self) -> None:
        dockerfile = DOCKERFILE.read_text(encoding="utf-8")
        self.assertIn("FROM eclipse-temurin:${JAVA_VERSION}-jre-alpine AS runtime-base", dockerfile)
        self.assertIn("USER 10001:10001", dockerfile)
        self.assertIn("HealthProbe", dockerfile)
        self.assertIn('"$HEALTHCHECK_URL"', dockerfile)
        self.assertIn("sed -i 's/\\r$//'", dockerfile)
        self.assertNotIn("curl ", dockerfile)
        self.assertNotIn("StrictHostKeyChecking=accept-new", dockerfile)

    def test_docker_build_accepts_only_deployable_services(self) -> None:
        dockerfile = DOCKERFILE.read_text(encoding="utf-8")
        self.assertIn("COPY codecoachai-core ./codecoachai-core", dockerfile)
        self.assertIn(
            "codecoachai-gateway|codecoachai-core|codecoachai-ai|codecoachai-search) ;;",
            dockerfile,
        )
        self.assertNotIn("codecoachai-auth|codecoachai-user", dockerfile)

    def test_compose_has_four_deployable_services_and_core_health_gate(self) -> None:
        compose = COMPOSE.read_text(encoding="utf-8")
        for service in (
            "codecoachai-gateway",
            "codecoachai-core",
            "codecoachai-ai",
            "codecoachai-search",
        ):
            self.assertIn(f"  {service}:", compose)
            self.assertIn(f"SERVICE: {service}", compose)
        self.assertIn('CODECOACHAI_CORE_PORT:-9200}:9200', compose)
        self.assertIn("HEALTHCHECK_URL: http://127.0.0.1:9200/actuator/health", compose)
        self.assertIn('CODECOACHAI_ELASTICSEARCH_PORT:-9210}:9200', compose)
        self.assertIn("rocketmq-broker:", compose)
        self.assertIn("condition: service_healthy", compose)
        self.assertIn("HEALTH_STARTUP_TIMEOUT_SECONDS", compose)
        self.assertIn("restart: on-failure", compose)
        self.assertNotIn("wget --header", compose)
        self.assertNotIn("CODECOACHAI_INTERNAL_SECRET:", compose)
        for secret in (
            "CODECOACHAI_CALLER_GATEWAY_SIGNING_SECRET",
            "CODECOACHAI_CALLER_CORE_SIGNING_SECRET",
            "CODECOACHAI_CALLER_AI_SIGNING_SECRET",
            "CODECOACHAI_CALLER_SEARCH_SIGNING_SECRET",
        ):
            self.assertIn(secret, compose)


class WorkflowContractTest(unittest.TestCase):
    def test_ci_covers_real_branches_and_quality_commands(self) -> None:
        workflow = WORKFLOW.read_text(encoding="utf-8")
        for branch in ("main", "dev-v3", "dev-260703", "dev-fb", "dev-fb-260803"):
            self.assertIn(f"- {branch}", workflow)
        for command in (
            "clean test",
            "-DskipTests package",
            "npm run type-check",
            "npm run test:unit:run",
            "npm run build",
            "build_release.py",
            "verify_release.py",
        ):
            self.assertIn(command, workflow)
        for service in (
            "codecoachai-gateway",
            "codecoachai-core",
            "codecoachai-ai",
            "codecoachai-search",
        ):
            self.assertIn(f"- service: {service}", workflow)
        self.assertNotIn("- service: codecoachai-auth", workflow)
        self.assertNotIn("- service: codecoachai-task", workflow)
        self.assertNotIn("dist-interview-layout", workflow)


class OperationsHealthCheckTest(unittest.TestCase):
    def test_parameterized_health_check_accepts_up_endpoint(self) -> None:
        with RunningHealthServer("UP") as address:
            stdout = io.StringIO()
            with contextlib.redirect_stdout(stdout):
                exit_code = health_check.main(
                    [
                        "--service",
                        f"search={address}",
                        "--attempts",
                        "1",
                    ]
                )
        self.assertEqual(0, exit_code)
        self.assertIn('"classification": "healthy"', stdout.getvalue())

    def test_running_container_with_down_application_is_false_up(self) -> None:
        with RunningHealthServer("DOWN") as address:
            stdout = io.StringIO()
            with mock.patch.object(
                health_check,
                "get_container_state",
                return_value=("running", "unhealthy", ""),
            ):
                with contextlib.redirect_stdout(stdout):
                    exit_code = health_check.main(
                        [
                            "--service",
                            f"search={address}",
                            "--container",
                            "search=codecoachai-search",
                            "--attempts",
                            "1",
                        ]
                    )
        self.assertEqual(1, exit_code)
        self.assertIn('"classification": "false-up"', stdout.getvalue())


if __name__ == "__main__":
    unittest.main()
