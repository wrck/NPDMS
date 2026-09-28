package cn.iocoder.yudao.module.pms.project.model;

import cn.iocoder.yudao.module.pms.platform.api.businessmodel.model.BusinessCapabilityBinding;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.model.BusinessCapabilityType;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.model.BusinessFieldDescriptor;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.model.BusinessModelDeclaration;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.model.BusinessModelDescriptor;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.model.BusinessModelKind;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.model.BusinessModelContributor;
import cn.iocoder.yudao.module.pms.platform.api.entity.EntityField;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.batchchange.TeamBatchChangeDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.normalclosure.NormalClosureExitRecordDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.portfolio.ProjectPortfolioDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectclosure.ProjectClosureDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectgovernance.ProjectGovernanceActionDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMasterDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectSiteDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectplan.ProjectPlanVersionDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.risk.ProjectRiskDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectsplit.ProjectSplitRequestDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projecttemplate.ProjectTemplateDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projecttree.ProjectTreeChangeDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projecttree.ProjectTreeVersionDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectschedule.StageSuggestionRuleDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.taskworkbench.ProjectTaskAssignmentDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.batchchange.TeamBatchChangeMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.normalclosure.ClosureProjectMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.portfolio.ProjectPortfolioMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectclosure.ProjectClosureMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectgovernance.ProjectGovernanceActionMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMasterMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectSiteMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectplan.ProjectPlanVersionMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.risk.ProjectRiskMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectsplit.ProjectSplitRequestMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projecttemplate.ProjectTemplateMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projecttree.ProjectTreeChangeMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projecttree.ProjectTreeVersionMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectschedule.StageSuggestionRuleMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.ProjectTaskAssignmentMapper;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * B6 批次统一目录声明（P12）：项目主档、组合、风险、模板、现场、拆分、项目树、
 * 团队批量变更、阶段建议规则、任务指派、计划版本、收尾记录、治理动作与退出记录
 * 进入统一目录。项目创建、生命周期状态机、门禁推进、计划变更、拆分与收尾审批
 * 等专业写路径保留本域，声明不开放通用 create/save 操作；
 * 阶段/任务/门禁/里程碑/交付件实例与执行契约仍由旧调度承载（ID 预分配且图内互引），
 * 按计划 B6 备注保留单独判断，不进入本批声明。
 */
@Component
public class ProjectBusinessModelContributor implements BusinessModelContributor {

    private final ProjectMasterMapper projectMasterMapper;
    private final ProjectPortfolioMapper projectPortfolioMapper;
    private final ProjectRiskMapper projectRiskMapper;
    private final ProjectTemplateMapper projectTemplateMapper;
    private final ProjectSiteMapper projectSiteMapper;
    private final ProjectSplitRequestMapper projectSplitRequestMapper;
    private final ProjectTreeVersionMapper projectTreeVersionMapper;
    private final ProjectTreeChangeMapper projectTreeChangeMapper;
    private final TeamBatchChangeMapper teamBatchChangeMapper;
    private final StageSuggestionRuleMapper stageSuggestionRuleMapper;
    private final ProjectTaskAssignmentMapper projectTaskAssignmentMapper;
    private final ProjectPlanVersionMapper projectPlanVersionMapper;
    private final ProjectClosureMapper projectClosureMapper;
    private final ProjectGovernanceActionMapper projectGovernanceActionMapper;
    private final ClosureProjectMapper closureProjectMapper;

    public ProjectBusinessModelContributor(ProjectMasterMapper projectMasterMapper,
                                           ProjectPortfolioMapper projectPortfolioMapper,
                                           ProjectRiskMapper projectRiskMapper,
                                           ProjectTemplateMapper projectTemplateMapper,
                                           ProjectSiteMapper projectSiteMapper,
                                           ProjectSplitRequestMapper projectSplitRequestMapper,
                                           ProjectTreeVersionMapper projectTreeVersionMapper,
                                           ProjectTreeChangeMapper projectTreeChangeMapper,
                                           TeamBatchChangeMapper teamBatchChangeMapper,
                                           StageSuggestionRuleMapper stageSuggestionRuleMapper,
                                           ProjectTaskAssignmentMapper projectTaskAssignmentMapper,
                                           ProjectPlanVersionMapper projectPlanVersionMapper,
                                           ProjectClosureMapper projectClosureMapper,
                                           ProjectGovernanceActionMapper projectGovernanceActionMapper,
                                           ClosureProjectMapper closureProjectMapper) {
        this.projectMasterMapper = projectMasterMapper;
        this.projectPortfolioMapper = projectPortfolioMapper;
        this.projectRiskMapper = projectRiskMapper;
        this.projectTemplateMapper = projectTemplateMapper;
        this.projectSiteMapper = projectSiteMapper;
        this.projectSplitRequestMapper = projectSplitRequestMapper;
        this.projectTreeVersionMapper = projectTreeVersionMapper;
        this.projectTreeChangeMapper = projectTreeChangeMapper;
        this.teamBatchChangeMapper = teamBatchChangeMapper;
        this.stageSuggestionRuleMapper = stageSuggestionRuleMapper;
        this.projectTaskAssignmentMapper = projectTaskAssignmentMapper;
        this.projectPlanVersionMapper = projectPlanVersionMapper;
        this.projectClosureMapper = projectClosureMapper;
        this.projectGovernanceActionMapper = projectGovernanceActionMapper;
        this.closureProjectMapper = closureProjectMapper;
    }

    private static BusinessFieldDescriptor field(String code, String name, EntityField.Type type) {
        return new BusinessFieldDescriptor(code, name, type, false, true, true, null);
    }

    private static BusinessFieldDescriptor required(String code, String name, EntityField.Type type) {
        return new BusinessFieldDescriptor(code, name, type, true, true, true, null);
    }

    @Override
    public List<BusinessModelDeclaration> declarations() {
        List<BusinessModelDeclaration> declarations = new ArrayList<>();
        BusinessModelDescriptor project = new BusinessModelDescriptor("PRJ", "project",
                "PRJ_PROJECT", 1, BusinessModelKind.AGGREGATE_ROOT, "项目主档",
                "pms:project:query",
                List.of(required("projectCode", "项目编码", EntityField.Type.TEXT),
                        required("projectName", "项目名称", EntityField.Type.TEXT),
                        field("parentId", "父项目", EntityField.Type.NUMBER),
                        field("rootId", "根项目", EntityField.Type.NUMBER),
                        field("businessLevelName", "业务层级", EntityField.Type.TEXT),
                        field("customerId", "客户", EntityField.Type.NUMBER),
                        field("customerName", "客户名称", EntityField.Type.TEXT),
                        field("managerId", "项目经理", EntityField.Type.NUMBER),
                        field("managerName", "项目经理姓名", EntityField.Type.TEXT),
                        field("companyName", "公司名称", EntityField.Type.TEXT),
                        field("departmentName", "部门名称", EntityField.Type.TEXT),
                        field("projectType", "项目类型", EntityField.Type.TEXT),
                        field("signingMethod", "签立方式", EntityField.Type.TEXT),
                        field("projectCategory", "项目类别", EntityField.Type.TEXT),
                        field("implementationMode", "实施模式", EntityField.Type.TEXT),
                        field("status", "状态", EntityField.Type.TEXT),
                        field("lifecycleStatus", "生命周期状态", EntityField.Type.TEXT),
                        field("currentStage", "当前阶段", EntityField.Type.TEXT),
                        field("assignmentStatus", "指派状态", EntityField.Type.TEXT)),
                List.of(), List.of(),
                // P06R：项目主档接入统一交付件能力，/pms/business-entity/PRJ/project/{id} 呈现交付面板
                List.of(new BusinessCapabilityBinding(BusinessCapabilityType.DELIVERY, null, true)),
                "proj_project");
        declarations.add(new BusinessModelDeclaration(project, ProjectMasterDO.class, projectMasterMapper, null));
        BusinessModelDescriptor portfolio = new BusinessModelDescriptor("PRJ", "portfolio",
                "PRJ_PORTFOLIO", 1, BusinessModelKind.AGGREGATE_ROOT, "项目组合",
                "pms:portfolio:query",
                List.of(required("code", "组合编码", EntityField.Type.TEXT),
                        required("name", "组合名称", EntityField.Type.TEXT),
                        field("purpose", "组合目的", EntityField.Type.TEXT),
                        field("ownerUserId", "负责人", EntityField.Type.NUMBER),
                        field("validFrom", "生效日期", EntityField.Type.DATE),
                        field("validTo", "失效日期", EntityField.Type.DATE),
                        field("status", "状态", EntityField.Type.NUMBER),
                        field("targetMetrics", "目标度量", EntityField.Type.TEXT),
                        field("memberType", "成员类型", EntityField.Type.TEXT)),
                List.of(), List.of(), List.of(), "proj_project_portfolio");
        declarations.add(new BusinessModelDeclaration(portfolio, ProjectPortfolioDO.class, projectPortfolioMapper, null));
        BusinessModelDescriptor projectRisk = new BusinessModelDescriptor("PRJ", "projectRisk",
                "PRJ_PROJECT_RISK", 1, BusinessModelKind.AGGREGATE_ROOT, "项目风险",
                "pms:project-risk:query",
                List.of(required("projectId", "项目", EntityField.Type.NUMBER),
                        required("title", "风险标题", EntityField.Type.TEXT),
                        field("riskLevel", "风险等级", EntityField.Type.TEXT),
                        field("riskType", "风险类型", EntityField.Type.TEXT),
                        field("cause", "原因", EntityField.Type.TEXT),
                        field("impact", "影响", EntityField.Type.TEXT),
                        field("mitigation", "缓解措施", EntityField.Type.TEXT),
                        field("contingency", "应急措施", EntityField.Type.TEXT),
                        field("ownerUserId", "责任人", EntityField.Type.NUMBER),
                        field("status", "状态", EntityField.Type.NUMBER),
                        field("warningThreshold", "预警阈值", EntityField.Type.TEXT),
                        field("identifiedAt", "识别时间", EntityField.Type.DATETIME),
                        field("closedAt", "关闭时间", EntityField.Type.DATETIME)),
                List.of(), List.of(), List.of(), "proj_project_risk");
        declarations.add(new BusinessModelDeclaration(projectRisk, ProjectRiskDO.class, projectRiskMapper, null));
        BusinessModelDescriptor projectTemplate = new BusinessModelDescriptor("PRJ", "projectTemplate",
                "PRJ_PROJECT_TEMPLATE", 1, BusinessModelKind.AGGREGATE_ROOT, "项目模板",
                "pms:project-template:query",
                List.of(required("code", "模板编码", EntityField.Type.TEXT),
                        required("name", "模板名称", EntityField.Type.TEXT),
                        field("status", "状态", EntityField.Type.TEXT),
                        field("matchPriority", "匹配优先级", EntityField.Type.NUMBER),
                        field("description", "描述", EntityField.Type.TEXT),
                        field("systemReserved", "系统保留", EntityField.Type.BOOLEAN)),
                List.of(), List.of(), List.of(), "proj_project_template");
        declarations.add(new BusinessModelDeclaration(projectTemplate, ProjectTemplateDO.class, projectTemplateMapper, null));
        BusinessModelDescriptor projectSite = new BusinessModelDescriptor("PRJ", "projectSite",
                "PRJ_PROJECT_SITE", 1, BusinessModelKind.AGGREGATE_ROOT, "项目现场",
                "pms:project:query",
                List.of(required("projectId", "项目", EntityField.Type.NUMBER),
                        field("siteId", "现场", EntityField.Type.NUMBER),
                        field("siteVersionSnapshot", "现场版本快照", EntityField.Type.NUMBER),
                        field("primarySite", "主现场", EntityField.Type.BOOLEAN),
                        field("scopeStatus", "范围状态", EntityField.Type.TEXT),
                        field("effectiveFrom", "生效时间", EntityField.Type.DATETIME),
                        field("effectiveTo", "失效时间", EntityField.Type.DATETIME),
                        field("siteCodeSnapshot", "现场编码快照", EntityField.Type.TEXT),
                        field("siteNameSnapshot", "现场名称快照", EntityField.Type.TEXT),
                        field("addressSnapshot", "地址快照", EntityField.Type.TEXT)),
                List.of(), List.of(), List.of(), "proj_project_site");
        declarations.add(new BusinessModelDeclaration(projectSite, ProjectSiteDO.class, projectSiteMapper, null));
        BusinessModelDescriptor splitRequest = new BusinessModelDescriptor("PRJ", "splitRequest",
                "PRJ_SPLIT_REQUEST", 1, BusinessModelKind.AGGREGATE_ROOT, "项目拆分申请",
                "pms:project:query",
                List.of(required("parentProjectId", "父项目", EntityField.Type.NUMBER),
                        field("status", "状态", EntityField.Type.TEXT),
                        field("draftVersion", "草稿版本", EntityField.Type.NUMBER),
                        field("parentVersion", "父项目版本", EntityField.Type.NUMBER),
                        field("scopeVersion", "范围版本", EntityField.Type.NUMBER),
                        field("treeVersion", "树版本", EntityField.Type.NUMBER),
                        field("templateRevisionId", "模板修订", EntityField.Type.NUMBER),
                        field("previewHash", "预览摘要", EntityField.Type.TEXT),
                        field("validationStatus", "校验状态", EntityField.Type.TEXT),
                        field("validationSummary", "校验结论", EntityField.Type.TEXT),
                        field("validatedAt", "校验时间", EntityField.Type.DATETIME),
                        field("appliedChangeBatchId", "应用变更批次", EntityField.Type.TEXT)),
                List.of(), List.of(), List.of(), "proj_project_split_request");
        declarations.add(new BusinessModelDeclaration(splitRequest, ProjectSplitRequestDO.class, projectSplitRequestMapper, null));
        BusinessModelDescriptor treeVersion = new BusinessModelDescriptor("PRJ", "treeVersion",
                "PRJ_TREE_VERSION", 1, BusinessModelKind.AGGREGATE_ROOT, "项目树版本",
                "pms:project-tree:query",
                List.of(required("rootProjectId", "根项目", EntityField.Type.NUMBER),
                        field("treeVersion", "树版本号", EntityField.Type.NUMBER),
                        field("status", "状态", EntityField.Type.TEXT),
                        field("changeBatchId", "变更批次", EntityField.Type.TEXT),
                        field("nodeCount", "节点数", EntityField.Type.NUMBER),
                        field("pathCount", "路径数", EntityField.Type.NUMBER),
                        field("activatedAt", "激活时间", EntityField.Type.DATETIME),
                        field("failedReason", "失败原因", EntityField.Type.TEXT)),
                List.of(), List.of(), List.of(), "proj_project_tree_version");
        declarations.add(new BusinessModelDeclaration(treeVersion, ProjectTreeVersionDO.class, projectTreeVersionMapper, null));
        BusinessModelDescriptor treeChange = new BusinessModelDescriptor("PRJ", "treeChange",
                "PRJ_TREE_CHANGE", 1, BusinessModelKind.AGGREGATE_ROOT, "项目树变更",
                "pms:project-tree:query",
                List.of(required("changeBatchId", "变更批次", EntityField.Type.TEXT),
                        field("operationType", "操作类型", EntityField.Type.TEXT),
                        field("projectId", "项目", EntityField.Type.NUMBER),
                        field("parentIdBefore", "原父项目", EntityField.Type.NUMBER),
                        field("parentIdAfter", "新父项目", EntityField.Type.NUMBER),
                        field("baseTreeVersion", "基线树版本", EntityField.Type.NUMBER),
                        field("newTreeVersion", "新树版本", EntityField.Type.NUMBER),
                        field("actorId", "操作人", EntityField.Type.NUMBER),
                        field("reason", "原因", EntityField.Type.TEXT),
                        field("occurredAt", "发生时间", EntityField.Type.DATETIME)),
                List.of(), List.of(), List.of(), "proj_project_tree_change");
        declarations.add(new BusinessModelDeclaration(treeChange, ProjectTreeChangeDO.class, projectTreeChangeMapper, null));
        BusinessModelDescriptor teamBatchChange = new BusinessModelDescriptor("PRJ", "teamBatchChange",
                "PRJ_TEAM_BATCH_CHANGE", 1, BusinessModelKind.AGGREGATE_ROOT, "团队批量变更",
                "pms:team-batch-change:query",
                List.of(required("batchNo", "批次编号", EntityField.Type.TEXT),
                        field("sourceUserId", "原负责人", EntityField.Type.NUMBER),
                        field("targetUserId", "新负责人", EntityField.Type.NUMBER),
                        field("scopeType", "范围类型", EntityField.Type.TEXT),
                        field("reason", "原因", EntityField.Type.TEXT),
                        field("status", "状态", EntityField.Type.NUMBER),
                        field("totalCount", "总数", EntityField.Type.NUMBER),
                        field("successCount", "成功数", EntityField.Type.NUMBER),
                        field("failureCount", "失败数", EntityField.Type.NUMBER),
                        field("remark", "备注", EntityField.Type.TEXT)),
                List.of(), List.of(), List.of(), "proj_team_batch_change");
        declarations.add(new BusinessModelDeclaration(teamBatchChange, TeamBatchChangeDO.class, teamBatchChangeMapper, null));
        BusinessModelDescriptor stageSuggestionRule = new BusinessModelDescriptor("PRJ", "stageSuggestionRule",
                "PRJ_STAGE_SUGGESTION_RULE", 1, BusinessModelKind.AGGREGATE_ROOT, "阶段计划建议规则",
                "pms:stage-plan-suggestion-rule:query",
                List.of(required("stageCode", "阶段", EntityField.Type.TEXT),
                        field("signingMethod", "签立方式", EntityField.Type.TEXT),
                        field("sourceType", "来源类型", EntityField.Type.TEXT),
                        field("referenceStageCode", "参照阶段", EntityField.Type.TEXT),
                        field("offsetMonths", "偏移月数", EntityField.Type.NUMBER),
                        field("offsetDays", "偏移天数", EntityField.Type.NUMBER),
                        field("remark", "备注", EntityField.Type.TEXT),
                        field("enabled", "启用", EntityField.Type.BOOLEAN)),
                List.of(), List.of(), List.of(), "proj_stage_suggestion_rule");
        declarations.add(new BusinessModelDeclaration(stageSuggestionRule, StageSuggestionRuleDO.class, stageSuggestionRuleMapper, null));
        BusinessModelDescriptor taskAssignment = new BusinessModelDescriptor("PRJ", "taskAssignment",
                "PRJ_TASK_ASSIGNMENT", 1, BusinessModelKind.AGGREGATE_ROOT, "项目任务指派",
                "pms:project-task:query",
                List.of(required("projectTaskId", "项目任务", EntityField.Type.NUMBER),
                        required("assigneeUserId", "责任人", EntityField.Type.NUMBER),
                        field("effectiveFrom", "生效时间", EntityField.Type.DATETIME),
                        field("effectiveTo", "失效时间", EntityField.Type.DATETIME),
                        field("currentMarker", "当前标记", EntityField.Type.NUMBER),
                        field("assignedBy", "指派人", EntityField.Type.NUMBER),
                        field("reason", "原因", EntityField.Type.TEXT)),
                List.of(), List.of(), List.of(), "proj_project_task_assignment");
        declarations.add(new BusinessModelDeclaration(taskAssignment, ProjectTaskAssignmentDO.class, projectTaskAssignmentMapper, null));
        BusinessModelDescriptor planVersion = new BusinessModelDescriptor("PRJ", "planVersion",
                "PRJ_PLAN_VERSION", 1, BusinessModelKind.AGGREGATE_ROOT, "项目计划版本",
                "pms:plan-change:query",
                List.of(required("projectId", "项目", EntityField.Type.NUMBER),
                        field("revisionNo", "修订号", EntityField.Type.NUMBER),
                        field("status", "状态", EntityField.Type.TEXT),
                        field("sourceTemplateRevisionId", "来源模板修订", EntityField.Type.NUMBER),
                        field("basePlanVersionId", "基线计划版本", EntityField.Type.NUMBER),
                        field("designerDocument", "设计文档", EntityField.Type.TEXT),
                        field("executionSnapshot", "执行快照", EntityField.Type.TEXT),
                        field("effectiveAt", "生效时间", EntityField.Type.DATETIME),
                        field("closedAt", "关闭时间", EntityField.Type.DATETIME),
                        field("closureResult", "关闭结果", EntityField.Type.TEXT)),
                List.of(), List.of(), List.of(), "proj_project_plan_version");
        declarations.add(new BusinessModelDeclaration(planVersion, ProjectPlanVersionDO.class, projectPlanVersionMapper, null));
        BusinessModelDescriptor projectClosure = new BusinessModelDescriptor("PRJ", "projectClosure",
                "PRJ_CLOSURE_RECORD", 1, BusinessModelKind.AGGREGATE_ROOT, "项目收尾记录",
                "pms:acc-project-closure:query",
                List.of(required("projectId", "项目", EntityField.Type.NUMBER),
                        required("code", "收尾编码", EntityField.Type.TEXT),
                        required("name", "收尾名称", EntityField.Type.TEXT),
                        field("closureType", "收尾类型", EntityField.Type.TEXT),
                        field("applicantUserId", "申请人", EntityField.Type.NUMBER),
                        field("applyTime", "申请时间", EntityField.Type.DATETIME),
                        field("approverUserId", "审批人", EntityField.Type.NUMBER),
                        field("approveTime", "审批时间", EntityField.Type.DATETIME),
                        field("approveOpinion", "审批意见", EntityField.Type.TEXT),
                        field("archiveTime", "归档时间", EntityField.Type.DATETIME),
                        field("status", "状态", EntityField.Type.NUMBER),
                        field("remark", "备注", EntityField.Type.TEXT)),
                List.of(), List.of(), List.of(), "acc_project_closure_record");
        declarations.add(new BusinessModelDeclaration(projectClosure, ProjectClosureDO.class, projectClosureMapper, null));
        BusinessModelDescriptor governanceAction = new BusinessModelDescriptor("PRJ", "governanceAction",
                "PRJ_GOVERNANCE_ACTION", 1, BusinessModelKind.AGGREGATE_ROOT, "项目治理动作",
                "pms:project-governance:query",
                List.of(required("projectId", "项目", EntityField.Type.NUMBER),
                        field("actionNo", "动作编号", EntityField.Type.TEXT),
                        field("actionType", "动作类型", EntityField.Type.TEXT),
                        field("reason", "原因", EntityField.Type.TEXT),
                        field("proofFiles", "证明材料", EntityField.Type.TEXT),
                        field("applicantUserId", "申请人", EntityField.Type.NUMBER),
                        field("applyTime", "申请时间", EntityField.Type.DATETIME),
                        field("approverUserId", "审批人", EntityField.Type.NUMBER),
                        field("approveTime", "审批时间", EntityField.Type.DATETIME),
                        field("approveOpinion", "审批意见", EntityField.Type.TEXT),
                        field("beforeProjectStatus", "变更前项目状态", EntityField.Type.NUMBER),
                        field("afterProjectStatus", "变更后项目状态", EntityField.Type.NUMBER),
                        field("beforeManagerUserId", "变更前项目经理", EntityField.Type.NUMBER),
                        field("afterManagerUserId", "变更后项目经理", EntityField.Type.NUMBER)),
                List.of(), List.of(), List.of(), "proj_project_governance_action");
        declarations.add(new BusinessModelDeclaration(governanceAction, ProjectGovernanceActionDO.class, projectGovernanceActionMapper, null));
        BusinessModelDescriptor exitRecord = new BusinessModelDescriptor("PRJ", "exitRecord",
                "PRJ_EXIT_RECORD", 1, BusinessModelKind.AGGREGATE_ROOT, "项目正常收尾退出记录",
                "pms:acc-project-closure:query",
                List.of(required("projectId", "项目", EntityField.Type.NUMBER),
                        field("applicationId", "收尾申请", EntityField.Type.NUMBER),
                        field("snapshotId", "快照", EntityField.Type.NUMBER),
                        field("projectVersion", "项目版本", EntityField.Type.NUMBER),
                        field("stageInstanceId", "阶段实例", EntityField.Type.NUMBER),
                        field("templateRevisionId", "模板修订", EntityField.Type.NUMBER),
                        field("scopeVersion", "范围版本", EntityField.Type.NUMBER),
                        field("gateSnapshotRef", "门禁快照引用", EntityField.Type.NUMBER),
                        field("sourceContext", "来源上下文", EntityField.Type.TEXT),
                        field("sourceRecordId", "来源记录", EntityField.Type.NUMBER),
                        field("sourceRecordRevision", "来源记录修订", EntityField.Type.NUMBER),
                        field("closureType", "收尾类型", EntityField.Type.TEXT),
                        field("closedFromStage", "收尾发起阶段", EntityField.Type.TEXT),
                        field("beforeLifecycleStatus", "变更前生命周期状态", EntityField.Type.TEXT),
                        field("afterLifecycleStatus", "变更后生命周期状态", EntityField.Type.TEXT)),
                List.of(), List.of(), List.of(), "proj_project_exit_record");
        declarations.add(new BusinessModelDeclaration(exitRecord, NormalClosureExitRecordDO.class, closureProjectMapper, null));
        return declarations;
    }
}
