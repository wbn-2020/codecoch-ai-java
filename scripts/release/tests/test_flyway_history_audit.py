from __future__ import annotations

import pathlib
import sys
import tempfile
import unittest

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parents[1]))
import audit_flyway_history as auditor


class HistoryAuditTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = pathlib.Path(self.temp.name)
        self.sql = self.root / "sql"
        self.sql.mkdir()
        for version in (142, 143, 144):
            (self.sql / f"V4_{version}__fixture.sql").write_text("SELECT 1;", encoding="utf-8")
        self.history = self.root / "history.tsv"
        self.rows = ["1\t2.999\tBASELINE\tbaseline\tNULL\t1"] + [
            f"{index}\t4.{version}\tSQL\tV4_{version}__fixture.sql\t-123\t1"
            for index, version in enumerate((142, 143, 144), 2)
        ]

    def audit(self, rows=None, required=None):
        self.history.write_text(
            "\t".join(auditor.FIELDS) + "\n" + "\n".join(self.rows if rows is None else rows) + "\n",
            encoding="utf-8",
        )
        return auditor.audit(self.history, self.sql, required or [])

    def test_expected_baseline_and_signed_checksums(self):
        self.assertEqual(4, self.audit(required=["4.142", "4.143", "4.144"]))

    def test_manual_null_sql_history_blocks(self):
        for null in ("NULL", "\\N", ""):
            with self.subTest(null=null), self.assertRaisesRegex(ValueError, "checksum is NULL"):
                self.audit([row.replace("-123", null) for row in self.rows])

    def test_failed_missing_and_duplicate_history(self):
        with self.assertRaisesRegex(ValueError, "not successful"):
            self.audit(self.rows[:-1] + [self.rows[-1][:-1] + "0"])
        with self.assertRaisesRegex(ValueError, "required.*missing"):
            self.audit(self.rows[:-1], ["4.144"])
        with self.assertRaisesRegex(ValueError, "duplicate normalized"):
            self.audit(self.rows + ["5\t4.0144.0\tSQL\tV4_144__fixture.sql\t123\t1"])
        with self.assertRaisesRegex(ValueError, "installed_rank"):
            self.audit(self.rows + [self.rows[-1]])

    def test_malformed_unknown_and_invalid_checksums(self):
        for value in ("2147483648", "-2147483649", "abc"):
            with self.subTest(value=value), self.assertRaisesRegex(ValueError, "32-bit"):
                self.audit([row.replace("-123", value) for row in self.rows])
        with self.assertRaisesRegex(ValueError, "does not match"):
            self.audit([row.replace("V4_144__fixture.sql", "V4_144__other.sql") for row in self.rows])
        with self.assertRaisesRegex(ValueError, "malformed"):
            self.audit(["1\t4.142"])
        with self.assertRaisesRegex(ValueError, "empty history"):
            self.audit([])
        with self.assertRaisesRegex(ValueError, "unsupported"):
            self.audit([row.replace("\tSQL\t", "\tDELETE\t") for row in self.rows])

    def test_baseline_is_not_a_null_checksum_escape(self):
        with self.assertRaisesRegex(ValueError, "unexpected baseline"):
            self.audit(["1\t4.144\tBASELINE\tbaseline\tNULL\t1"])
        with self.assertRaisesRegex(ValueError, "unexpected baseline"):
            self.audit([self.rows[1], "5\t2.999\tBASELINE\tbaseline\tNULL\t1"])

    def test_duplicate_local_versions_block(self):
        (self.sql / "V4_0144__duplicate.sql").write_text("SELECT 1;", encoding="utf-8")
        with self.assertRaisesRegex(ValueError, "duplicate migration version"):
            self.audit()

    def test_cli_missing_input_fails_closed(self):
        self.assertEqual(1, auditor.main([
            "--history", str(self.root / "missing.tsv"), "--migrations", str(self.sql)
        ]))


if __name__ == "__main__":
    unittest.main()
