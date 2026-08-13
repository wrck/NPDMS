from __future__ import annotations

import importlib.util
import sys
import unittest
from pathlib import Path


MODULE_PATH = Path(__file__).resolve().parents[1] / "validate_prd_baseline.py"
sys.path.insert(0, str(MODULE_PATH.parent))
SPEC = importlib.util.spec_from_file_location("validate_prd_baseline", MODULE_PATH)
MODULE = importlib.util.module_from_spec(SPEC)
assert SPEC.loader is not None
sys.modules[SPEC.name] = MODULE
SPEC.loader.exec_module(MODULE)


class ValidatePrdBaselineTest(unittest.TestCase):
    def test_requirement_blocks_support_mixed_heading_levels(self) -> None:
        text = """#### 4.2.1 PM-01 项目创建
| 需求编号 | PM-01 |
| 目标版本 | V1 |
**核心业务规则：**项目规则

### 10.2 割接任务管理（CUT-01）
| 需求编号 | CUT-01 |
| 目标版本 | V1任务闭环；V2增强 |
**核心业务规则：**割接规则

### 11.2 巡检任务管理（INS-01）
| 需求编号 | INS-01 |
| 目标版本 | V2 |
**核心业务规则：**巡检规则

# 附录
"""
        blocks = dict(MODULE.requirement_blocks(text))

        self.assertEqual({"PM-01", "CUT-01", "INS-01"}, set(blocks))
        self.assertIn("| 目标版本 | V1任务闭环；V2增强 |", blocks["CUT-01"])
        self.assertNotIn("巡检规则", blocks["CUT-01"])
        self.assertNotIn("# 附录", blocks["INS-01"])

    def test_requirement_id_pattern_accepts_domain_addendum_ids(self) -> None:
        text = """#### 13.5.15 AST-02 设备维保客观状态计算
| 需求编号 | AST-02 |
| 目标版本 | V1 |
"""

        self.assertEqual(["AST-02"], [identifier for identifier, _ in MODULE.requirement_blocks(text)])


if __name__ == "__main__":
    unittest.main()
