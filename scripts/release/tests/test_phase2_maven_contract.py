import unittest
import xml.etree.ElementTree as ET
from pathlib import Path


REPO_ROOT = Path(__file__).resolve().parents[3]
ROOT_POM = REPO_ROOT / "pom.xml"
CORE_POM = REPO_ROOT / "codecoachai-core" / "pom.xml"
NS = {"m": "http://maven.apache.org/POM/4.0.0"}

FINAL_REACTOR_MODULES = {
    "codecoachai-common",
    "codecoachai-core",
    "codecoachai-gateway",
    "codecoachai-ai",
    "codecoachai-search",
}
LEGACY_MODULES = {
    "codecoachai-auth",
    "codecoachai-user",
    "codecoachai-system",
    "codecoachai-file",
    "codecoachai-question",
    "codecoachai-resume",
    "codecoachai-interview",
    "codecoachai-task",
}
TARGET_CORE_DEPENDENCIES = {
    ("com.codecoachai", "common-core"),
    ("com.codecoachai", "common-web"),
    ("com.codecoachai", "common-security"),
    ("com.codecoachai", "common-mybatis"),
    ("com.codecoachai", "common-redis"),
    ("com.codecoachai", "common-feign"),
    ("com.codecoachai", "common-oss"),
    ("com.codecoachai", "common-mq"),
    ("com.codecoachai", "common-vector"),
    ("org.springframework.boot", "spring-boot-starter-aop"),
    ("org.springframework.boot", "spring-boot-starter-data-redis"),
    ("org.springframework.boot", "spring-boot-starter-jdbc"),
    ("org.springframework.boot", "spring-boot-starter-mail"),
    ("org.springframework.boot", "spring-boot-starter-validation"),
    ("org.springframework.boot", "spring-boot-starter-web"),
    ("org.springframework.cloud", "spring-cloud-starter-openfeign"),
    ("com.alibaba.cloud", "spring-cloud-starter-alibaba-nacos-discovery"),
    ("com.alibaba.cloud", "spring-cloud-starter-alibaba-nacos-config"),
    ("com.mysql", "mysql-connector-j"),
    ("cn.dev33", "sa-token-spring-boot3-starter"),
    ("cn.dev33", "sa-token-redis-jackson"),
    ("org.apache.commons", "commons-pool2"),
    ("org.apache.rocketmq", "rocketmq-spring-boot-starter"),
    (
        "com.github.xiaoymin",
        "knife4j-openapi3-jakarta-spring-boot-starter",
    ),
    ("com.alibaba", "easyexcel"),
    ("org.apache.poi", "poi-ooxml"),
    ("org.apache.poi", "poi-scratchpad"),
    ("org.apache.pdfbox", "pdfbox"),
    ("com.baomidou", "mybatis-plus-spring-boot3-starter"),
    ("org.springframework.security", "spring-security-crypto"),
    ("com.fasterxml.jackson.core", "jackson-databind"),
    ("io.github.openfeign", "feign-core"),
    ("io.swagger.core.v3", "swagger-annotations-jakarta"),
    ("org.projectlombok", "lombok"),
}
CONVERGENCE_EXCLUDES = {
    "org.apache.httpcomponents:httpclient",
    "com.alibaba.fastjson2:fastjson2",
    "com.squareup.okio:okio-jvm",
    "com.google.errorprone:error_prone_annotations",
}
UPPER_BOUND_EXCLUDES = CONVERGENCE_EXCLUDES | {"org.yaml:snakeyaml"}
COMMONS_LOGGING_EXCLUSION_TARGETS = {
    ("org.apache.pdfbox", "pdfbox"),
    ("com.aliyun.oss", "aliyun-sdk-oss"),
    ("com.aliyun", "aliyun-java-sdk-sts"),
    ("com.aliyun", "aliyun-java-sdk-core"),
    ("org.apache.rocketmq", "rocketmq-spring-boot-starter"),
}


def parse(path: Path) -> ET.Element:
    return ET.parse(path).getroot()


def child_text(element: ET.Element, name: str) -> str:
    child = element.find(f"m:{name}", NS)
    return "" if child is None or child.text is None else child.text.strip()


def dependency_coordinates(element: ET.Element) -> tuple[str, str]:
    return child_text(element, "groupId"), child_text(element, "artifactId")


class Phase2MavenContractTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.root = parse(ROOT_POM)
        cls.core = parse(CORE_POM)

    def test_reactor_and_transitional_core_artifacts_stay_in_lockstep(self) -> None:
        modules = [
            element.text.strip()
            for element in self.root.findall("m:modules/m:module", NS)
            if element.text
        ]
        self.assertEqual(len(modules), len(set(modules)), "root modules must be unique")
        self.assertTrue(FINAL_REACTOR_MODULES.issubset(modules))
        self.assertEqual(
            set(modules),
            FINAL_REACTOR_MODULES | (set(modules) & LEGACY_MODULES),
            "phase two permits only final modules plus not-yet-migrated legacy modules",
        )

        core_dependencies = self.core.findall("m:dependencies/m:dependency", NS)
        legacy_artifacts = {
            artifact_id
            for group_id, artifact_id in map(
                dependency_coordinates, core_dependencies
            )
            if group_id == "com.codecoachai" and artifact_id in LEGACY_MODULES
        }
        self.assertEqual(
            set(modules) & LEGACY_MODULES,
            legacy_artifacts,
            "legacy root modules and Core transitional artifact dependencies must match",
        )

    def test_core_target_direct_dependencies_are_frozen(self) -> None:
        dependencies = self.core.findall("m:dependencies/m:dependency", NS)
        coordinates = [dependency_coordinates(element) for element in dependencies]
        self.assertEqual(
            len(coordinates),
            len(set(coordinates)),
            "Core direct dependencies must not contain duplicates",
        )
        target_coordinates = {
            coordinate
            for coordinate in coordinates
            if coordinate[1] not in LEGACY_MODULES
        }
        self.assertEqual(TARGET_CORE_DEPENDENCIES, target_coordinates)

        by_coordinate = {
            dependency_coordinates(element): element for element in dependencies
        }
        self.assertEqual(
            "runtime",
            child_text(by_coordinate[("com.mysql", "mysql-connector-j")], "scope"),
        )
        self.assertEqual(
            "true",
            child_text(by_coordinate[("org.projectlombok", "lombok")], "optional"),
        )

        for coordinate, element in by_coordinate.items():
            version = child_text(element, "version")
            if coordinate[0] == "com.codecoachai":
                self.assertEqual("${project.version}", version)
            else:
                self.assertEqual(
                    "",
                    version,
                    f"external dependency {coordinate} must use root dependency management",
                )

    def test_phase2_managed_versions_and_logging_exclusions_are_stable(self) -> None:
        properties = self.root.find("m:properties", NS)
        self.assertIsNotNone(properties)
        expected_properties = {
            "maven-enforcer.version": "3.6.3",
            "maven-dependency.version": "3.8.1",
            "poi.version": "5.4.1",
            "commons-io.version": "2.18.0",
            "bouncycastle.version": "1.80.2",
            "swagger.version": "2.2.19",
        }
        for name, expected in expected_properties.items():
            element = properties.find(f"m:{name}", NS)
            self.assertIsNotNone(element, f"missing root property {name}")
            self.assertEqual(expected, element.text.strip())

        managed = {
            dependency_coordinates(element): element
            for element in self.root.findall(
                "m:dependencyManagement/m:dependencies/m:dependency", NS
            )
        }
        commons_logging = ("commons-logging", "commons-logging")
        self.assertNotIn(
            commons_logging,
            managed,
            "Spring Boot must use spring-jcl instead of managing commons-logging.jar",
        )
        for coordinate in COMMONS_LOGGING_EXCLUSION_TARGETS:
            self.assertIn(coordinate, managed)
            exclusions = {
                dependency_coordinates(element)
                for element in managed[coordinate].findall(
                    "m:exclusions/m:exclusion", NS
                )
            }
            self.assertIn(
                commons_logging,
                exclusions,
                f"{coordinate} must exclude commons-logging",
            )

    def test_phase2_surefire_reports_directory_is_cli_overridable(self) -> None:
        properties = self.root.find("m:properties", NS)
        self.assertIsNotNone(properties)
        reports_property = properties.find(
            "m:phase2.surefire.reportsDirectory",
            NS,
        )
        self.assertIsNotNone(reports_property)
        self.assertEqual(
            "${project.build.directory}/surefire-reports",
            reports_property.text.strip(),
        )

        surefire_plugins = [
            plugin
            for plugin in self.root.findall("m:build/m:plugins/m:plugin", NS)
            if dependency_coordinates(plugin)
            == ("org.apache.maven.plugins", "maven-surefire-plugin")
        ]
        self.assertEqual(1, len(surefire_plugins))
        reports_directory = surefire_plugins[0].find(
            "m:configuration/m:reportsDirectory",
            NS,
        )
        self.assertIsNotNone(reports_directory)
        self.assertEqual(
            "${phase2.surefire.reportsDirectory}",
            reports_directory.text.strip(),
        )

    def test_enforcer_profile_has_only_reviewed_exceptions(self) -> None:
        profiles = self.root.findall("m:profiles/m:profile", NS)
        profile = next(
            (
                candidate
                for candidate in profiles
                if child_text(candidate, "id") == "phase2-dependency-gates"
            ),
            None,
        )
        self.assertIsNotNone(profile)
        self.assertIsNone(
            profile.find("m:activation", NS),
            "phase two gates must stay opt-in until the migration is complete",
        )

        convergence = profile.find(".//m:dependencyConvergence", NS)
        upper_bound = profile.find(".//m:requireUpperBoundDeps", NS)
        self.assertIsNotNone(convergence)
        self.assertIsNotNone(upper_bound)

        execution = profile.find(
            ".//m:execution[m:id='phase2-dependency-convergence']",
            NS,
        )
        self.assertIsNotNone(execution)
        self.assertEqual("verify", child_text(execution, "phase"))
        self.assertEqual(
            ["enforce"],
            [
                goal.text.strip()
                for goal in execution.findall("m:goals/m:goal", NS)
                if goal.text
            ],
        )

        convergence_excludes = {
            element.text.strip()
            for element in convergence.findall("m:excludes/m:exclude", NS)
            if element.text
        }
        upper_bound_excludes = {
            element.text.strip()
            for element in upper_bound.findall("m:excludes/m:exclude", NS)
            if element.text
        }
        self.assertEqual(CONVERGENCE_EXCLUDES, convergence_excludes)
        self.assertEqual(UPPER_BOUND_EXCLUDES, upper_bound_excludes)


if __name__ == "__main__":
    unittest.main()
