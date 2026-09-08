from __future__ import annotations
import hashlib
import importlib.util
from pathlib import Path
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]
def load(name, path):
    spec=importlib.util.spec_from_file_location(name,path)
    module=importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module
trace=load("trace016",ROOT/"scripts/generate_requirement_traceability.py")
validator=load("validator016",ROOT/"scripts/validate_prd_revision_016_alignment.py")

class BaselineQualificationTests(unittest.TestCase):
    def test_historical_full_is_not_current_complete_when_pending(self):
        mappings=[{"coverage":"FULL","task_status":"COMPLETE","revalidation_required":True}]
        self.assertEqual("REVALIDATION_REQUIRED",trace.derived_status(mappings))
        self.assertEqual("COMPLETE",mappings[0]["task_status"])
    def test_unaffected_done_is_preserved(self):
        self.assertEqual("IMPLEMENTATION_COMPLETE",trace.derived_status([{"coverage":"FULL","task_status":"COMPLETE"}]))
    def test_partial_done_does_not_close_requirement(self):
        self.assertEqual("IMPLEMENTATION_PARTIAL",trace.derived_status([{"coverage":"PARTIAL","task_status":"COMPLETE"}]))
    def test_no_task_does_not_close_requirement(self):
        self.assertEqual("NOT_STARTED",trace.derived_status([{"coverage":"FULL","task_status":"NO_TASK"}]))
    def test_revision_and_hash_are_derived_not_constant(self):
        with tempfile.TemporaryDirectory() as temp:
            path=Path(temp)/"prd.md"
            raw=b"| V1.8\xe4\xbf\xae\xe8\xae\xa2017 | 2026-09-07 | owner | `CHG-PRD-2026-09-07-017` |\n"
            path.write_bytes(raw)
            identity=trace.baseline_identity(path)
            self.assertEqual("017",identity["revision"])
            self.assertEqual(hashlib.sha1(b"blob "+str(len(raw)).encode()+b"\0"+raw).hexdigest(),identity["gitBlob"])
    def test_unknown_revalidation_key_fails_closed(self):
        with self.assertRaises(SystemExit):
            trace.feature_revalidation_slices("> PRD差量重验证：`BOGUS-01@V1`",{"PM-03@V1"})
    def test_valid_revalidation_keys_are_precise(self):
        self.assertEqual({"PM-03@V1"},trace.feature_revalidation_slices("> PRD差量重验证：`PM-03@V1`",{"PM-03@V1","PM-01@V1"}))
    def test_missing_gate_is_unknown_not_approved(self):
        with tempfile.TemporaryDirectory() as temp:
            self.assertTrue(all(item["当前结论"]=="UNKNOWN" for item in trace.engineering_gate_states(Path(temp)/"prd.md").values()))
    def test_prd_all_sixteen_static_regressions(self):
        text=(ROOT/"docs/baseline/prd-v1.8.md").read_text()
        self.assertEqual([], [key for key, value in validator.inspect_prd(text).items() if not value])
    def test_reintroduced_save_advance_is_detected(self):
        text=(ROOT/"docs/baseline/prd-v1.8.md").read_text()
        bad=text.replace("暂存不触发流程推进", "采集清单保存后通过任务状态流转自动进入P4",1)
        self.assertFalse(validator.inspect_prd(bad)["R05"])
    def test_reintroduced_signature_or_key_is_detected(self):
        text=(ROOT/"docs/baseline/prd-v1.8.md").read_text()
        bad=text.replace("必须同时校验来源身份", "需校验来源签名或幂等键",1)
        self.assertFalse(validator.inspect_prd(bad)["R09"])
    def test_reintroduced_sixth_stage_is_detected(self):
        text=(ROOT/"docs/baseline/prd-v1.8.md").read_text()
        self.assertTrue(validator.inspect_prd(text)["R01"])
        marker="售前预置模板配置`S0→S4`"
        self.assertIn(marker,text)
        bad=text.replace(marker, "售前预置模板配置`S0→S4→S6`",1)
        self.assertFalse(validator.inspect_prd(bad)["R01"])

    def test_failed_conclusion_still_cannot_satisfy_acceptance(self):
        text=(ROOT/"docs/baseline/prd-v1.8.md").read_text()
        self.assertTrue(validator.inspect_prd(text)["R04"])
        bad=text.replace("不能满足通过谓词", "可以满足通过谓词",1)
        self.assertFalse(validator.inspect_prd(bad)["R04"])

    def test_new_scope_cannot_create_second_current_report(self):
        text=(ROOT/"docs/baseline/prd-v1.8.md").read_text()
        self.assertTrue(validator.inspect_prd(text)["R12"])
        bad=text.replace("不新建第二个当前报告", "允许新建第二个当前报告",1)
        self.assertFalse(validator.inspect_prd(bad)["R12"])

if __name__ == "__main__":
    unittest.main()
