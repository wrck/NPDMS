import subprocess
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
LOCKED_INPUT = "6719ab94"
# The approved inspection retirement removes old runtime code; executed migrations remain immutable.
HISTORICAL_MIGRATIONS = (
    "sql/migrations/V14__pms_service_tables.sql",
    "sql/migrations/V15__pms_service_menus.sql",
    "sql/migrations/V16__pms_business_button_permissions.sql",
    "sql/migrations/V19__pms_test_data.sql",
    "sql/migrations/V20__pms_test_data_expansion.sql",
    "sql/migrations/V43__pms_dict_types.sql",
)


class FIns001LegacyPreservationTest(unittest.TestCase):

    def test_executed_legacy_migrations_remain_unchanged(self):
        committed = subprocess.run(
            ["git", "diff", "--name-only", f"{LOCKED_INPUT}..HEAD", "--", *HISTORICAL_MIGRATIONS],
            cwd=ROOT,
            check=True,
            capture_output=True,
            text=True).stdout.strip()
        working_tree = subprocess.run(
            ["git", "status", "--porcelain", "--", *HISTORICAL_MIGRATIONS],
            cwd=ROOT,
            check=True,
            capture_output=True,
            text=True).stdout.strip()
        self.assertEqual("", committed)
        self.assertEqual("", working_tree)

    def test_new_package_does_not_depend_on_legacy_rule_runtime(self):
        source_root = ROOT / "pms-module-service/src/main/java/cn/iocoder/yudao/module/pms/service"
        sources = []
        for folder in source_root.rglob("inspectionrule"):
            sources.extend(path.read_text(encoding="utf-8-sig") for path in folder.rglob("*.java"))
        combined = "\n".join(sources)
        self.assertNotIn("SrvRuleService", combined)
        self.assertNotIn("SrvRuleMapper", combined)
        self.assertNotIn("pms_srv_rule", combined)


if __name__ == "__main__":
    unittest.main()
