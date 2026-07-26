from __future__ import annotations

import argparse
import pathlib
import sys

from release_common import verify_release


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(
        description="Verify a CodeCoachAI release directory and SHA-256 manifest."
    )
    parser.add_argument("release_dir", type=pathlib.Path)
    arguments = parser.parse_args(argv)
    try:
        entries = verify_release(arguments.release_dir)
    except (OSError, ValueError) as exception:
        print(f"Release verification failed: {exception}", file=sys.stderr)
        return 1
    print(f"Verified {len(entries)} release files")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
