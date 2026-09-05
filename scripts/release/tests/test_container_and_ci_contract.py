from __future__ import annotations

import contextlib
import http.server
import importlib.util
import io
import os
import pathlib
import re
import shutil
import subprocess
import sys
import tempfile
import threading
import unittest
from unittest import mock

import yaml


REPO_ROOT = pathlib.Path(__file__).resolve().parents[3]
HEALTH_PROBE_SOURCE = REPO_ROOT / "scripts" / "docker" / "HealthProbe.java"
ENTRYPOINT = REPO_ROOT / "scripts" / "docker" / "entrypoint.sh"
DOCKERFILE = REPO_ROOT / "Dockerfile"
COMPOSE = REPO_ROOT / "docker-compose.yml"
RELEASE_COMPOSE = REPO_ROOT / "docker-compose.release.yml"
ENV_EXAMPLE = REPO_ROOT / ".env.example"
WORKFLOW = REPO_ROOT / ".github" / "workflows" / "ci.yml"
RELEASE_ENV = REPO_ROOT / "scripts" / "release" / "release.env.example"
NACOS_CHECKLIST = REPO_ROOT / "docs" / "nacos" / "NACOS_CONFIG_CHECKLIST.md"
NACOS_STARTUP_GUIDE = REPO_ROOT / "docs" / "nacos" / "CodeCoachAI_本地Nacos启动说明.md"
OPERATIONS_RUNBOOK = REPO_ROOT / "docs" / "operations" / "release-engineering-runbook.md"
NACOS_INITIALIZER = REPO_ROOT / "scripts" / "docker" / "nacos-config-init.sh"
NACOS_START_SCRIPT = REPO_ROOT / "scripts" / "nacos" / "start-nacos-dev.ps1"
NACOS_IMPORT_SCRIPT = REPO_ROOT / "scripts" / "nacos" / "import-nacos-config.sh"
NACOS_IMPORT_PS_SCRIPT = REPO_ROOT / "scripts" / "nacos" / "import-nacos-config.ps1"
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

    def test_nacos_config_initializer_has_valid_shell_syntax(self) -> None:
        shell = shutil.which("sh")
        if not shell:
            self.skipTest("POSIX shell is unavailable")
        result = subprocess.run(
            [shell, "-n", str(NACOS_INITIALIZER)],
            check=False,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            text=True,
        )
        self.assertEqual(0, result.returncode, result.stderr)

    def test_nacos_initializer_uses_token_guard_and_blocks_drift(self) -> None:
        initializer = NACOS_INITIALIZER.read_text(encoding="utf-8")
        compose = COMPOSE.read_text(encoding="utf-8")

        self.assertIn('NACOS_NAMESPACE must be a non-empty dedicated namespace ID', initializer)
        self.assertIn("--target namespace", initializer)
        self.assertIn("--access-token", initializer)
        self.assertIn("--create-missing-only", initializer)
        self.assertNotIn("curl --user", initializer)
        self.assertIn('NACOS_SERVER_ADDR="${NACOS_SERVER_ADDR:-nacos:8848}"', initializer)
        self.assertNotIn('NACOS_CONFIG_SERVER_URL="${NACOS_CONFIG_SERVER_URL:-', initializer)
        self.assertIn("python:3.12-alpine", compose)
        self.assertIn("nacos_config_guard.py:/scripts/nacos_config_guard.py:ro", compose)

    def test_nacos_start_import_requires_dedicated_namespace(self) -> None:
        start_script = NACOS_START_SCRIPT.read_text(encoding="utf-8")

        self.assertIn('[ValidateSet("namespace")]', start_script)
        self.assertIn('Namespace -or $Namespace -eq "public"', start_script)
        self.assertIn("-Namespace $Namespace", start_script)
        self.assertIn("-Target $Target", start_script)

    def test_bash_nacos_import_defaults_to_current_service_configs(self) -> None:
        import_script = NACOS_IMPORT_SCRIPT.read_text(encoding="utf-8")

        self.assertIn(
            'if [[ -z "${NACOS_DATA_IDS}" ]]',
            import_script,
        )
        self.assertIn('profile="${SPRING_PROFILES_ACTIVE:-dev}"', import_script)
        for data_id in (
            "codecoachai-common-${profile}.yml",
            "codecoachai-redis-${profile}.yml",
            "codecoachai-gateway-${profile}.yml",
            "codecoachai-core-${profile}.yml",
            "codecoachai-ai-${profile}.yml",
            "codecoachai-search-${profile}.yml",
        ):
            self.assertIn(data_id, import_script)

    def test_powershell_nacos_import_uses_the_same_default_data_id_policy(self) -> None:
        import_script = NACOS_IMPORT_PS_SCRIPT.read_text(encoding="utf-8")

        self.assertIn("$env:NACOS_DATA_IDS", import_script)
        self.assertIn("ForEach-Object { $_.Trim() }", import_script)
        self.assertIn("if ($DataId.Count -eq 0)", import_script)
        for data_id in (
            "codecoachai-common-$profile.yml",
            "codecoachai-redis-$profile.yml",
            "codecoachai-gateway-$profile.yml",
            "codecoachai-core-$profile.yml",
            "codecoachai-ai-$profile.yml",
            "codecoachai-search-$profile.yml",
        ):
            self.assertIn(data_id, import_script)

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
        root_pom = (REPO_ROOT / "pom.xml").read_text(encoding="utf-8")
        reactor_modules = set(re.findall(r"<module>\s*([^<]+?)\s*</module>", root_pom))

        source_builder = re.search(
            r"(?ms)^FROM\s+maven:[^\n]+AS source-builder\s*$"
            r"(.*?)^RUN case \"\$\{SERVICE\}\" in\s*\\?\s*$",
            dockerfile,
        )
        self.assertIsNotNone(source_builder, "Dockerfile source-builder stage is required")
        copy_block = source_builder.group(1)
        copied_modules = re.findall(
            r"(?m)^COPY\s+(codecoachai-[^\s]+)\s+\./\1\s*$",
            copy_block,
        )

        self.assertEqual(
            reactor_modules,
            set(copied_modules),
            "source-builder COPY modules must match the current root POM modules",
        )
        self.assertEqual(
            len(copied_modules),
            len(set(copied_modules)),
            "source-builder must not copy a top-level module more than once",
        )
        self.assertIn("COPY codecoachai-core ./codecoachai-core", dockerfile)
        self.assertIn(
            "codecoachai-gateway|codecoachai-core|codecoachai-ai|codecoachai-search) ;;",
            dockerfile,
        )
        service_gate = re.search(
            r'(?ms)^RUN case "\$\{SERVICE\}" in\s*\\?\s*$'
            r"(.*?)^\s*esac\s*\\?\s*$",
            dockerfile,
        )
        self.assertIsNotNone(service_gate, "Dockerfile service allowlist is required")
        self.assertEqual(
            {
                "codecoachai-gateway",
                "codecoachai-core",
                "codecoachai-ai",
                "codecoachai-search",
            },
            set(re.findall(r"\b(codecoachai-[a-z-]+)\|?", service_gate.group(1))),
        )

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
            "CODECOACHAI_GATEWAY_TO_CORE_SIGNING_SECRET",
            "CODECOACHAI_GATEWAY_TO_AI_SIGNING_SECRET",
            "CODECOACHAI_GATEWAY_TO_SEARCH_SIGNING_SECRET",
            "CODECOACHAI_CALLER_CORE_SIGNING_SECRET",
            "CODECOACHAI_CALLER_AI_SIGNING_SECRET",
            "CODECOACHAI_CALLER_SEARCH_SIGNING_SECRET",
        ):
            self.assertIn(secret, compose)
        self.assertIn("nacos-config-init:", compose)
        self.assertIn("NACOS_CONFIG_BOOTSTRAP_ENABLED", compose)
        self.assertIn("NACOS_SERVER_ADDR: ${NACOS_SERVER_ADDR:-nacos:8848}", compose)
        self.assertIn("NACOS_CONFIG_SCHEME: ${NACOS_CONFIG_SCHEME:-http}", compose)
        self.assertIn("NACOS_CONFIG_CONTEXT_PATH: ${NACOS_CONFIG_CONTEXT_PATH:-/nacos}", compose)
        self.assertEqual(
            5,
            compose.count("NACOS_SERVER_ADDR: ${NACOS_SERVER_ADDR:-nacos:8848}"),
        )
        self.assertIn("nacos-data:/home/nacos/data", compose)
        self.assertIn("rocketmq-store:/home/rocketmq/store", compose)
        self.assertIn("rocketmq-logs:/home/rocketmq/logs", compose)
        self.assertIn("CODECOACHAI_TASK_CONSUMERS_ENABLED", compose)
        self.assertNotIn("CALLER_AI_KEY_CURRENT", compose)
        self.assertNotIn("CALLER_CORE_KEY_CURRENT", compose)
        self.assertNotIn("CALLER_SEARCH_KEY_CURRENT", compose)
        for application_only_variable in (
            "NACOS_NAMESPACE",
            "OSS_BUCKET",
            "OSS_AK",
            "OSS_SK",
            "DEEPSEEK_API_KEY",
            "CODECOACHAI_AI_CRYPTO_SECRET_KEY",
            "CODECOACHAI_GATEWAY_TO_CORE_SIGNING_SECRET",
        ):
            self.assertNotIn(f"${{{application_only_variable}:?", compose)
        self.assertIn('NACOS_NAMESPACE: "${NACOS_NAMESPACE:-}"', compose)
        for application_setting in (
            'OSS_BUCKET: "${OSS_BUCKET:-}"',
            'OSS_AK: "${OSS_AK:-}"',
            'OSS_SK: "${OSS_SK:-}"',
            'QDRANT_BASE_URL: "${QDRANT_BASE_URL:-}"',
            'QDRANT_API_KEY: "${QDRANT_API_KEY:-}"',
        ):
            self.assertIn(application_setting, compose)
        self.assertRegex(
            compose,
            r"(?s)  qdrant:.*?profiles:\s+- vector\s+- app-services",
        )

    def test_example_environment_satisfies_base_compose_required_interpolation(self) -> None:
        compose = COMPOSE.read_text(encoding="utf-8")
        example_environment = {}
        for raw_line in ENV_EXAMPLE.read_text(encoding="utf-8").splitlines():
            line = raw_line.strip()
            if not line or line.startswith("#"):
                continue
            name, value = line.split("=", 1)
            example_environment[name] = value

        required_variables = set(re.findall(r"\$\{([A-Z0-9_]+):\?[^}]*\}", compose))
        self.assertEqual({"MYSQL_PASSWORD", "REDIS_PASSWORD"}, required_variables)
        for variable in required_variables:
            self.assertTrue(
                example_environment.get(variable),
                f"{variable} must be non-empty so base Compose can render from .env.example",
            )
        self.assertEqual(
            "codecoachai-local-elastic",
            example_environment.get("ELASTIC_PASSWORD"),
        )
        self.assertNotIn("${ELASTIC_PASSWORD:-}", compose)

class ReleaseDeploymentContractTest(unittest.TestCase):
    def test_release_compose_mounts_active_release_jars(self) -> None:
        compose = RELEASE_COMPOSE.read_text(encoding="utf-8")
        self.assertIn("target: runtime-base", compose)
        self.assertIn("CODECOACHAI_REMOTE_ROOT", compose)
        self.assertNotIn("CODECOACHAI_RELEASE_ROOT", compose)
        self.assertEqual(
            4,
            compose.count(
                "image: codecoachai/runtime-base:"
                "${CODECOACHAI_RUNTIME_IMAGE_TAG:?"
            ),
        )
        self.assertEqual(6, compose.count("pull_policy: never"))
        self.assertIn(
            "image: ${CODECOACHAI_NACOS_GUARD_IMAGE:"
            "?CODECOACHAI_NACOS_GUARD_IMAGE is required for release}",
            compose,
        )
        self.assertIn(
            "image: ${CODECOACHAI_FLYWAY_TOOL_IMAGE:"
            "?CODECOACHAI_FLYWAY_TOOL_IMAGE is required for release}",
            compose,
        )
        self.assertRegex(
            compose,
            r'(?s)nacos-config-init:.*?NACOS_CONFIG_BOOTSTRAP_ENABLED: "false"',
        )
        for service in (
            "codecoachai-gateway",
            "codecoachai-core",
            "codecoachai-ai",
            "codecoachai-search",
        ):
            self.assertIn(f"{service}.jar:/app/app.jar:ro", compose)
        self.assertIn("MYSQL_HOST: ${MYSQL_HOST:?MYSQL_HOST is required for release}", compose)
        self.assertIn("REDIS_HOST: ${REDIS_HOST:?REDIS_HOST is required for release}", compose)
        self.assertIn(
            "NACOS_SERVER_ADDR: ${NACOS_SERVER_ADDR:?NACOS_SERVER_ADDR is required for release}",
            compose,
        )
        self.assertIn(
            "NACOS_USERNAME: ${NACOS_USERNAME:?NACOS_USERNAME is required for release}",
            compose,
        )
        self.assertIn(
            "NACOS_PASSWORD: ${NACOS_PASSWORD:?NACOS_PASSWORD is required for release}",
            compose,
        )
        for required_secret in (
            "MYSQL_PASSWORD",
            "REDIS_PASSWORD",
            "ELASTIC_PASSWORD",
            "QDRANT_API_KEY",
            "OSS_BUCKET",
            "OSS_AK",
            "OSS_SK",
            "DEEPSEEK_API_KEY",
            "CODECOACHAI_AI_CRYPTO_SECRET_KEY",
            "CODECOACHAI_GATEWAY_TO_CORE_SIGNING_SECRET",
            "CODECOACHAI_CALLER_CORE_SIGNING_SECRET",
        ):
            self.assertIn(f"${{{required_secret}:?", compose)
        self.assertEqual(5, compose.count('SPRING_PROFILES_ACTIVE: "dev"'))

    def test_release_environment_pins_runtime_and_compose_identity(self) -> None:
        environment = RELEASE_ENV.read_text(encoding="utf-8")
        for variable in (
            "NACOS_USERNAME=",
            "NACOS_PASSWORD=",
            "CODECOACHAI_RUNTIME_IMAGE_TAG=",
            "CODECOACHAI_NACOS_GUARD_IMAGE=",
            "CODECOACHAI_FLYWAY_TOOL_IMAGE=",
            "COMPOSE_PROJECT_NAME=codecoachai",
        ):
            self.assertIn(variable, environment)

    def test_nacos_profile_contract_is_fail_closed_to_reviewed_dev_set(self) -> None:
        initializer = NACOS_INITIALIZER.read_text(encoding="utf-8")
        release_compose = RELEASE_COMPOSE.read_text(encoding="utf-8")
        example_environment = ENV_EXAMPLE.read_text(encoding="utf-8")
        runbook = OPERATIONS_RUNBOOK.read_text(encoding="utf-8")

        components = ("common", "redis", "gateway", "core", "ai", "search")
        self.assertIn(
            "for component in common redis gateway core ai search; do",
            initializer,
        )
        for component in components:
            self.assertTrue(
                (REPO_ROOT / "docs" / "nacos" / f"codecoachai-{component}-dev.yml").is_file()
            )
        self.assertIn("SPRING_PROFILES_ACTIVE=dev", example_environment)
        self.assertEqual(5, release_compose.count('SPRING_PROFILES_ACTIVE: "dev"'))
        self.assertIn(
            "must use `SPRING_PROFILES_ACTIVE=dev`",
            runbook,
        )
        self.assertIn(
            "complete reviewed `codecoachai-*-<profile>.yml` set is added",
            runbook,
        )

    def test_release_runbook_recreates_only_application_services(self) -> None:
        runbook = OPERATIONS_RUNBOOK.read_text(encoding="utf-8")

        self.assertIn(
            'export CANDIDATE_CONTROL_ROOT="${CANDIDATE_RELEASE_ROOT}/control"',
            runbook,
        )
        self.assertIn(
            'docker load \\\n'
            '  --input "${CANDIDATE_RELEASE_ROOT}/runtime/'
            'codecoachai-runtime-base.tar"',
            runbook,
        )
        self.assertIn(
            'docker image inspect "${CODECOACHAI_NACOS_GUARD_IMAGE}"',
            runbook,
        )
        self.assertIn(
            'docker image inspect "${CODECOACHAI_FLYWAY_TOOL_IMAGE}"',
            runbook,
        )
        self.assertIn("must be pinned by digest", runbook)
        self.assertIn("run --rm --no-deps nacos-config-init", runbook)
        self.assertIn("run --rm --no-deps flyway-migrate", runbook)
        activate_index = runbook.index("release_transport.py activate")
        self.assertLess(
            runbook.index("run --rm --no-deps nacos-config-init"),
            runbook.index("run --rm --no-deps flyway-migrate"),
        )
        self.assertLess(
            runbook.index("run --rm --no-deps flyway-migrate"),
            activate_index,
        )
        activation_segment = runbook[
            runbook.index("### Activate After Both Gates"):
            runbook.index("### Phased Application Handover")
        ]
        self.assertIn(
            "export CANDIDATE_RELEASE_ID=20260804-001",
            activation_segment,
        )

        phase_markers = (
            "#### Phase 1: AI",
            "#### Phase 2: Core With Consumers Disabled",
            "#### Phase 3: Stop Legacy Task",
            "#### Phase 4: Core With Consumers Enabled",
            "#### Phase 5: Gateway",
            "#### Phase 6: Search",
        )
        phase_positions = [runbook.index(marker) for marker in phase_markers]
        self.assertEqual(sorted(phase_positions), phase_positions)
        self.assertLess(
            activate_index,
            phase_positions[0],
        )
        disabled_export = runbook.index(
            "export CODECOACHAI_TASK_CONSUMERS_ENABLED=false"
        )
        enabled_export = runbook.index(
            "export CODECOACHAI_TASK_CONSUMERS_ENABLED=true"
        )
        self.assertLess(phase_positions[0], disabled_export)
        self.assertLess(disabled_export, phase_positions[1])
        self.assertLess(phase_positions[3], enabled_export)
        self.assertLess(enabled_export, phase_positions[4])

        application_services = (
            "codecoachai-ai",
            "codecoachai-core",
            "codecoachai-gateway",
            "codecoachai-search",
        )
        application_up_blocks = [
            block
            for block in runbook.split("\n\n")
            if "docker compose" in block
            and " up -d " in block
            and any(service in block for service in application_services)
        ]
        self.assertEqual(5, len(application_up_blocks))
        for block in application_up_blocks:
            self.assertIn("--no-deps", block)
            self.assertIn("--no-build", block)
            self.assertIn("--force-recreate", block)
            self.assertEqual(
                1,
                sum(service in block for service in application_services),
                block,
            )

        phase_health_contracts = (
            (phase_positions[0], phase_positions[1], "ai", "9206"),
            (phase_positions[1], phase_positions[2], "core", "9200"),
            (phase_positions[3], phase_positions[4], "core", "9200"),
            (phase_positions[4], phase_positions[5], "gateway", "8080"),
            (phase_positions[5], runbook.index("### Rollback"), "search", "8091"),
        )
        for start, end, service, port in phase_health_contracts:
            segment = runbook[start:end]
            self.assertIn(
                f"--service {service}=http://127.0.0.1:{port}/actuator/health",
                segment,
            )
            self.assertIn(f"--container {service}=codecoachai-{service}", segment)

        previous_image_load = runbook.index(
            '--input "${CODECOACHAI_REMOTE_ROOT}/previous/runtime/'
            'codecoachai-runtime-base.tar"'
        )
        rollback_execute = runbook.index(
            "release_transport.py rollback \\\n  --execute"
        )
        self.assertLess(previous_image_load, rollback_execute)
        self.assertIn(
            "repeat the application recreation and\n"
            "health sequence with `--no-deps --no-build --force-recreate`",
            runbook,
        )

    def test_nacos_docs_match_four_service_and_directional_key_topology(self) -> None:
        checklist = NACOS_CHECKLIST.read_text(encoding="utf-8")
        startup_guide = NACOS_STARTUP_GUIDE.read_text(encoding="utf-8")
        ai_application = yaml.safe_load(
            (REPO_ROOT / "codecoachai-ai" / "src" / "main" / "resources" / "application.yml")
            .read_text(encoding="utf-8")
        )
        ai_nacos = yaml.safe_load(
            (REPO_ROOT / "docs" / "nacos" / "codecoachai-ai-dev.yml")
            .read_text(encoding="utf-8")
        )

        for service in ("Gateway", "Core", "AI", "Search"):
            self.assertIn(service, checklist)
        self.assertIn("legacy-shared-secret-enabled: false", startup_guide)
        self.assertNotIn("8 个服务", checklist)
        self.assertNotIn("legacy-shared-secret-enabled: true", startup_guide)

        migration = (REPO_ROOT / "docs" / "nacos" / "internal-auth-caller-keyring-migration.md").read_text(
            encoding="utf-8"
        )
        self.assertIn("CODECOACHAI_CALLER_AI_SIGNING_SECRET", migration)
        self.assertIn("STREAMING-UNSIGNED-PAYLOAD", migration)
        self.assertIn("does not authenticate file bytes", migration)
        self.assertIn("Require authenticated TLS", migration)
        self.assertNotIn("CALLER_GATEWAY_KEY_CURRENT", migration)
        self.assertNotIn("CALLER_TASK_KEY_CURRENT", migration)
        self.assertNotIn("CALLER_AI_KEY_CURRENT", migration)

        runbook = OPERATIONS_RUNBOOK.read_text(encoding="utf-8")
        self.assertIn("ports remain bound to host loopback", runbook)
        self.assertIn("Gateway-to-Core/AI uses authenticated", runbook)
        self.assertIn("prefer mTLS", runbook)
        for config in (ai_application, ai_nacos):
            multipart = config["spring"]["servlet"]["multipart"]
            self.assertEqual("8MB", multipart["max-file-size"])
            self.assertEqual("9MB", multipart["max-request-size"])

        for module in ("gateway", "core", "ai", "search"):
            application = yaml.safe_load(
                (
                    REPO_ROOT
                    / f"codecoachai-{module}"
                    / "src"
                    / "main"
                    / "resources"
                    / "application.yml"
                ).read_text(encoding="utf-8")
            )
            imports = application["spring"]["config"]["import"]
            self.assertTrue(
                any("codecoachai-common-" in item for item in imports),
                f"{module} must import the shared Nacos config",
            )
            self.assertTrue(
                any("codecoachai-redis-" in item for item in imports),
                f"{module} must import the shared Redis Nacos config",
            )
            self.assertTrue(
                any(f"codecoachai-{module}-" in item for item in imports),
                f"{module} must import its service-specific Nacos config",
            )


class WorkflowContractTest(unittest.TestCase):
    def test_ci_covers_real_branches_and_quality_commands(self) -> None:
        workflow = WORKFLOW.read_text(encoding="utf-8")
        for branch in (
            "main",
            "dev-v3",
            "dev-260703",
            "dev-fb",
            "dev-fb-260803",
            "dev-fb-260805",
        ):
            self.assertIn(f"- {branch}", workflow)
        for command in (
            "clean test",
            "-Pphase2-dependency-gates",
            "-DskipTests verify",
            "npm run type-check",
            "npm run test:unit:run",
            "npm run build",
            "build_release.py",
            "verify_release.py",
            "docker save",
            "--backend-control-source",
            "--runtime-image",
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
        self.assertIn(
            "name: runtime-image-${{ steps.image.outputs.release_id }}",
            workflow,
        )
        self.assertRegex(
            workflow,
            r"(?s)release:\s+name: Build immutable release directory.*?"
            r"needs:.*?- runtime-image.*?- docker",
        )


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
