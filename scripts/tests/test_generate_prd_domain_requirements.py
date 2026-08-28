from __future__ import annotations

import unittest

from scripts import generate_prd_domain_requirements as generator


class GeneratePrdDomainRequirementsTest(unittest.TestCase):

    def test_group_heading_does_not_shadow_detailed_requirement_block(self) -> None:
        prd = """### 11.2 巡检任务管理（INS-01）

#### 11.2.1 INS-01 巡检任务管理

| 属性 | 内容 |
| --- | --- |
| 需求编号 | INS-01 |
| 优先级 | P1 |
| 目标版本 | V1 |

**业务场景与需求描述：**

平台应支持巡检任务管理。

### 11.3 下一节
"""

        requirement = generator._extract_requirements(prd, {"INS-01"})["INS-01"]

        self.assertEqual("P1", requirement.priority)
        self.assertEqual("V1", requirement.version)
        self.assertIn("平台应支持巡检任务管理", requirement.body)


if __name__ == "__main__":
    unittest.main()
