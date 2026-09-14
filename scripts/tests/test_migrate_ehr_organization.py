import importlib.util
from pathlib import Path
import sys
import unittest


SCRIPT = Path(__file__).parents[1] / "migrate_ehr_organization.py"
SPEC = importlib.util.spec_from_file_location("migrate_ehr_organization", SCRIPT)
MODULE = importlib.util.module_from_spec(SPEC)
assert SPEC.loader is not None
sys.modules[SPEC.name] = MODULE
SPEC.loader.exec_module(MODULE)


class MigrateEhrOrganizationTest(unittest.TestCase):
    def test_legacy_null_is_enabled(self):
        self.assertEqual(0, MODULE.target_status(None))
        self.assertEqual(0, MODULE.target_status(b"\x00"))
        self.assertEqual(1, MODULE.target_status(b"\x01"))

    def test_rejects_orphan_department_parent(self):
        companies = [MODULE.Company(1, "001", "Company", 0)]
        departments = [MODULE.Department(10, "D10", "Department", 1, 99, 0)]
        with self.assertRaisesRegex(ValueError, "parent is missing"):
            MODULE.validate_source(companies, departments)

    def test_rejects_duplicate_department_code(self):
        companies = [MODULE.Company(1, "001", "Company", 0)]
        departments = [
            MODULE.Department(10, "D", "First", 1, None, 0),
            MODULE.Department(11, "D", "Second", 1, None, 0),
        ]
        with self.assertRaisesRegex(ValueError, "duplicate department code"):
            MODULE.validate_source(companies, departments)

    def test_rejects_department_parent_cycle(self):
        companies = [MODULE.Company(1, "001", "Company", 0)]
        departments = [
            MODULE.Department(10, "D10", "First", 1, 11, 0),
            MODULE.Department(11, "D11", "Second", 1, 10, 0),
        ]
        with self.assertRaisesRegex(ValueError, "parent cycle"):
            MODULE.validate_source(companies, departments)

    def test_rejects_target_length_overflow(self):
        with self.assertRaisesRegex(ValueError, "exceeds target 30"):
            MODULE.required_text("x" * 31, "depName", 1, 30)


if __name__ == "__main__":
    unittest.main()
