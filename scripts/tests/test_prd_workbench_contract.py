from __future__ import annotations
import sys
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / 'scripts'))
from prd_workbench_contract import current_workbench_contract
from validate_prd_baseline import requirement_blocks, project_workbench_contract


class WorkbenchContractTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.text = (ROOT / 'docs/baseline/prd-v1.8.md').read_text(encoding='utf-8-sig')
        cls.blocks = dict(requirement_blocks(cls.text))

    def check(self, replacement=None):
        blocks = dict(self.blocks)
        if replacement:
            req, old, new = replacement
            self.assertIn(old, blocks[req])
            blocks[req] = blocks[req].replace(old,new)
        return current_workbench_contract(*(blocks[key] for key in ('PM-03','PM-11','CUT-01','CUT-03')))

    def test_current_contract_and_baseline_integration(self):
        results = self.check()
        self.assertEqual([], [key for key, value in results.items() if not value])
        self.assertEqual(results, project_workbench_contract(self.text))

    def test_missing_stage_graph_definition_is_rejected(self):
        self.assertFalse(self.check(('PM-03','StageTransitionDefinition','RemovedTransitionModel'))['模板定义StageTask绑定'])

    def test_optional_competing_work_bindings_are_rejected(self):
        self.assertFalse(self.check(('PM-03','每个可执行ProjectStage和ProjectTask必须且只能有一个当前有效主`WorkBinding`','节点绑定可选且可并列多个入口'))['WorkBinding统一必填'])

    def test_fixed_depth_navigation_is_rejected(self):
        self.assertFalse(self.check(('PM-11','任务导航可按需展开任意深度','任务导航只允许两层'))['StageTask导航不限制树深'])

    def test_generic_summary_cannot_replace_bound_business(self):
        self.assertFalse(self.check(('PM-11','不得用通用内容替代、伪造或完成绑定业务','允许通用摘要替代绑定业务'))['通用详情不替代绑定业务'])

    def test_generic_completion_cannot_bypass_owner(self):
        self.assertFalse(self.check(('PM-11','非原生绑定不得通过通用“完成”操作绕过目标事实','非原生绑定可通过通用完成跳过业务事实'))['任务完成按绑定类型判定'])

    def test_template_cannot_grant_domain_permissions(self):
        self.assertFalse(self.check(('PM-03','模板配置不得扩大领域对象权限','模板允许扩大领域对象权限'))['绑定视图不扩大领域权限'])

    def test_missing_requirements_fail_closed(self):
        self.assertTrue(all(not value for value in current_workbench_contract('','','','').values()))


if __name__ == '__main__':
    unittest.main()
