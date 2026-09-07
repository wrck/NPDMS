"""Check current PRD workbench obligations without requiring superseded prose.

These are document contract checks, not application or browser acceptance tests.
The caller supplies individual Requirement blocks so unrelated text cannot satisfy
an obligation accidentally. Historical-model checks remain in validate_prd_baseline.
"""
from __future__ import annotations


def contains(text: str, *markers: str) -> bool:
    return all(marker in text for marker in markers)


def current_workbench_contract(pm03: str, pm11: str, cut01: str, cut03: str) -> dict[str, bool]:
    """Validate the published graph model and both Stage and Task execution binds."""
    return {
        "模板定义StageTask绑定": contains(pm03,
            "ProjectTemplateVersion", "TemplateStageDefinition", "TemplateTaskDefinition",
            "StageTransitionDefinition", "StageWorkBinding", "TaskWorkBinding",
            "PermissionPolicy", "CompletionRule", "GateRef"),
        "不重复配置业务导航": contains(pm03,
            "导航只由ProjectStage和ProjectTask实例投影", "不建立第二套菜单或导航配置"),
        "WorkBinding类型完整": contains(pm03,
            "原生、业务实体、业务组件、动态表单、审批和组合", "STAGE_NATIVE", "TASK_NATIVE", "COMPOSITE"),
        "WorkBinding统一必填": contains(pm03,
            "每个可执行ProjectStage和ProjectTask必须且只能有一个当前有效主`WorkBinding`",
            "阶段缺省使用`STAGE_NATIVE`", "任务缺省使用`TASK_NATIVE`",
            "不允许为同一节点配置多个相互竞争的主执行入口"),
        "StageTask导航不限制树深": contains(pm11,
            "以ProjectStage作为一级业务导航、ProjectTask作为二级及可展开的深层任务导航",
            "任务导航可按需展开任意深度"),
        "通用任务详情基础能力": contains(pm11,
            "用户点击`TASK_NATIVE`任务", "任务工作台展示通用基础信息、任务交付件和本人获权操作",
            "按ProjectTask自身状态机及完成规则执行"),
        "绑定任务按关系执行": contains(pm11,
            "用户点击绑定业务实体、业务组件、动态表单、审批或组合视图的任务",
            "平台在任务上下文内加载相应真实业务界面", "不复制目标业务正文"),
        "通用详情不替代绑定业务": contains(pm11,
            "通用摘要仍可按权限查看", "不得用通用内容替代、伪造或完成绑定业务"),
        "项目概览六页签": contains(pm11,
            "基本信息", "项目树", "团队成员", "项目任务", "设备清单", "实施范围"),
        "任务完成按绑定类型判定": contains(pm11,
            "任务完成由Task `CompletionRule`判定", "按ProjectTask自身状态机及完成规则执行",
            "非原生绑定不得通过通用“完成”操作绕过目标事实",
            "节点版本、绑定版本、规则版本和事实版本并重新校验"),
        "割接入口与五步工作台": contains(cut01,
            "P1是任务接入入口", "五步工作台按P2～P6展示"),
        "CUT03同一P3工作台": contains(cut03,
            "同一个P3任务工作台", "不新增采集阶段", "CollectionTask"),
        "采集结果不等于业务通过": contains(cut03,
            "不把技术回调成功直接解释为风险项通过", "任何回调不得直接把采集项判定为通过"),
        "阶段推进准出和准入均校验": contains(pm03,
            "重新校验当前阶段`CompletionRule`和准出门禁",
            "唯一解析目标阶段", "校验目标阶段准入条件", "任何一步失败均保持原阶段"),
        "绑定视图不扩大领域权限": contains(pm03,
            "模板配置不得扩大领域对象权限", "前端组件是否显示不得作为授权依据"),
    }
