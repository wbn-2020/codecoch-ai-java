import unittest
from pathlib import Path


REPO_ROOT = Path(__file__).resolve().parents[3]
DOCKERFILE = REPO_ROOT / "Dockerfile"
LOCAL_COMPOSE = REPO_ROOT / "docker-compose.yml"
RELEASE_COMPOSE = REPO_ROOT / "docker-compose.release.yml"
PDF_RENDERER = (
    REPO_ROOT
    / "codecoachai-core"
    / "src"
    / "main"
    / "java"
    / "com"
    / "codecoachai"
    / "resume"
    / "export"
    / "PdfResumeDocumentRenderer.java"
)
RUNBOOK = REPO_ROOT / "docs" / "operations" / "release-engineering-runbook.md"
STABLE_FONT_PATH = "/opt/codecoachai/fonts/NotoSansCJK-Regular.ttc"


class PdfFontDeploymentContractTest(unittest.TestCase):
    def test_runtime_image_and_core_compose_share_a_stable_cjk_font_path(self) -> None:
        dockerfile = DOCKERFILE.read_text(encoding="utf-8")
        local_compose = LOCAL_COMPOSE.read_text(encoding="utf-8")
        release_compose = RELEASE_COMPOSE.read_text(encoding="utf-8")
        renderer = PDF_RENDERER.read_text(encoding="utf-8")
        runbook = RUNBOOK.read_text(encoding="utf-8")

        self.assertIn("fontconfig font-noto-cjk", dockerfile)
        self.assertIn(
            'ln -s "$font_path" /opt/codecoachai/fonts/NotoSansCJK-Regular.ttc',
            dockerfile,
        )
        self.assertIn("fc-cache -f", dockerfile)
        self.assertIn(f"RESUME_EXPORT_PDF_FONT_PATH: {STABLE_FONT_PATH}", local_compose)
        self.assertIn(f"RESUME_EXPORT_PDF_FONT_PATH: {STABLE_FONT_PATH}", release_compose)
        self.assertIn(f'"{STABLE_FONT_PATH}"', renderer)
        self.assertIn("### PDF CJK Font Gate", runbook)
        self.assertIn("fc-scan", runbook)


if __name__ == "__main__":
    unittest.main()
