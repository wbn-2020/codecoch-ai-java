"""Offline, read-only audit of a complete MySQL Flyway history TSV export.

No database client, network access, migration or repair is provided here.
Checksum equality remains the responsibility of the pinned Flyway validate goal.
"""
from __future__ import annotations

import argparse
import csv
import pathlib
import re
import sys

FIELDS = ["installed_rank", "version", "type", "script", "checksum", "success"]
NULLS = {"", "NULL", "\\N"}
VERSION = re.compile(r"[0-9]+(?:[._][0-9]+)*")


def version_key(value: str) -> tuple[int, ...]:
    if not VERSION.fullmatch(value):
        raise ValueError(f"invalid numeric migration version: {value!r}")
    parts = [int(part) for part in value.replace("_", ".").split(".")]
    while len(parts) > 1 and parts[-1] == 0:
        parts.pop()
    return tuple(parts)


def audit(history: pathlib.Path, migrations: pathlib.Path,
          required_versions: list[str]) -> int:
    scripts: dict[tuple[int, ...], str] = {}
    if not migrations.is_dir():
        raise ValueError("migration directory is missing")
    for path in sorted(migrations.rglob("V*.sql")):
        match = re.fullmatch(r"V([0-9._]+)__(.+)\.sql", path.name)
        if not match:
            raise ValueError(f"invalid migration filename: {path.name}")
        key = version_key(match[1])
        if key in scripts:
            raise ValueError(f"duplicate migration version: {path.name}")
        scripts[key] = path.name
    if not scripts:
        raise ValueError("no versioned SQL migrations found")

    seen: set[tuple[int, ...]] = set()
    applied: set[tuple[int, ...]] = set()
    last_rank = -1
    count = 0
    baseline_seen = False
    with history.open(encoding="utf-8-sig", newline="") as stream:
        reader = csv.DictReader(stream, delimiter="\t", quoting=csv.QUOTE_NONE)
        if reader.fieldnames != FIELDS:
            raise ValueError(f"expected complete TSV with header: {FIELDS}")
        for line, row in enumerate(reader, 2):
            if None in row or any(value is None for value in row.values()):
                raise ValueError(f"line {line}: malformed history row")
            label = f"line {line}, version {row['version']}"
            rank_text = row["installed_rank"]
            if not re.fullmatch(r"[0-9]+", rank_text):
                raise ValueError(f"{label}: invalid installed_rank")
            rank = int(rank_text)
            if rank <= last_rank:
                raise ValueError(f"{label}: duplicate or unordered installed_rank")
            last_rank = rank
            count += 1
            if row["success"] != "1":
                raise ValueError(f"{label}: migration is not successful")
            key = version_key(row["version"])
            if key in seen:
                raise ValueError(f"{label}: duplicate normalized version")
            seen.add(key)
            if row["type"] == "BASELINE":
                if baseline_seen or count != 1 or key != version_key("2.999"):
                    raise ValueError(f"{label}: unexpected baseline; manual review required")
                if row["checksum"] not in NULLS:
                    raise ValueError(f"{label}: baseline must not have a SQL checksum")
                baseline_seen = True
                continue
            if row["type"] != "SQL":
                raise ValueError(f"{label}: unsupported history type; manual review required")
            checksum = row["checksum"]
            if checksum in NULLS:
                raise ValueError(f"{label}: SQL checksum is NULL; release blocked")
            if not re.fullmatch(r"-?[0-9]+", checksum) or not -(2**31) <= int(checksum) < 2**31:
                raise ValueError(f"{label}: invalid signed 32-bit SQL checksum")
            if scripts.get(key) != row["script"]:
                raise ValueError(f"{label}: SQL version/script does not match candidate")
            if baseline_seen and key <= version_key("2.999"):
                raise ValueError(f"{label}: SQL version is at or below baseline")
            applied.add(key)
    if not count:
        raise ValueError("empty history; initialization requires separate review")
    for value in required_versions:
        if version_key(value) not in applied:
            raise ValueError(f"required successful SQL version is missing: {value}")
    return count


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--history", required=True, type=pathlib.Path)
    parser.add_argument("--migrations", required=True, type=pathlib.Path)
    parser.add_argument("--require-version", action="append", default=[])
    args = parser.parse_args(argv)
    try:
        count = audit(args.history, args.migrations, args.require_version)
    except (OSError, ValueError, UnicodeError, csv.Error) as exception:
        print(f"History audit BLOCKED: {exception}", file=sys.stderr)
        return 1
    print(f"History shape audit passed ({count} rows); checksum equality, export "
          "provenance, schema and data still require approved verification.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
