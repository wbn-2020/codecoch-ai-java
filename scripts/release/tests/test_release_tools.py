from __future__ import annotations

import argparse
import contextlib
import io
import json
import os
import pathlib
import stat
import sys
import tarfile
import tempfile
import types
import unittest
import zipfile
from unittest import mock


RELEASE_DIR = pathlib.Path(__file__).resolve().parents[1]
sys.path.insert(0, str(RELEASE_DIR))

import build_release
import release_common
import release_transport


TEST_SHA = "1" * 40
TEST_FRONTEND_SHA = "2" * 40
TEST_FINGERPRINT = "SHA256:" + ("A" * 43)


def create_jar(path: pathlib.Path, marker: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(path, "w") as archive:
        archive.writestr("META-INF/MANIFEST.MF", "Manifest-Version: 1.0\n")
        archive.writestr("marker.txt", marker)


def add_tar_bytes(archive: tarfile.TarFile, name: str, content: bytes) -> None:
    info = tarfile.TarInfo(name)
    info.size = len(content)
    archive.addfile(info, io.BytesIO(content))


def create_runtime_image(
    path: pathlib.Path,
    tag: str,
) -> None:
    manifest = [
        {
            "Config": "config.json",
            "RepoTags": [f"{release_common.RUNTIME_IMAGE_REPOSITORY}:{tag}"],
            "Layers": ["layer/layer.tar"],
        }
    ]
    with tarfile.open(path, "w") as archive:
        add_tar_bytes(
            archive,
            "manifest.json",
            json.dumps(manifest).encode("utf-8"),
        )
        add_tar_bytes(archive, "config.json", b"{}")
        add_tar_bytes(archive, "layer/layer.tar", b"layer")


class ReleaseBuildTest(unittest.TestCase):
    def setUp(self) -> None:
        self.temporary = tempfile.TemporaryDirectory()
        self.root = pathlib.Path(self.temporary.name)
        self.backend = self.root / "backend"
        self.frontend = self.root / "frontend-dist"
        self.control = self.root / "backend-control"
        self.runtime_image = self.root / "codecoachai-runtime-base.tar"
        self.output = self.root / "releases"
        for service in release_common.SERVICE_MODULES:
            create_jar(
                self.backend / service / "target" / f"{service}-1.0.jar",
                service,
            )
        self.frontend.mkdir()
        (self.frontend / "index.html").write_text(
            "<!doctype html><title>CodeCoachAI</title>",
            encoding="utf-8",
        )
        assets = self.frontend / "assets"
        assets.mkdir()
        (assets / "app.js").write_text("console.log('ok');\n", encoding="utf-8")
        (self.frontend / "historical.tar.gz").write_bytes(b"must not ship")
        for relative_name in build_release.CONTROL_FILES:
            path = self.control.joinpath(*pathlib.PurePosixPath(relative_name).parts)
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(f"fixture: {relative_name}\n", encoding="utf-8")
        for relative_name in build_release.CONTROL_DIRECTORIES:
            path = self.control.joinpath(*pathlib.PurePosixPath(relative_name).parts)
            path.mkdir(parents=True, exist_ok=True)
            (path / "V1__fixture.sql").write_text("select 1;\n", encoding="ascii")
        create_runtime_image(self.runtime_image, "release-001")

    def tearDown(self) -> None:
        self.temporary.cleanup()

    def arguments(self, release_id: str = "release-001") -> argparse.Namespace:
        return argparse.Namespace(
            backend_artifacts=self.backend,
            backend_control_source=self.control,
            frontend_dist=self.frontend,
            runtime_image=self.runtime_image,
            runtime_image_tag=release_id,
            output_root=self.output,
            release_id=release_id,
            backend_repo=None,
            frontend_repo=None,
            backend_source_sha=TEST_SHA,
            frontend_source_sha=TEST_FRONTEND_SHA,
            allow_dirty=False,
        )

    def test_release_contract_has_only_four_deployable_services(self) -> None:
        self.assertEqual(
            (
                "codecoachai-gateway",
                "codecoachai-core",
                "codecoachai-ai",
                "codecoachai-search",
            ),
            release_common.SERVICE_MODULES,
        )

    def test_builds_manifested_release_without_historical_archives(self) -> None:
        release_path = build_release.build_release(self.arguments())
        entries = release_common.verify_release(release_path)

        self.assertEqual(
            len(release_common.SERVICE_MODULES),
            len(list((release_path / "backend").glob("*.jar"))),
        )
        self.assertTrue((release_path / "frontend" / "index.html").is_file())
        self.assertTrue(
            (release_path / "runtime" / build_release.RUNTIME_IMAGE_NAME).is_file()
        )
        self.assertTrue(
            (release_path / "control" / "docker-compose.release.yml").is_file()
        )
        self.assertTrue(
            (release_path / "control" / "sql" / "migration" / "V1__fixture.sql").is_file()
        )
        self.assertFalse((release_path / "frontend" / "historical.tar.gz").exists())
        self.assertFalse(
            any(
                release_common.is_forbidden_archive(
                    pathlib.PurePosixPath(entry.relative_path)
                )
                for entry in entries
            )
        )

        metadata = json.loads(
            (release_path / release_common.RELEASE_METADATA_NAME).read_text(
                encoding="ascii"
            )
        )
        self.assertEqual(release_common.RELEASE_FORMAT_VERSION, metadata["formatVersion"])
        self.assertEqual(["historical.tar.gz"], metadata["excludedFrontendArchives"])
        self.assertEqual(TEST_SHA, metadata["backendSource"]["sha"])
        self.assertEqual(TEST_FRONTEND_SHA, metadata["frontendSource"]["sha"])
        self.assertEqual("release-001", metadata["runtimeImage"]["tag"])
        self.assertEqual(
            "runtime/codecoachai-runtime-base.tar",
            metadata["runtimeImage"]["path"],
        )
        self.assertEqual("control", metadata["controlBundle"]["path"])
        self.assertEqual(
            TEST_SHA,
            metadata["controlBundle"]["backendSourceSha"],
        )

    def test_verifier_detects_tampering(self) -> None:
        release_path = build_release.build_release(self.arguments())
        (release_path / "frontend" / "index.html").write_text(
            "tampered",
            encoding="utf-8",
        )
        with self.assertRaisesRegex(ValueError, "SHA-256 mismatch"):
            release_common.verify_release(release_path)

    def test_rejects_release_id_path_injection(self) -> None:
        with self.assertRaises(ValueError):
            build_release.build_release(self.arguments("../escape"))

    def test_rejects_runtime_image_tag_that_does_not_match_release(self) -> None:
        arguments = self.arguments()
        arguments.runtime_image_tag = "different-release"
        with self.assertRaisesRegex(ValueError, "must match release ID"):
            build_release.build_release(arguments)

    def test_rejects_runtime_archive_with_wrong_repo_tag(self) -> None:
        create_runtime_image(self.runtime_image, "different-release")
        with self.assertRaisesRegex(ValueError, "must contain exactly tag"):
            build_release.build_release(self.arguments())

    def test_verifier_rejects_runtime_archive_manifest_tampering(self) -> None:
        release_path = build_release.build_release(self.arguments())
        runtime_path = (
            release_path
            / "runtime"
            / release_common.RUNTIME_IMAGE_NAME
        )
        create_runtime_image(runtime_path, "different-release")
        release_common.write_manifest(release_path)
        with self.assertRaisesRegex(ValueError, "must contain exactly tag"):
            release_common.verify_release(release_path)


class DeploymentConfigTest(unittest.TestCase):
    def setUp(self) -> None:
        self.temporary = tempfile.TemporaryDirectory()
        self.root = pathlib.Path(self.temporary.name)
        self.known_hosts = self.root / "known_hosts"
        self.known_hosts.write_text(
            "[example.test]:2222 ssh-ed25519 AAAATEST\n",
            encoding="ascii",
        )

    def tearDown(self) -> None:
        self.temporary.cleanup()

    def environment(self) -> dict[str, str]:
        return {
            "CODECOACHAI_DEPLOY_HOST": "example.test",
            "CODECOACHAI_DEPLOY_PORT": "2222",
            "CODECOACHAI_DEPLOY_USER": "deploy",
            "CODECOACHAI_REMOTE_ROOT": "/opt/codecoachai",
            "CODECOACHAI_KNOWN_HOSTS": str(self.known_hosts),
            "CODECOACHAI_DEPLOY_HOST_FINGERPRINT": TEST_FINGERPRINT,
            "CODECOACHAI_DEPLOY_PASSWORD": "environment-only-secret",
        }

    def test_loads_password_only_from_environment_and_redacts_summary(self) -> None:
        config = release_transport.DeploymentConfig.from_env(self.environment())
        summary = json.dumps(config.public_summary())
        self.assertEqual("environment-only-secret", config.password)
        self.assertNotIn("environment-only-secret", summary)
        self.assertEqual("password-env", config.public_summary()["authentication"])

    def test_rejects_host_and_remote_path_injection(self) -> None:
        environment = self.environment()
        environment["CODECOACHAI_DEPLOY_HOST"] = "example.test;touch-pwned"
        with self.assertRaises(ValueError):
            release_transport.DeploymentConfig.from_env(environment)

        environment = self.environment()
        environment["CODECOACHAI_REMOTE_ROOT"] = "/opt/codecoachai;touch-pwned"
        with self.assertRaises(ValueError):
            release_transport.DeploymentConfig.from_env(environment)

    def test_requires_pinned_host_key_inputs_and_one_authentication_source(self) -> None:
        environment = self.environment()
        del environment["CODECOACHAI_DEPLOY_HOST_FINGERPRINT"]
        with self.assertRaisesRegex(ValueError, "FINGERPRINT"):
            release_transport.DeploymentConfig.from_env(environment)

        environment = self.environment()
        identity = self.root / "identity"
        identity.write_text("test", encoding="ascii")
        environment["CODECOACHAI_DEPLOY_IDENTITY_FILE"] = str(identity)
        with self.assertRaisesRegex(ValueError, "exactly one"):
            release_transport.DeploymentConfig.from_env(environment)

    def test_dry_run_does_not_connect(self) -> None:
        stdout = io.StringIO()
        with mock.patch.dict(os.environ, self.environment(), clear=True):
            with mock.patch.object(
                release_transport,
                "connect",
                side_effect=AssertionError("dry-run must not connect"),
            ):
                with contextlib.redirect_stdout(stdout):
                    exit_code = release_transport.main(["status"])
        self.assertEqual(0, exit_code)
        self.assertIn('"execute": false', stdout.getvalue())

    def test_connection_rejects_implicit_credentials_and_checks_fingerprint(self) -> None:
        class FakeKey:
            def asbytes(self) -> bytes:
                return b"pinned-host-key"

        class FakeTransport:
            def is_active(self) -> bool:
                return True

            def get_remote_server_key(self) -> FakeKey:
                return FakeKey()

        class FakeRejectPolicy:
            pass

        class FakeClient:
            def __init__(self) -> None:
                self.host_keys: str | None = None
                self.policy: object | None = None
                self.connect_arguments: dict[str, object] = {}
                self.closed = False

            def load_host_keys(self, path: str) -> None:
                self.host_keys = path

            def set_missing_host_key_policy(self, policy: object) -> None:
                self.policy = policy

            def connect(self, **arguments: object) -> None:
                self.connect_arguments = arguments

            def get_transport(self) -> FakeTransport:
                return FakeTransport()

            def close(self) -> None:
                self.closed = True

        fake_client = FakeClient()
        fake_paramiko = types.SimpleNamespace(
            SSHClient=lambda: fake_client,
            RejectPolicy=FakeRejectPolicy,
        )
        environment = self.environment()
        environment["CODECOACHAI_DEPLOY_HOST_FINGERPRINT"] = (
            release_transport.key_fingerprint(FakeKey())
        )
        config = release_transport.DeploymentConfig.from_env(environment)

        with mock.patch.dict(sys.modules, {"paramiko": fake_paramiko}):
            with release_transport.connect(config) as connected:
                self.assertIs(fake_client, connected)

        self.assertIsInstance(fake_client.policy, FakeRejectPolicy)
        self.assertFalse(fake_client.connect_arguments["allow_agent"])
        self.assertFalse(fake_client.connect_arguments["look_for_keys"])
        self.assertEqual(
            "environment-only-secret",
            fake_client.connect_arguments["password"],
        )
        self.assertTrue(fake_client.closed)

    def test_connection_aborts_on_fingerprint_mismatch(self) -> None:
        class FakeKey:
            def asbytes(self) -> bytes:
                return b"unexpected-host-key"

        class FakeTransport:
            def is_active(self) -> bool:
                return True

            def get_remote_server_key(self) -> FakeKey:
                return FakeKey()

        class FakeClient:
            def load_host_keys(self, path: str) -> None:
                return

            def set_missing_host_key_policy(self, policy: object) -> None:
                return

            def connect(self, **arguments: object) -> None:
                return

            def get_transport(self) -> FakeTransport:
                return FakeTransport()

            def close(self) -> None:
                return

        fake_paramiko = types.SimpleNamespace(
            SSHClient=FakeClient,
            RejectPolicy=object,
        )
        config = release_transport.DeploymentConfig.from_env(self.environment())
        with mock.patch.dict(sys.modules, {"paramiko": fake_paramiko}):
            with self.assertRaisesRegex(RuntimeError, "fingerprint mismatch"):
                with release_transport.connect(config):
                    self.fail("fingerprint mismatch must abort before yielding")


class FakeLinkAttributes:
    st_mode = stat.S_IFLNK | 0o777


class FakeLinkSftp:
    def __init__(self) -> None:
        self.links: dict[str, str] = {}

    def lstat(self, path: str) -> FakeLinkAttributes:
        if path not in self.links:
            raise FileNotFoundError(2, "missing", path)
        return FakeLinkAttributes()

    def symlink(self, target: str, path: str) -> None:
        self.links[path] = target

    def posix_rename(self, source: str, target: str) -> None:
        self.links[target] = self.links.pop(source)

    def readlink(self, path: str) -> str:
        return self.links[path]

    def remove(self, path: str) -> None:
        self.links.pop(path)

    def listdir(self, path: str) -> list[str]:
        return ["release-old", "release-new"]


class LinkOnlyReleaseManager(release_transport.RemoteReleaseManager):
    def verify_remote_release(
        self,
        release_id: str,
    ) -> list[release_common.ManifestEntry]:
        release_common.validate_release_id(release_id)
        return []

    @contextlib.contextmanager
    def _pointer_lock(self):
        yield


class RollbackPointerTest(unittest.TestCase):
    def test_activation_and_rollback_swap_atomic_release_links(self) -> None:
        sftp = FakeLinkSftp()
        sftp.links["/opt/codecoachai/current"] = "releases/release-old"
        manager = LinkOnlyReleaseManager(sftp, "/opt/codecoachai")

        activated = manager.activate("release-new")
        self.assertEqual("releases/release-new", activated["current"])
        self.assertEqual("releases/release-old", activated["previous"])

        rolled_back = manager.rollback()
        self.assertEqual("releases/release-old", rolled_back["current"])
        self.assertEqual("releases/release-new", rolled_back["previous"])


if __name__ == "__main__":
    unittest.main()
