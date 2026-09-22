"""PM-03/PM-11: scope, reference integrity and fresh/upgrade lineage separation."""
import json
from pathlib import Path
import sys
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import generate_project_template_seed_pack as pack
import prepare_fresh_project_template_migrations as fresh


class ProjectTemplateSeedPackTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.asset = json.loads(pack.ASSET.read_text(encoding="utf8"))
        cls.scenarios = pack.derive(cls.asset)

    def test_accepted_snapshot_is_transferred_without_recompiling_or_inventing_facts(self):
        before = json.dumps(self.asset, sort_keys=True)
        rows = pack.rows(self.asset, self.scenarios)
        revision = rows["proj_project_template_revision"][0]
        self.assertEqual(self.asset["revision"]["snapshot"], revision["execution_snapshot"])
        self.assertEqual(self.asset["revision"]["document"], revision["designer_document"])
        self.assertIsNone(revision["snapshot_hash"])
        self.assertEqual(before, json.dumps(self.asset, sort_keys=True))
        self.assertEqual(4, len(rows["plt_business_view_revision"]))
        self.assertEqual({"plt_business_view_revision", "proj_project_template", "proj_project_template_revision"}, set(rows))

    def test_scenario_boundaries_and_no_dangling_gate_references(self):
        for s in self.scenarios:
            d = s["document"]
            pre = s["code"].endswith("_PRE")
            direct = "_DS_" in s["code"]
            engineering = s["code"].endswith("_ENG")
            self.assertEqual(["S0", "S4"] if pre else [f"S{i}" for i in range(7)], [v["code"] for v in d["stages"]])
            self.assertEqual(direct, any(t["code"].endswith("_INITIAL") for t in d["tasks"]))
            self.assertEqual(engineering, any(t["code"].endswith("_KICKOFF") for t in d["tasks"]))
            self.assertEqual("DRAFT", s["status"])
            if pre:
                self.assertTrue(s["explicitSelectionOnly"])
                self.assertEqual({"EXE03", "EXE04"}, {t["code"].rsplit("_", 1)[1] for t in d["tasks"]})
            targets = {"TASK": {t["code"] for t in d["tasks"]}, "DELIVERABLE": {f["code"] for f in d["deliverables"]}}
            for g in d["gates"]:
                for ref in g["references"]:
                    self.assertIn(ref["refCode"], targets[ref["refType"]])
            rules = {r["key"]: r for r in d["rules"]}
            for stage in d["stages"][1:]:
                self.assertEqual("STATE", rules[stage["admissionRuleKey"]]["expression"]["predicate"])
            for deliverable in d["deliverables"]:
                self.assertGreaterEqual(deliverable["configuration"]["minimumQuantity"], 1)
                confirmation = json.dumps(deliverable["configuration"]["confirmationRule"])
                self.assertNotIn("TASK_NATIVE_STATUS", confirmation)

    def test_upgrade_only_retires_exact_seed_identities(self):
        sql = pack.render(self.asset, self.scenarios)
        self.assertEqual(1, sql.count("UPDATE proj_project_template SET"))
        self.assertNotIn("DELETE FROM", sql)
        self.assertNotIn("UPDATE proj_project_template_revision", sql)
        self.assertIn("tenant_id=1 AND deleted=b'0' AND system_reserved=b'0'", sql)
        self.assertIn("ROLLBACK; RESIGNAL", sql)
        self.assertNotIn("DETAIL_REMEDIATION", str(pack.OLD))

    def test_sql_splitter_preserves_semicolons_quotes_and_procedure_body(self):
        sql = "-- ;\nINSERT INTO t VALUES ('a;''b', 'c\\\'d');\nDELIMITER $$\nCREATE PROCEDURE p() BEGIN SELECT 'x;y'; END$$\nDELIMITER ;\nSELECT 1;"
        parts = list(fresh.statements(sql))
        self.assertEqual(sql, "".join(parts))
        self.assertEqual(5, len(parts))
        self.assertIn("SELECT 'x;y'; END$$", parts[2])

    def test_fresh_keeps_ddl_and_platform_seeds_but_omits_old_projects(self):
        for path in fresh.SOURCE.glob("V*.sql"):
            version = int(path.name.split("__")[0][1:].split("_")[0])
            original = path.read_text(encoding="utf-8-sig")
            projected = fresh.project(path.name, original)
            if version in fresh.OMIT:
                self.assertFalse(any(word in projected for word in ("INSERT", "UPDATE", "DELETE", "CREATE")))
            elif version in fresh.FILTER:
                for statement in fresh.statements(projected):
                    self.assertFalse(fresh.project_fixture(statement, version))
                self.assertIn("system_" if version != 105 else "plt_dynamic_form_template", projected)
            elif version == 160:
                self.assertEqual(10, projected.count("CREATE TABLE"))
                self.assertIn("RENAME TABLE", projected)
                self.assertNotIn("INSERT INTO", projected)
            elif version == 289:
                self.assertIn("CREATE TABLE IF NOT EXISTS", projected)
                self.assertNotIn("RENAME TABLE", projected)
                self.assertNotIn("RENAME COLUMN", projected)
            else:
                self.assertEqual(original, projected)


if __name__ == "__main__":
    unittest.main()
