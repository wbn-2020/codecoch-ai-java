from __future__ import annotations

import argparse
import dataclasses
import json
import os
import re
import subprocess
import sys
import time
import urllib.error
import urllib.parse
import urllib.request


SERVICE_NAME_PATTERN = re.compile(r"^[A-Za-z0-9][A-Za-z0-9._-]{0,63}$")
MAX_BODY_BYTES = 64 * 1024


class NoRedirectHandler(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None


@dataclasses.dataclass(frozen=True)
class ServiceTarget:
    name: str
    url: str
    container: str | None


@dataclasses.dataclass
class HealthResult:
    name: str
    url: str
    healthy: bool
    classification: str
    detail: str
    attempts: int
    container: str | None = None
    container_status: str | None = None
    container_health: str | None = None


def parse_mapping(values: list[str], label: str) -> dict[str, str]:
    parsed: dict[str, str] = {}
    for value in values:
        name, separator, target = value.partition("=")
        if not separator or not SERVICE_NAME_PATTERN.fullmatch(name) or not target:
            raise ValueError(f"{label} must use NAME=VALUE with a safe NAME")
        if name in parsed:
            raise ValueError(f"duplicate {label} name: {name}")
        parsed[name] = target
    return parsed


def environment_mappings(name: str) -> list[str]:
    raw_value = os.environ.get(name, "")
    return [item.strip() for item in raw_value.split(",") if item.strip()]


def validate_health_url(value: str) -> str:
    parsed = urllib.parse.urlsplit(value)
    if parsed.scheme not in {"http", "https"} or not parsed.hostname:
        raise ValueError(f"health URL must use http or https: {value}")
    if parsed.username or parsed.password or parsed.query or parsed.fragment:
        raise ValueError(f"health URL contains unsupported fields: {value}")
    return value


def get_container_state(container: str) -> tuple[str | None, str | None, str]:
    try:
        result = subprocess.run(
            [
                "docker",
                "inspect",
                "--format",
                "{{json .State}}",
                container,
            ],
            check=False,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            text=True,
            encoding="utf-8",
            errors="replace",
            timeout=10,
        )
    except FileNotFoundError:
        return None, None, "docker command is unavailable"
    except subprocess.TimeoutExpired:
        return None, None, "docker inspect timed out"

    if result.returncode != 0:
        detail = result.stderr.strip() or f"docker inspect exited {result.returncode}"
        return None, None, detail
    try:
        state = json.loads(result.stdout)
    except json.JSONDecodeError:
        return None, None, "docker inspect returned malformed JSON"
    health = state.get("Health") if isinstance(state, dict) else None
    return (
        state.get("Status") if isinstance(state, dict) else None,
        health.get("Status") if isinstance(health, dict) else None,
        "",
    )


def probe_actuator(url: str, timeout_seconds: float) -> tuple[bool, str]:
    request = urllib.request.Request(
        url,
        headers={"Accept": "application/json"},
        method="GET",
    )
    opener = urllib.request.build_opener(NoRedirectHandler())
    try:
        with opener.open(request, timeout=timeout_seconds) as response:
            status_code = response.status
            body = response.read(MAX_BODY_BYTES + 1)
    except urllib.error.HTTPError as exception:
        return False, f"HTTP {exception.code}"
    except (urllib.error.URLError, TimeoutError, OSError) as exception:
        return False, str(exception)

    if status_code < 200 or status_code >= 300:
        return False, f"HTTP {status_code}"
    if len(body) > MAX_BODY_BYTES:
        return False, "health response exceeds 64 KiB"
    try:
        payload = json.loads(body.decode("utf-8"))
    except (UnicodeDecodeError, json.JSONDecodeError):
        return False, "health response is not valid UTF-8 JSON"
    actuator_status = payload.get("status") if isinstance(payload, dict) else None
    if str(actuator_status or "").upper() != "UP":
        return False, f"Actuator status is {actuator_status!r}"
    return True, "Actuator status is UP"


def check_target(
    target: ServiceTarget,
    attempts: int,
    interval_seconds: float,
    timeout_seconds: float,
) -> HealthResult:
    detail = ""
    for attempt in range(1, attempts + 1):
        healthy, detail = probe_actuator(target.url, timeout_seconds)
        if healthy:
            container_status = None
            container_health = None
            if target.container:
                container_status, container_health, _ = get_container_state(
                    target.container
                )
            return HealthResult(
                name=target.name,
                url=target.url,
                healthy=True,
                classification="healthy",
                detail=detail,
                attempts=attempt,
                container=target.container,
                container_status=container_status,
                container_health=container_health,
            )
        if attempt < attempts:
            time.sleep(interval_seconds)

    container_status = None
    container_health = None
    container_detail = ""
    if target.container:
        container_status, container_health, container_detail = get_container_state(
            target.container
        )
    classification = "application-unhealthy"
    if container_status == "running":
        classification = "false-up"
    if container_detail:
        detail = f"{detail}; container inspection: {container_detail}"
    return HealthResult(
        name=target.name,
        url=target.url,
        healthy=False,
        classification=classification,
        detail=detail,
        attempts=attempts,
        container=target.container,
        container_status=container_status,
        container_health=container_health,
    )


def parse_args(argv: list[str] | None = None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description=(
            "Check parameterized Spring Boot Actuator endpoints and flag "
            "containers that are running while the application is not healthy."
        )
    )
    parser.add_argument(
        "--service",
        action="append",
        default=[],
        metavar="NAME=URL",
        help="Repeat for each Actuator endpoint.",
    )
    parser.add_argument(
        "--container",
        action="append",
        default=[],
        metavar="NAME=CONTAINER",
        help="Optional Docker container mapping for false-Up classification.",
    )
    parser.add_argument("--attempts", type=int, default=3)
    parser.add_argument("--interval-seconds", type=float, default=2.0)
    parser.add_argument("--timeout-seconds", type=float, default=3.0)
    return parser.parse_args(argv)


def main(argv: list[str] | None = None) -> int:
    arguments = parse_args(argv)
    try:
        service_values = arguments.service or environment_mappings(
            "CODECOACHAI_HEALTH_SERVICES"
        )
        container_values = arguments.container or environment_mappings(
            "CODECOACHAI_HEALTH_CONTAINERS"
        )
        services = parse_mapping(service_values, "service")
        containers = parse_mapping(container_values, "container")
        if not services:
            raise ValueError(
                "at least one --service or CODECOACHAI_HEALTH_SERVICES entry is required"
            )
        unknown_containers = sorted(set(containers) - set(services))
        if unknown_containers:
            raise ValueError(
                f"container mappings have no matching service: {unknown_containers}"
            )
        if arguments.attempts < 1 or arguments.attempts > 20:
            raise ValueError("--attempts must be between 1 and 20")
        if arguments.interval_seconds < 0 or arguments.interval_seconds > 60:
            raise ValueError("--interval-seconds must be between 0 and 60")
        if arguments.timeout_seconds <= 0 or arguments.timeout_seconds > 60:
            raise ValueError("--timeout-seconds must be greater than 0 and at most 60")

        targets = [
            ServiceTarget(
                name=name,
                url=validate_health_url(url),
                container=containers.get(name),
            )
            for name, url in services.items()
        ]
        results = [
            check_target(
                target,
                attempts=arguments.attempts,
                interval_seconds=arguments.interval_seconds,
                timeout_seconds=arguments.timeout_seconds,
            )
            for target in targets
        ]
    except ValueError as exception:
        print(f"Health check configuration failed: {exception}", file=sys.stderr)
        return 2

    print(
        json.dumps(
            [dataclasses.asdict(result) for result in results],
            ensure_ascii=True,
            indent=2,
            sort_keys=True,
        )
    )
    return 0 if all(result.healthy for result in results) else 1


if __name__ == "__main__":
    raise SystemExit(main())
