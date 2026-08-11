from __future__ import annotations

import sys
import unittest
from pathlib import Path


sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from validate_prd_semantics import validate_text


GENERIC_RULE = (
    "本需求的状态、字段、门禁、审批、同步频率和版本边界，"
    "以本条业务场景、已确认事项及验收标准中明确的内容为准。"
)
GENERIC_PERMISSION = (
    "仅本条“用户角色”及其被明确授权人员可执行；"
    "查看、编辑和审批范围遵循第二章角色权限及数据隔离规则。"
)
GENERIC_EXCEPTION = (
    "前置依赖或外部能力不可用时，执行本条或关联集成需求已明确的降级路径。"
)
GENERIC_ACCEPTANCE = (
    "- **WHEN** 依赖条件满足且有权用户在本需求适用场景发起业务操作\n"
    "- **THEN** 产生以下已定义业务结果\n"
    "- **WHEN** 必填数据、角色权限、数据范围或前置门禁不满足\n"
    "- **THEN** 平台阻止形成成功结果且不改变原业务对象的有效状态"
)


def requirement(
    req_id: str,
    *,
    rule: str,
    acceptance: str,
    permission: str,
    exception: str,
    data: str,
    role: str = "项目经理",
) -> str:
    return f"""
#### {req_id} 测试需求

| 字段 | 内容 |
|---|---|
| 需求编号 | {req_id} |
| 目标版本 | V1 |
| 用户角色 | {role} |

**业务场景与需求描述：**

项目经理需要完成项目拆分并保留原项目关系。

**核心业务规则：**

{rule}

**用户故事：**

作为项目经理，我希望完成项目拆分，以便按交付范围执行。

**业务验收标准：**

{acceptance}

**涉及数据字段：**

{data}

**权限与数据范围：**

{permission}

**异常、降级及留痕要求：**

{exception}

**依赖关系：**

依赖项目创建。
"""


SPECIFIC_RULE = (
    "项目经理提交拆分申请后，父项目保持执行中；审批通过才生成子项目，"
    "并保存拆分前后的父子关系。全部直接子项目权重之和必须为100%。"
)
SPECIFIC_ACCEPTANCE = (
    "- **WHEN** 项目经理提交权重合计100%的拆分申请\n"
    "- **THEN** 审批通过后生成子项目，父项目保持执行中并新增拆分版本记录\n"
    "- **WHEN** 子项目权重合计不等于100%\n"
    "- **THEN** 申请保持草稿状态，不生成子项目，并记录校验失败原因"
)
SPECIFIC_PERMISSION = (
    "项目经理可创建和编辑本人负责项目的拆分申请；上级项目经理只读查看后代项目汇总，"
    "平级项目默认不可见；仅工程管理部审批人可批准拆分。"
)
SPECIFIC_EXCEPTION = (
    "子项目权重合计不等于100%时阻止提交，申请保持草稿状态；项目经理修正后可重新提交。"
    "平台记录失败规则、申请人、发生时间和修改前后权重。"
)
SPECIFIC_DATA = "拆分申请编号、父项目ID、子项目ID、子项目权重、申请状态、审批记录"


class ValidatePrdSemanticsTest(unittest.TestCase):
    def test_known_templates_fail(self) -> None:
        text = requirement(
            "PM-01",
            rule=GENERIC_RULE,
            acceptance=GENERIC_ACCEPTANCE,
            permission=GENERIC_PERMISSION,
            exception=GENERIC_EXCEPTION,
            data=(
                "需求编号、关联业务对象ID、业务状态，以及本条业务场景中已明确列示的业务字段。"
            ),
        )

        codes = {issue.code for issue in validate_text(text)}

        self.assertIn("GENERIC_RULE", codes)
        self.assertIn("GENERIC_ACCEPTANCE", codes)
        self.assertIn("GENERIC_PERMISSION", codes)
        self.assertIn("GENERIC_EXCEPTION", codes)
        self.assertIn("GENERIC_DATA", codes)

    def test_specific_content_passes(self) -> None:
        text = requirement(
            "PM-01",
            rule=SPECIFIC_RULE,
            acceptance=SPECIFIC_ACCEPTANCE,
            permission=SPECIFIC_PERMISSION,
            exception=SPECIFIC_EXCEPTION,
            data=SPECIFIC_DATA,
        )

        self.assertEqual([], validate_text(text))

    def test_duplicate_fields_are_reported_for_both_requirements(self) -> None:
        text = requirement(
            "PM-01",
            rule=SPECIFIC_RULE,
            acceptance=SPECIFIC_ACCEPTANCE,
            permission=SPECIFIC_PERMISSION,
            exception=SPECIFIC_EXCEPTION,
            data=SPECIFIC_DATA,
        ) + requirement(
            "PM-02",
            rule=SPECIFIC_RULE,
            acceptance=SPECIFIC_ACCEPTANCE.replace("子项目", "项目节点"),
            permission=SPECIFIC_PERMISSION.replace("拆分申请", "节点申请"),
            exception=SPECIFIC_EXCEPTION.replace("权重", "节点数量"),
            data="项目节点ID、父节点ID、节点状态、审批记录",
        )

        issues = [issue for issue in validate_text(text) if issue.code == "DUPLICATE_FIELD"]

        self.assertEqual({"PM-01", "PM-02"}, {issue.req_id for issue in issues})
        self.assertEqual({"核心业务规则"}, {issue.field for issue in issues})

    def test_requirement_filter_excludes_other_requirement_issues(self) -> None:
        text = requirement(
            "PM-01",
            rule=SPECIFIC_RULE,
            acceptance=SPECIFIC_ACCEPTANCE,
            permission=SPECIFIC_PERMISSION,
            exception=SPECIFIC_EXCEPTION,
            data=SPECIFIC_DATA,
        ) + requirement(
            "PM-02",
            rule=GENERIC_RULE,
            acceptance=GENERIC_ACCEPTANCE,
            permission=GENERIC_PERMISSION,
            exception=GENERIC_EXCEPTION,
            data="相关信息、业务所需字段、以实际为准",
        )

        self.assertEqual([], validate_text(text, {"PM-01"}))

    def test_observable_configuration_results_and_tree_failures_pass(self) -> None:
        acceptance = (
            "- **WHEN** 管理员创建项目模板\n"
            "- **THEN** 系统提供模板表单并加载阶段、任务和交付件清单\n"
            "- **WHEN** 项目没有匹配的生效模板\n"
            "- **THEN** 项目保持创建草稿状态，不实例化阶段任务并记录冲突项"
        )
        exception = (
            "父项目不存在、跨租户或形成循环时，拆分申请保持草稿状态；"
            "项目经理修正父项目后可重新提交，平台记录校验原因、申请人和发生时间。"
        )
        text = requirement(
            "PM-01",
            rule=SPECIFIC_RULE,
            acceptance=acceptance,
            permission=SPECIFIC_PERMISSION,
            exception=exception,
            data=SPECIFIC_DATA,
        )

        self.assertEqual([], validate_text(text))


if __name__ == "__main__":
    unittest.main()
